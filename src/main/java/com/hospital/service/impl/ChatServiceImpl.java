package com.hospital.service.impl;

import com.hospital.dto.request.SendMessageRequest;
import com.hospital.dto.response.ChatMessageResponse;
import com.hospital.dto.response.ChatRoomResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.ChatMessageRepository;
import com.hospital.repository.ChatRoomRepository;
import com.hospital.repository.DoctorRepository;
import com.hospital.repository.PatientRepository;
import com.hospital.repository.UserRepository;
import com.hospital.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Реализация сервиса внутреннего чата больницы.
 *
 * Система поддерживает два типа чат-комнат (ChatRoomType):
 *   - SUPPORT        — клиент общается со службой поддержки (без конкретного сотрудника)
 *   - DOCTOR_CLIENT  — клиент общается с конкретным врачом
 *
 * Принцип изоляции данных: каждый участник видит только те комнаты, в которых он
 * является стороной разговора. Администратор — единственная роль с полным доступом
 * ко всем комнатам (необходимо для модерации и разбора конфликтных ситуаций).
 *
 * --- Аннотация @Transactional(readOnly = true) на уровне класса ---
 * Большинство методов сервиса выполняют только чтение данных. Выносить readOnly=true
 * на уровень класса — стандартный паттерн Spring: это позволяет Hibernate пропускать
 * механизм «грязной проверки» (dirty checking) при flush'е сессии, что ускоряет
 * SELECT-запросы. Методы, изменяющие состояние БД, явно переопределяют транзакцию
 * аннотацией @Transactional (без readOnly), получая полноценную read-write транзакцию.
 *
 * --- @RequiredArgsConstructor (Lombok) ---
 * Lombok генерирует конструктор, принимающий все final-поля. Spring использует этот
 * конструктор для внедрения зависимостей (constructor injection). Это предпочтительнее
 * @Autowired на полях, потому что делает зависимости явными и упрощает тестирование
 * (можно передать mock напрямую в конструктор без рефлексии).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    // ─── Зависимости (внедряются через конструктор) ───────────────────────────

    /**
     * Репозиторий чат-комнат. Содержит кастомные JPQL-запросы с JOIN FETCH
     * для загрузки связанных пользователей (clientUser, staffUser) в одном SQL-запросе,
     * что предотвращает проблему N+1 при последующем обращении к полям сущности.
     */
    private final ChatRoomRepository chatRoomRepository;

    /**
     * Репозиторий сообщений чата. Предоставляет методы выборки по комнате,
     * подсчёта непрочитанных и выборки «с ID больше X» (long polling).
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Репозиторий пользователей. Используется только в одном месте — при создании
     * комнаты DOCTOR_CLIENT, где необходимо проверить, что переданный userId
     * действительно принадлежит врачу, а не любому другому пользователю системы.
     */
    private final UserRepository userRepository;

    /** Репозиторий врачей — для поиска doctor-entity по linkedUserId при авто-создании комнат. */
    private final DoctorRepository doctorRepository;

    /** Репозиторий пациентов — для поиска пациентов с клиентскими аккаунтами. */
    private final PatientRepository patientRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Публичные методы интерфейса ChatService
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает комнату поддержки для клиента. Если комната ещё не существует —
     * создаёт её. Это классический паттерн «getOrCreate»:
     *   1. Сначала ищем в БД по уникальному набору критериев.
     *   2. Если не нашли — создаём новую запись.
     *
     * Почему @Transactional (без readOnly) здесь обязателен:
     * Внутри orElseGet() вызывается chatRoomRepository.save(), то есть происходит
     * INSERT. Без явной read-write транзакции Hibernate не сможет сбросить изменения
     * в БД и выбросит исключение. Аннотация на методе переопределяет readOnly=true
     * класса, открывая полноценную транзакцию с поддержкой записи.
     *
     * Почему используется orElseGet(), а не orElse():
     * orElse(value) вычисляет значение-заглушку безусловно, то есть save() вызвался бы
     * даже если комната уже найдена. orElseGet(supplier) вычисляет значение лениво —
     * только если Optional пуст. Это принципиально важно, так как нежелательный save()
     * создал бы дублирующую запись.
     *
     * @param client аутентифицированный пользователь с ролью ROLE_CLIENT;
     *               уже загружен Spring Security из SecurityContext, не требует
     *               повторного запроса к БД
     */
    @Override
    @Transactional
    public ChatRoomResponse getOrCreateSupportRoom(User client) {
        // Ищем существующую SUPPORT-комнату этого клиента.
        // В ChatRoomRepository метод findByTypeAndClientUserId реализован через
        // JPQL с JOIN FETCH clientUser, чтобы поля clientUser были доступны
        // сразу без дополнительных SELECT при обращении к toRoomResponse().
        ChatRoom room = chatRoomRepository
                .findByTypeAndClientUserId(ChatRoomType.SUPPORT, client.getId())
                // Если комнаты нет — создаём новую.
                // Builder-паттерн (Lombok @Builder на сущности) гарантирует,
                // что мы не забудем инициализировать обязательные поля.
                // staffUser намеренно не выставляется: комната поддержки не
                // привязана к конкретному сотруднику до момента ответа.
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .type(ChatRoomType.SUPPORT)
                                .clientUser(client)
                                .build()
                ));

        // Преобразуем сущность в DTO. Передаём client.getId() как viewerUserId,
        // чтобы счётчик непрочитанных считался с точки зрения клиента
        // (исключает его собственные сообщения из счётчика).
        return toRoomResponse(room, client.getId());
    }

    /**
     * Возвращает список всех SUPPORT-комнат для администратора или поддержки.
     *
     * Почему нет @Transactional (только readOnly наследуется от класса):
     * Метод не изменяет данные, поэтому readOnly=true оптимален — Hibernate
     * не будет отслеживать изменения сущностей, загруженных в этой транзакции.
     *
     * viewerUserId передаётся как null — счётчик непрочитанных в toRoomResponse()
     * при null возвращает 0. Это намеренное решение: администратору не нужен
     * счётчик непрочитанных «от своего лица», ему важен контекст каждого клиента.
     */
    @Override
    public List<ChatRoomResponse> getAllSupportRooms() {
        // findAllSupportRooms() — кастомный JPQL-запрос в репозитории:
        //   SELECT r FROM ChatRoom r
        //   JOIN FETCH r.clientUser
        //   LEFT JOIN FETCH r.staffUser
        //   WHERE r.type = 'SUPPORT'
        // JOIN FETCH загружает связанные сущности в ОДНОМ SQL-запросе,
        // избегая N+1: без него обращение к r.getClientUser().getFullName()
        // внутри toRoomResponse() вызвало бы отдельный SELECT на каждую строку.
        return chatRoomRepository.findAllSupportRooms().stream()
                .map(r -> toRoomResponse(r, null))
                .toList();
    }

    /**
     * Возвращает или создаёт комнату типа DOCTOR_CLIENT между конкретным клиентом
     * и конкретным врачом.
     *
     * Логика метода сложнее, чем в getOrCreateSupportRoom(), по двум причинам:
     *   1. Необходима проверка роли: нельзя открыть «врачебный» чат с пользователем,
     *      который не является врачом. Иначе любой userId из фронтенда позволит
     *      обойти ролевую изоляцию данных.
     *   2. Комната уникальна по тройке (type, clientUser, staffUser), поэтому
     *      поиск ведётся по трём критериям одновременно.
     *
     * Почему проверка роли сделана через .filter() до .orElseThrow():
     * Это идиоматичный функциональный стиль — filter() «обнуляет» Optional, если
     * пользователь найден, но его роль не ROLE_DOCTOR, после чего orElseThrow()
     * выбрасывает ResourceNotFoundException. Альтернатива — два отдельных if — менее
     * выразительна и создаёт риск пропустить проверку при будущем рефакторинге.
     *
     * @param client       аутентифицированный клиент
     * @param doctorUserId ID учётной записи (User), которую клиент хочет открыть
     */
    @Override
    @Transactional
    public ChatRoomResponse getOrCreateDoctorRoom(User caller, Long otherUserId) {
        // Определяем, кто из двух сторон клиент, а кто врач.
        // Эндпоинт могут вызывать обе стороны:
        //   - ROLE_CLIENT вызывает POST /api/chat/doctor/{doctorUserId}   → otherUserId = userId врача
        //   - ROLE_DOCTOR вызывает POST /api/chat/doctor/{clientUserId}   → otherUserId = userId клиента
        final User clientUser;
        final User doctorUser;

        if (caller.getRole() == Role.ROLE_DOCTOR) {
            // Врач инициирует чат: caller — врач, otherUserId — userId клиента
            doctorUser = caller;
            clientUser = userRepository.findById(otherUserId)
                    .filter(u -> u.getRole() == Role.ROLE_CLIENT)
                    .orElseThrow(() -> new ResourceNotFoundException("Клиент с userId=" + otherUserId + " не найден"));
        } else {
            // Клиент инициирует чат: caller — клиент, otherUserId — userId врача
            clientUser = caller;
            doctorUser = userRepository.findById(otherUserId)
                    .filter(u -> u.getRole() == Role.ROLE_DOCTOR)
                    .orElseThrow(() -> new ResourceNotFoundException("Врач с userId=" + otherUserId + " не найден"));
        }

        // Ищем существующую комнату для этой пары клиент-врач.
        // Уникальность на уровне БД подкреплена UNIQUE-ограничением (V6-миграция).
        ChatRoom room = chatRoomRepository
                .findByTypeAndClientUserIdAndStaffUserId(ChatRoomType.DOCTOR_CLIENT, clientUser.getId(), doctorUser.getId())
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .type(ChatRoomType.DOCTOR_CLIENT)
                                .clientUser(clientUser)
                                .staffUser(doctorUser)
                                .build()
                ));

        // Счётчик непрочитанных — с точки зрения инициатора вызова.
        return toRoomResponse(room, caller.getId());
    }

    /**
     * Возвращает все DOCTOR_CLIENT-комнаты, где данный врач является staffUser.
     *
     * Бизнес-смысл: врач видит список всех клиентов, написавших ему. Это «входящие»
     * сообщения врача. Каждая комната — отдельный разговор с отдельным пациентом.
     *
     * Счётчик непрочитанных передаётся как doctorUser.getId(), поэтому
     * countUnread() внутри toRoomResponse() подсчитает сообщения, которые
     * НЕ отправлял этот врач и которые ещё не помечены как прочитанные.
     */
    @Override
    @Transactional
    public List<ChatRoomResponse> getDoctorRooms(User doctorUser) {
        // Авто-создаём комнаты для пациентов, у которых есть аккаунт портала,
        // но комнаты ещё нет — чтобы врач сразу видел всех подключённых пациентов.
        doctorRepository.findByLinkedUserIdAndActiveTrue(doctorUser.getId()).ifPresent(doctor ->
            patientRepository.findActivePatientsWithClientUserByDoctorId(doctor.getId()).forEach(patient -> {
                Long clientId = patient.getClientUser().getId();
                chatRoomRepository
                    .findByTypeAndClientUserIdAndStaffUserId(ChatRoomType.DOCTOR_CLIENT, clientId, doctorUser.getId())
                    .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                            .type(ChatRoomType.DOCTOR_CLIENT)
                            .clientUser(patient.getClientUser())
                            .staffUser(doctorUser)
                            .build()
                    ));
            })
        );
        return chatRoomRepository.findDoctorRoomsByStaffUserId(doctorUser.getId()).stream()
                .map(r -> toRoomResponse(r, doctorUser.getId()))
                .toList();
    }

    /**
     * Возвращает все чат-комнаты клиента: и SUPPORT, и DOCTOR_CLIENT.
     *
     * Это «список диалогов» на главном экране клиентского чата. Клиент видит
     * все свои переписки в одном месте независимо от типа.
     *
     * Почему не разделяем на два отдельных запроса (SUPPORT + DOCTOR_CLIENT):
     * Клиенту незачем знать о внутренней классификации комнат на уровне UX.
     * Единый список удобнее, а тип комнаты передаётся в DTO-поле type для
     * фронтенда, который может отобразить иконку/подпись при необходимости.
     */
    @Override
    @Transactional
    public List<ChatRoomResponse> getMyRooms(User client) {
        // Авто-создаём комнату с назначенным врачом, если её ещё нет.
        patientRepository.findActivePatientsWithDoctorByClientUserId(client.getId()).forEach(patient -> {
            User doctorUser = patient.getCurrentDoctor().getLinkedUser();
            chatRoomRepository
                .findByTypeAndClientUserIdAndStaffUserId(ChatRoomType.DOCTOR_CLIENT, client.getId(), doctorUser.getId())
                .orElseGet(() -> chatRoomRepository.save(
                    ChatRoom.builder()
                        .type(ChatRoomType.DOCTOR_CLIENT)
                        .clientUser(client)
                        .staffUser(doctorUser)
                        .build()
                ));
        });
        return chatRoomRepository.findByClientUserId(client.getId()).stream()
                .map(r -> toRoomResponse(r, client.getId()))
                .toList();
    }

    /**
     * Возвращает все сообщения указанной комнаты в хронологическом порядке.
     *
     * Перед загрузкой сообщений вызывается getAccessibleRoom() — централизованная
     * проверка доступа. Это важно: нельзя допустить, чтобы пользователь напрямую
     * передавал roomId произвольной комнаты и читал чужую переписку.
     *
     * Порядок сортировки (ORDER BY sentAt ASC) гарантируется на уровне репозитория,
     * а не в памяти, что важно для корректности при большом количестве сообщений.
     */
    @Override
    @Transactional
    public List<ChatMessageResponse> getRoomMessages(Long roomId, User requester) {
        ChatRoom room = getAccessibleRoom(roomId, requester);
        // Помечаем входящие сообщения как прочитанные — сбрасывает unread badge у собеседника.
        // UPDATE WHERE sender_id != requester.id AND read = false — только входящие и непрочитанные.
        chatMessageRepository.markMessagesAsRead(room.getId(), requester.getId());
        return chatMessageRepository.findByRoomIdOrderBySentAt(room.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    /**
     * Отправляет новое сообщение в комнату.
     *
     * @Transactional обязателен: метод выполняет INSERT в chat_message.
     * Без него save() работал бы вне транзакции, что запрещено при readOnly=true.
     *
     * Почему сначала проверяем доступ, а потом сохраняем:
     * Это базовый принцип «fail fast» — дорогостоящую операцию (INSERT) имеет
     * смысл выполнять только после успешного прохождения контроля доступа.
     * Если пользователь не имеет права писать в эту комнату, мы бросаем
     * исключение до любого изменения БД.
     *
     * @param roomId  ID комнаты, в которую отправляется сообщение
     * @param request DTO с полем content — текстом сообщения
     * @param sender  аутентифицированный отправитель
     */
    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long roomId, SendMessageRequest request, User sender) {
        // Проверяем, что отправитель является участником комнаты.
        // Нельзя полагаться только на то, что roomId взят из «своего» интерфейса:
        // злоумышленник может подменить ID в запросе.
        ChatRoom room = getAccessibleRoom(roomId, sender);

        // Создаём и сохраняем сообщение.
        // ChatMessage.builder() — Lombok @Builder на сущности ChatMessage.
        // Поля sentAt и read устанавливаются автоматически через @PrePersist
        // или @Column(columnDefinition="DEFAULT ...") в миграции:
        //   sentAt = NOW(), read = false.
        // Это обеспечивает единое время сервера, а не время клиента.
        ChatMessage message = chatMessageRepository.save(
                ChatMessage.builder()
                        .room(room)
                        .sender(sender)
                        .content(request.getContent())
                        .build()
        );

        // Возвращаем созданное сообщение как DTO.
        // После save() сущность содержит назначенный БД id и sentAt,
        // поэтому DTO будет содержать актуальные данные.
        return toMessageResponse(message);
    }

    /**
     * Long polling: возвращает сообщения комнаты с ID строго больше sinceId.
     *
     * Механизм работы long polling на клиенте:
     *   1. Клиент запрашивает GET /rooms/{id}/messages?since=42.
     *   2. Сервер возвращает все новые сообщения с id > 42.
     *   3. Клиент получает последний id из ответа и повторяет запрос с ним.
     *
     * Почему используется ID как курсор, а не timestamp:
     *   - ID (BIGINT SERIAL) монотонно возрастает в пределах одной таблицы.
     *   - Временные метки могут совпадать при одновременной отправке нескольких
     *     сообщений, что приведёт к пропускам или дублям при polling.
     *   - ID-курсор гарантирует точную выборку «всего, что появилось позже».
     *
     * @param sinceId ID последнего известного клиенту сообщения;
     *                первый запрос передаёт sinceId=0 для получения всех сообщений
     */
    @Override
    @Transactional
    public List<ChatMessageResponse> pollMessages(Long roomId, Long sinceId, User requester) {
        ChatRoom room = getAccessibleRoom(roomId, requester);
        // Сбрасываем unread badge при каждом poll — даже если новых сообщений нет.
        // Это гарантирует, что значок «непрочитанных» исчезнет в течение одного poll-интервала.
        chatMessageRepository.markMessagesAsRead(room.getId(), requester.getId());
        return chatMessageRepository.findByRoomIdAndIdGreaterThan(room.getId(), sinceId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Приватные вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Центральный метод контроля доступа к комнате.
     *
     * Вынесен в отдельный приватный метод по принципу DRY: проверка доступа
     * нужна в трёх публичных методах (getRoomMessages, sendMessage, pollMessages).
     * Дублирование этой логики было бы источником ошибок при рефакторинге.
     *
     * Механизм проверки — switch expression (Java 14+):
     * В отличие от switch statement, switch expression:
     *   - exhaustive: компилятор требует покрыть все значения enum
     *   - возвращает значение напрямую (нет break/return в каждой ветке)
     *   - безопаснее при добавлении новых ролей — компилятор укажет на непокрытый case
     *
     * Правила доступа по ролям:
     *   ROLE_ADMIN  — полный доступ ко всем комнатам (модерация, аудит, помощь)
     *   ROLE_CLIENT — только комнаты, где он является clientUser
     *   ROLE_DOCTOR — только комнаты, где он является staffUser
     *   другие роли — доступ запрещён (медсёстры, прочий персонал не ведут чат)
     *
     * Почему для ROLE_DOCTOR проверяется staffUser != null:
     * Комнаты типа SUPPORT не имеют staffUser (null). Без проверки null
     * обращение к room.getStaffUser().getId() выбросило бы NullPointerException,
     * а не корректный отказ в доступе.
     *
     * @param roomId ID комнаты из HTTP-запроса (не доверенный ввод)
     * @param user   аутентифицированный пользователь из SecurityContext
     * @return сущность ChatRoom если доступ разрешён
     * @throws ResourceNotFoundException если комната не существует (HTTP 404)
     * @throws BusinessRuleException     если пользователь не является участником (HTTP 422/403)
     */
    private ChatRoom getAccessibleRoom(Long roomId, User user) {
        // Загружаем комнату по ID. findById всегда выполняет SELECT по PK —
        // это самый быстрый тип запроса, который БД выполняет по индексу.
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Чат-комната #" + roomId + " не найдена"));

        // Определяем, является ли пользователь легитимным участником комнаты.
        // switch expression проверяет роль без instanceof/cast, опираясь на
        // хранящееся в User поле role (enum Role), что надёжнее проверки классов.
        boolean isParticipant = switch (user.getRole()) {
            case ROLE_ADMIN -> true;                         // админ видит всё
            case ROLE_CLIENT -> room.getClientUser().getId().equals(user.getId());
            case ROLE_DOCTOR -> room.getStaffUser() != null && room.getStaffUser().getId().equals(user.getId());
            default -> false;
        };

        // Если пользователь не является участником — запрещаем доступ.
        // BusinessRuleException семантически точнее SecurityException или
        // AccessDeniedException: нарушение не аутентификации, а бизнес-правила
        // «видеть только свои переписки».
        if (!isParticipant) {
            throw new BusinessRuleException("Нет доступа к этой чат-комнате");
        }
        return room;
    }

    /**
     * Преобразует сущность ChatRoom в DTO ChatRoomResponse.
     *
     * Это паттерн «ручного маппинга» без использования MapStruct или ModelMapper.
     * Причина: поля DTO (unreadCount, lastMessage) вычисляются динамически через
     * отдельные запросы к БД, а не берутся напрямую из полей сущности.
     * Автоматические маперы не умеют обрабатывать такую логику без кастомизации,
     * поэтому явный маппинг здесь проще и читаемее.
     *
     * Проблема N+1 запросов и почему она не возникает здесь:
     * Когда этот метод вызывается в цикле (через .map() в stream), каждый вызов
     * выполняет два дополнительных запроса к chatMessageRepository:
     *   1. countUnread(roomId, viewerUserId)
     *   2. findByRoomIdOrderBySentAt(roomId) — для получения последнего сообщения
     * Формально это N+1 по сообщениям для N комнат. Для учебного проекта это
     * приемлемо. В production следует заменить на один запрос с агрегацией
     * (например, через DTO-проекцию или native query с оконными функциями).
     *
     * Обработка staffUser == null:
     * Комнаты SUPPORT не имеют назначенного сотрудника. Для таких комнат
     * staffUserName подставляется как «Служба поддержки» — это «отображаемое имя»
     * для интерфейса, когда реального сотрудника ещё нет.
     *
     * Ленивая загрузка (Lazy Loading) и открытая транзакция:
     * clientUser и staffUser в ChatRoom могут быть помечены как FetchType.LAZY.
     * Обращение к room.getClientUser().getFullName() внутри этого метода безопасно,
     * ПОКА метод вызывается в рамках транзакции (т.е. из @Transactional-метода).
     * Если бы этот метод вызывался вне транзакции, Hibernate выбросил бы
     * LazyInitializationException. Именно поэтому все публичные методы класса
     * охвачены транзакцией (либо через @Transactional, либо через readOnly=true).
     *
     * @param room          сущность комнаты с загруженными clientUser и staffUser
     * @param viewerUserId  ID пользователя, с чьей точки зрения считаются непрочитанные;
     *                      null означает «не считать непрочитанные» (для администратора)
     */
    private ChatRoomResponse toRoomResponse(ChatRoom room, Long viewerUserId) {
        // Подсчёт непрочитанных сообщений: SELECT COUNT(*) WHERE room_id=? AND sender_id != ? AND read=false.
        // Если viewerUserId == null (режим администратора) — не вычисляем, возвращаем 0.
        long unread = viewerUserId != null
                ? chatMessageRepository.countUnread(room.getId(), viewerUserId)
                : 0L;

        // Загружаем ВСЕ сообщения комнаты для получения последнего.
        // Это неэффективно при большом количестве сообщений — в production
        // следует заменить на отдельный запрос типа findTopByRoomIdOrderBySentAtDesc.
        // Для учебного проекта допустимо.
        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderBySentAt(room.getId());
        ChatMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);

        // Строим DTO через Lombok @Builder — все поля явны, нет скрытого сеттера.
        return ChatRoomResponse.builder()
                .id(room.getId())
                .type(room.getType().name())                   // enum → String для JSON
                .clientUserId(room.getClientUser().getId())
                .clientUserName(room.getClientUser().getFullName())
                // staffUser может быть null для SUPPORT-комнат — используем тернарный оператор
                .staffUserId(room.getStaffUser() != null ? room.getStaffUser().getId() : null)
                .staffUserName(room.getStaffUser() != null ? room.getStaffUser().getFullName() : "Служба поддержки")
                .createdAt(room.getCreatedAt())
                .unreadCount(unread)
                // Поля последнего сообщения — null если история пуста (новая комната)
                .lastMessage(last != null ? last.getContent() : null)
                .lastMessageAt(last != null ? last.getSentAt() : null)
                .build();
    }

    /**
     * Преобразует сущность ChatMessage в DTO ChatMessageResponse.
     *
     * Этот метод — пример простого маппинга «один-к-одному»: каждое поле DTO
     * соответствует полю сущности или связанной сущности (sender).
     *
     * Обращение к message.getSender() и message.getRoom() безопасно в рамках
     * открытой транзакции. Если sender помечен FetchType.LAZY, Hibernate выполнит
     * SELECT при первом обращении к getSender(). Чтобы избежать этого в цикле,
     * репозиторий должен использовать JOIN FETCH sender в запросе
     * findByRoomIdOrderBySentAt.
     *
     * senderRole передаётся как строка (enum.name()), чтобы фронтенд мог
     * отображать роль отправителя (например, «Врач» / «Клиент») без знания
     * Java-enum на стороне JavaScript.
     *
     * @param message сущность сообщения с загруженными room и sender
     */
    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoom().getId())             // обратная ссылка на комнату
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderRole(message.getSender().getRole().name()) // ROLE_CLIENT / ROLE_DOCTOR / ROLE_ADMIN
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .read(message.isRead())                        // false до явного подтверждения прочтения
                .build();
    }
}
