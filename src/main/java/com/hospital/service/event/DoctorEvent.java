package com.hospital.service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Доменное событие: изменение данных врача.
 *
 * =====================================================================
 * НАЗНАЧЕНИЕ И СТРУКТУРА СОБЫТИЯ
 * =====================================================================
 *
 * DoctorEvent публикуется при создании, обновлении или удалении врача.
 * Подписчики (другие микросервисы или модули) могут реагировать на эти
 * события для синхронизации своих данных без прямых вызовов.
 *
 * Пример use-case: модуль расписания (Schedule Service) хранит имена врачей
 * в собственной БД (денормализация для производительности). При получении
 * DoctorEvent с eventType="DOCTOR_UPDATED" он обновит кешированные данные.
 *
 * Обязательные поля (общие для всех событий системы):
 *
 *   eventId (String / UUID):
 *     Уникальный идентификатор этого конкретного события.
 *     Позволяет потребителю гарантировать идемпотентность: повторная
 *     доставка одного и того же сообщения из Kafka не приведёт
 *     к двойному обновлению данных о враче.
 *     Проверяется через outbox_event.eventId (processed = true/false).
 *
 *   eventType (String):
 *     Тип действия: "DOCTOR_CREATED", "DOCTOR_UPDATED", "DOCTOR_DELETED".
 *     Один топик doctor-events несёт все типы — eventType помогает
 *     потребителю выбрать нужную ветку обработки.
 *
 *   occurredAt (LocalDateTime):
 *     Бизнес-время события — когда изменение произошло в домене.
 *     Важно при восстановлении состояния по журналу событий (event sourcing-like).
 *
 * Бизнес-поля:
 *
 *   doctorId:      ID врача в основной БД.
 *   doctorName:    Имя врача (денормализовано — потребитель может отобразить его
 *                  в UI без дополнительного HTTP-запроса к Doctor Service).
 *   departmentId:  К какому отделению относится врач. Позволяет потребителям,
 *                  подписанным на события врачей, фильтровать по отделению.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorEvent {

    // UUID события — уникальный идентификатор для идемпотентности и трассировки в логах.
    private String eventId;

    // Тип действия: "DOCTOR_CREATED" / "DOCTOR_UPDATED" / "DOCTOR_DELETED".
    // Потребитель смотрит на это поле, чтобы понять, какую логику применить.
    private String eventType;

    // Бизнес-время события. Устанавливается как LocalDateTime.now() в сервисе при публикации.
    private LocalDateTime occurredAt;

    // ID врача — первичный ключ в таблице doctor.
    private Long doctorId;

    // Имя врача — денормализованное поле для удобства потребителей.
    private String doctorName;

    // ID отделения, к которому принадлежит врач.
    // Полезен для агрегации статистики по отделениям на стороне потребителя.
    private Long departmentId;
}
