package com.scalegrams.training;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.ConflictException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.common.PageResponse;
import com.scalegrams.common.PaginationProperties;
import com.scalegrams.training.TrainingDtos.AnnulCardioServiceRequest;
import com.scalegrams.training.TrainingDtos.CardioDaySummaryResponse;
import com.scalegrams.training.TrainingDtos.CardioRecordResponse;
import com.scalegrams.training.TrainingDtos.CardioServiceResponse;
import com.scalegrams.training.TrainingDtos.CardioSummaryResponse;
import com.scalegrams.training.TrainingDtos.CreateCardioServiceRequest;
import com.scalegrams.training.TrainingDtos.WeeklyCardioSummaryResponse;
import com.scalegrams.training.TrainingDtos.UpdateCardioServiceRequest;
import com.scalegrams.training.TrainingDtos.UpsertCardioRecordRequest;
import com.scalegrams.user.AppUser;
import com.scalegrams.user.UserRepository;

@Service
public class TrainingCardioService {
    private static final BigDecimal STEP_LENGTH_FACTOR = new BigDecimal("0.415");

    private final TrainingCardioRecordRepository cardioRecords;
    private final TrainingCardioServiceEventRepository cardioServices;
    private final UserRepository users;
    private final PaginationProperties pagination;
    private final TrainingProperties trainingProperties;

