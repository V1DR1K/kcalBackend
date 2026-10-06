package com.scalegrams.nutrition;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.scalegrams.nutrition.NutritionDtos.AiRegistrationMatchesRequest;
import com.scalegrams.nutrition.NutritionDtos.AiRegistrationMatchesResponse;
import com.scalegrams.nutrition.NutritionDtos.AiRegistrationItemMatch;
import com.scalegrams.nutrition.NutritionDtos.AiCatalogFoodMatchResponse;
import com.scalegrams.nutrition.NutritionDtos.AiCatalogChoice;
import com.scalegrams.nutrition.NutritionDtos.AiRegistrationResolution;
import com.scalegrams.nutrition.NutritionDtos.RefineAiEstimateRequest;
import com.scalegrams.user.AppUser;

@Service
public class AiNutritionService {
    private static final Logger log = LoggerFactory.getLogger(AiNutritionService.class);
    private static final List<String> ACCEPTED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> ACCEPTED_AUDIO_TYPES = List.of("audio/aac", "audio/m4a", "audio/mp4", "audio/mpeg", "audio/ogg", "audio/wav", "audio/webm");

    private final AiEstimateUsageRepository usages;
    private final GeminiNutritionClient gemini;
    private final AiNutritionProperties properties;
    private final AiFoodMatcher foodMatcher;
    private final AiCaptureRepository captures;
    private final ObjectMapper objectMapper;
    private final NutritionService nutritionService;

    public AiNutritionService(AiEstimateUsageRepository usages, GeminiNutritionClient gemini,
            AiNutritionProperties properties, AiFoodMatcher foodMatcher, AiCaptureRepository captures,
            ObjectMapper objectMapper, NutritionService nutritionService) {
        this.usages = usages;
        this.gemini = gemini;
        this.properties = properties;
        this.foodMatcher = foodMatcher;
        this.captures = captures;
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
        int dailyLimit = properties.getDailyLimit();
        String status = !available ? "La estimación por foto no está disponible." : blocked
                ? "Gemini informó que su cuota está agotada temporalmente."
                : dailyLimit > 0 ? "Usaste " + used + " de " + dailyLimit + " estimaciones disponibles hoy."
                : "Sin límite diario interno. La disponibilidad depende de Gemini.";
        return new AiEstimateUsageResponse(available, used, dailyLimit, blocked ? blockedUntil : null, status);
    }

    public AiEstimateResponse analyze(AppUser user, MultipartFile image, String context) {
        return analyze(user, image, context, AiCaptureTarget.RECIPE);
    }

    public AiEstimateResponse analyze(AppUser user, MultipartFile image, String context, AiCaptureTarget targetType) {
        AiCaptureTarget target = targetType == null ? AiCaptureTarget.RECIPE : targetType;
        return estimate(user, image, context, target, "analysis",
                (content, contentType, normalizedContext) -> gemini.analyze(content, contentType, normalizedContext, target));
    }

    public AiEstimateResponse refine(AppUser user, MultipartFile image, String context, RefineAiEstimateRequest request) {
        return refine(user, image, context, request, AiCaptureTarget.RECIPE);
    }

    public AiEstimateResponse refine(AppUser user, MultipartFile image, String context,
            RefineAiEstimateRequest request, AiCaptureTarget targetType) {
        String correction = normalizeCorrection(request.correction());
        AiCaptureTarget target = targetType == null ? AiCaptureTarget.RECIPE : targetType;
        return estimate(user, image, context, target, "refinement",
                (content, contentType, normalizedContext) -> gemini.refine(content, contentType, normalizedContext,
                        request.currentEstimate(), correction, target));
    }

