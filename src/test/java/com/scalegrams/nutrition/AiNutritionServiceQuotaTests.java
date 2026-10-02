package com.scalegrams.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.user.AppUser;

class AiNutritionServiceQuotaTests {
    @Test
    void reservesQuotaOnceForAnEstimateAndReturnsNoJevDecision() throws Exception {
        AiEstimateUsageRepository usages = mock(AiEstimateUsageRepository.class);
        GeminiNutritionClient gemini = mock(GeminiNutritionClient.class);
        AiFoodMatcher matcher = mock(AiFoodMatcher.class);
        AiCaptureRepository captures = mock(AiCaptureRepository.class);
        NutritionService nutrition = mock(NutritionService.class);
        AiNutritionProperties properties = new AiNutritionProperties();
        properties.setEnabled(true);
        properties.setGeminiApiKey("test-key");
        ObjectMapper mapper = new ObjectMapper();
        AiNutritionService service = new AiNutritionService(usages, gemini, properties, matcher, captures, mapper, nutrition);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(7L);
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getContentType()).thenReturn("image/jpeg");
        when(image.getSize()).thenReturn(10L);
        when(image.getBytes()).thenReturn(new byte[] {1});
        when(usages.reserve(anyLong(), any(LocalDate.class), any(OffsetDateTime.class), anyInt())).thenReturn(1);
        when(usages.findByUserAndUsageDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        var item = new GeminiNutritionClient.AiNutritionItem("Avena", BigDecimal.valueOf(100), FoodCategory.CEREAL,
                FoodPreparation.COOKED, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, java.util.Map.of());
        when(gemini.analyze(any(byte[].class), anyString(), anyString(), any())).thenReturn(
                new GeminiNutritionClient.AiNutritionResult("Avena", "", 90, List.of(), List.of(item)));
        when(matcher.enrich(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(captures.save(any(AiCapture.class))).thenAnswer(invocation -> {
            AiCapture capture = invocation.getArgument(0);
            capture.setId(UUID.randomUUID());
            return capture;
        });

        var response = service.analyze(user, image, "");

        assertThat(response.targetType()).isEqualTo(AiCaptureTarget.FOOD);
        assertThat(mapper.writeValueAsString(response)).doesNotContain("decision", "jev");
        verify(usages, times(1)).reserve(anyLong(), any(LocalDate.class), any(OffsetDateTime.class), anyInt());
        verify(gemini, times(1)).analyze(any(byte[].class), anyString(), anyString(), any());
        verify(usages, never()).release(anyLong(), any(LocalDate.class));
    }
}
