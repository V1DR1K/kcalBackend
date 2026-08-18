package com.scalegrams.nutrition;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nutrient_definition")
@Getter @Setter @NoArgsConstructor
public class NutrientDefinition {
    @Id private String code;
    private String name;
    private String nutrientGroup;
    private String unit;
    private Integer displayOrder;
    private boolean visible = true;
}
