package com.scalegrams.nutrition;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.nutrition.NutritionDtos.CreateDayPresetRequest;
import com.scalegrams.nutrition.NutritionDtos.DayPresetItemRequest;
import com.scalegrams.nutrition.NutritionDtos.DayPresetResponse;
import com.scalegrams.nutrition.NutritionDtos.UpdateDayPresetRequest;
import com.scalegrams.user.AppUser;

@Service
public class DayPresetService {
    private final DayPresetRepository dayPresets;
    private final DayPresetCodec codec;
    private final NutritionService nutritionService;

    public DayPresetService(DayPresetRepository dayPresets, DayPresetCodec codec, NutritionService nutritionService) {
        this.dayPresets = dayPresets;
        this.codec = codec;
        this.nutritionService = nutritionService;
    }

    @Transactional(readOnly = true)
    public List<DayPresetResponse> list(AppUser user) {
        return dayPresets.findByUserAndDeletedAtIsNullOrderByUpdatedAtDesc(user).stream()
                .map(codec::toResponse).toList();
    }

    @Transactional
    public DayPresetResponse create(AppUser user, CreateDayPresetRequest request) {
        nutritionService.ensureDayPresetItemsAvailable(request.items(), request.acknowledgedArchivedFoodIds());
        String name = normalizedName(request.name());
        ensureNameAvailable(user, name, null);
        DayPreset preset = new DayPreset();
        preset.setUser(user);
        preset.setName(name);
        preset.setDescription(cleanDescription(request.description()));
        preset.setItemsJson(codec.write(request.items()));
        preset.setCreatedAt(OffsetDateTime.now());
        preset.setUpdatedAt(OffsetDateTime.now());
        return codec.toResponse(dayPresets.save(preset));
    }

    @Transactional
    public DayPresetResponse update(AppUser user, Long id, UpdateDayPresetRequest request) {
        nutritionService.ensureDayPresetItemsAvailable(request.items(), request.acknowledgedArchivedFoodIds());
        DayPreset preset = owned(user, id);
        String name = normalizedName(request.name());
        ensureNameAvailable(user, name, id);
        preset.setName(name);
        preset.setDescription(cleanDescription(request.description()));
        preset.setItemsJson(codec.write(request.items()));
        preset.setUpdatedAt(OffsetDateTime.now());
        return codec.toResponse(dayPresets.save(preset));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        DayPreset preset = owned(user, id);
        preset.setDeletedAt(OffsetDateTime.now());
        preset.setUpdatedAt(OffsetDateTime.now());
        dayPresets.save(preset);
    }

    private DayPreset owned(AppUser user, Long id) {
        return dayPresets.findByIdAndUserAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("El preset no existe."));
    }

    private String normalizedName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) throw new BadRequestException("El nombre del preset es obligatorio.");
        return name;
    }

    private String cleanDescription(String value) {
        String description = value == null ? "" : value.trim();
        return description.isBlank() ? null : description;
    }

    private void ensureNameAvailable(AppUser user, String name, Long ignoredId) {
        boolean taken = dayPresets.existsActiveName(user, name);
        if (taken && (ignoredId == null || dayPresets.findByIdAndUserAndDeletedAtIsNull(ignoredId, user)
                .map(preset -> !preset.getName().equalsIgnoreCase(name)).orElse(true))) {
            throw new BadRequestException("Ya existe un preset con ese nombre.");
        }
    }
}
