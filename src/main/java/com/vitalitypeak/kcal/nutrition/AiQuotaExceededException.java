package com.vitalitypeak.kcal.nutrition;

import java.time.OffsetDateTime;

public class AiQuotaExceededException extends RuntimeException {
    private final OffsetDateTime retryAt;

    public AiQuotaExceededException(OffsetDateTime retryAt) {
        super("Gemini alcanzó su cuota disponible. Probá nuevamente cuando se renueve.");
        this.retryAt = retryAt;
    }

    public OffsetDateTime getRetryAt() {
        return retryAt;
    }
}