    public TrainingCardioService(TrainingCardioRecordRepository cardioRecords,
            TrainingCardioServiceEventRepository cardioServices, UserRepository users,
            PaginationProperties pagination, TrainingProperties trainingProperties) {
        this.cardioRecords = cardioRecords;
        this.cardioServices = cardioServices;
        this.users = users;
        this.pagination = pagination;
        this.trainingProperties = trainingProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<CardioRecordResponse> cardio(AppUser user, int page, int size) {
        Page<TrainingCardioRecord> result = cardioRecords.findByUser(user,
                pagination.pageRequest(page, size, Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))));
        return PageResponse.from(result.map(this::toCardioRecordResponse));
    }

    @Transactional
    public CardioRecordResponse createCardio(AppUser user, UpsertCardioRecordRequest request) {
        TrainingCardioRecord record = new TrainingCardioRecord();
        record.setUser(user);
        applyCardioRecord(record, request);
        return toCardioRecordResponse(cardioRecords.saveAndFlush(record));
    }

    @Transactional
    public CardioRecordResponse updateCardio(AppUser user, Long id, UpsertCardioRecordRequest request) {
        TrainingCardioRecord record = cardioRecords.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Registro de cardio no encontrado."));
        applyCardioRecord(record, request);
        return toCardioRecordResponse(record);
    }

    @Transactional
    public void deleteCardio(AppUser user, Long id) {
        TrainingCardioRecord record = cardioRecords.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Registro de cardio no encontrado."));
        cardioRecords.delete(record);
    }

    @Transactional
    public CardioServiceResponse createCardioService(AppUser user, CreateCardioServiceRequest request) {
        lockCardioServiceOwner(user);
        validateCardioService(user, null, request.equipment(), request.servicedAt(), request.notes());
        TrainingCardioServiceEvent service = new TrainingCardioServiceEvent();
        service.setUser(user);
        service.setEquipment(request.equipment());
        service.setServicedAt(request.servicedAt());
        service.setNotes(blankToNull(request.notes()));
        return toCardioServiceResponse(cardioServices.saveAndFlush(service));
    }

    @Transactional(readOnly = true)
    public PageResponse<CardioServiceResponse> cardioServices(AppUser user, TrainingEquipment equipment, int page, int size) {
        var pageable = pagination.pageRequest(page, size, Sort.by(Sort.Order.desc("servicedAt"), Sort.Order.desc("id")));
        var result = equipment == null ? cardioServices.findByUser(user, pageable)
                : cardioServices.findByUserAndEquipment(user, equipment, pageable);
        return PageResponse.from(result.map(this::toCardioServiceResponse));
    }

    @Transactional
    public CardioServiceResponse updateCardioService(AppUser user, Long id, UpdateCardioServiceRequest request) {
        lockCardioServiceOwner(user);
        var event = ownedCardioService(user, id);
        if (event.getAnnulledAt() != null) throw new ConflictException("Un mantenimiento anulado no se puede editar.");
        requireCardioServiceVersion(event, request.version());
        validateCardioService(user, id, request.equipment(), request.servicedAt(), request.notes());
        event.setEquipment(request.equipment());
        event.setServicedAt(request.servicedAt());
        event.setNotes(blankToNull(request.notes()));
        event.setUpdatedAt(OffsetDateTime.now());
        return toCardioServiceResponse(cardioServices.saveAndFlush(event));
    }

    @Transactional
    public CardioServiceResponse annulCardioService(AppUser user, Long id, AnnulCardioServiceRequest request) {
        lockCardioServiceOwner(user);
        var event = ownedCardioService(user, id);
        if (event.getAnnulledAt() != null) return toCardioServiceResponse(event);
        requireCardioServiceVersion(event, request.version());
        event.setAnnulledAt(OffsetDateTime.now());
        event.setAnnulledByUserId(user.getId());
        event.setAnnulmentReason(blankToNull(request.reason()));
        event.setUpdatedAt(event.getAnnulledAt());
        return toCardioServiceResponse(cardioServices.saveAndFlush(event));
    }

    @Transactional(readOnly = true)
    public CardioSummaryResponse cardioSummary(AppUser user) {
        TrainingEquipment equipment = TrainingEquipment.TREADMILL;
        TrainingCardioServiceEvent latestService = cardioServices
                .findFirstByUserAndEquipmentAndAnnulledAtIsNullOrderByServicedAtDescIdDesc(user, equipment).orElse(null);
        long totalMinutes = latestService == null ? cardioRecords.sumDuration(user, equipment)
                : cardioRecords.sumDurationAfter(user, equipment, latestService.getServicedAt());
        BigDecimal totalDistance = latestService == null ? cardioRecords.sumDistance(user, equipment)
                : cardioRecords.sumDistanceAfter(user, equipment, latestService.getServicedAt());
        long remainingMinutes = Math.max(1200L - totalMinutes, 0L);
        BigDecimal allTimeDistance = cardioRecords.sumDistance(user, equipment);
        return new CardioSummaryResponse(equipment, 1200, totalMinutes, remainingMinutes, totalMinutes >= 1200L,
                totalDistance, estimatedSteps(allTimeDistance, user.getHeightCm()), user.getHeightCm(),
                latestService == null ? null : toCardioServiceResponse(latestService));
    }

    @Transactional(readOnly = true)
    public WeeklyCardioSummaryResponse cardioWeekly(AppUser user, LocalDate date, String timeZone) {
        ZoneId zone = trainingProperties.resolveTimeZone(timeZone);
        LocalDate anchor = date == null ? LocalDate.now(zone) : date;
        LocalDate from = anchor.minusDays(6);
        LocalDate to = anchor;
        OffsetDateTime fromInstant = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime toInstant = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<TrainingCardioRecord> records = cardioRecords
                .findByUserAndEquipmentAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(user,
                        TrainingEquipment.TREADMILL, fromInstant, toInstant);
        Map<LocalDate, List<TrainingCardioRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(record -> record.getRecordedAt().atZoneSameInstant(zone).toLocalDate()));
        List<CardioDaySummaryResponse> days = from.datesUntil(to.plusDays(1)).map(day -> {
            List<TrainingCardioRecord> dayRecords = byDate.getOrDefault(day, List.of());
            BigDecimal distance = dayRecords.stream().map(TrainingCardioRecord::getDistanceKm)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CardioDaySummaryResponse(day, distance, estimatedSteps(distance, user.getHeightCm()), dayRecords.size());
        }).toList();
        BigDecimal totalDistance = records.stream().map(TrainingCardioRecord::getDistanceKm)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WeeklyCardioSummaryResponse(from, to, days, totalDistance,
                estimatedSteps(totalDistance, user.getHeightCm()), user.getHeightCm() != null);
    }

    private void lockCardioServiceOwner(AppUser user) {
        users.findByIdForUpdate(user.getId()).orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
    }

    private TrainingCardioServiceEvent ownedCardioService(AppUser user, Long id) {
        return cardioServices.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Mantenimiento no encontrado."));
    }

    private void requireCardioServiceVersion(TrainingCardioServiceEvent event, Long version) {
        if (!java.util.Objects.equals(event.getVersion(), version)) {
            throw new ConflictException("El mantenimiento cambió. Recargá el historial antes de volver a editarlo.");
        }
    }

    private void validateCardioService(AppUser user, Long excludedId, TrainingEquipment equipment, OffsetDateTime date,
            String notes) {
        validateNotFuture(date, "La fecha de mantenimiento no puede ser futura.");
        if (cardioServices.existsDuplicate(user, equipment, date, blankToNull(notes), excludedId)) {
            throw new ConflictException("Ya existe un mantenimiento con el mismo equipo, fecha y notas.");
        }
    }

    private void applyCardioRecord(TrainingCardioRecord record, UpsertCardioRecordRequest request) {
        validateNotFuture(request.recordedAt(), "La fecha del cardio no puede ser futura.");
        TrainingEquipment equipment = request.equipment() == null ? TrainingEquipment.TREADMILL : request.equipment();
        if (equipment != TrainingEquipment.TREADMILL) {
            throw new BadRequestException("Por ahora los registros de cardio solo admiten cinta de correr.");
        }
        record.setEquipment(equipment);
        record.setRecordedAt(request.recordedAt());
        record.setDistanceKm(resolveCardioDistance(request));
        record.setDurationMinutes(request.durationMinutes());
        record.setInclined(request.inclined());
        record.setUpdatedAt(OffsetDateTime.now());
    }

    private void validateNotFuture(OffsetDateTime timestamp, String message) {
        if (timestamp.isAfter(OffsetDateTime.now())) throw new BadRequestException(message);
    }

    private CardioRecordResponse toCardioRecordResponse(TrainingCardioRecord record) {
        return new CardioRecordResponse(record.getId(), record.getEquipment(), record.getRecordedAt(), record.getDistanceKm(),
                record.getDurationMinutes(), record.isInclined(), speedKmh(record.getDistanceKm(), record.getDurationMinutes()),
                estimatedSteps(record.getDistanceKm(), record.getUser().getHeightCm()), record.getCreatedAt(), record.getUpdatedAt());
    }

    private BigDecimal speedKmh(BigDecimal distanceKm, int durationMinutes) {
        if (distanceKm == null || durationMinutes <= 0) return null;
        return distanceKm.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(durationMinutes), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveCardioDistance(UpsertCardioRecordRequest request) {
        boolean hasSpeed = request.speedKmh() != null;
        boolean hasDistance = request.distanceKm() != null;
        if (hasSpeed == hasDistance) throw new BadRequestException("Informá velocidad o distancia, junto con el tiempo.");
        if (hasSpeed) {
            return request.speedKmh().multiply(BigDecimal.valueOf(request.durationMinutes()))
                    .divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP);
        }
        return request.distanceKm().setScale(3, RoundingMode.HALF_UP);
    }

    private Long estimatedSteps(BigDecimal distanceKm, BigDecimal heightCm) {
        if (distanceKm == null || heightCm == null || heightCm.signum() <= 0) return null;
        BigDecimal stepLengthMeters = heightCm.multiply(STEP_LENGTH_FACTOR).movePointLeft(2);
        if (stepLengthMeters.signum() <= 0) return null;
        return distanceKm.movePointRight(3).divide(stepLengthMeters, 0, RoundingMode.HALF_UP).longValue();
    }

    private CardioServiceResponse toCardioServiceResponse(TrainingCardioServiceEvent service) {
        return new CardioServiceResponse(service.getId(), service.getEquipment(), service.getServicedAt(), service.getNotes(),
                service.getCreatedAt(), service.getVersion(), service.getUpdatedAt(), service.getAnnulledAt(),
                service.getAnnulledByUserId(), service.getAnnulmentReason());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
