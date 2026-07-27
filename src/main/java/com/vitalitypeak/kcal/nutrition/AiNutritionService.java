package com.vitalitypeak.kcal.nutrition;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.vitalitypeak.kcal.common.BadRequestException;
import com.vitalitypeak.kcal.nutrition.GeminiNutritionClient.AiNutritionResult;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AiEstimateItem;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AiEstimateResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AiEstimateUsageResponse;
import com.vitalitypeak.kcal.user.AppUser;

@Service
public class AiNutritionService {
    private static final List<String> ACCEPTED_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final AiEstimateUsageRepository usages;
    private final GeminiNutritionClient gemini;
    private final AiNutritionProperties properties;

    public AiNutritionService(AiEstimateUsageRepository usages, GeminiNutritionClient gemini,
            AiNutritionProperties properties) {
        this.usages = usages;
        this.gemini = gemini;
        this.properties = properties;
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

    @Transactional
    public AiEstimateResponse analyze(AppUser user, MultipartFile image) {
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
        usage.setUsedCount(usage.getUsedCount() + 1);
        usages.save(usage);

        try {
            AiNutritionResult result = gemini.analyze(image.getBytes(), contentType);
            List<AiEstimateItem> items = result.items().stream()
                    .map(item -> new AiEstimateItem(item.name(), item.estimatedGrams(), item.proteinGrams(), item.carbsGrams(), item.fatGrams()))
                    .toList();
            usage.setBlockedUntil(null);
            usage.setProviderStatus(null);
            usages.save(usage);
            return new AiEstimateResponse(result.name(), result.description(), result.confidence(), result.assumptions(), items, usage(user));
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

}
