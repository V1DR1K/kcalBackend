package com.scalegrams.training;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TrainingEquipment {
    NONE,
    BODYWEIGHT,
    BARBELL,
    DUMBBELL,
    KETTLEBELL,
    CABLE,
    MACHINE,
    SMITH_MACHINE,
    BENCH,
    PULL_UP_BAR,
    PARALLEL_BARS,
    RINGS,
    PARALLETTES,
    RESISTANCE_BAND,
    BOX,
    AB_WHEEL,
    HEX_BAR,
    SLED,
    MEDICINE_BALL,
    JUMP_ROPE,
    TREADMILL,
    STATIONARY_BIKE,
    ROWING_MACHINE,
    PLATES,
    TRX,
    FOAM_ROLLER,
    OTHER;

    @JsonCreator
    public static TrainingEquipment fromApiValue(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("BAND".equals(normalized)) return RESISTANCE_BAND;
        return valueOf(normalized);
    }

    @JsonValue
    public String toApiValue() {
        return this == RESISTANCE_BAND ? "BAND" : name();
    }
}
