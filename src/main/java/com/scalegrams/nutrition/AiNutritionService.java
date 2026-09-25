package com.scalegrams.nutrition;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalegrams.nutrition.GeminiNutritionClient.AiNutritionResult;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateResponse;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateUsageResponse;
import com.scalegrams.nutrition.NutritionDtos.AiTranscriptionResponse;
import com.scalegrams.nutrition.NutritionDtos.AiRegistrationResponse;
import com.scalegrams.nutrition.NutritionDtos.ConfirmAiRegistrationRequest;
import com.scalegrams.nutrition.NutritionDtos.RefineAiEstimateRequest;
import com.scalegrams.user.AppUser;

@Service
public class AiNutritionService {
    private static final List<String> ACCEPTED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> ACCEPTED_AUDIO_TYPES = List.of("audio/aac", "audio/m4a", "audio/mp4", "audio/mpeg", "audio/ogg", "audio/wav", "audio/webm");

    private final AiEstimateUsageRepository usages;
    private final GeminiNutritionClient gemini;
    private final AiNutritionProperties properties;
    private final AiFoodMatcher foodMatcher;
    private final AiCaptureRepository captures;
    private final JevNutritionClient jev;
    private final ObjectMapper objectMapper;
    private final NutritionService nutritionService;

    public AiNutritionService(AiEstimateUsageRepository usages, GeminiNutritionClient gemini,
            AiNutritionProperties properties, AiFoodMatcher foodMatcher, AiCaptureRepository captures,
            JevNutritionClient jev, ObjectMapper objectMapper, NutritionService nutritionService) {
        this.usages = usages;
        this.gemini = gemini;
        this.properties = properties;
        this.foodMatcher = foodMatcher;
        this.captures = captures;
        this.jev = jev;
        this.objectMapper = objectMapper;
        this.nutritionService = nutritionService;
    }

    @Transactional(readOnly = true)
    public AiEstimateUsageResponse usage(AppUser user) {
        AiEstimateUsage usage = usages.findByUserAndUsageDate(user, LocalDate.now()).orElse(null);
        int used = usage == null ? 0 : usage.getUsedCount();
        boolean available = properties.isEnabled() && properties.getGeminiApiKey() != null && !properties.getGeminiApiKey().isBlank();
        OffsetDateTime blockedUntil = usage == null ? null : usage.getBlockedUntil();
        boolean blocked = blockedUntil != null && blockedUntil.isAfter(OffsetDateTime.now());
        String status = !available ? "La estimación por foto no está disponible." : blocked
                ? "Gemini informó que su cuota está agotada temporalmente."
                : "Sin límite interno. Gemini no expone un saldo gratuito exacto.";
        return new AiEstimateUsageResponse(available, used, blocked ? blockedUntil : null, status);
    }

    public AiEstimateResponse analyze(AppUser user, MultipartFile image, String context) {
        return analyze(user, image, context, AiCaptureTarget.RECIPE);
    }

    public AiEstimateResponse analyze(AppUser user, MultipartFile image, String context, AiCaptureTarget targetType) {
        AiCaptureTarget target = targetType == null ? AiCaptureTarget.RECIPE : targetType;
        return estimate(user, image, context, target,
                (content, contentType, normalizedContext) -> gemini.analyze(content, contentType, normalizedContext, target));
    }

    public AiEstimateResponse refine(AppUser user, MultipartFile image, String context, RefineAiEstimateRequest request) {
        return refine(user, image, context, request, AiCaptureTarget.RECIPE);
    }

    public AiEstimateResponse refine(AppUser user, MultipartFile image, String context,
            RefineAiEstimateRequest request, AiCaptureTarget targetType) {
        String correction = normalizeCorrection(request.correction());
        AiCaptureTarget target = targetType == null ? AiCaptureTarget.RECIPE : targetType;
        return estimate(user, image, context, target,
                (content, contentType, normalizedContext) -> gemini.refine(content, contentType, normalizedContext,
                        request.currentEstimate(), correction, target));
    }

