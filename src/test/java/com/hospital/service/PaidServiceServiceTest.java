package com.hospital.service;

import com.hospital.dto.request.CreatePaidServiceRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PaidServiceResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.PaidServiceMapper;
import com.hospital.repository.PaidServiceRepository;
import com.hospital.repository.PatientPaidServiceRepository;
import com.hospital.repository.PatientRepository;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.impl.PaidServiceServiceImpl;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты сервиса платных услуг.
 *
 * Этот сервис управляет двумя сущностями:
 *   PaidService        — справочник услуг (МРТ, УЗИ и т.д.)
 *   PatientPaidService — назначение конкретной услуги конкретному пациенту
 *
 * Ключевые бизнес-правила, которые проверяем:
 *   1. При назначении услуги — проверяем существование пациента и услуги.
 *   2. markPaid(): IDOR-защита — нельзя отметить оплаченной услугу чужого пациента.
 *      IDOR (Insecure Direct Object Reference) — атака, когда злоумышленник
 *      передаёт чужой ID напрямую в URL, чтобы изменить чужие данные.
 */
@ExtendWith(MockitoExtension.class)
class PaidServiceServiceTest {

    @Mock private PaidServiceRepository paidServiceRepository;
    @Mock private PatientRepository patientRepository;
    // ppsRepository — сокращение от PatientPaidServiceRepository
    @Mock private PatientPaidServiceRepository ppsRepository;
    @Mock private PaidServiceMapper paidServiceMapper;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks
    private PaidServiceServiceImpl paidServiceService;

    private PaidService paidService;
    private Patient patient;

    @BeforeEach
    void setUp() {
        // BigDecimal для цены — точные деньги без ошибок округления float/double
        paidService = PaidService.builder()
                .id(1L).name("MRI").price(new BigDecimal("5000.00")).active(true).build();

        patient = Patient.builder()
                .id(10L).fullName("Fluffy Cat")
                .birthDate(LocalDate.of(2018, 3, 1))
                .gender(Gender.MALE)
                .snils("111-222-333 44")
                .active(true)
                .status(PatientStatus.TREATMENT)
                .build();
    }

    @Test
    void create_setsActiveAndSaves() {
        // active = true должен устанавливать СЕРВИС, а не маппер.
        // Маппер не должен знать о бизнес-правиле «новая услуга всегда активна».
        CreatePaidServiceRequest request = new CreatePaidServiceRequest();
        request.setName("Ultrasound");
        request.setPrice(new BigDecimal("2000.00"));

        // Маппер возвращает сущность БЕЗ проставленного active (только поля из DTO)
        PaidService entity = PaidService.builder().id(2L).name("Ultrasound").build();
        when(paidServiceMapper.toEntity(request)).thenReturn(entity);
        when(paidServiceRepository.save(any())).thenReturn(entity);
        PaidServiceResponse response = new PaidServiceResponse();
        response.setId(2L);
        when(paidServiceMapper.toResponse(entity)).thenReturn(response);

        PaidServiceResponse result = paidServiceService.create(request);

        assertThat(result.getId()).isEqualTo(2L);
        // Ключевая проверка: сервис явно установил active = true на сущности
        assertThat(entity.isActive()).isTrue();
        verify(paidServiceRepository).save(entity);
    }

    @Test
    void getById_whenFound_returnsResponse() {
        // findByIdAndActiveTrue — ищем только активные услуги (soft delete)
        when(paidServiceRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(paidService));
        PaidServiceResponse response = new PaidServiceResponse();
        response.setId(1L);
        when(paidServiceMapper.toResponse(paidService)).thenReturn(response);

        PaidServiceResponse result = paidServiceService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_whenNotFound_throwsResourceNotFoundException() {
        when(paidServiceRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paidServiceService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_returnsPaginatedResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        // PageImpl — стандартный «тестовый» Page: список элементов + пагинация + total
        Page<PaidService> page = new PageImpl<>(List.of(paidService), pageable, 1);
        when(paidServiceRepository.findAllByActiveTrue(pageable)).thenReturn(page);
        when(paidServiceMapper.toResponse(paidService)).thenReturn(new PaidServiceResponse());

        PageResponse<PaidServiceResponse> result = paidServiceService.getAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void assignToPatient_success_createsLinkAndPublishesEvent() {
        // Успешный сценарий: оба объекта найдены, связка создана, событие опубликовано
        PatientPaidService link = PatientPaidService.builder()
                .id(100L).patient(patient).paidService(paidService).paid(false).build();

        when(patientRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(patient));
        when(paidServiceRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(paidService));
        when(ppsRepository.save(any())).thenReturn(link);
        PatientPaidServiceResponse response = new PatientPaidServiceResponse();
        response.setId(100L);
        when(paidServiceMapper.toLinkResponse(link)).thenReturn(response);

        PatientPaidServiceResponse result = paidServiceService.assignToPatient(10L, 1L);

        assertThat(result.getId()).isEqualTo(100L);
        // Событие SERVICE_ASSIGNED должно быть опубликовано (для системы биллинга)
        verify(eventPublisher).publishPaidServiceEvent(any());
    }

    @Test
    void assignToPatient_whenPatientNotFound_throwsResourceNotFoundException() {
        // Если пациент не найден — не назначаем услугу, возвращаем 404
        when(patientRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paidServiceService.assignToPatient(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignToPatient_whenServiceNotFound_throwsResourceNotFoundException() {
        // Пациент найден, но услуга — нет: тоже 404
        when(patientRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(patient));
        when(paidServiceRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paidServiceService.assignToPatient(10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markPaid_whenLinkBelongsToPatient_setsPaidTrue() {
        // Успешный сценарий: запись принадлежит пациенту — ставим отметку об оплате
        PatientPaidService link = PatientPaidService.builder()
                .id(100L).patient(patient).paidService(paidService).paid(false).build();

        when(ppsRepository.findById(100L)).thenReturn(Optional.of(link));
        when(ppsRepository.save(any())).thenReturn(link);
        PatientPaidServiceResponse response = new PatientPaidServiceResponse();
        response.setId(100L);
        when(paidServiceMapper.toLinkResponse(link)).thenReturn(response);

        paidServiceService.markPaid(10L, 100L);

        // Ключевая мутация: поле paid должно стать true
        assertThat(link.isPaid()).isTrue();
        verify(ppsRepository).save(link);
    }

    @Test
    void markPaid_whenLinkBelongsToDifferentPatient_throwsBusinessRuleException() {
        // IDOR-защита: patientId=10 пытается отметить оплаченной услугу пациента 20.
        // Без этой проверки злоумышленник мог бы подобрать linkId и закрыть чужой счёт.
        Patient otherPatient = Patient.builder().id(20L).fullName("Other Patient").build();
        PatientPaidService link = PatientPaidService.builder()
                .id(100L)
                .patient(otherPatient) // <-- запись принадлежит ДРУГОМУ пациенту
                .paidService(paidService)
                .paid(false)
                .build();

        when(ppsRepository.findById(100L)).thenReturn(Optional.of(link));

        // BusinessRuleException (HTTP 409) — запрос технически корректен,
        // но нарушает бизнес-правило «патент владеет своей услугой»
        assertThatThrownBy(() -> paidServiceService.markPaid(10L, 100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong to patient");
    }

    @Test
    void markPaid_whenLinkNotFound_throwsResourceNotFoundException() {
        // Если сама запись PatientPaidService не найдена — 404
        when(ppsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paidServiceService.markPaid(10L, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
