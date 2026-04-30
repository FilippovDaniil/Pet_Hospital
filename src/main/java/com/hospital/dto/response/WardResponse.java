package com.hospital.dto.response;

import lombok.Data;

/**
 * DTO ответа с данными палаты (GET /api/wards/{id} и другие эндпоинты).
 *
 * <p>Формируется MapStruct-маппером из Entity {@code Ward}.
 *
 * <p><b>Поля из вложенного объекта (маппинг MapStruct):</b>
 * <ul>
 *   <li>{@code departmentId} ← {@code ward.department.id}</li>
 *   <li>{@code departmentName} ← {@code ward.department.name}</li>
 * </ul>
 *
 * <p><b>Вычисляемые поля:</b><br>
 * {@code currentOccupancy} — количество текущих пациентов в палате,
 * подсчитывается в сервисе запросом к БД.<br>
 * {@code freeSlots = capacity - currentOccupancy} — вычисляется
 * в сервисе или маппере и позволяет клиенту сразу видеть наличие мест
 * без дополнительных вычислений на стороне UI.
 */
@Data
public class WardResponse {

    /** Уникальный идентификатор палаты в БД. */
    private Long id;

    /** Номер палаты (например, «101», «А-5»). */
    private String wardNumber;

    /** Максимальное число мест в палате. */
    private int capacity;

    /**
     * Текущее число пациентов в палате.
     * Подсчитывается в сервисном слое как количество активных
     * госпитализаций, связанных с данной палатой.
     */
    private int currentOccupancy;

    /**
     * Число свободных мест: {@code capacity - currentOccupancy}.
     * Вычисляется на сервере — клиент не должен делать это сам,
     * так как данные могут устареть между запросами.
     */
    private int freeSlots;

    /**
     * ID отделения — берётся из {@code ward.department.id}.
     */
    private Long departmentId;

    /**
     * Название отделения — берётся из {@code ward.department.name}.
     * Удобно для отображения палаты в списке без дополнительного запроса.
     */
    private String departmentName;
}