    private AiEstimateResponse estimate(AppUser user, MultipartFile image, String context, AiCaptureTarget targetType,
            String stage,
            EstimateOperation operation) {
        if (!properties.isEnabled() || properties.getGeminiApiKey() == null || properties.getGeminiApiKey().isBlank()) {
            throw new BadRequestException("La estimación por foto no está disponible por el momento.");
        }
        if (image == null || image.isEmpty()) throw new BadRequestException("Seleccioná una foto de la comida.");
        String contentType = image.getContentType();
        if (!ACCEPTED_TYPES.contains(contentType)) throw new BadRequestException("Usá una foto JPEG, PNG o WebP.");
        if (image.getSize() > properties.getMaxImageBytes()) throw new BadRequestException("La foto es demasiado grande. Probá con una imagen más liviana.");

        LocalDate date = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();
        if (usages.reserve(user.getId(), date, now, properties.getDailyLimit()) == 0) {
            AiEstimateUsage usage = usages.findByUserAndUsageDate(user, date).orElse(null);
            if (usage != null && usage.getBlockedUntil() != null && usage.getBlockedUntil().isAfter(now)) {
            throw new BadRequestException("Gemini alcanzó su cuota disponible. Probá nuevamente más tarde.");
            }
            throw new BadRequestException("Alcanzaste el límite diario de estimaciones. Probá nuevamente mañana.");
        }

        String failureStage = stage;
        try {
            AiNutritionResult result = operation.estimate(image.getBytes(), contentType, normalizeContext(context));
            log.info("AI estimate completed; requestId={} stage={}", org.slf4j.MDC.get("requestId"), stage);
            failureStage = "save";
            List<AiEstimateItem> items = result.items().stream()
                    .map(item -> new AiEstimateItem(item.name(), item.estimatedGrams(), item.category(), item.preparation(), item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients()))
                    .toList();
            items = foodMatcher.enrich(items);
            AiCaptureTarget resolvedTarget = targetFor(items.size());
            usages.updateProviderState(user.getId(), date, null, null);
            AiEstimateResponse draft = new AiEstimateResponse(null, resolvedTarget, result.name(), result.description(),
                    result.confidence(), result.assumptions(), items, usage(user));
            AiCapture capture = new AiCapture();
            capture.setUser(user);
            capture.setTargetType(resolvedTarget);
            capture.setDraftJson(objectMapper.writeValueAsString(draft));
            capture = captures.save(capture);
            log.info("AI estimate saved; requestId={} stage=save", org.slf4j.MDC.get("requestId"));
            return new AiEstimateResponse(capture.getId(), resolvedTarget, draft.name(), draft.description(),
                    draft.confidence(), draft.assumptions(), draft.items(), draft.usage());
        } catch (AiQuotaExceededException ex) {
            log.warn("AI estimate stopped by provider quota; requestId={} stage={} cause={}",
                    org.slf4j.MDC.get("requestId"), stage, ex.getClass().getSimpleName());
            usages.updateProviderState(user.getId(), date, ex.getRetryAt(), "Gemini informó cuota agotada.");
            throw new BadRequestException("Gemini alcanzó su cuota disponible. Probá nuevamente cuando se renueve.");
        } catch (Exception ex) {
            usages.release(user.getId(), date);
            log.warn("AI estimate operation failed; requestId={} stage={} cause={}",
                    org.slf4j.MDC.get("requestId"), failureStage, ex.getClass().getSimpleName());
            if (ex instanceof BadRequestException badRequest) throw badRequest;
            throw new BadRequestException("No se pudo analizar la foto. Intentá nuevamente en unos minutos.");
        }
    }

