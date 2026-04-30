package com.hospital.dto.response;

import com.hospital.entity.Gender;
import com.hospital.entity.PatientStatus;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO ответа с данными пациента (GET /api/patients/{id} и другие эндпоинты).
 *
 * <p>Этот класс формируется MapStruct-маппером из Entity {@code Patient}.
 * Часть полей берётся напрямую из {@code Patient}, часть — из связанных
 * объектов через навигацию по ссылкам (вложенные сущности).
 *
 * <p><b>Поля из вложенных объектов (маппинг MapStruct):</b>
 * <ul>
 *   <li>{@code currentDoctorId} ← {@code patient.currentDoctor.id}</li>
 *   <li>{@code currentDoctorName} ← {@code patient.currentDoctor.fullName}</li>
 *   <li>{@code currentWardId} ← {@code patient.currentWard.id}</li>
 *   <li>{@code currentWardNumber} ← {@code patient.currentWard.wardNumber}</li>
 * </ul>
 * MapStruct генерирует код маппинга на этапе компиляции — в отличие от
 * рефлексивных маппингов (ModelMapper), это даёт нулевые накладные расходы
 * во время выполнения и раннее обнаружение ошибок.
 *
 * <p>Entity {@code Patient} не отправляется клиенту напрямую, потому что
 * содержит JPA-аннотации, ленивые коллекции и поля, которые не нужны
 * на фронтенде (например, список всех платных услуг).
 */
@Data
public class PatientResponse {

    /** Уникальный идентификатор пациента в БД. */
    private Long id;

    /** Полное имя пациента. */
    private String fullName;

    /** Дата рождения пациента. */
    private LocalDate birthDate;

    /** Пол пациента (MALE / FEMALE). */
    private Gender gender;

    /** СНИЛС — уникальный идентификатор пациента в системе ОПС. */
    private String snils;

    /** Контактный телефон пациента. */
    private String phone;

    /** Адрес проживания пациента. */
    private String address;

    /** Дата первичной регистрации пациента в системе. */
    private LocalDate registrationDate;

    /**
     * Текущий статус пациента (REGISTERED, HOSPITALIZED, DISCHARGED и т.д.).
     * Изменяется бизнес-операциями (госпитализация, выписка), а не напрямую.
     */
    private PatientStatus status;

    /**
     * ID лечащего врача — берётся из {@code patient.currentDoctor.id}.
     * Может быть {@code null}, если врач ещё не назначен.
     */
    private Long currentDoctorId;

    /**
     * Полное имя лечащего врача — берётся из {@code patient.currentDoctor.fullName}.
     * Удобно для отображения без дополнительного запроса к /api/doctors.
     */
    private String currentDoctorName;

    /**
     * ID палаты, в которой находится пациент — из {@code patient.currentWard.id}.
     * Может быть {@code null}, если пациент не госпитализирован.
     */
    private Long currentWardId;

    /**
     * Номер палаты — берётся из {@code patient.currentWard.wardNumber}.
     * Отображается напрямую без дополнительного запроса к /api/wards.
     */
    private String currentWardNumber;

    /**
     * Флаг активности пациента в системе.
     * {@code false} означает «мягкое удаление» (soft delete) —
     * запись сохраняется в БД, но скрывается из стандартных выборок.
     */
    private boolean active;
}
