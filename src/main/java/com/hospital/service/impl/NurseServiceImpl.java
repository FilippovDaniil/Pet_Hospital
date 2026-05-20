package com.hospital.service.impl;

import com.hospital.dto.request.AdjustSupplyRequest;
import com.hospital.dto.request.CreateAssignmentRequest;
import com.hospital.dto.request.CreateSupplyRequest;
import com.hospital.dto.response.MedicalSupplyResponse;
import com.hospital.dto.response.NurseAssignmentResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.MedicalSupplyRepository;
import com.hospital.repository.NurseAssignmentRepository;
import com.hospital.repository.UserRepository;
import com.hospital.service.NurseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Реализация модуля медсестры.
 *
 * Покрывает два домена:
 *   1. Склад медикаментов (MedicalSupply) — CRUD + корректировка остатка.
 *   2. Назначения процедур клиентам (NurseAssignment) — создание, фильтрация по статусу,
 *      смена статуса, удаление.
 *
 * @Transactional(readOnly = true) на уровне класса — оптимизация для всех
 * SELECT-запросов (меньше блокировок в БД, Hibernate не отслеживает изменения).
 * Методы с записью явно переопределяют это поведение через @Transactional.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NurseServiceImpl implements NurseService {

    /**
     * Словарь «enum-ключ → русский ярлык» для категорий склада.
     * Хранится здесь (а не в enum), чтобы не добавлять в enum зависимость от UI-слоя.
     * Map.of() создаёт неизменяемую карту, что безопасно для static-поля.
     */
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "MEDICINE",   "Медикамент",   // таблетки, ампулы, сиропы
            "CONSUMABLE", "Расходник",    // шприцы, бинты, перчатки
            "EQUIPMENT",  "Оборудование"  // тонометры, термометры и т.п.
    );

    /**
     * Словарь «enum-ключ → русский ярлык» для типов процедур.
     * Используется в toAssignmentResponse() для поля procedureTypeLabel.
     */
    private static final Map<String, String> PROCEDURE_LABELS = Map.of(
            "INJECTION", "Укол",
            "PILL",      "Приём таблеток",
            "DRESSING",  "Перевязка",
            "PROCEDURE", "Процедура",
            "OTHER",     "Прочее"
    );

    /** Репозиторий медикаментов и расходников. */
    private final MedicalSupplyRepository supplyRepository;

    /** Репозиторий назначений процедур. */
    private final NurseAssignmentRepository assignmentRepository;

    /** Репозиторий пользователей — нужен для разрешения медсестры и клиента по имени/id. */
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Склад медикаментов
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает все позиции склада, отсортированные по категории и имени.
     * Сортировка на уровне БД — ORDER BY в имени метода репозитория.
     */
    @Override
    public List<MedicalSupplyResponse> getAllSupplies() {
        return supplyRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(this::toSupplyResponse) // конвертируем каждую сущность в DTO
                .toList();
    }

    /**
     * Создаёт новую позицию на складе.
     *
     * SupplyCategory.valueOf(request.getCategory()) — конвертация строки "MEDICINE"
     * в enum-константу. Если строка невалидна — бросает IllegalArgumentException (→ HTTP 400).
     */
    @Override
    @Transactional // переопределяем readOnly=true — нужна запись
    public MedicalSupplyResponse createSupply(CreateSupplyRequest request) {
        MedicalSupply supply = MedicalSupply.builder()
                .name(request.getName())
                .category(SupplyCategory.valueOf(request.getCategory())) // строка → enum
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .description(request.getDescription())
                .minQuantity(request.getMinQuantity()) // порог низкого остатка
                .build();
        return toSupplyResponse(supplyRepository.save(supply)); // сохранить и вернуть DTO
    }

    /**
     * Обновляет все поля существующей позиции.
     *
     * Паттерн «найди → обнови → сохрани»: используем JPA dirty-checking —
     * после save() Hibernate обновит только изменённые поля (UPDATE ... SET ...).
     */
    @Override
    @Transactional
    public MedicalSupplyResponse updateSupply(Long id, CreateSupplyRequest request) {
        // Найти или бросить HTTP 404.
        MedicalSupply supply = supplyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalSupply", id));
        // Обновляем все поля — включая категорию и порог минимума.
        supply.setName(request.getName());
        supply.setCategory(SupplyCategory.valueOf(request.getCategory()));
        supply.setQuantity(request.getQuantity());
        supply.setUnit(request.getUnit());
        supply.setDescription(request.getDescription());
        supply.setMinQuantity(request.getMinQuantity());
        return toSupplyResponse(supplyRepository.save(supply));
    }

    /**
     * Корректирует остаток на складе: delta > 0 — пополнение, delta < 0 — расход.
     *
     * Проверка newQty < 0 предотвращает отрицательный остаток:
     * нельзя «выдать» больше, чем есть на складе.
     */
    @Override
    @Transactional
    public MedicalSupplyResponse adjustSupply(Long id, AdjustSupplyRequest request) {
        MedicalSupply supply = supplyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalSupply", id));
        int newQty = supply.getQuantity() + request.getDelta(); // вычисляем новый остаток
        if (newQty < 0) throw new BusinessRuleException("Недостаточно остатка на складе"); // бизнес-правило
        supply.setQuantity(newQty); // обновляем поле в managed-entity
        return toSupplyResponse(supplyRepository.save(supply));
    }

    /**
     * Удаляет позицию склада.
     * findById перед deleteById — чтобы вернуть HTTP 404 для несуществующего id,
     * а не просто «сделать ничего».
     */
    @Override
    @Transactional
    public void deleteSupply(Long id) {
        supplyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("MedicalSupply", id));
        supplyRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Назначения процедур
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает назначения с опциональной фильтрацией по статусу.
     *
     * status == null или пустая строка → все назначения (findAllWithUsers).
     * status == "ACTIVE" / "DONE" / "CANCELLED" → фильтр по статусу.
     *
     * JOIN FETCH в обоих методах репозитория загружает clientUser и nurseUser
     * одним запросом — без N+1.
     */
    @Override
    public List<NurseAssignmentResponse> getAllAssignments(String status) {
        if (status != null && !status.isBlank()) {
            // Фильтр: AssignmentStatus.valueOf() конвертирует строку в enum.
            return assignmentRepository.findByStatusWithUsers(AssignmentStatus.valueOf(status)).stream()
                    .map(this::toAssignmentResponse).toList();
        }
        // Без фильтра — возвращаем все, отсортированные по дате создания (DESC).
        return assignmentRepository.findAllWithUsers().stream()
                .map(this::toAssignmentResponse).toList();
    }

    /**
     * Создаёт назначение процедуры.
     *
     * Два обязательных условия:
     *   1. Медсестра должна существовать (findByUsername).
     *   2. Клиент должен существовать И иметь роль ROLE_CLIENT.
     *      filter(u -> u.getRole() == Role.ROLE_CLIENT) — исключает врачей и других
     *      сотрудников; если роль не CLIENT — Optional.empty() → ResourceNotFoundException.
     */
    @Override
    @Transactional
    public NurseAssignmentResponse createAssignment(String nurseUsername, CreateAssignmentRequest request) {
        // Разрешаем медсестру по имени пользователя из JWT.
        User nurse = userRepository.findByUsername(nurseUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        // Разрешаем клиента по id из запроса + проверяем роль.
        User client = userRepository.findById(request.getClientUserId())
                .filter(u -> u.getRole() == Role.ROLE_CLIENT) // только клиенты могут получать назначения
                .orElseThrow(() -> new ResourceNotFoundException("Клиент", request.getClientUserId()));

        NurseAssignment assignment = NurseAssignment.builder()
                .clientUser(client)
                .nurseUser(nurse)
                .procedureType(ProcedureType.valueOf(request.getProcedureType())) // строка → enum
                .title(request.getTitle())
                .description(request.getDescription())
                .dosage(request.getDosage())             // доза/инструкция (может быть null)
                .scheduledDate(request.getScheduledDate()) // запланированная дата (может быть null)
                .scheduledTime(request.getScheduledTime()) // запланированное время (может быть null)
                .status(AssignmentStatus.ACTIVE)         // новое назначение всегда активно
                .build();
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    /**
     * Меняет статус назначения: ACTIVE → DONE | CANCELLED.
     * Паттерн «найди → обнови поле → сохрани».
     */
    @Override
    @Transactional
    public NurseAssignmentResponse updateAssignmentStatus(Long id, String status) {
        NurseAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NurseAssignment", id));
        assignment.setStatus(AssignmentStatus.valueOf(status)); // обновляем статус
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    /**
     * Удаляет назначение.
     * findById перед deleteById — чтобы отличить «не найдено» от «удалено».
     */
    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        assignmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("NurseAssignment", id));
        assignmentRepository.deleteById(id);
    }

    /**
     * Возвращает назначения для конкретного клиента.
     * Используется в клиентском кабинете (GET /api/client/my-assignments).
     */
    @Override
    public List<NurseAssignmentResponse> getClientAssignments(Long clientUserId) {
        return assignmentRepository.findByClientUserId(clientUserId).stream()
                .map(this::toAssignmentResponse).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Приватные маппинг-методы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Конвертирует сущность склада в DTO.
     *
     * lowStock вычисляется прямо здесь — не хранится в БД, всегда актуален.
     * getOrDefault() защищает от ситуации «у enum появился новый ключ, но словарь не обновлён».
     */
    private MedicalSupplyResponse toSupplyResponse(MedicalSupply s) {
        return MedicalSupplyResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .category(s.getCategory().name())                            // machine-readable: "MEDICINE"
                .categoryLabel(CATEGORY_LABELS.getOrDefault(s.getCategory().name(), s.getCategory().name())) // human-readable: "Медикамент"
                .quantity(s.getQuantity())
                .unit(s.getUnit())
                .description(s.getDescription())
                .minQuantity(s.getMinQuantity())
                .lowStock(s.getQuantity() <= s.getMinQuantity())              // флаг «низкий остаток»
                .build();
    }

    /**
     * Конвертирует сущность назначения в DTO.
     *
     * clientName и nurseName берутся из связанных User-объектов — они загружены
     * через JOIN FETCH в репозитории, поэтому нет дополнительных SQL-запросов.
     */
    private NurseAssignmentResponse toAssignmentResponse(NurseAssignment a) {
        return NurseAssignmentResponse.builder()
                .id(a.getId())
                .clientUserId(a.getClientUser().getId())
                .clientName(a.getClientUser().getFullName())    // имя клиента из связанного User
                .nurseUserId(a.getNurseUser().getId())
                .nurseName(a.getNurseUser().getFullName())      // имя медсестры из связанного User
                .procedureType(a.getProcedureType().name())     // machine-readable: "INJECTION"
                .procedureTypeLabel(PROCEDURE_LABELS.getOrDefault(a.getProcedureType().name(), a.getProcedureType().name())) // human-readable: "Укол"
                .title(a.getTitle())
                .description(a.getDescription())
                .dosage(a.getDosage())
                .scheduledDate(a.getScheduledDate())
                .scheduledTime(a.getScheduledTime())
                .status(a.getStatus().name())                   // machine-readable статус
                .createdAt(a.getCreatedAt())                    // устанавливается @PrePersist
                .build();
    }
}
