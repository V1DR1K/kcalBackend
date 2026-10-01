package com.scalegrams.nutrition;
import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
class NutritionMathTests {
 @Test void missingValuesStayUnknownAndValidZeroStaysZero() {
  assertThat(NutritionMath.scale(null)).isNull(); assertThat(NutritionMath.scaled(null, BigDecimal.TEN)).isNull();
  assertThat(NutritionMath.calories(null, BigDecimal.ZERO, BigDecimal.ZERO)).isNull();
  assertThat(NutritionMath.calories(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).isZero();
  assertThat(NutritionMath.add(BigDecimal.TEN, null)).isNull();
 }
 @Test void scalesAndCalculatesKnownValues() {
  assertThat(NutritionMath.scaled(new BigDecimal("22.5"), new BigDecimal("0.5"))).isEqualByComparingTo("11.3");
  assertThat(NutritionMath.calories(new BigDecimal("31"), BigDecimal.ZERO, new BigDecimal("3.6"))).isEqualTo(156);
 }
}
