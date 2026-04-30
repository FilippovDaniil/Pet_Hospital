package com.hospital.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO для создания новой палаты (POST /api/wards).
 *
 * <p>Палата принадлежит конкретному отделению и имеет ограниченную
 * вместимость. DTO содержит минимально необходимые поля для создания:
 * номер палаты, вместимость и ссылку на отделение.
 *
 * <p>Поля {@code currentOccupancy} и {@code freeSlots} в Entity
 * вычисляются динамически — они не принимаются от клиента,
 * а рассчитываются сервисом на основе текущих госпитализаций.
 */
@Data
public class CreateWardRequest {

    /**
     * Номер (или название) палаты, например «101», «А-5».
     *
     * <p><b>@NotBlank</b> — поле обязательно; нельзя создать палату
     * без номера.
     */
    @NotBlank(message = "Ward number is required")
    private String wardNumber;

    /**
     * Максимальная вместимость палаты — количество мест для пациентов.
     *
     * <p><b>@NotNull</b> — поле обязательно (Integer, а не примитив,
     * поэтому допускает null без этой аннотации).<br>
     * <b>@Min(value = 1)</b> — вместимость должна быть не меньше 1.
     * Это бизнес-правило: палата без мест лишена смысла. Нулевая или
     * отрицательная вместимость исключается на уровне валидации,
     * не требуя дополнительных проверок в сервисе.
     */
    @NotNull
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    /**
     * Идентификатор отделения, которому принадлежит палата.
     *
     * <p><b>@NotNull</b> — обязательно, потому что каждая палата
     * должна быть привязана к отделению. Сервис найдёт отделение
     * по данному {@code id} и выбросит {@code ResourceNotFoundException},
     * если отделение не существует.
     */
    @NotNull(message = "Department ID is required")
    private Long departmentId;
}