    @Transactional
    public AiRegistrationResponse confirmRegistration(AppUser user, ConfirmAiRegistrationRequest request) {
        try {
            AiCapture capture = captures.findOwnedForUpdate(request.captureId(), user)
                    .orElseThrow(() -> new NotFoundException("Captura asistida no encontrada."));
            if (capture.getStatus() == AiCaptureStatus.CONFIRMED) {
                return nutritionService.findAiRegistrationResult(user, capture.getTargetType(),
                        capture.getConfirmedFoodId(), capture.getConfirmedRecipeId(), capture.getConfirmedLogId());
            }
            if (capture.getStatus() != AiCaptureStatus.DRAFT) {
                throw new BadRequestException("Esta captura ya no se puede confirmar.");
            }
            if (capture.getExpiresAt().isBefore(OffsetDateTime.now())) {
                throw new BadRequestException("La captura venció. Analizá las fotos nuevamente.");
            }
            List<AiEstimateItem> resolvedItems = applyResolutions(request.items(), request.resolutions());
            ConfirmAiRegistrationRequest resolvedRequest = new ConfirmAiRegistrationRequest(request.captureId(),
                    request.name(), request.description(), request.mealType(), request.logDate(), request.confidence(),
                    request.addToDiary(), resolvedItems, request.acknowledgedArchivedFoodIds(), List.of());
            var registration = nutritionService.confirmAiRegistration(user, resolvedRequest,
                    "ai-capture:" + capture.getId());
            capture.setStatus(AiCaptureStatus.CONFIRMED);
            capture.setTargetType(registration.targetType());
            capture.setConfirmedFoodId(registration.food() == null ? null : registration.food().id());
            capture.setConfirmedRecipeId(registration.recipe() == null ? null : registration.recipe().id());
            capture.setConfirmedLogId(registration.log() == null ? null : registration.log().id());
            captures.save(capture);
            log.info("AI registration saved; requestId={} stage=save", org.slf4j.MDC.get("requestId"));
            return registration;
        } catch (RuntimeException ex) {
            log.warn("AI registration failed; requestId={} stage=save cause={}",
                    org.slf4j.MDC.get("requestId"), ex.getClass().getSimpleName());
            throw ex;
        }
    }

    public AiRegistrationMatchesResponse previewRegistrationMatches(AppUser user,
            AiRegistrationMatchesRequest request) {
        AiCapture capture = captures.findOwned(request.captureId(), user)
                .orElseThrow(() -> new NotFoundException("Captura asistida no encontrada."));
        validateDraftCapture(capture);
        List<AiRegistrationItemMatch> matches = new java.util.ArrayList<>(request.items().size());
        for (int index = 0; index < request.items().size(); index++) {
            AiEstimateItem item = request.items().get(index);
            var match = foodMatcher.preview(item).map(candidate -> new AiCatalogFoodMatchResponse(
                    candidate.food().getId(), candidate.food().getName(), candidate.food().getBrand(),
                    candidate.similarity(), candidate.proteinGrams(), candidate.carbsGrams(),
                    candidate.fatGrams(), candidate.macrosDiffer())).orElse(null);
            matches.add(new AiRegistrationItemMatch(index, match));
        }
        return new AiRegistrationMatchesResponse(matches);
    }

    private static void validateDraftCapture(AiCapture capture) {
        if (capture.getStatus() != AiCaptureStatus.DRAFT) {
            throw new BadRequestException("Esta captura ya no se puede confirmar.");
        }
        if (capture.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("La captura venció. Analizá las fotos nuevamente.");
        }
    }

