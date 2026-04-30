package com.hospital.service;

import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.dto.request.UpdateDoctorRequest;
import com.hospital.dto.response.DoctorResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Patient;
import com.hospital.entity.Specialty;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.repository.DepartmentRepository;
import com.hospital.repository.DoctorRepository;
import com.hospital.repository.PatientRepository;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.impl.DoctorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты сервиса управления врачами.
 *
 * Особенности этого сервиса по сравнению с DepartmentService:
 *   - Пагинация: методы getAll() и getBySpecialty() возвращают PageResponse.
 *     Тесты проверяют корректность маппинга Spring Data Page → PageResponse DTO.
 *   - Soft delete: врач не удаляется физически, только active = false.
 *   - Вложенный ресурс: getPatients() возвращает пациентов конкретного врача.
 */
@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock private DoctorRepository doctorRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DoctorMapper doctorMapper;
    @Mock private PatientMapper patientMapper;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks
    private DoctorServiceImpl doctorService;

    private Doctor doctor;
    private Department department;

    @BeforeEach
    void setUp() {
        department = Department.builder().id(5L).name("Cardiology").build();

        doctor = Doctor.builder()
                .id(1L).fullName("Dr. House")
                .specialty(Specialty.CARDIOLOGIST)
                .active(true)
                .build();
    }

    @Test
    void create_withoutDepartment_savesAndPublishesEvent() {
        // Врач может быть создан без привязки к отделению (departmentId = null).
        // Проверяем: сохранение происходит, событие публикуется, active = true.
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFullName("Dr. House");
        request.setSpecialty(Specialty.CARDIOLOGIST);
        // departmentId не задан — врач создаётся «без отделения»

        when(doctorMapper.toEntity(request)).thenReturn(doctor);
        when(doctorRepository.save(any())).thenReturn(doctor);
        DoctorResponse response = new DoctorResponse();
        response.setId(1L);
        when(doctorMapper.toResponse(doctor)).thenReturn(response);

        DoctorResponse result = doctorService.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        // active = true устанавливается сервисом вручную (маппер это не делает)
        assertThat(doctor.isActive()).isTrue();
        verify(eventPublisher).publishDoctorEvent(argThat(e -> "DOCTOR_CREATED".equals(e.getEventType())));
    }

    @Test
    void create_withDepartment_linksDepartmentToDoctor() {
        // Когда передан departmentId — сервис должен найти отделение и привязать его.
        // Маппер не обрабатывает вложенные объекты по ID — это делает сервис вручную.
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFullName("Dr. House");
        request.setSpecialty(Specialty.CARDIOLOGIST);
        request.setDepartmentId(5L); // передаём ID отделения

        when(doctorMapper.toEntity(request)).thenReturn(doctor);
        // departmentRepository.findById() (без activeTrue!) — у Department нет soft delete
        when(departmentRepository.findById(5L)).thenReturn(Optional.of(department));
        when(doctorRepository.save(any())).thenReturn(doctor);
        when(doctorMapper.toResponse(doctor)).thenReturn(new DoctorResponse());

        doctorService.create(request);

        // Главная проверка: сущность Doctor теперь ссылается на отделение
        assertThat(doctor.getDepartment()).isEqualTo(department);
    }

    @Test
    void create_withNonExistentDepartment_throwsResourceNotFoundException() {
        // Fail-fast: если отделение не найдено — бросаем исключение ДО сохранения врача
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFullName("Dr. Strange");
        request.setSpecialty(Specialty.SURGEON);
        request.setDepartmentId(999L);

        when(doctorMapper.toEntity(request)).thenReturn(doctor);
        when(departmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_whenFound_returnsMappedResponse() {
        // findByIdAndActiveTrue — ищет только АКТИВНЫХ врачей (soft delete)
        when(doctorRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(doctor));
        DoctorResponse response = new DoctorResponse();
        response.setId(1L);
        when(doctorMapper.toResponse(doctor)).thenReturn(response);

        DoctorResponse result = doctorService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_whenNotFound_throwsResourceNotFoundException() {
        // Если врач не найден (или деактивирован) — 404
        when(doctorRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_returnsPaginatedResponse() {
        // PageImpl — тестовая реализация Page<T> из Spring Data.
        // Передаём: список элементов, параметры пагинации, общее число элементов.
        Pageable pageable = PageRequest.of(0, 10);
        Page<Doctor> page = new PageImpl<>(List.of(doctor), pageable, 1);
        when(doctorRepository.findAllByActiveTrue(pageable)).thenReturn(page);
        when(doctorMapper.toResponse(doctor)).thenReturn(new DoctorResponse());

        var result = doctorService.getAll(pageable);

        // Проверяем что PageResponse содержит правильные метаданные пагинации
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getBySpecialty_filtersCorrectly() {
        // Тест проверяет, что при передаче специальности вызывается ДРУГОЙ метод
        // репозитория: findBySpecialtyAndActiveTrue, а не findAllByActiveTrue.
        Pageable pageable = PageRequest.of(0, 10);
        Page<Doctor> page = new PageImpl<>(List.of(doctor), pageable, 1);
        when(doctorRepository.findBySpecialtyAndActiveTrue(Specialty.CARDIOLOGIST, pageable)).thenReturn(page);
        when(doctorMapper.toResponse(doctor)).thenReturn(new DoctorResponse());

        var result = doctorService.getBySpecialty(Specialty.CARDIOLOGIST, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        // verify убеждается, что был вызван именно метод фильтрации по специальности
        verify(doctorRepository).findBySpecialtyAndActiveTrue(Specialty.CARDIOLOGIST, pageable);
    }

    @Test
    void update_whenFound_updatesFields() {
        UpdateDoctorRequest request = new UpdateDoctorRequest();
        request.setFullName("Dr. Updated");

        when(doctorRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any())).thenReturn(doctor);
        when(doctorMapper.toResponse(doctor)).thenReturn(new DoctorResponse());

        doctorService.update(1L, request);

        // updateFromRequest() должен быть вызван — именно он меняет поля врача через маппер
        verify(doctorMapper).updateFromRequest(doctor, request);
        verify(doctorRepository).save(doctor);
    }

    @Test
    void update_withNewDepartment_changesDepartment() {
        // Смена отделения — отдельная ветка в сервисе (маппер это не делает)
        UpdateDoctorRequest request = new UpdateDoctorRequest();
        request.setDepartmentId(5L);

        when(doctorRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(doctor));
        when(departmentRepository.findById(5L)).thenReturn(Optional.of(department));
        when(doctorRepository.save(any())).thenReturn(doctor);
        when(doctorMapper.toResponse(doctor)).thenReturn(new DoctorResponse());

        doctorService.update(1L, request);

        assertThat(doctor.getDepartment()).isEqualTo(department);
    }

    @Test
    void softDelete_setsActiveToFalse() {
        // Soft delete: физически запись остаётся, только active меняется на false.
        // Это важно для ссылочной целостности: история лечения ссылается на врача.
        when(doctorRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any())).thenReturn(doctor);

        doctorService.softDelete(1L);

        // Проверяем изменение состояния объекта, а не только факт вызова save()
        assertThat(doctor.isActive()).isFalse();
        verify(doctorRepository).save(doctor);
    }

    @Test
    void softDelete_whenNotFound_throwsResourceNotFoundException() {
        when(doctorRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.softDelete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPatients_whenDoctorNotFound_throwsResourceNotFoundException() {
        // Fail-fast: прежде чем вернуть список пациентов — проверяем существование врача.
        // Без этой проверки несуществующий врач вернул бы пустой список вместо 404.
        when(doctorRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.getPatients(99L, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPatients_returnsPaginatedPatients() {
        Pageable pageable = PageRequest.of(0, 10);
        // Создаём тестового пациента и оборачиваем в Page
        Patient patient = Patient.builder().id(1L).fullName("Test Patient").build();
        Page<Patient> page = new PageImpl<>(List.of(patient), pageable, 1);

        when(doctorRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(doctor));
        // findByDoctorId — кастомный метод репозитория для получения пациентов врача
        when(patientRepository.findByDoctorId(1L, pageable)).thenReturn(page);
        when(patientMapper.toResponse(patient)).thenReturn(new PatientResponse());

        var result = doctorService.getPatients(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
