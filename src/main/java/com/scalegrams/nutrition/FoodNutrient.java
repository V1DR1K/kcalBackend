package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.scalegrams.catalog.Food;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "food_nutrient", uniqueConstraints = @UniqueConstraint(columnNames = {"food_id", "nutrient_code"}))
@Getter @Setter @NoArgsConstructor
public class FoodNutrient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "food_id") private Food food;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "nutrient_code") private NutrientDefinition definition;
    @Column(name = "nutrient_value") private BigDecimal value;
    @Enumerated(EnumType.STRING) private NutrientSource source;
    @Enumerated(EnumType.STRING) private NutrientStatus status;
    private String externalReference;
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
