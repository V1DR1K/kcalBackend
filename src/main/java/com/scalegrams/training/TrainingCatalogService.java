package com.scalegrams.training;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.common.PageResponse;
import com.scalegrams.common.PaginationProperties;
import com.scalegrams.training.TrainingDtos.TrainingCategoryResponse;
import com.scalegrams.training.TrainingDtos.TrainingExerciseResponse;
import com.scalegrams.training.TrainingDtos.TrainingModuleResponse;
import com.scalegrams.training.TrainingDtos.UpsertExerciseRequest;
import com.scalegrams.training.TrainingDtos.UpsertTrainingCategoryRequest;
import com.scalegrams.user.AppUser;

@Service
public class TrainingCatalogService {
    private final TrainingExerciseRepository exercises;
    private final TrainingCategoryRepository categories;
    private final TrainingPlanExerciseRepository presetExercises;
    private final PaginationProperties pagination;

    public TrainingCatalogService(TrainingExerciseRepository exercises, TrainingCategoryRepository categories,
            TrainingPlanExerciseRepository presetExercises, PaginationProperties pagination) {
        this.exercises = exercises;
        this.categories = categories;
        this.presetExercises = presetExercises;
        this.pagination = pagination;
    }

    @Transactional(readOnly = true)
    public List<TrainingModuleResponse> modules() {
        return List.of(new TrainingModuleResponse(TrainingModule.GYM, "Gimnasio"),
                new TrainingModuleResponse(TrainingModule.CALISTHENICS, "Calistenia"));
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingCategoryResponse> categories(AppUser user, String query, TrainingModule module,
            boolean includeInactive, int page, int size) {
        Page<TrainingCategory> result = categories.search(user, module, query == null ? "" : query.trim(),
                includeInactive, pagination.pageRequest(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
        return PageResponse.from(result.map(this::toCategoryResponse));
    }

    @Transactional
    public TrainingCategoryResponse createCategory(AppUser user, UpsertTrainingCategoryRequest request) {
        String name = normalized(request.name());
        String normalizedName = normalizedKey(name);
        if (categories.existsOwnedName(user, request.module(), normalizedName, null)
                || categories.findSystem(request.module(), normalizedName).isPresent()) {
            throw new BadRequestException("Ya existe una categoría con ese nombre para este módulo.");
        }
        TrainingCategory category = new TrainingCategory();
        category.setOwner(user);
        category.setModule(request.module());
        category.setName(name);
        category.setNormalizedName(normalizedName);
        category.setSystemCategory(false);
        category.setActive(request.active() == null || request.active());
        return toCategoryResponse(categories.save(category));
    }

    @Transactional
    public TrainingCategoryResponse updateCategory(AppUser user, Long id, UpsertTrainingCategoryRequest request) {
        TrainingCategory category = categories.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada."));
        String name = normalized(request.name());
        String normalizedName = normalizedKey(name);
        if (categories.existsOwnedName(user, request.module(), normalizedName, id)
                || categories.findSystem(request.module(), normalizedName).isPresent()) {
            throw new BadRequestException("Ya existe una categoría con ese nombre para este módulo.");
        }
        if (category.getModule() != request.module()) {
            throw new BadRequestException("No podés cambiar el módulo de una categoría.");
        }
        category.setName(name);
        category.setNormalizedName(normalizedName);
        if (request.active() != null) category.setActive(request.active());
        category.setUpdatedAt(OffsetDateTime.now());
        return toCategoryResponse(category);
    }

    @Transactional
    public void deleteCategory(AppUser user, Long id) {
        TrainingCategory category = categories.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada."));
        OffsetDateTime now = OffsetDateTime.now();
        category.setActive(false);
        category.setDeletedAt(now);
        category.setUpdatedAt(now);
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingExerciseResponse> exercises(AppUser user, String query, TrainingModule module,
            Long categoryId, String categoryName, TrainingEquipment equipment, TrainingDifficulty difficulty,
            TrainingRegistrationType registrationType, boolean includeInactive, int page, int size) {
        Page<TrainingExercise> result = exercises.search(user, module, query == null ? "" : query.trim(), categoryId,
                categoryName == null ? null : normalizedKey(categoryName), equipment, difficulty, registrationType,
                includeInactive,
                pagination.pageRequest(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
        return PageResponse.from(result.map(this::toExerciseResponse));
    }

    public PageResponse<TrainingExerciseResponse> exercises(AppUser user, String query, TrainingModule module,
            int page, int size) {
        return exercises(user, query, module, null, null, null, null, null, false, page, size);
    }

    @Transactional(readOnly = true)
    public TrainingExerciseResponse exercise(AppUser user, Long id) {
        return toExerciseResponse(exercises.findSelectable(id, user)
                .orElseThrow(() -> new NotFoundException("Ejercicio no encontrado.")));
    }

    @Transactional
    public TrainingExerciseResponse createExercise(AppUser user, UpsertExerciseRequest request) {
        String name = normalized(request.name());
        boolean global = Boolean.TRUE.equals(request.global());
        TrainingExercise existing = global
                ? exercises.findGlobalByModuleAndName(request.module(), name).orElse(null)
                : exercises.findByOwnerAndModuleAndNameIgnoreCaseAndDeletedAtIsNull(user, request.module(), name)
                        .orElse(null);
        if (existing != null) return toExerciseResponse(existing);

        TrainingExercise exercise = new TrainingExercise();
        exercise.setOwner(global ? null : user);
        exercise.setName(name);
        exercise.setNormalizedName(normalizedKey(name));
        exercise.setModule(request.module());
        exercise.setDescription(blankToNull(request.description()));
        exercise.setCategory(resolveCategory(user, request.module(), request.categoryId(), request.category(), global));
        exercise.setGlobalExercise(global);
        exercise.setCode(resolveExerciseCode(user, request.module(), name, request.code(), null, global));
        exercise.setPrimaryMuscles(normalizedValues(request.primaryMuscles()));
        exercise.setSecondaryMuscles(normalizedValues(request.secondaryMuscles()));
        exercise.setEquipment(request.equipment() == null ? defaultEquipment(request.module()) : request.equipment());
        exercise.setDifficulty(request.difficulty() == null ? TrainingDifficulty.BEGINNER : request.difficulty());
        exercise.setRegistrationType(request.registrationType() == null ? defaultRegistrationType(request.module())
                : request.registrationType());
        exercise.setUnilateral(Boolean.TRUE.equals(request.unilateral()));
        exercise.setExternalLoad(request.externalLoad() == null ? request.module() == TrainingModule.GYM
                : request.externalLoad());
        exercise.setActive(request.active() == null || request.active());
        return toExerciseResponse(exercises.save(exercise));
    }

    @Transactional
    public TrainingExerciseResponse updateExercise(AppUser user, Long id, UpsertExerciseRequest request) {
        TrainingExercise exercise = requireExercise(user, id);
        if (exercise.isGlobalExercise()) throw new NotFoundException("El ejercicio global es de solo lectura.");
        String name = normalized(request.name());
        if (exercises.existsLiveName(user, request.module(), name, exercise.getId())) {
            throw new BadRequestException("Ya existe un ejercicio con ese nombre para este módulo.");
        }
        if (exercise.getModule() != request.module() && presetExercises.existsByExerciseAndDeletedAtIsNull(exercise)) {
            throw new BadRequestException("No podés cambiar el módulo de un ejercicio usado en una rutina.");
        }
        exercise.setName(name);
        exercise.setNormalizedName(normalizedKey(name));
        exercise.setModule(request.module());
        exercise.setDescription(blankToNull(request.description()));
        exercise.setCategory(resolveCategory(user, request.module(), request.categoryId(), request.category(), false));
        exercise.setCode(resolveExerciseCode(user, request.module(), name, request.code(), exercise.getId(), false));
        exercise.setPrimaryMuscles(normalizedValues(request.primaryMuscles()));
        exercise.setSecondaryMuscles(normalizedValues(request.secondaryMuscles()));
        if (request.equipment() != null) exercise.setEquipment(request.equipment());
        if (request.difficulty() != null) exercise.setDifficulty(request.difficulty());
        if (request.registrationType() != null) exercise.setRegistrationType(request.registrationType());
        if (request.unilateral() != null) exercise.setUnilateral(request.unilateral());
        if (request.externalLoad() != null) exercise.setExternalLoad(request.externalLoad());
        if (request.active() != null) exercise.setActive(request.active());
        exercise.setUpdatedAt(OffsetDateTime.now());
        return toExerciseResponse(exercise);
    }

    @Transactional
    public void deleteExercise(AppUser user, Long id) {
        TrainingExercise exercise = requireExercise(user, id);
        if (exercise.isGlobalExercise()) throw new NotFoundException("El ejercicio global es de solo lectura.");
        OffsetDateTime now = OffsetDateTime.now();
        exercise.setActive(false);
        exercise.setDeletedAt(now);
        exercise.setUpdatedAt(now);
    }

    TrainingExercise requireSelectableExercise(AppUser user, Long id) {
        TrainingExercise exercise = exercises.findSelectable(id, user)
                .orElseThrow(() -> new NotFoundException("Ejercicio no encontrado."));
        if (!exercise.isActive()) throw new BadRequestException("El ejercicio está archivado.");
        return exercise;
    }

    TrainingExercise requireExercise(AppUser user, Long id) {
        return exercises.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Ejercicio no encontrado."));
    }

    void validateExerciseModule(TrainingModule module, TrainingExercise exercise) {
        if (exercise.getModule() != module) {
            throw new BadRequestException("El ejercicio pertenece a otro módulo de entrenamiento.");
        }
    }

    TrainingRegistrationType effectiveRegistrationType(TrainingExercise exercise,
            TrainingRegistrationType requested) {
        return validateRegistrationType(exercise, requested);
    }

    TrainingRegistrationType validateRegistrationType(TrainingExercise exercise,
            TrainingRegistrationType requested) {
        TrainingRegistrationType actual = exercise.getRegistrationType() == null
                ? defaultRegistrationType(exercise.getModule()) : exercise.getRegistrationType();
        if (requested != null && requested != actual) {
            throw new BadRequestException("El tipo de registro no coincide con el tipo del ejercicio.");
        }
        return actual;
    }

    private TrainingCategory resolveCategory(AppUser user, TrainingModule module, Long categoryId, String legacyName,
            boolean global) {
        if (categoryId != null) {
            TrainingCategory category = categories.findSelectable(categoryId, user)
                    .orElseThrow(() -> new BadRequestException("La categoría no existe o no está disponible."));
            if (category.getModule() != module) {
                throw new BadRequestException("La categoría pertenece a otro módulo de entrenamiento.");
            }
            if (global && !category.isSystemCategory()) {
                throw new BadRequestException("Un ejercicio global debe usar una categoría base.");
            }
            if (!category.isActive()) throw new BadRequestException("La categoría está archivada.");
            return category;
        }
        if (legacyName != null && !legacyName.isBlank()) {
            String key = normalizedKey(legacyName);
            var category = categories.findSystem(module, key);
            if (!global) {
                category = category.or(() -> categories.findByOwnerAndModuleAndNormalizedNameAndDeletedAtIsNull(user,
                        module, key));
            }
            return category.filter(TrainingCategory::isActive)
                    .orElseThrow(() -> new BadRequestException("La categoría indicada no existe para este módulo."));
        }
        return categories.findSystem(module, normalizedKey("ACONDICIONAMIENTO"))
                .orElseThrow(() -> new BadRequestException("No hay una categoría predeterminada disponible."));
    }

    private String resolveExerciseCode(AppUser user, TrainingModule module, String name, String requested,
            Long excludedId, boolean global) {
        String code = blankToNull(requested);
        if (code == null) {
            String slug = normalizedKey(name).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (slug.length() > 42) slug = slug.substring(0, 42);
            String scope = global ? "global" : user.getId() == null ? "new" : user.getId().toString();
            code = "SG-" + module.name() + "-" + slug + "-" + Integer.toUnsignedString(
                    (module.name() + ":" + normalizedKey(name) + ":" + scope).hashCode(), 36);
        } else {
            code = code.toUpperCase(Locale.ROOT);
        }
        if (exercises.existsLiveCode(code, excludedId)) {
            throw new BadRequestException("Ya existe un ejercicio con ese código para este módulo.");
        }
        return code;
    }

    private static TrainingRegistrationType defaultRegistrationType(TrainingModule module) {
        return module == TrainingModule.GYM ? TrainingRegistrationType.WEIGHT_AND_REPETITIONS
                : TrainingRegistrationType.REPETITIONS;
    }

    private static TrainingEquipment defaultEquipment(TrainingModule module) {
        return module == TrainingModule.GYM ? TrainingEquipment.NONE : TrainingEquipment.BODYWEIGHT;
    }

    private TrainingExerciseResponse toExerciseResponse(TrainingExercise exercise) {
        return new TrainingExerciseResponse(exercise.getId(), exercise.getName(), exercise.getDescription(),
                exercise.getCategory().getName(), exercise.getModule(), exercise.isGlobalExercise(),
                !exercise.isGlobalExercise(), exercise.isActive(), exercise.getCreatedAt(), exercise.getUpdatedAt(),
                exercise.getCategory().getId(), exercise.getNormalizedName(), exercise.getCode(),
                List.copyOf(exercise.getPrimaryMuscles()), List.copyOf(exercise.getSecondaryMuscles()),
                exercise.getEquipment(), exercise.getDifficulty(), exercise.getRegistrationType(),
                exercise.isUnilateral(), exercise.isExternalLoad(), exercise.isSystemExercise());
    }

    private TrainingCategoryResponse toCategoryResponse(TrainingCategory category) {
        return new TrainingCategoryResponse(category.getId(), category.getName(), category.getModule(),
                category.isSystemCategory(), !category.isSystemCategory(), category.isActive(), category.getCreatedAt(),
                category.getUpdatedAt());
    }

    private static String normalized(String value) { return value.trim(); }
    private static String normalizedKey(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static Set<String> normalizedValues(List<String> values) {
        if (values == null) return new LinkedHashSet<>();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
