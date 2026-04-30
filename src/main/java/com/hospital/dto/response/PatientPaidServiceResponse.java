package com.hospital.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO ответа с данными о назначении платной услуги пациенту.
 *
 * <p>Соответствует Entity {@code PatientPaidService} — связующей таблице
 * между пациентом и услугой (отношение многие-ко-многим с атрибутами).
 * Формируется MapStruct-маппером.
 *
 * <p><b>Поля из вложенных объектов (маппинг MapStruct):</b>
 * <ul>
 *   <li>{@code patientId} ← {@code patientPaidService.patient.id}</li>
 *   <li>{@code patientName} ← {@code patientPaidService.patient.fullName}</li>
 *   <li>{@code serviceId} ← {@code patientPaidService.paidService.id}</li>
 *   <li>{@code serviceName} ← {@code patientPaidService.paidService.name}</li>
 *   <li>{@code price} ← {@code patientPaidService.paidService.price}</li>
 * </ul>
 * Цена копируется из услуги в момент назначения — это важно: если цена услуги
 * изменится в будущем, исторические назначения останутся с прежней ценой.
 * (В реальной системе цену лучше фиксировать в самой Entity {@code PatientPaidService}.)
 *
 * <p>Плоская структура DTO (без вложенных объектов) упрощает рендеринг таблицы
 * назначений на фронтенде.
 */
@Data
public class PatientPaidServiceResponse {

    /** Уникальный идентификатор записи о назначении. */
    private Long id;

    /**
     * ID пациента — берётся из {@code patientPaidService.patient.id}.
     */
    private Long patientId;

    /**
     * Полное имя пациента — берётся из {@code patientPaidService.patient.fullName}.
     * Позволяет сразу отобразить имя в таблице без дополнительного запроса.
     */
    private String patientName;

    /**
     * ID платной услуги — берётся из {@code patientPaidService.paidService.id}.
     */
    private Long serviceId;

    /**
     * Название услуги — берётся из {@code patientPaidService.paidService.name}.
     */
    private String serviceName;

    /**
     * Стоимость услуги на момент назначения — берётся из
     * {@code patientPaidService.paidService.price}.
     * Тип {@code BigDecimal} гарантирует точность денежных сумм.
     */
    private BigDecimal price;

    /**
     * Дата и время назначения услуги пациенту.
     * Устанавливается сервисом автоматически (LocalDateTime.now()) —
     * клиент не может указать произвольное время.
     */
    private LocalDateTime assignedDate;

    /**
     * Статус оплаты услуги.
     * {@code true} — услуга оплачена, {@code false} — ожидает оплаты.
     * Изменяется через отдельный эндпоинт (PATCH /api/patient-services/{id}/pay).
     */
    private boolean paid;
}
