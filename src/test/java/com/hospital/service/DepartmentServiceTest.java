package com.hospital.service;

import com.hospital.dto.request.CreateDepartmentRequest;
import com.hospital.dto.request.UpdateDepartmentRequest;
import com.hospital.dto.response.DepartmentResponse;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Specialty;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.repository.DepartmentRepository;
import com.hospital.repository.DoctorRepository;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.impl.DepartmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты сервиса управления отделениями.
 *
 * ЧТО ТЕСТИРУЕМ: DepartmentServiceImpl — бизнес-логику изолированно,
 * без поднятия Spring-контекста, базы данных и Kafka.
 *
 * ИНСТРУМЕНТЫ:
 *   @ExtendWith(MockitoExtension.class) — подключает Mockito к JUnit 5.
 *   Mockito создаёт «заглушки» (mock-объекты) вместо реальных зависимостей.
 *   Тест проверяет только ЛОГИКУ метода, не инфраструктуру.
 *
 * ПАТТЕРН КАЖДОГО ТЕСТА — AAA (Arrange / Act / Assert):
 *   Arrange — настраиваем моки («что вернуть при таком-то вызове»)
 *   Act     — вызываем тестируемый метод
 *   Assert  — проверяем результат и/или взаимодействия с моками
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    // @Mock — Mockito создаёт заглушку интерфейса/класса.
    // Реальная логика НЕ выполняется: findById(), save() и т.д. возвращают null
    // до тех пор, пока мы явно не настроим поведение через when(...).thenReturn(...)
    @Mock private DepartmentRepository departmentRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private EventPublisher eventPublisher;

    // @InjectMocks — Mockito создаёт РЕАЛЬНЫЙ экземпляр DepartmentServiceImpl
    // и внедряет в него все поля, помеченные @Mock, через конструктор или сеттеры.
    // Именно этот объект мы будем тестировать.
    @InjectMocks
    private DepartmentServiceImpl departmentService;

    // Переиспользуемые тестовые объекты — создаются заново перед каждым тестом.
    private Department department;
    private Doctor doctor;

    // @BeforeEach — метод запускается ПЕРЕД каждым @Test.
    // Это гарантирует, что каждый тест работает с «чистыми» объектами
    // и не зависит от того, что сделал предыдущий тест.
    @BeforeEach
    void setUp() {
        // Создаём сущности через Lombok @Builder — читаемый fluent-стиль
        department = Department.builder()
                .id(1L).name("Cardiology").build();

        doctor = Doctor.builder()
                .id(10L).fullName("Dr. House")
                .specialty(Specialty.CARDIOLOGIST)
                .active(true)
                .build();
    }

    @Test
    void create_withoutHeadDoctor_savesAndPublishesEvent() {
        // Arrange: запрос без заведующего (headDoctorId = null)
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("Cardiology");

        // when(...).thenReturn(...) — «когда вызовут этот метод с этим аргументом,
        // вернуть вот это». Без этой настройки мок вернул бы null.
        when(departmentMapper.toEntity(request)).thenReturn(department);
        when(departmentRepository.save(any())).thenReturn(department);
        DepartmentResponse response = new DepartmentResponse();
        response.setId(1L);
        when(departmentMapper.toResponse(department)).thenReturn(response);

        // Act: вызываем реальный метод сервиса
        DepartmentResponse result = departmentService.create(request);

        // Assert: проверяем возвращаемое значение
        assertThat(result.getId()).isEqualTo(1L);
        // verify(...) — проверяем, что метод был вызван ровно один раз.
        // Это важно: нельзя создать отделение без вызова save().
        verify(departmentRepository).save(department);
        // argThat(...) — проверяем не просто факт вызова, а СОДЕРЖИМОЕ аргумента.
        // Убеждаемся, что тип события правильный.
        verify(eventPublisher).publishDepartmentEvent(argThat(e -> "DEPARTMENT_CREATED".equals(e.getEventType())));
    }

    @Test
    void create_withHeadDoctor_assignsDoctorToEntity() {
        // Arrange: запрос С заведующим
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("Cardiology");
        request.setHeadDoctorId(10L); // передаём ID врача-заведующего

        when(departmentMapper.toEntity(request)).thenReturn(department);
        // Мокируем поиск врача: doctorRepository вернёт нашего тестового врача
        when(doctorRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(doctor));
        when(departmentRepository.save(any())).thenReturn(department);
        when(departmentMapper.toResponse(department)).thenReturn(new DepartmentResponse());

        // Act
        departmentService.create(request);

        // Assert: проверяем, что сервис ДЕЙСТВИТЕЛЬНО установил врача на сущность.
        // Это ключевая бизнес-логика: маппер не умеет делать этот шаг сам.
        assertThat(department.getHeadDoctor()).isEqualTo(doctor);
        verify(doctorRepository).findByIdAndActiveTrue(10L);
    }

    @Test
    void create_withNonExistentHeadDoctor_throwsResourceNotFoundException() {
        // Arrange: запрос с несуществующим ID врача
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("Surgery");
        request.setHeadDoctorId(999L);

        when(departmentMapper.toEntity(request)).thenReturn(department);
        // Optional.empty() — симулируем ситуацию «врач не найден в БД»
        when(doctorRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        // assertThatThrownBy — проверяем, что при вызове метода бросается исключение.
        // Это идиоматичный способ AssertJ для проверки исключений.
        assertThatThrownBy(() -> departmentService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
        // Мы НЕ проверяем здесь вызов save() — сервис должен упасть ДО сохранения.
        // При желании можно добавить: verifyNoInteractions(departmentRepository);
    }

    @Test
    void getById_whenFound_returnsResponse() {
        // Arrange
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        DepartmentResponse response = new DepartmentResponse();
        response.setId(1L);
        when(departmentMapper.toResponse(department)).thenReturn(response);

        // Act
        DepartmentResponse result = departmentService.getById(1L);

        // Assert: метод нашёл отделение — возвращает корректный DTO
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_whenNotFound_throwsResourceNotFoundException() {
        // findById возвращает Optional.empty() — отделение не существует
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_returnsMappedList() {
        // Arrange: два отделения в репозитории
        Department dept2 = Department.builder().id(2L).name("Surgery").build();
        when(departmentRepository.findAll()).thenReturn(List.of(department, dept2));
        // Каждому объекту сущности настраиваем отдельный ответ маппера
        when(departmentMapper.toResponse(department)).thenReturn(new DepartmentResponse());
        when(departmentMapper.toResponse(dept2)).thenReturn(new DepartmentResponse());

        // Act
        List<DepartmentResponse> result = departmentService.getAll();

        // Assert: список должен содержать оба элемента
        assertThat(result).hasSize(2);
    }

    @Test
    void update_whenFound_updatesAndReturnsResponse() {
        // Arrange
        UpdateDepartmentRequest request = new UpdateDepartmentRequest();
        request.setName("Updated Name");

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(departmentRepository.save(any())).thenReturn(department);
        DepartmentResponse response = new DepartmentResponse();
        response.setId(1L);
        when(departmentMapper.toResponse(department)).thenReturn(response);

        // Act
        DepartmentResponse result = departmentService.update(1L, request);

        // Assert: маппер должен был вызвать updateFromRequest() для обновления полей
        assertThat(result.getId()).isEqualTo(1L);
        verify(departmentMapper).updateFromRequest(department, request);
    }

    @Test
    void update_withNewHeadDoctor_changesHeadDoctor() {
        // Проверяем ветку обновления заведующего
        UpdateDepartmentRequest request = new UpdateDepartmentRequest();
        request.setHeadDoctorId(10L);

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(doctorRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(doctor));
        when(departmentRepository.save(any())).thenReturn(department);
        when(departmentMapper.toResponse(department)).thenReturn(new DepartmentResponse());

        departmentService.update(1L, request);

        // Ключевая проверка: после update заведующий сущности изменился
        assertThat(department.getHeadDoctor()).isEqualTo(doctor);
    }

    @Test
    void delete_whenFound_deletesAndPublishesEvent() {
        // Важный нюанс: событие публикуется ДО физического удаления (чтобы id был доступен).
        // Проверяем оба шага: delete() и publishDepartmentEvent().
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        // doNothing() явно указывает, что void-метод должен «ничего не делать» — это дефолт,
        // но запись делает намерение теста явным.
        doNothing().when(departmentRepository).delete(department);

        departmentService.delete(1L);

        verify(departmentRepository).delete(department);
        verify(eventPublisher).publishDepartmentEvent(argThat(e -> "DEPARTMENT_DELETED".equals(e.getEventType())));
    }

    @Test
    void delete_whenNotFound_throwsResourceNotFoundException() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
