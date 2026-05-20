package com.hospital.service;

import com.hospital.dto.request.SendMessageRequest;
import com.hospital.dto.response.ChatMessageResponse;
import com.hospital.dto.response.ChatRoomResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.ChatMessageRepository;
import com.hospital.repository.ChatRoomRepository;
import com.hospital.repository.UserRepository;
import com.hospital.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для ChatServiceImpl.
 *
 * Стратегия тестирования:
 *   - Все зависимости (репозитории) заменяются Mockito-заглушками (mock).
 *   - Тестируется только бизнес-логика сервиса, без обращений к реальной БД.
 *   - Каждый тест покрывает один сценарий: «счастливый путь» или конкретный
 *     граничный случай / ошибку.
 *
 * @ExtendWith(MockitoExtension.class) — подключает расширение Mockito для JUnit 5:
 *   автоматически создаёт mock-объекты для полей с @Mock и внедряет их
 *   в экземпляр, помеченный @InjectMocks.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    // ─── Заглушки зависимостей ────────────────────────────────────────────────

    /** Репозиторий чат-комнат — подменяется mock-объектом. */
    @Mock
    private ChatRoomRepository chatRoomRepository;

    /** Репозиторий сообщений — подменяется mock-объектом. */
    @Mock
    private ChatMessageRepository chatMessageRepository;

    /** Репозиторий пользователей — подменяется mock-объектом. */
    @Mock
    private UserRepository userRepository;

    /**
     * Тестируемый объект. Mockito создаёт его и внедряет все @Mock-поля
     * через конструктор (благодаря @RequiredArgsConstructor в сервисе).
     */
    @InjectMocks
    private ChatServiceImpl chatService;

    // ─── Тестовые данные ──────────────────────────────────────────────────────

    /** Клиент — инициатор чата. */
    private User clientUser;

    /** Врач — участник чата DOCTOR_CLIENT. */
    private User doctorUser;

    /** Администратор — видит все SUPPORT-чаты. */
    private User adminUser;

    /** Готовая SUPPORT-комната. */
    private ChatRoom supportRoom;

    /** Готовая DOCTOR_CLIENT-комната. */
    private ChatRoom doctorRoom;

    /** Тестовое сообщение в комнате. */
    private ChatMessage chatMessage;

    @BeforeEach
    void setUp() {
        // Создаём пользователей через Builder (Lombok @Builder на сущности User).
        clientUser = User.builder()
                .id(1L).username("client1").fullName("Клиент Иван")
                .role(Role.ROLE_CLIENT).active(true).build();

        doctorUser = User.builder()
                .id(2L).username("doctor1").fullName("Иванов Сергей")
                .role(Role.ROLE_DOCTOR).active(true).build();

        adminUser = User.builder()
                .id(3L).username("admin").fullName("Главный Администратор")
                .role(Role.ROLE_ADMIN).active(true).build();

        // SUPPORT-комната: staffUser == null (любой admin может ответить).
        supportRoom = ChatRoom.builder()
                .id(10L).type(ChatRoomType.SUPPORT)
                .clientUser(clientUser).staffUser(null)
                .createdAt(LocalDateTime.now()).build();

        // DOCTOR_CLIENT-комната: staffUser == doctorUser.
        doctorRoom = ChatRoom.builder()
                .id(20L).type(ChatRoomType.DOCTOR_CLIENT)
                .clientUser(clientUser).staffUser(doctorUser)
                .createdAt(LocalDateTime.now()).build();

        // Сообщение, отправленное клиентом в supportRoom.
        chatMessage = ChatMessage.builder()
                .id(100L).room(supportRoom).sender(clientUser)
                .content("Здравствуйте, нужна помощь")
                .sentAt(LocalDateTime.now()).read(false).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOrCreateSupportRoom
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Если SUPPORT-комнаты ещё нет — должна быть создана новая.
     * Метод должен вызвать save() и вернуть ответ с данными созданной комнаты.
     */
    @Test
    void getOrCreateSupportRoom_whenNoRoomExists_createsAndReturnsNewRoom() {
        // Arrange: комнаты не существует → findByTypeAndClientUserId возвращает пустой Optional.
        when(chatRoomRepository.findByTypeAndClientUserId(ChatRoomType.SUPPORT, 1L))
                .thenReturn(Optional.empty());
        // save() возвращает новую сохранённую комнату.
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(supportRoom);
        // countUnread и findByRoomIdOrderBySentAt — для построения ответа.
        when(chatMessageRepository.countUnread(10L, 1L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(10L)).thenReturn(List.of());

        // Act
        ChatRoomResponse response = chatService.getOrCreateSupportRoom(clientUser);

        // Assert: save вызван ровно один раз, тип и клиент совпадают.
        verify(chatRoomRepository).save(any(ChatRoom.class));
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getType()).isEqualTo("SUPPORT");
        assertThat(response.getClientUserId()).isEqualTo(1L);
        assertThat(response.getStaffUserName()).isEqualTo("Служба поддержки"); // staffUser == null
    }

    /**
     * Если SUPPORT-комната уже существует — должна быть возвращена существующая,
     * без вызова save().
     */
    @Test
    void getOrCreateSupportRoom_whenRoomAlreadyExists_returnsExistingRoomWithoutSave() {
        // Arrange: комната найдена.
        when(chatRoomRepository.findByTypeAndClientUserId(ChatRoomType.SUPPORT, 1L))
                .thenReturn(Optional.of(supportRoom));
        when(chatMessageRepository.countUnread(10L, 1L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(10L)).thenReturn(List.of());

        // Act
        ChatRoomResponse response = chatService.getOrCreateSupportRoom(clientUser);

        // Assert: save НЕ вызван — комната уже существует.
        verify(chatRoomRepository, never()).save(any());
        assertThat(response.getId()).isEqualTo(10L);
    }

    /**
     * Счётчик unread показывает количество сообщений, отправленных
     * другими участниками (исключает сообщения самого viewer'а).
     */
    @Test
    void getOrCreateSupportRoom_unreadCountIsCorrectlyPopulated() {
        when(chatRoomRepository.findByTypeAndClientUserId(ChatRoomType.SUPPORT, 1L))
                .thenReturn(Optional.of(supportRoom));
        // 3 непрочитанных сообщения от администратора.
        when(chatMessageRepository.countUnread(10L, 1L)).thenReturn(3L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(10L)).thenReturn(List.of());

        ChatRoomResponse response = chatService.getOrCreateSupportRoom(clientUser);

        assertThat(response.getUnreadCount()).isEqualTo(3L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOrCreateDoctorRoom
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Нельзя открыть врачебный чат с пользователем, у которого нет роли ROLE_DOCTOR.
     * Ожидаем ResourceNotFoundException (HTTP 404 в контроллере).
     */
    @Test
    void getOrCreateDoctorRoom_whenUserIsNotDoctor_throwsResourceNotFoundException() {
        // Arrange: userId=99 принадлежит обычному клиенту, не врачу.
        User notADoctor = User.builder().id(99L).role(Role.ROLE_CLIENT).build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(notADoctor));

        // Act + Assert: ожидаем ResourceNotFoundException.
        assertThatThrownBy(() -> chatService.getOrCreateDoctorRoom(clientUser, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    /**
     * Если комната уже существует — возвращается существующая без создания новой.
     */
    @Test
    void getOrCreateDoctorRoom_whenRoomExists_returnsExistingRoom() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
        when(chatRoomRepository.findByTypeAndClientUserIdAndStaffUserId(
                ChatRoomType.DOCTOR_CLIENT, 1L, 2L))
                .thenReturn(Optional.of(doctorRoom));
        when(chatMessageRepository.countUnread(20L, 1L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(20L)).thenReturn(List.of());

        ChatRoomResponse response = chatService.getOrCreateDoctorRoom(clientUser, 2L);

        verify(chatRoomRepository, never()).save(any());
        assertThat(response.getType()).isEqualTo("DOCTOR_CLIENT");
        assertThat(response.getStaffUserId()).isEqualTo(2L);
    }

    /**
     * Если комнаты нет — создаётся новая DOCTOR_CLIENT комната.
     */
    @Test
    void getOrCreateDoctorRoom_whenNoRoom_createsNewRoom() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
        when(chatRoomRepository.findByTypeAndClientUserIdAndStaffUserId(
                ChatRoomType.DOCTOR_CLIENT, 1L, 2L))
                .thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(doctorRoom);
        when(chatMessageRepository.countUnread(20L, 1L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(20L)).thenReturn(List.of());

        ChatRoomResponse response = chatService.getOrCreateDoctorRoom(clientUser, 2L);

        verify(chatRoomRepository).save(any(ChatRoom.class));
        assertThat(response.getId()).isEqualTo(20L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getAllSupportRooms / getDoctorRooms / getMyRooms
    // ─────────────────────────────────────────────────────────────────────────

    /** Администратор получает список всех SUPPORT-комнат. */
    @Test
    void getAllSupportRooms_returnsAllRooms() {
        when(chatRoomRepository.findAllSupportRooms()).thenReturn(List.of(supportRoom));
        // viewerUserId == null → unread = 0 (без запроса к countUnread).
        when(chatMessageRepository.findByRoomIdOrderBySentAt(10L)).thenReturn(List.of());

        List<ChatRoomResponse> rooms = chatService.getAllSupportRooms();

        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).getType()).isEqualTo("SUPPORT");
        // Счётчик непрочитанных не запрашивается для null-viewer'а.
        verify(chatMessageRepository, never()).countUnread(anyLong(), anyLong());
    }

    /** Врач получает список комнат только со своими пациентами. */
    @Test
    void getDoctorRooms_returnsOnlyDoctorsRooms() {
        when(chatRoomRepository.findDoctorRoomsByStaffUserId(2L)).thenReturn(List.of(doctorRoom));
        when(chatMessageRepository.countUnread(20L, 2L)).thenReturn(1L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(20L)).thenReturn(List.of());

        List<ChatRoomResponse> rooms = chatService.getDoctorRooms(doctorUser);

        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).getUnreadCount()).isEqualTo(1L);
    }

    /** Клиент видит все свои чаты (поддержка + врачи). */
    @Test
    void getMyRooms_returnsAllRoomsForClient() {
        when(chatRoomRepository.findByClientUserId(1L)).thenReturn(List.of(supportRoom, doctorRoom));
        when(chatMessageRepository.countUnread(anyLong(), eq(1L))).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(anyLong())).thenReturn(List.of());

        List<ChatRoomResponse> rooms = chatService.getMyRooms(clientUser);

        assertThat(rooms).hasSize(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getRoomMessages — доступ и данные
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Клиент может читать сообщения из своей комнаты.
     */
    @Test
    void getRoomMessages_clientAccessingOwnRoom_returnsMessages() {
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(supportRoom));
        when(chatMessageRepository.findByRoomIdOrderBySentAt(10L)).thenReturn(List.of(chatMessage));

        List<ChatMessageResponse> messages = chatService.getRoomMessages(10L, clientUser);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Здравствуйте, нужна помощь");
        assertThat(messages.get(0).getSenderName()).isEqualTo("Клиент Иван");
    }

    /**
     * Клиент не может читать чужую комнату — ожидаем BusinessRuleException (HTTP 403).
     * Проверяет защиту от IDOR (Insecure Direct Object Reference).
     */
    @Test
    void getRoomMessages_clientAccessingAnotherClientsRoom_throwsBusinessRuleException() {
        // Другой клиент в комнате.
        User otherClient = User.builder().id(99L).role(Role.ROLE_CLIENT).build();
        ChatRoom otherRoom = ChatRoom.builder()
                .id(55L).type(ChatRoomType.SUPPORT)
                .clientUser(otherClient).build();

        when(chatRoomRepository.findById(55L)).thenReturn(Optional.of(otherRoom));

        // clientUser (id=1) пытается зайти в комнату другого клиента (clientUser.id=99).
        assertThatThrownBy(() -> chatService.getRoomMessages(55L, clientUser))
                .isInstanceOf(BusinessRuleException.class);
    }

    /**
     * Администратор имеет доступ к любой комнате (нужно для модерации).
     */
    @Test
    void getRoomMessages_adminAccessesAnyRoom_returnsMessages() {
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(supportRoom));
        when(chatMessageRepository.findByRoomIdOrderBySentAt(10L)).thenReturn(List.of(chatMessage));

        // admin не является clientUser этой комнаты, но всё равно должен получить доступ.
        List<ChatMessageResponse> messages = chatService.getRoomMessages(10L, adminUser);

        assertThat(messages).hasSize(1);
    }

    /**
     * Врач не имеет доступа к SUPPORT-комнате (staffUser == null).
     */
    @Test
    void getRoomMessages_doctorAccessingSupportRoom_throwsBusinessRuleException() {
        // staffUser == null в supportRoom → врач не является участником.
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(supportRoom));

        assertThatThrownBy(() -> chatService.getRoomMessages(10L, doctorUser))
                .isInstanceOf(BusinessRuleException.class);
    }

    /**
     * Запрос несуществующей комнаты → ResourceNotFoundException.
     */
    @Test
    void getRoomMessages_roomNotFound_throwsResourceNotFoundException() {
        when(chatRoomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getRoomMessages(999L, clientUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sendMessage
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Отправка сообщения сохраняет его в БД и возвращает DTO с заполненными полями.
     */
    @Test
    void sendMessage_savesMessageAndReturnsResponse() {
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(supportRoom));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Здравствуйте, нужна помощь");

        ChatMessageResponse response = chatService.sendMessage(10L, request, clientUser);

        verify(chatMessageRepository).save(any(ChatMessage.class));
        assertThat(response.getContent()).isEqualTo("Здравствуйте, нужна помощь");
        assertThat(response.getSenderId()).isEqualTo(1L);
        assertThat(response.getSenderRole()).isEqualTo("ROLE_CLIENT");
        assertThat(response.isRead()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // pollMessages
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * pollMessages возвращает только сообщения с id > sinceId.
     * Это реализует механизм short-polling: клиент помнит последний id
     * и запрашивает только новые сообщения.
     */
    @Test
    void pollMessages_returnsSinceId_onlyNewMessages() {
        ChatMessage newMessage = ChatMessage.builder()
                .id(200L).room(supportRoom).sender(adminUser)
                .content("Добро пожаловать!").sentAt(LocalDateTime.now()).build();

        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(supportRoom));
        // Передаём sinceId=100 → репозиторий вернёт только сообщения с id > 100.
        when(chatMessageRepository.findByRoomIdAndIdGreaterThan(10L, 100L))
                .thenReturn(List.of(newMessage));

        List<ChatMessageResponse> result = chatService.pollMessages(10L, 100L, clientUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(200L);
        assertThat(result.get(0).getContent()).isEqualTo("Добро пожаловать!");
    }

    /** Если новых сообщений нет — возвращается пустой список, не null. */
    @Test
    void pollMessages_whenNoNewMessages_returnsEmptyList() {
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(supportRoom));
        when(chatMessageRepository.findByRoomIdAndIdGreaterThan(10L, 100L)).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.pollMessages(10L, 100L, clientUser);

        assertThat(result).isEmpty();
    }

    /**
     * При sinceId=0 возвращаются ВСЕ сообщения комнаты.
     * Это первый вызов от только что открытого чата.
     */
    @Test
    void pollMessages_withSinceIdZero_returnsAllMessages() {
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(supportRoom));
        when(chatMessageRepository.findByRoomIdAndIdGreaterThan(10L, 0L))
                .thenReturn(List.of(chatMessage));

        List<ChatMessageResponse> result = chatService.pollMessages(10L, 0L, clientUser);

        assertThat(result).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // lastMessage в ответе комнаты
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Если комната содержит сообщения — lastMessage и lastMessageAt заполнены.
     */
    @Test
    void getOrCreateSupportRoom_withMessages_populatesLastMessagePreview() {
        when(chatRoomRepository.findByTypeAndClientUserId(ChatRoomType.SUPPORT, 1L))
                .thenReturn(Optional.of(supportRoom));
        when(chatMessageRepository.countUnread(10L, 1L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(10L)).thenReturn(List.of(chatMessage));

        ChatRoomResponse response = chatService.getOrCreateSupportRoom(clientUser);

        assertThat(response.getLastMessage()).isEqualTo("Здравствуйте, нужна помощь");
        assertThat(response.getLastMessageAt()).isNotNull();
    }

    /**
     * Если комната пустая — lastMessage == null, lastMessageAt == null.
     */
    @Test
    void getOrCreateSupportRoom_noMessages_lastMessageIsNull() {
        when(chatRoomRepository.findByTypeAndClientUserId(ChatRoomType.SUPPORT, 1L))
                .thenReturn(Optional.of(supportRoom));
        when(chatMessageRepository.countUnread(10L, 1L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(10L)).thenReturn(List.of());

        ChatRoomResponse response = chatService.getOrCreateSupportRoom(clientUser);

        assertThat(response.getLastMessage()).isNull();
        assertThat(response.getLastMessageAt()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOrCreateDoctorRoom — вызов от имени врача (двунаправленная инициация)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Врач вызывает getOrCreateDoctorRoom с clientUserId — должна быть создана
     * комната, где clientUser = client (id=1), staffUser = doctor (id=2).
     * Это обратный порядок по сравнению с клиентским путём.
     */
    @Test
    void getOrCreateDoctorRoom_calledByDoctor_createsRoomWithCorrectRoles() {
        // doctor (id=2) вызывает с otherUserId=1 (clientUser)
        when(userRepository.findById(1L)).thenReturn(Optional.of(clientUser));
        when(chatRoomRepository.findByTypeAndClientUserIdAndStaffUserId(
                ChatRoomType.DOCTOR_CLIENT, 1L, 2L))
                .thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(doctorRoom);
        when(chatMessageRepository.countUnread(20L, 2L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(20L)).thenReturn(List.of());

        ChatRoomResponse response = chatService.getOrCreateDoctorRoom(doctorUser, 1L);

        // Проверяем, что в сохранённой комнате client и doctor расставлены правильно
        verify(chatRoomRepository).save(argThat(room ->
                room.getType() == ChatRoomType.DOCTOR_CLIENT
                && room.getClientUser().getId().equals(1L)
                && room.getStaffUser().getId().equals(2L)));
        assertThat(response.getId()).isEqualTo(20L);
    }

    /**
     * Врач вызывает getOrCreateDoctorRoom — если комната уже существует,
     * новая не создаётся.
     */
    @Test
    void getOrCreateDoctorRoom_calledByDoctor_returnsExistingRoomWithoutSave() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(clientUser));
        when(chatRoomRepository.findByTypeAndClientUserIdAndStaffUserId(
                ChatRoomType.DOCTOR_CLIENT, 1L, 2L))
                .thenReturn(Optional.of(doctorRoom));
        when(chatMessageRepository.countUnread(20L, 2L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(20L)).thenReturn(List.of());

        ChatRoomResponse response = chatService.getOrCreateDoctorRoom(doctorUser, 1L);

        verify(chatRoomRepository, never()).save(any());
        assertThat(response.getId()).isEqualTo(20L);
        assertThat(response.getType()).isEqualTo("DOCTOR_CLIENT");
    }

    /**
     * Врач вызывает getOrCreateDoctorRoom с otherUserId, у которого нет роли ROLE_CLIENT —
     * ожидаем ResourceNotFoundException.
     * Врач не может открыть чат с администратором или другим врачом.
     */
    @Test
    void getOrCreateDoctorRoom_calledByDoctor_withNonClientOtherUser_throwsException() {
        // adminUser имеет ROLE_ADMIN, а не ROLE_CLIENT
        when(userRepository.findById(3L)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> chatService.getOrCreateDoctorRoom(doctorUser, 3L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("3");
    }

    /**
     * Клиент вызывает getOrCreateDoctorRoom — существующее поведение сохраняется.
     * Проверяет, что рефакторинг под двунаправленность не сломал клиентский путь:
     * clientUser остаётся clientUser, doctorUser — staffUser.
     */
    @Test
    void getOrCreateDoctorRoom_calledByClient_assignsRolesCorrectly() {
        // clientUser (id=1) вызывает с otherUserId=2 (doctorUser)
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
        when(chatRoomRepository.findByTypeAndClientUserIdAndStaffUserId(
                ChatRoomType.DOCTOR_CLIENT, 1L, 2L))
                .thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(doctorRoom);
        when(chatMessageRepository.countUnread(20L, 1L)).thenReturn(0L);
        when(chatMessageRepository.findByRoomIdOrderBySentAt(20L)).thenReturn(List.of());

        chatService.getOrCreateDoctorRoom(clientUser, 2L);

        verify(chatRoomRepository).save(argThat(room ->
                room.getClientUser().getId().equals(1L)
                && room.getStaffUser().getId().equals(2L)));
    }
}
