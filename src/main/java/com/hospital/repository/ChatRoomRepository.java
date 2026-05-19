package com.hospital.repository;

import com.hospital.entity.ChatRoom;
import com.hospital.entity.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с чат-комнатами ({@link ChatRoom}).
 *
 * <p>Ключевые особенности запросов:
 * <ul>
 *   <li><b>JOIN FETCH везде, где нужен clientUser</b>: поле {@code clientUser} в
 *       {@link ChatRoom} объявлено LAZY. Явный {@code JOIN FETCH r.clientUser} в
 *       JPQL-запросах предотвращает N+1-проблему: без него Hibernate загружал бы
 *       каждого клиента отдельным SELECT при итерации по списку комнат.</li>
 *   <li><b>Derived query methods</b>: Spring Data автоматически генерирует SQL
 *       по именам методов ({@code findByTypeAndClientUserId}). Они используются
 *       там, где нет необходимости в JOIN FETCH (достаточно получить одну комнату
 *       по ключу).</li>
 *   <li><b>Сортировка по createdAt DESC</b>: все списочные запросы возвращают
 *       комнаты от новых к старым, что соответствует ожидаемому порядку отображения
 *       в интерфейсе.</li>
 * </ul>
 */
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * Возвращает все чат-комнаты клиента (как SUPPORT, так и DOCTOR_CLIENT),
     * отсортированные от новых к старым.
     *
     * <p>{@code JOIN FETCH r.clientUser} — загружает объект клиента за один запрос,
     * предотвращая N+1. Используется при отображении списка чатов на клиентском портале.
     *
     * @param clientId идентификатор клиентского пользователя ({@link com.hospital.entity.User#getId()})
     * @return список комнат данного клиента, упорядоченный по дате создания (новые первыми)
     */
    @Query("SELECT r FROM ChatRoom r JOIN FETCH r.clientUser WHERE r.clientUser.id = :clientId ORDER BY r.createdAt DESC")
    List<ChatRoom> findByClientUserId(@Param("clientId") Long clientId);

    /**
     * Ищет единственную SUPPORT-комнату для указанного клиента.
     *
     * <p>Derived query method: Spring Data генерирует запрос автоматически по имени метода.
     * Каждый клиент может иметь не более одной SUPPORT-комнаты — это обеспечивается
     * сервисным слоем через паттерн get-or-create.
     *
     * @param type          тип комнаты (ожидается {@code ChatRoomType.SUPPORT})
     * @param clientUserId  идентификатор клиентского пользователя
     * @return Optional с комнатой, или пустой Optional если комнаты нет
     */
    Optional<ChatRoom> findByTypeAndClientUserId(ChatRoomType type, Long clientUserId);

    /**
     * Ищет DOCTOR_CLIENT-комнату для конкретной пары клиент–врач.
     *
     * <p>Derived query method: Spring Data генерирует запрос по трём полям.
     * Уникальность комбинации (type, clientUser, staffUser) гарантирует, что
     * для каждой пары клиент–врач существует не более одной комнаты.
     *
     * @param type          тип комнаты (ожидается {@code ChatRoomType.DOCTOR_CLIENT})
     * @param clientUserId  идентификатор клиентского пользователя
     * @param staffUserId   идентификатор пользователя-врача
     * @return Optional с комнатой, или пустой Optional если комнаты нет
     */
    Optional<ChatRoom> findByTypeAndClientUserIdAndStaffUserId(ChatRoomType type, Long clientUserId, Long staffUserId);

    /**
     * Возвращает все SUPPORT-комнаты для отображения в очереди обращений у администраторов.
     *
     * <p>Фильтрация по типу {@code 'SUPPORT'} вынесена прямо в JPQL-строку
     * (не через параметр), так как метод имеет единственное предназначение.
     * {@code JOIN FETCH r.clientUser} — предотвращает N+1: при отображении
     * списка обращений имя клиента нужно для каждой строки.
     *
     * @return список всех SUPPORT-комнат, упорядоченный по дате создания (новые первыми)
     */
    @Query("SELECT r FROM ChatRoom r JOIN FETCH r.clientUser WHERE r.type = 'SUPPORT' ORDER BY r.createdAt DESC")
    List<ChatRoom> findAllSupportRooms();

    /**
     * Возвращает все DOCTOR_CLIENT-комнаты, в которых данный врач является собеседником.
     *
     * <p>Используется врачом для просмотра всех своих переписок с клиентами.
     * {@code JOIN FETCH r.clientUser} — предотвращает N+1: имя клиента отображается
     * в каждой строке списка чатов врача.
     *
     * @param staffId идентификатор пользователя-врача ({@link com.hospital.entity.User#getId()})
     * @return список DOCTOR_CLIENT-комнат данного врача, упорядоченный от новых к старым
     */
    @Query("SELECT r FROM ChatRoom r JOIN FETCH r.clientUser WHERE r.type = 'DOCTOR_CLIENT' AND r.staffUser.id = :staffId ORDER BY r.createdAt DESC")
    List<ChatRoom> findDoctorRoomsByStaffUserId(@Param("staffId") Long staffId);
}
