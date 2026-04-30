package com.hospital.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO ответа с данными платной услуги (GET /api/paid-services/{id} и другие эндпоинты).
 *
 * <p>Формируется MapStruct-маппером из Entity {@code PaidService}.
 * Все поля маппируются напрямую из Entity — вложенных объектов нет.
 *
 * <p>Цена передаётся в {@code BigDecimal} — тип с точной десятичной арифметикой,
 * без ошибок округления {@code float}/{@code double}. На стороне JSON
 * Jackson сериализует {@code BigDecimal} как десятичное число (например, 1500.00).
 *
 * <p>Поле {@code active} реализует паттерн мягкого удаления (soft delete):
 * услуга не удаляется из БД, а помечается неактивной. Это позволяет
 * сохранить историю назначений, ссылающихся на уже недействующую услугу.
 */
@Data
public class PaidServiceResponse {

    /** Уникальный идентификатор услуги в БД. */
    private Long id;

    /** Наименование услуги (например, «УЗИ брюшной полости»). */
    private String name;

    /** Стоимость услуги в рублях с точностью до копеек. */
    private BigDecimal price;

    /** Описание услуги: состав, показания, длительность. */
    private String description;

    /**
     * Флаг активности услуги (soft delete).
     * {@code false} — услуга деактивирована и недоступна для назначения,
     * но её история в {@code PatientPaidService} сохраняется.
     */
    private boolean active;
}