    private AiEstimateResponse estimate(AppUser user, MultipartFile image, String context, AiCaptureTarget targetType,
            EstimateOperation operation) {
        if (!properties.isEnabled() || properties.getGeminiApiKey() == null || properties.getGeminiApiKey().isBlank()) {
            throw new BadRequestException("La estimación por foto no está disponible por el momento.");
        }
        if (image == null || image.isEmpty()) throw new BadRequestException("Seleccioná una foto de la comida.");
        String contentType = image.getContentType();
        if (!ACCEPTED_TYPES.contains(contentType)) throw new BadRequestException("Usá una foto JPEG, PNG o WebP.");
        if (image.getSize() > properties.getMaxImageBytes()) throw new BadRequestException("La foto es demasiado grande. Probá con una imagen más liviana.");

        LocalDate date = LocalDate.now();
        AiEstimateUsage usage = usages.findByUserAndUsageDate(user, date).orElseGet(() -> {
            AiEstimateUsage next = new AiEstimateUsage();
            next.setUser(user);
            next.setUsageDate(date);
            return next;
        });
        if (usage.getBlockedUntil() != null && usage.getBlockedUntil().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Gemini alcanzó su cuota disponible. Probá nuevamente más tarde.");
        }
        if (properties.getDailyLimit() > 0 && usage.getUsedCount() >= properties.getDailyLimit()) {
            throw new BadRequestException("Alcanzaste el límite diario de estimaciones. Probá nuevamente mañana.");
        }
        usage.setUsedCount(usage.getUsedCount() + 1);
        usages.save(usage);

        try {
            AiNutritionResult result = operation.estimate(image.getBytes(), contentType, normalizeContext(context));
            List<AiEstimateItem> items = result.items().stream()
                    .map(item -> new AiEstimateItem(item.name(), item.estimatedGrams(), item.category(), item.preparation(), item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients()))
                    .toList();
            items = foodMatcher.enrich(items);
            usage.setBlockedUntil(null);
            usage.setProviderStatus(null);
            usages.save(usage);
            var decision = jev.classify(targetType, result).orElse(null);
            AiEstimateResponse draft = new AiEstimateResponse(null, targetType, result.name(), result.description(),
                    result.confidence(), result.assumptions(), items, usage(user), decision);
            AiCapture capture = new AiCapture();
            capture.setUser(user);
            capture.setTargetType(targetType);
            capture.setDraftJson(objectMapper.writeValueAsString(draft));
            if (decision != null) capture.setJevDecisionJson(objectMapper.writeValueAsString(decision));
            capture = captures.save(capture);
            return new AiEstimateResponse(capture.getId(), targetType, draft.name(), draft.description(),
                    draft.confidence(), draft.assumptions(), draft.items(), draft.usage(), decision);
        } catch (AiQuotaExceededException ex) {
            usage.setBlockedUntil(ex.getRetryAt());
            usage.setProviderStatus("Gemini informó cuota agotada.");
            usages.save(usage);
            throw new BadRequestException("Gemini alcanzó su cuota disponible. Probá nuevamente cuando se renueve.");
        } catch (Exception ex) {
            usage.setUsedCount(Math.max(0, usage.getUsedCount() - 1));
            usages.save(usage);
            if (ex instanceof BadRequestException badRequest) throw badRequest;
            throw new BadRequestException("No se pudo analizar la foto. Intentá nuevamente en unos minutos.");
        }
    }

    @Transactional
    public AiRegistrationResponse confirmRegistration(AppUser user, ConfirmAiRegistrationRequest request) {
        AiCapture capture = captures.findOwnedForUpdate(request.captureId(), user)
                .orElseThrow(() -> new NotFoundException("Captura asistida no encontrada."));
        if (capture.getStatus() == AiCaptureStatus.CONFIRMED && capture.getConfirmedLogId() != null) {
            return new AiRegistrationResponse(capture.getTargetType(),
                    nutritionService.findOwnedFoodLog(user, capture.getConfirmedLogId()));
        }
        if (capture.getStatus() != AiCaptureStatus.DRAFT) {
            throw new BadRequestException("Esta captura ya no se puede confirmar.");
        }
        if (capture.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("La captura venció. Analizá las fotos nuevamente.");
        }
        var log = nutritionService.confirmAiRegistration(user, capture.getTargetType(), request,
                "ai-capture:" + capture.getId());
        capture.setStatus(AiCaptureStatus.CONFIRMED);
        capture.setConfirmedLogId(log.id());
        captures.save(capture);
        return new AiRegistrationResponse(capture.getTargetType(), log);
    }

    @Transactional(readOnly = true)
    public AiTranscriptionResponse transcribe(AppUser user, MultipartFile audio) {
        if (!properties.isEnabled() || properties.getGeminiApiKey() == null || properties.getGeminiApiKey().isBlank()) {
            throw new BadRequestException("La estimación por foto no está disponible por el momento.");
        }
        if (audio == null || audio.isEmpty()) throw new BadRequestException("Grabá una descripción breve de la comida.");
        String contentType = normalizeContentType(audio.getContentType());
        if (!ACCEPTED_AUDIO_TYPES.contains(contentType)) throw new BadRequestException("Usá una nota de audio compatible.");
        if (audio.getSize() > properties.getMaxAudioBytes()) throw new BadRequestException("La nota de audio es demasiado larga. Probá con una descripción más breve.");
        try {
            return new AiTranscriptionResponse(gemini.transcribe(audio.getBytes(), contentType));
        } catch (Exception ex) {
            if (ex instanceof BadRequestException badRequest) throw badRequest;
            throw new BadRequestException("No se pudo transcribir la nota. Intentá nuevamente.");
        }
    }

    private static String normalizeContentType(String value) {
        return value == null ? "" : value.split(";", 2)[0].trim().toLowerCase();
    }

    private static String normalizeContext(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 240) throw new BadRequestException("La descripción puede tener hasta 240 caracteres.");
        return normalized;
    }

    private static String normalizeCorrection(String value) {
        if (value == null) throw new BadRequestException("Escribí una corrección para la estimación.");
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) throw new BadRequestException("Escribí una corrección para la estimación.");
        if (normalized.length() > 240) throw new BadRequestException("La corrección puede tener hasta 240 caracteres.");
        return normalized;
    }

    @FunctionalInterface
    private interface EstimateOperation {
        AiNutritionResult estimate(byte[] image, String contentType, String context) throws Exception;
    }

}
