package com.hospital.service;

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
import com.hospital.service.impl.NurseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для NurseServiceImpl.
 *
 * Охватывает два домена модуля медсестры:
 *   1. Управление складом медикаментов (MedicalSupply):
 *      getAllSupplies, createSupply, updateSupply, adjustSupply, deleteSupply.
 *   2. Управление назначениями процедур клиентам (NurseAssignment):
 *      getAllAssignments, createAssignment, updateAssignmentStatus,
 *      deleteAssignment, getClientAssignments.
 *
 * Стратегия: все репозитории заменяются Mockito-заглушками (@Mock).
 * Тестируется только бизнес-логика сервиса, без обращений к реальной БД.
 */
@ExtendWith(MockitoExtension.class)
class NurseServiceTest {

    // ─── Заглушки зависимостей ────────────────────────────────────────────────

    /** Репозиторий медикаментов — подменяется mock-объектом. */
    @Mock
    private MedicalSupplyRepository supplyRepository;

    /** Репозиторий назначений — подменяется mock-объектом. */
    @Mock
    private NurseAssignmentRepository assignmentRepository;

    /** Репозиторий пользователей — подменяется mock-объектом. */
    @Mock
    private UserRepository userRepository;

    /**
     * Тестируемый объект. Mockito создаёт его и внедряет все @Mock-поля
     * через конструктор (благодаря @RequiredArgsConstructor в сервисе).
     */
    @InjectMocks
    private NurseServiceImpl nurseService;

    // ─── Тестовые данные ──────────────────────────────────────────────────────

    /** Позиция склада: достаточный остаток (quantity > minQuantity). */
    private MedicalSupply supply;

    /** Позиция склада: низкий остаток (quantity <= minQuantity). */
    private MedicalSupply lowStockSupply;

    /** Пользователь с ролью ROLE_NURSE. */
    private User nurseUser;

    /** Пользователь с ролью ROLE_CLIENT. */
    private User clientUser;

    /** Готовое назначение процедуры (INJECTION, ACTIVE). */
    private NurseAssignment assignment;

