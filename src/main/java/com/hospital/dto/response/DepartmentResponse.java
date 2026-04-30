package com.hospital.dto.response;

import lombok.Data;

/**
 * DTO ответа с данными отделения (GET /api/departments/{id} и другие эндпоинты).
 *
 * <p>Формируется MapStruct-маппером из Entity {@code Department}.
 *
 * <p><b>Поля из вложенного объекта (маппинг MapStruct):</b>
 * <ul>
 *   <li>{@code headDoctorId} ← {@code department.headDoctor.id}</li>
 *   <li>{@code headDoctorName} ← {@code department.headDoctor.fullName}</li>
 * </ul>
 * Маппер «разворачивает» вложенную Entity врача в два плоских поля —
 * идентификатор и имя. Это позволяет избежать рекурсии в JSON
 * (Department → Doctor → Department → ...) и передавать ровно
 * столько информации, сколько нужно для отображения заголовка отделения.
 *
 * <p>Список палат и врачей отделения намеренно не включён —
 * он доступен через отдельные эндпоинты с пагинацией.
 */
@Data
public class DepartmentResponse {

    /** Уникальный идентификатор отделения в БД. */
    private Long id;

    /** Название отделения (например, «Кардиология»). */
    private String name;

    /** Описание профиля и специализации отделения. */
    private String description;

    /** Физическое расположение (этаж, корпус, номера кабинетов). */
    private String location;

    /**
     * ID заведующего отделением — берётся из {@code department.headDoctor.id}.
     * Может быть {@code null}, если заведующий не назначен.
     */
    private Long headDoctorId;

    /**
     * Полное имя заведующего — берётся из {@code department.headDoctor.fullName}.
     * Клиент может сразу отобразить имя без дополнительного GET /api/doctors/{id}.
     */
    private String headDoctorName;
}
