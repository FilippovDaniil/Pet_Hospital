package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Сущность «Сообщение чата» — единица переписки внутри {@link ChatRoom}.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Иммутабельность содержимого</b>: поле {@code content} не помечено
 *       {@code updatable = false}, однако логика приложения не предусматривает
 *       редактирование отправленных сообщений. Сообщения только создаются и читаются.</li>
 *   <li><b>Флаг {@code read} вместо {@code isRead}</b>: поле намеренно названо
 *       {@code read}, а не {@code isRead}. Lombok генерирует accessor {@code isRead()},
 *       следуя JavaBeans-конвенции для булевых полей. Если бы поле называлось
 *       {@code isRead}, Lombok создал бы метод {@code isIsRead()}, что является
 *       дублированием префикса.</li>
 *   <li><b>{@code @Builder.Default} для {@code read}</b>: без этой аннотации Lombok
 *       {@code @Builder} игнорирует инициализатор поля ({@code = false}), и все
 *       сообщения, созданные через билдер, имели бы {@code read = false} только
 *       случайно (значение по умолчанию для примитива). {@code @Builder.Default}
 *       делает намерение явным и защищает от ошибок при рефакторинге.</li>
 *   <li><b>Все связи LAZY</b>: {@code room} и {@code sender} не загружаются
 *       автоматически. В запросах репозитория применяется JOIN FETCH для загрузки
 *       отправителя за один запрос, так как имя отправителя всегда нужно при
 *       отображении сообщений.</li>
 * </ul>
 */
@Entity
@Table(name = "chat_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatMessage {

    /**
     * Суррогатный первичный ключ, генерируется базой данных.
     * Участвует в equals/hashCode для корректного сравнения сущностей.
     * Также используется как курсор в polling-запросах: клиент передаёт
     * {@code sinceId}, и возвращаются только сообщения с {@code id > sinceId}.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Чат-комната, которой принадлежит сообщение.
     *
     * <p>{@code fetch = FetchType.LAZY} — комната не загружается при каждом чтении
     * сообщения. Как правило, при работе с сообщениями уже известен ID комнаты,
     * поэтому повторная загрузка объекта комнаты избыточна.
     *
     * <p>{@code @ToString.Exclude} — предотвращает рекурсию: ChatMessage → ChatRoom →
     * List(ChatMessage) → ChatMessage → ...
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @ToString.Exclude
    private ChatRoom room;

    /**
     * Пользователь, отправивший сообщение (клиент, врач или администратор).
     *
     * <p>{@code fetch = FetchType.LAZY} — отправитель не загружается автоматически.
     * В запросах репозитория используется {@code JOIN FETCH m.sender}, поскольку
     * имя отправителя требуется при каждом отображении списка сообщений.
     * JOIN FETCH заменяет два отдельных SELECT одним запросом с JOIN.
     *
     * <p>{@code @ToString.Exclude} — предотвращает циклическую рекурсию в toString().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @ToString.Exclude
    private User sender;

    /**
     * Текстовое содержимое сообщения.
     * {@code columnDefinition = "TEXT"} — в PostgreSQL тип TEXT не ограничен
     * длиной, в отличие от VARCHAR(n). Это позволяет клиентам и врачам
     * отправлять сообщения любой длины без усечения данных.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Момент отправки сообщения. Заполняется автоматически в {@link #onCreate()}.
     * {@code updatable = false} — временная метка отправки неизменна после сохранения.
     * Используется для хронологической сортировки сообщений в комнате.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt;

    /**
     * Флаг прочтения сообщения получателем.
     *
     * <p>Поле названо {@code read}, а не {@code isRead}: Lombok генерирует accessor
     * {@code isRead()} для булевых полей по JavaBeans-конвенции. Название {@code isRead}
     * привело бы к двойному префиксу — методу {@code isIsRead()}.
     *
     * <p>{@code @Builder.Default} — устанавливает значение {@code false} при создании
     * объекта через Lombok-билдер. Без этой аннотации Lombok игнорирует инициализатор
     * поля при использовании {@code @Builder}, что делало бы умолчание неявным.
     * Явное {@code @Builder.Default} документирует намерение: новые сообщения
     * всегда создаются непрочитанными.
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    /**
     * JPA lifecycle-колбэк, вызываемый Hibernate перед первым сохранением сущности (INSERT).
     * Устанавливает {@code sentAt} в текущий момент времени сервера.
     * Явный вызов из прикладного кода не требуется.
     */
    @PrePersist
    void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