    @BeforeEach
    void setUp() {
        // supply: 20 таблеток, минимум 5 → lowStock=false.
        supply = MedicalSupply.builder()
                .id(1L)
                .name("Аспирин")
                .category(SupplyCategory.MEDICINE)
                .quantity(20)
                .unit("таб.")
                .description("Жаропонижающее")
                .minQuantity(5)
                .build();

        // lowStockSupply: 3 штуки, минимум 5 → lowStock=true.
        lowStockSupply = MedicalSupply.builder()
                .id(2L)
                .name("Бинт")
                .category(SupplyCategory.CONSUMABLE)
                .quantity(3)
                .unit("шт.")
                .minQuantity(5)
                .build();

        // Медсестра — инициатор назначения.
        nurseUser = User.builder()
                .id(10L).username("nurse1").fullName("Медсестра Анна")
                .role(Role.ROLE_NURSE).active(true).build();

        // Клиент — получатель назначения (роль обязательна).
        clientUser = User.builder()
                .id(20L).username("client1").fullName("Клиент Иван")
                .role(Role.ROLE_CLIENT).active(true).build();

        // Назначение: укол витамина C, запланирован на 2026-05-20 10:00, статус ACTIVE.
        assignment = NurseAssignment.builder()
                .id(100L)
                .clientUser(clientUser)
                .nurseUser(nurseUser)
                .procedureType(ProcedureType.INJECTION)
                .title("Укол витамина C")
                .dosage("1 мл")
                .scheduledDate(LocalDate.of(2026, 5, 20))
                .scheduledTime(LocalTime.of(10, 0))
                .status(AssignmentStatus.ACTIVE)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getAllSupplies
    // ─────────────────────────────────────────────────────────────────────────

    /** Возвращает список всех позиций со склада через репозиторий. */
    @Test
    void getAllSupplies_returnsAllSuppliesFromRepository() {
        // Репозиторий возвращает одну позицию, отсортированную по категории и имени.
        when(supplyRepository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(supply));

        List<MedicalSupplyResponse> result = nurseService.getAllSupplies();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Аспирин");
        // category — machine-readable enum name для программной логики.
        assertThat(result.get(0).getCategory()).isEqualTo("MEDICINE");
        // categoryLabel — русское название для отображения в UI.
        assertThat(result.get(0).getCategoryLabel()).isEqualTo("Медикамент");
    }

    /** Пустой склад возвращает пустой список, а не null. */
    @Test
    void getAllSupplies_whenEmpty_returnsEmptyList() {
        when(supplyRepository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of());

        assertThat(nurseService.getAllSupplies()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // lowStock flag
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Флаг lowStock=false, если quantity > minQuantity.
     * Проверяет, что нормальный остаток не вызывает предупреждение.
     */
    @Test
    void getAllSupplies_whenQuantityAboveMin_lowStockIsFalse() {
        when(supplyRepository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(supply));

        // supply: quantity=20, minQuantity=5 → 20 > 5 → не низкий.
        assertThat(nurseService.getAllSupplies().get(0).isLowStock()).isFalse();
    }

    /**
     * Флаг lowStock=true, если quantity <= minQuantity.
     * Проверяет, что недостаточный остаток правильно сигнализирует о нехватке.
     */
    @Test
    void getAllSupplies_whenQuantityAtOrBelowMin_lowStockIsTrue() {
        when(supplyRepository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(lowStockSupply));

        // lowStockSupply: quantity=3, minQuantity=5 → 3 <= 5 → низкий остаток.
        assertThat(nurseService.getAllSupplies().get(0).isLowStock()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createSupply
    // ─────────────────────────────────────────────────────────────────────────

    /** Создаёт сущность MedicalSupply и сохраняет её в репозитории. */
    @Test
    void createSupply_savesEntityAndReturnsResponse() {
        CreateSupplyRequest req = new CreateSupplyRequest();
        req.setName("Аспирин");
        req.setCategory("MEDICINE");
        req.setQuantity(20);
        req.setUnit("таб.");
        req.setDescription("Жаропонижающее");
        req.setMinQuantity(5);

        // save() возвращает готовую сущность с id от БД.
        when(supplyRepository.save(any(MedicalSupply.class))).thenReturn(supply);

        MedicalSupplyResponse result = nurseService.createSupply(req);

        // Убеждаемся, что сохранение было вызвано.
        verify(supplyRepository).save(any(MedicalSupply.class));
        assertThat(result.getName()).isEqualTo("Аспирин");
        assertThat(result.getQuantity()).isEqualTo(20);
    }

    /** categoryLabel для CONSUMABLE должен быть «Расходник». */
    @Test
    void createSupply_consumableCategoryLabelIsCorrect() {
        CreateSupplyRequest req = new CreateSupplyRequest();
        req.setName("Шприц");
        req.setCategory("CONSUMABLE");
        req.setQuantity(50);
        req.setUnit("шт.");
        req.setMinQuantity(10);

        MedicalSupply consumable = MedicalSupply.builder()
                .id(3L).name("Шприц").category(SupplyCategory.CONSUMABLE)
                .quantity(50).unit("шт.").minQuantity(10).build();
        when(supplyRepository.save(any(MedicalSupply.class))).thenReturn(consumable);

        assertThat(nurseService.createSupply(req).getCategoryLabel()).isEqualTo("Расходник");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateSupply
    // ─────────────────────────────────────────────────────────────────────────

    /** Обновляет все поля существующей позиции и возвращает актуальный ответ. */
    @Test
    void updateSupply_updatesFieldsAndReturnsResponse() {
        CreateSupplyRequest req = new CreateSupplyRequest();
        req.setName("Аспирин Плюс");
        req.setCategory("MEDICINE");
        req.setQuantity(30);
        req.setUnit("таб.");
        req.setMinQuantity(10);

        when(supplyRepository.findById(1L)).thenReturn(Optional.of(supply));
        // thenAnswer(inv -> inv.getArgument(0)) — возвращаем тот же объект,
        // который передали на сохранение (уже с обновлёнными полями).
        when(supplyRepository.save(any(MedicalSupply.class))).thenAnswer(inv -> inv.getArgument(0));

        MedicalSupplyResponse result = nurseService.updateSupply(1L, req);

        assertThat(result.getName()).isEqualTo("Аспирин Плюс");
        assertThat(result.getQuantity()).isEqualTo(30);
        assertThat(result.getMinQuantity()).isEqualTo(10);
    }

    /** Обновление несуществующей позиции выбрасывает ResourceNotFoundException (→ HTTP 404). */
    @Test
    void updateSupply_whenNotFound_throwsResourceNotFoundException() {
        when(supplyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nurseService.updateSupply(999L, new CreateSupplyRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // adjustSupply
    // ─────────────────────────────────────────────────────────────────────────

    /** Положительный delta увеличивает остаток на складе. */
    @Test
    void adjustSupply_positiveDelta_increasesQuantity() {
        AdjustSupplyRequest req = new AdjustSupplyRequest();
        req.setDelta(10); // пополнение

        when(supplyRepository.findById(1L)).thenReturn(Optional.of(supply));
        when(supplyRepository.save(any(MedicalSupply.class))).thenAnswer(inv -> inv.getArgument(0));

        MedicalSupplyResponse result = nurseService.adjustSupply(1L, req);

        // 20 + 10 = 30.
        assertThat(result.getQuantity()).isEqualTo(30);
    }

    /** Отрицательный delta уменьшает остаток на складе. */
    @Test
    void adjustSupply_negativeDelta_decreasesQuantity() {
        AdjustSupplyRequest req = new AdjustSupplyRequest();
        req.setDelta(-5); // расход

        when(supplyRepository.findById(1L)).thenReturn(Optional.of(supply));
        when(supplyRepository.save(any(MedicalSupply.class))).thenAnswer(inv -> inv.getArgument(0));

        MedicalSupplyResponse result = nurseService.adjustSupply(1L, req);

        // 20 - 5 = 15.
        assertThat(result.getQuantity()).isEqualTo(15);
    }

    /**
     * Нельзя уйти в отрицательный остаток — сервис выбрасывает BusinessRuleException.
     * Это защищает от ситуации «выдать больше, чем есть на складе».
     */
    @Test
    void adjustSupply_whenResultBelowZero_throwsBusinessRuleException() {
        AdjustSupplyRequest req = new AdjustSupplyRequest();
        req.setDelta(-100); // 20 - 100 = -80 → бизнес-правило нарушено

        when(supplyRepository.findById(1L)).thenReturn(Optional.of(supply));

        assertThatThrownBy(() -> nurseService.adjustSupply(1L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Недостаточно");
    }

    /** Корректировка несуществующей позиции → ResourceNotFoundException. */
    @Test
    void adjustSupply_whenSupplyNotFound_throwsResourceNotFoundException() {
        when(supplyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nurseService.adjustSupply(999L, new AdjustSupplyRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteSupply
    // ─────────────────────────────────────────────────────────────────────────

    /** Удаляет позицию склада по id. */
    @Test
    void deleteSupply_deletesById() {
        when(supplyRepository.findById(1L)).thenReturn(Optional.of(supply));

        nurseService.deleteSupply(1L);

        verify(supplyRepository).deleteById(1L);
    }

    /**
     * Удаление несуществующей позиции → ResourceNotFoundException.
     * deleteById при этом не вызывается.
     */
    @Test
    void deleteSupply_whenNotFound_throwsResourceNotFoundException() {
        when(supplyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nurseService.deleteSupply(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
        verify(supplyRepository, never()).deleteById(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getAllAssignments
    // ─────────────────────────────────────────────────────────────────────────

    /** Без фильтра по статусу возвращает все назначения. */
    @Test
    void getAllAssignments_withNullStatus_returnsAll() {
        when(assignmentRepository.findAllWithUsers()).thenReturn(List.of(assignment));

        List<NurseAssignmentResponse> result = nurseService.getAllAssignments(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Укол витамина C");
    }

    /**
     * Пустая строка статуса трактуется как «без фильтра» — вызывается
     * findAllWithUsers, а не findByStatusWithUsers.
     */
    @Test
    void getAllAssignments_withBlankStatus_returnsAll() {
        when(assignmentRepository.findAllWithUsers()).thenReturn(List.of(assignment));

        nurseService.getAllAssignments("  ");

        // Фильтрация по статусу не применяется при пустой строке.
        verify(assignmentRepository).findAllWithUsers();
        verify(assignmentRepository, never()).findByStatusWithUsers(any());
    }

    /** Передача конкретного статуса вызывает фильтрацию через findByStatusWithUsers. */
    @Test
    void getAllAssignments_withStatusFilter_returnsByStatus() {
        when(assignmentRepository.findByStatusWithUsers(AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));

        List<NurseAssignmentResponse> result = nurseService.getAllAssignments("ACTIVE");

        assertThat(result).hasSize(1);
        // Статус в ответе — строковое представление enum.
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createAssignment
    // ─────────────────────────────────────────────────────────────────────────

    /** Создаёт назначение с корректными данными и возвращает ответ. */
    @Test
    void createAssignment_savesAndReturnsResponse() {
        CreateAssignmentRequest req = new CreateAssignmentRequest();
        req.setClientUserId(20L);
        req.setProcedureType("INJECTION");
        req.setTitle("Укол витамина C");
        req.setDosage("1 мл");
        req.setScheduledDate(LocalDate.of(2026, 5, 20));
        req.setScheduledTime(LocalTime.of(10, 0));

        when(userRepository.findByUsername("nurse1")).thenReturn(Optional.of(nurseUser));
        when(userRepository.findById(20L)).thenReturn(Optional.of(clientUser));
        when(assignmentRepository.save(any(NurseAssignment.class))).thenReturn(assignment);

        NurseAssignmentResponse result = nurseService.createAssignment("nurse1", req);

        verify(assignmentRepository).save(any(NurseAssignment.class));
        assertThat(result.getTitle()).isEqualTo("Укол витамина C");
        assertThat(result.getProcedureType()).isEqualTo("INJECTION");
        // procedureTypeLabel — русский ярлык для UI.
        assertThat(result.getProcedureTypeLabel()).isEqualTo("Укол");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    /** Медсестра не найдена в БД → ResourceNotFoundException. */
    @Test
    void createAssignment_nurseNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("unknownNurse")).thenReturn(Optional.empty());

        CreateAssignmentRequest req = new CreateAssignmentRequest();
        req.setClientUserId(20L);
        req.setProcedureType("INJECTION");
        req.setTitle("Test");

        assertThatThrownBy(() -> nurseService.createAssignment("unknownNurse", req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Клиент с указанным id не найден в БД → ResourceNotFoundException. */
    @Test
    void createAssignment_clientNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("nurse1")).thenReturn(Optional.of(nurseUser));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        CreateAssignmentRequest req = new CreateAssignmentRequest();
        req.setClientUserId(999L);
        req.setProcedureType("INJECTION");
        req.setTitle("Test");

        assertThatThrownBy(() -> nurseService.createAssignment("nurse1", req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    /**
     * Нельзя создать назначение для пользователя, у которого нет роли ROLE_CLIENT.
     * Защита от назначения процедур врачам и другим сотрудникам.
     */
    @Test
    void createAssignment_whenUserIsNotClient_throwsResourceNotFoundException() {
        // doctorUser имеет ROLE_DOCTOR — filter() вернёт пустой Optional.
        User nonClient = User.builder().id(30L).role(Role.ROLE_DOCTOR).build();
        when(userRepository.findByUsername("nurse1")).thenReturn(Optional.of(nurseUser));
        when(userRepository.findById(30L)).thenReturn(Optional.of(nonClient));

        CreateAssignmentRequest req = new CreateAssignmentRequest();
        req.setClientUserId(30L);
        req.setProcedureType("INJECTION");
        req.setTitle("Test");

        assertThatThrownBy(() -> nurseService.createAssignment("nurse1", req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("30");
    }

    /** Тип процедуры PILL должен маппиться в «Приём таблеток». */
    @Test
    void createAssignment_procedureTypePillLabelIsCorrect() {
        CreateAssignmentRequest req = new CreateAssignmentRequest();
        req.setClientUserId(20L);
        req.setProcedureType("PILL");
        req.setTitle("Приём Аспирина");

        NurseAssignment pillAssignment = NurseAssignment.builder()
                .id(200L).clientUser(clientUser).nurseUser(nurseUser)
                .procedureType(ProcedureType.PILL).title("Приём Аспирина")
                .status(AssignmentStatus.ACTIVE).build();

        when(userRepository.findByUsername("nurse1")).thenReturn(Optional.of(nurseUser));
        when(userRepository.findById(20L)).thenReturn(Optional.of(clientUser));
        when(assignmentRepository.save(any(NurseAssignment.class))).thenReturn(pillAssignment);

        assertThat(nurseService.createAssignment("nurse1", req).getProcedureTypeLabel())
                .isEqualTo("Приём таблеток");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateAssignmentStatus
    // ─────────────────────────────────────────────────────────────────────────

    /** Обновляет статус назначения и возвращает актуальный ответ. */
    @Test
    void updateAssignmentStatus_updatesStatusAndReturnsResponse() {
        when(assignmentRepository.findById(100L)).thenReturn(Optional.of(assignment));
        // Возвращаем тот же объект — к этому моменту setStatus уже вызван.
        when(assignmentRepository.save(any(NurseAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        NurseAssignmentResponse result = nurseService.updateAssignmentStatus(100L, "DONE");

        assertThat(result.getStatus()).isEqualTo("DONE");
        verify(assignmentRepository).save(assignment);
    }

    /** Обновление несуществующего назначения → ResourceNotFoundException. */
    @Test
    void updateAssignmentStatus_whenNotFound_throwsResourceNotFoundException() {
        when(assignmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nurseService.updateAssignmentStatus(999L, "DONE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteAssignment
    // ─────────────────────────────────────────────────────────────────────────

    /** Удаляет назначение по id. */
    @Test
    void deleteAssignment_deletesById() {
        when(assignmentRepository.findById(100L)).thenReturn(Optional.of(assignment));

        nurseService.deleteAssignment(100L);

        verify(assignmentRepository).deleteById(100L);
    }

    /**
     * Удаление несуществующего назначения → ResourceNotFoundException.
     * deleteById при этом не вызывается.
     */
    @Test
    void deleteAssignment_whenNotFound_throwsResourceNotFoundException() {
        when(assignmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nurseService.deleteAssignment(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
        verify(assignmentRepository, never()).deleteById(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getClientAssignments
    // ─────────────────────────────────────────────────────────────────────────

    /** Возвращает все назначения конкретного клиента. */
    @Test
    void getClientAssignments_returnsAssignmentsForClient() {
        when(assignmentRepository.findByClientUserId(20L)).thenReturn(List.of(assignment));

        List<NurseAssignmentResponse> result = nurseService.getClientAssignments(20L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientUserId()).isEqualTo(20L);
        assertThat(result.get(0).getNurseName()).isEqualTo("Медсестра Анна");
    }

    /** Если у клиента нет назначений — возвращается пустой список, не null. */
    @Test
    void getClientAssignments_whenNoAssignments_returnsEmptyList() {
        when(assignmentRepository.findByClientUserId(20L)).thenReturn(List.of());

        assertThat(nurseService.getClientAssignments(20L)).isEmpty();
    }
}
