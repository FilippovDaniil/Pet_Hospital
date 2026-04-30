package com.hospital.dto.request;

import com.hospital.entity.Specialty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для создания нового врача (POST /api/doctors).
 *
 * <p>Содержит только те данные, которые допустимо принимать от клиента
 * при создании. Поля {@code id} и {@code active} устанавливаются
 * автоматически в сервисном слое: {@code id} генерирует БД,
 * {@code active = true} выставляется по умолчанию.
 *
 * <p>Привязка к отделению производится через {@code departmentId} (FK),
 * а не через вложенный объект {@code Department} — это снижает риск
 * случайного изменения связанных сущностей через один запрос.
 */
@Data
public class CreateDoctorRequest {

    /**
     * Полное имя врача (ФИО).
     *
     * <p><b>@NotBlank</b> — обязательное поле; null и пустая строка запрещены.<br>
     * <b>@Size(max = 255)</b> — ограничение длины по размеру колонки в БД.
     */
    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    private String fullName;

    /**
     * Специализация врача — значение перечисления {@code Specialty}
     * (например, THERAPIST, SURGEON, CARDIOLOGIST и т.д.).
     *
     * <p><b>@NotNull</b> — специализация обязательна. Jackson автоматически
     * конвертирует строку из JSON в значение enum; при несовпадении
     * возвращается 400 Bad Request ещё до Bean Validation.
     */
    @NotNull(message = "Specialty is required")
    private Specialty specialty;

    /**
     * Номер кабинета врача (необязательное поле).
     * Не проходит дополнительную валидацию — любая строка допустима.
     */
    private String cabinetNumber;

    /**
     * Номер телефона врача (необязательное поле).
     * Формат не проверяется здесь; при необходимости можно добавить
     * {@code @Pattern} аналогично полю телефона в {@code CreatePatientRequest}.
     */
    private String phone;

    /**
     * Идентификатор отделения, к которому прикреплён врач (необязательное поле).
     *
     * <p>Если передан — сервис найдёт отделение по {@code id} и установит
     * связь. Если {@code null} — врач создаётся без привязки к отделению.
     * Такой подход (передача только ID, а не вложенного объекта) называется
     * «ссылка по идентификатору» и является стандартной практикой в REST API.
     */
    private Long departmentId;
}
