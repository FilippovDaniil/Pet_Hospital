package com.hospital.service.impl;

import com.hospital.dto.response.PaidServiceSummaryReport;
import com.hospital.dto.response.PatientResponse;
import com.hospital.dto.response.WardOccupancyReport;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.PatientMapper;
import com.hospital.repository.*;
import com.hospital.service.AdminService;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.event.PatientEvent;
import com.hospital.config.CacheConfig;
import com.hospital.service.strategy.DischargeStrategyFactory;
import com.hospital.service.strategy.DischargeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Реализация административного сервиса.
 *
 * Этот сервис отвечает за операции, требующие привилегий администратора:
 * - Генерация отчётов по занятости палат.
 * - Генерация отчётов по платным услугам.
 * - Полная выписка пациента из больницы (со сменой статуса).
 *
 * Ключевые паттерны, применённые в этом сервисе:
 * - Кэширование отчётов (@Cacheable / @CacheEvict) — отчёты дорогостоящие,
 *   их результаты кэшируются и сбрасываются при изменении данных.
 * - Паттерн Strategy (DischargeStrategyFactory) — тип выписки определяется
 *   в рантайме, а не через if-else цепочку.
 * - Domain Events (EventPublisher) — уведомление системы о выписке пациента.
 *
 * @Transactional(readOnly = true) на классе — методы чтения (отчёты) оптимизированы
 * для read-only транзакций. Метод dischargePatient переопределяет это.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final PatientRepository patientRepository;
    private final WardRepository wardRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientPaidServiceRepository ppsRepository;
    private final PatientDoctorHistoryRepository doctorHistoryRepository;
    private final WardOccupationHistoryRepository wardHistoryRepository;
    private final PatientMapper patientMapper;

    /**
     * DischargeStrategyFactory — фабрика стратегий выписки (паттерн Strategy + Factory).
     * Вместо switch/if-else по DischargeType здесь делегируем выбор стратегии фабрике.
     * Каждая стратегия (например, HomeDischargeStrategy, DeceasedDischargeStrategy)
     * по-своему изменяет статус пациента и другие поля.
     * Добавление нового типа выписки требует только новой реализации стратегии —
     * AdminServiceImpl не нужно трогать (принцип Open/Closed).
     */
    private final DischargeStrategyFactory strategyFactory;

    private final EventPublisher eventPublisher;

    /**
     * Отчёт по занятости палат, сгруппированный по отделениям.
     *
     * @Cacheable(CacheConfig.WARD_OCCUPANCY) — кэширует результат метода.
     * Как это работает:
     * 1. При первом вызове Spring проверяет кэш — там пусто.
     * 2. Выполняется метод, результат сохраняется в кэш с ключом по параметрам.
     * 3. При следующих вызовах Spring возвращает данные ИЗ КЭША, не выполняя метод.
     *
     * Почему кэшируем этот отчёт?
     * - Отчёт агрегирует данные по всем палатам и отделениям — это дорогой запрос.
     * - Данные меняются редко (госпитализация/выписка — не частые события).
     * - Устаревший кэш на несколько минут/секунд приемлем для административного отчёта.
     *
     * Когда кэш инвалидируется? При вызове dischargePatient() (см. @CacheEvict там).
     * CacheConfig.WARD_OCCUPANCY — строковая константа с именем кэша, вынесенная
     * в конфигурацию, чтобы не дублировать "магическую строку" в нескольких местах.
     */
    @Override
    @Cacheable(CacheConfig.WARD_OCCUPANCY)
    public List<WardOccupancyReport> getWardOccupancyReport() {
        // Загружаем все палаты одним запросом с JOIN FETCH по отделению.
        List<Ward> wards = wardRepository.findAllWithDepartment();

        // Группируем палаты по ID отделения.
        // Collectors.groupingBy — создаёт Map<Long, List<Ward>>,
        // где ключ = department_id, значение = список палат этого отделения.
        Map<Long, List<Ward>> byDepartment = wards.stream()
                .collect(Collectors.groupingBy(w -> w.getDepartment().getId()));

        // Для каждого отделения формируем строку отчёта.
        return byDepartment.entrySet().stream()
                .map(entry -> {
                    Long deptId = entry.getKey();
                    List<Ward> deptWards = entry.getValue();
                    // Название отделения берём из первой палаты (все палаты принадлежат одному отделению).
                    String deptName = deptWards.get(0).getDepartment().getName();

                    // Формируем список строк по каждой палате отделения.
                    List<WardOccupancyReport.WardOccupancyItem> items = deptWards.stream()
                            .map(w -> WardOccupancyReport.WardOccupancyItem.builder()
                                    .wardId(w.getId())
                                    .wardNumber(w.getWardNumber())
                                    .capacity(w.getCapacity())          // вместимость
                                    .occupied(w.getCurrentOccupancy())  // занято
                                    .free(w.freeSlots())                // свободно (capacity - occupied)
                                    .build())
                            .collect(Collectors.toList());

                    // Итоговые суммы по отделению.
                    // mapToInt + sum() — специализированный стрим для примитивного int,
                    // работает эффективнее чем Stream<Integer> с boxing/unboxing.
                    int totalCap = deptWards.stream().mapToInt(Ward::getCapacity).sum();
                    int totalOcc = deptWards.stream().mapToInt(Ward::getCurrentOccupancy).sum();

                    return WardOccupancyReport.builder()
                            .departmentId(deptId)
                            .departmentName(deptName)
                            .wards(items)
                            .totalCapacity(totalCap)
                            .totalOccupied(totalOcc)
                            .totalFree(totalCap - totalOcc)
                            .build();
                })
                // Сортируем по ID отделения для предсказуемого порядка в отчёте.
                .sorted(Comparator.comparing(WardOccupancyReport::getDepartmentId))
                .collect(Collectors.toList());
    }

    /**
     * Отчёт по платным услугам, сгруппированный по пациентам.
     *
     * @Cacheable(CacheConfig.SERVICES_SUMMARY) — кэшируем результат.
     * Аналогично отчёту по палатам: дорогой запрос с агрегацией, результат
     * кэшируется и инвалидируется при изменении данных через @CacheEvict.
     *
     * Отчёт показывает для каждого пациента:
     * - Количество назначенных платных услуг.
     * - Суммарную стоимость услуг.
     * - Общую сумму по всем пациентам (grandTotal).
     */
    @Override
    @Cacheable(CacheConfig.SERVICES_SUMMARY)
    public PaidServiceSummaryReport getPaidServicesSummary() {
        // Загружаем все записи PatientPaidService (связка пациент↔услуга).
        // В production-системе здесь лучше использовать кастомный запрос с агрегацией на стороне БД.
        List<PatientPaidService> all = ppsRepository.findAll();

        // Группируем по ID пациента.
        Map<Long, List<PatientPaidService>> byPatient = all.stream()
                .collect(Collectors.groupingBy(pps -> pps.getPatient().getId()));

        // Формируем сводку по каждому пациенту.
        List<PaidServiceSummaryReport.PatientSummary> summaries = byPatient.entrySet().stream()
                .map(entry -> {
                    List<PatientPaidService> services = entry.getValue();

                    // Суммируем стоимость всех услуг пациента.
                    // reduce(ZERO, BigDecimal::add) — fold-операция: начинаем с нуля
                    // и последовательно прибавляем каждую цену.
                    // BigDecimal — используется для денег (нет погрешности округления как у float/double).
                    BigDecimal total = services.stream()
                            .map(pps -> pps.getPaidService().getPrice())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return PaidServiceSummaryReport.PatientSummary.builder()
                            .patientId(entry.getKey())
                            .patientName(services.get(0).getPatient().getFullName()) // имя из первой записи
                            .total(total)
                            .serviceCount(services.size())
                            .build();
                })
                .sorted(Comparator.comparing(PaidServiceSummaryReport.PatientSummary::getPatientId))
                .collect(Collectors.toList());

        // Общая сумма по всем пациентам — ещё одна fold-операция.
        BigDecimal grandTotal = summaries.stream()
                .map(PaidServiceSummaryReport.PatientSummary::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PaidServiceSummaryReport.builder()
                .byPatient(summaries)
                .grandTotal(grandTotal)
                .build();
    }

    /**
     * Полная выписка пациента из больницы (административная операция).
     *
     * Это самый сложный метод AdminService. Выполняется в одной транзакции и включает:
     * 1. Освобождение палаты (если пациент в ней).
     * 2. Закрытие истории назначения врача.
     * 3. Применение стратегии выписки (смена статуса пациента).
     * 4. Инвалидацию кэша отчётов.
     * 5. Публикацию события PATIENT_DISCHARGED.
     *
     * @Transactional — полная транзакция с поддержкой записи.
     *            Переопределяет readOnly=true уровня класса.
     *
     * @CacheEvict(cacheNames = {...}, allEntries = true) — сбрасывает кэши отчётов.
     *            Почему нужно сбрасывать кэш?
     *            После выписки данные по занятости палат и услугам изменились.
     *            Если оставить старый кэш — администратор увидит устаревший отчёт.
     *            allEntries = true — удаляем ВСЕ записи из кэша (не только по ключу),
     *            потому что выписка влияет на агрегированные данные всего отчёта.
     *            Порядок выполнения: @CacheEvict срабатывает ПОСЛЕ успешного выполнения метода.
     *            Это гарантирует, что кэш сброшен только при успешной выписке.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.WARD_OCCUPANCY, CacheConfig.SERVICES_SUMMARY}, allEntries = true)
    public PatientResponse dischargePatient(Long patientId, DischargeType dischargeType) {
        // Загружаем активного пациента. Неактивных выписать нельзя.
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // Шаг 1: Освобождаем палату, если пациент в ней находится.
        // Проверка != null защищает от попытки выписать пациента, который не в палате.
        if (patient.getCurrentWard() != null) {
            Ward ward = patient.getCurrentWard();

            // Уменьшаем счётчик занятых мест в палате.
            // Math.max(0, ...) — защитное программирование от отрицательного счётчика.
            ward.setCurrentOccupancy(Math.max(0, ward.getCurrentOccupancy() - 1));

            // Закрываем историю пребывания в палате (проставляем дату выписки).
            // ifPresent() — безопасная обработка Optional (нет NPE).
            wardHistoryRepository.findByPatientIdAndDischargedAtIsNull(patientId)
                    .ifPresent(h -> {
                        h.setDischargedAt(LocalDateTime.now());
                        wardHistoryRepository.save(h);
                    });

            // Отвязываем пациента от палаты.
            patient.setCurrentWard(null);
        }

        // Шаг 2: Закрываем историю назначения врача (если врач назначен).
        // При выписке врач больше не ведёт пациента — закрываем активную запись истории.
        if (patient.getCurrentDoctor() != null) {
            doctorHistoryRepository.findByPatientIdAndAssignedToIsNull(patientId)
                    .ifPresent(h -> {
                        h.setAssignedTo(LocalDateTime.now());
                        doctorHistoryRepository.save(h);
                    });
        }

        // Шаг 3: Применяем стратегию выписки (паттерн Strategy).
        // DischargeType — перечисление возможных типов выписки:
        //   HOME     → пациент выписан домой (статус = DISCHARGED_HOME)
        //   TRANSFER → переведён в другое учреждение (статус = TRANSFERRED)
        //   DECEASED → пациент скончался (статус = DECEASED)
        // strategyFactory.getStrategy(dischargeType) — находит нужную реализацию Strategy.
        // strategy.discharge(patient) — изменяет статус пациента и другие поля в соответствии
        // с типом выписки. Логика каждого типа инкапсулирована в своём классе-стратегии.
        // Это гораздо лучше, чем switch(dischargeType) { case HOME: ...; case DECEASED: ... }
        // — добавление нового типа не требует изменения этого метода.
        strategyFactory.getStrategy(dischargeType).discharge(patient);
        patientRepository.save(patient);

        // Шаг 4: Публикуем событие о выписке пациента.
        // Это @Transactional(MANDATORY) метод — он выполняется в ТЕКУЩЕЙ транзакции.
        // Если транзакция откатится (исключение после этой строки) — событие не будет
        // зафиксировано (зависит от реализации EventPublisher: Outbox, Spring Events и т.д.).
        // Такой подход обеспечивает согласованность: данные и события меняются атомарно.
        eventPublisher.publishPatientEvent(PatientEvent.builder()
                .eventId(UUID.randomUUID().toString())  // уникальный ID события
                .eventType("PATIENT_DISCHARGED")         // тип события для подписчиков
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .newStatus(patient.getStatus())          // новый статус после применения стратегии
                .build());

        log.info("Patient {} discharged with type={}", patientId, dischargeType);
        return patientMapper.toResponse(patient);
    }
}
