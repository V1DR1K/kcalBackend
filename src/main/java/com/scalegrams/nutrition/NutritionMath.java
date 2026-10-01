package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.scalegrams.common.BadRequestException;

/** Unknown values stay unknown; totals of known contributions are explicit. */
public final class NutritionMath {
    private NutritionMath() {}
    public static BigDecimal scale(BigDecimal value) { return value == null ? null : value.setScale(1, RoundingMode.HALF_UP); }
    public static BigDecimal scaled(BigDecimal value, BigDecimal ratio) { return value == null ? null : scale(value.multiply(ratio)); }
    public static BigDecimal add(BigDecimal left, BigDecimal right) { return left == null || right == null ? null : left.add(right); }
    public static Integer calories(BigDecimal protein, BigDecimal carbs, BigDecimal fat) {
        if (protein == null || carbs == null || fat == null) return null;
        try { return protein.multiply(BigDecimal.valueOf(4)).add(carbs.multiply(BigDecimal.valueOf(4))).add(fat.multiply(BigDecimal.valueOf(9))).setScale(0, RoundingMode.HALF_UP).intValueExact(); }
        catch (ArithmeticException failure) { throw new BadRequestException("Los valores nutricionales exceden el rango permitido."); }
    }
}
