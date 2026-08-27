package com.scalegrams.training;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TrainingEquipmentConverter implements Converter<String, TrainingEquipment> {
    @Override
    public TrainingEquipment convert(String source) {
        return TrainingEquipment.fromApiValue(source);
    }
}
