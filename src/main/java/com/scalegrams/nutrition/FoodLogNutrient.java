package com.scalegrams.nutrition;

import java.math.BigDecimal;
import com.scalegrams.nutrition.FoodLog;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "food_log_nutrient", uniqueConstraints = @UniqueConstraint(columnNames = {"food_log_id", "nutrient_code"}))
@Getter @Setter @NoArgsConstructor
public class FoodLogNutrient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "food_log_id") private FoodLog foodLog;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "nutrient_code") private NutrientDefinition definition;
    @Column(name = "nutrient_value") private BigDecimal value;
    @Enumerated(EnumType.STRING) private NutrientSource source;
    @Enumerated(EnumType.STRING) private NutrientStatus status;
}
