package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Сущность «Чат-комната» — канал переписки между клиентом и персоналом больницы.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Два типа комнат ({@link ChatRoomType})</b>:
 *       {@code SUPPORT} — клиент пишет в общую поддержку, ответить может любой
 *       администратор; {@code DOCTOR_CLIENT} — приватный чат клиента с конкретным
 *       врачом. Разделение на типы позволяет реализовать разные правила маршрутизации
 *       и отображения без создания отдельных таблиц.</li>
 *   <li><b>Nullable {@code staffUser} для SUPPORT-комнат</b>: ни один конкретный
 *       сотрудник не «владеет» комнатой поддержки — её видят все администраторы.
 *       Это упрощает реализацию очереди обращений без явного назначения оператора.</li>
 *   <li><b>Неизменяемый {@code createdAt}</b>: метка времени создания проставляется
 *       однократно в {@link #onCreate()} и заблокирована от изменений через
 *       {@code updatable = false}. Используется для хронологической сортировки комнат.</li>
 *   <li><b>Все связи LAZY</b>: {@code clientUser} и {@code staffUser} не загружаются
 *       автоматически. В запросах репозитория используется JOIN FETCH для предотвращения
 *       N+1-проблемы при работе со списком комнат.</li>
 * </ul>
 */
@Entity
@Table(name = "chat_room")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatRoom {

    /**
     * Суррогатный первичный ключ, генерируется базой данных.
     * Участвует в equals/hashCode для корректного сравнения сущностей.
     * Аннотация {@code @EqualsAndHashCode.Include} означает, что Lombok включает
     * только это поле при генерации equals/hashCode (см. {@code onlyExplicitlyIncluded = true}).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Тип чат-комнаты: {@code SUPPORT} или {@code DOCTOR_CLIENT}.
     *
     * <p>{@code @Enumerated(EnumType.STRING)} — значение enum сохраняется в базе как строка,
     * а не как числовой индекс. Это защищает данные: если добавить новую константу в
     * середину enum, числовые индексы других значений сместятся, но строки останутся
     * корректными. Данные в БД читаются напрямую без дополнительного маппинга.
     *
     * <p>{@code length = 20} — достаточная длина для текущих значений enum с запасом
     * на расширение (например, добавление типа {@code NURSE_CLIENT}) без изменения схемы.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomType type;

    /**
     * Клиент (пользователь с ролью ROLE_CLIENT), которому принадлежит комната.
     *
     * <p>{@code fetch = FetchType.LAZY} — объект User не загружается автоматически
     * при чтении ChatRoom из базы. Это снижает количество JOIN-запросов в сценариях,
     * где данные клиента не нужны. Когда они нужны, запросы репозитория используют
     * {@code JOIN FETCH r.clientUser} для загрузки за один запрос.
     *
     * <p>{@code @ToString.Exclude} — исключает поле из генерируемого Lombok методом
     * toString(). Предотвращает циклическую рекурсию: ChatRoom → User → список ролей → ...
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id", nullable = false)
    @ToString.Exclude
    private User clientUser;

    /**
     * Сотрудник (администратор или врач), участвующий в чате со стороны больницы.
     *
     * <p>Равен {@code null} для комнат типа {@code SUPPORT}: любой администратор
     * может зайти в такую комнату и ответить клиенту. Это позволяет нескольким
     * администраторам работать с общей очередью обращений без закрепления за конкретным
     * оператором.
     *
     * <p>Для комнат типа {@code DOCTOR_CLIENT} содержит учётную запись ({@link User})
     * врача, с которым клиент ведёт переписку. Уникальность пары (client, staff)
     * в рамках типа обеспечивается на уровне сервиса — дубликаты комнат не создаются,
     * а возвращается существующая (паттерн get-or-create).
     *
     * <p>{@code @ToString.Exclude} — аналогично clientUser, предотвращает рекурсию в toString().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_user_id")
    @ToString.Exclude
    private User staffUser;

    /**
     * Момент создания комнаты в системе.
     * Заполняется автоматически в {@link #onCreate()} перед первым сохранением.
     * {@code updatable = false} — запрещает изменение поля через UPDATE-запросы Hibernate,
     * гарантируя неизменность временной метки. Используется для сортировки списка
     * комнат в интерфейсе (новые — первыми).
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA lifecycle-колбэк, вызываемый Hibernate перед первым сохранением сущности (INSERT).
     * Устанавливает {@code createdAt} в текущий момент времени сервера.
     * Явный вызов из прикладного кода не требуется — Hibernate вызывает метод автоматически.
     */
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