    private List<AiEstimateItem> applyResolutions(List<AiEstimateItem> items,
            List<AiRegistrationResolution> resolutions) {
        Map<Integer, AiRegistrationResolution> byIndex = new java.util.HashMap<>();
        if (resolutions != null) {
            for (AiRegistrationResolution resolution : resolutions) {
                if (resolution.itemIndex() < 0 || resolution.itemIndex() >= items.size()
                        || byIndex.putIfAbsent(resolution.itemIndex(), resolution) != null) {
                    throw new BadRequestException("La resolución de alimentos no coincide con la estimación.");
                }
            }
        }
        if (!byIndex.isEmpty() && byIndex.size() != items.size()) {
            throw new BadRequestException("Elegí cómo guardar cada alimento con coincidencia en el catálogo.");
        }
        List<AiEstimateItem> resolved = new java.util.ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            AiEstimateItem item = items.get(index);
            AiRegistrationResolution resolution = byIndex.get(index);
            var candidate = resolution == null ? java.util.Optional.<AiFoodMatcher.CatalogMatch>empty()
                    : foodMatcher.preview(item);
            Long catalogFoodId = null;
            if (candidate.isPresent() && !candidate.get().macrosDiffer()) {
                catalogFoodId = candidate.get().food().getId();
            } else if (resolution != null && resolution.choice() == AiCatalogChoice.USE_CATALOG) {
                if (candidate.isEmpty() || !candidate.get().food().getId().equals(resolution.foodId())) {
                    throw new BadRequestException("La ficha elegida ya no coincide con este alimento. Revisá nuevamente la sugerencia.");
                }
                catalogFoodId = candidate.get().food().getId();
            } else if (resolution != null && resolution.choice() == AiCatalogChoice.KEEP_ESTIMATE
                    && candidate.isEmpty()) {
                catalogFoodId = null;
            }
            Integer catalogMatchConfidence = catalogFoodId == null ? null
                    : (int) Math.round(candidate.orElseThrow().similarity() * 100);
            resolved.add(new AiEstimateItem(item.name(), item.estimatedGrams(), item.category(), item.preparation(),
                    item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients(), catalogFoodId,
                    catalogFoodId == null ? null : "SEMANTIC", catalogMatchConfidence));
        }
        return List.copyOf(resolved);
    }

    private static AiCaptureTarget targetFor(int itemCount) {
        return itemCount > 1 ? AiCaptureTarget.RECIPE : AiCaptureTarget.FOOD;
    }

    public AiTranscriptionResponse transcribe(AppUser user, MultipartFile audio) {
        if (!properties.isEnabled() || properties.getGeminiApiKey() == null || properties.getGeminiApiKey().isBlank()) {
            throw new BadRequestException("La estimación por foto no está disponible por el momento.");
        }
        if (audio == null || audio.isEmpty()) throw new BadRequestException("Grabá una descripción breve de la comida.");
        String contentType = normalizeContentType(audio.getContentType());
        if (!ACCEPTED_AUDIO_TYPES.contains(contentType)) throw new BadRequestException("Usá una nota de audio compatible.");
        if (audio.getSize() > properties.getMaxAudioBytes()) throw new BadRequestException("La nota de audio es demasiado larga. Probá con una descripción más breve.");
        LocalDate date = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();
        if (usages.reserve(user.getId(), date, now, properties.getDailyLimit()) == 0) {
            AiEstimateUsage usage = usages.findByUserAndUsageDate(user, date).orElse(null);
            if (usage != null && usage.getBlockedUntil() != null && usage.getBlockedUntil().isAfter(now)) {
                throw new BadRequestException("Gemini alcanzó su cuota disponible. Probá nuevamente más tarde.");
            }
            throw new BadRequestException("Alcanzaste el límite diario de solicitudes asistidas. Probá nuevamente mañana.");
        }
        try {
            return new AiTranscriptionResponse(gemini.transcribe(audio.getBytes(), contentType));
        } catch (AiQuotaExceededException ex) {
            log.warn("AI transcription stopped by provider quota; requestId={} stage=transcription cause={}",
                    org.slf4j.MDC.get("requestId"), ex.getClass().getSimpleName());
            usages.updateProviderState(user.getId(), date, ex.getRetryAt(), "Gemini informó cuota agotada.");
            throw new BadRequestException("Gemini alcanzó su cuota disponible. Probá nuevamente cuando se renueve.");
        } catch (Exception ex) {
            usages.release(user.getId(), date);
            if (ex instanceof BadRequestException badRequest) throw badRequest;
            log.warn("AI transcription failed; requestId={} stage=transcription cause={}",
                    org.slf4j.MDC.get("requestId"), ex.getClass().getSimpleName());
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
