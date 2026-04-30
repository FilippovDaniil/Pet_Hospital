package com.hospital.service;

import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.dto.request.UpdatePatientRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.PatientStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Контракт сервисного слоя для работы с пациентами.
 *
 * --- Зачем интерфейс, а не сразу реализация? ---
 * В классической трёхзвенной архитектуре (Controller → Service → Repository)
 * принято объявлять сервисы через интерфейс. Причины:
 *
 * 1. Принцип инверсии зависимостей (DIP из SOLID):
 *    Controller зависит от абстракции PatientService, а не от конкретного класса
 *    PatientServiceImpl. Это означает, что реализацию можно поменять (или подменить
 *    тестовым Mock-объектом), не изменяя код контроллера.
 *
 * 2. DI (Dependency Injection) в Spring:
 *    Spring внедряет зависимости по типу. Когда контроллер объявляет:
 *      private final PatientService patientService;
 *    Spring ищет Bean, реализующий интерфейс PatientService, и находит PatientServiceImpl.
 *    Если реализаций несколько — используется @Primary или @Qualifier.
 *
 * 3. Тестируемость:
 *    В тестах PatientServiceImpl заменяется моком:
 *      @MockBean PatientService patientService;
 *    Контроллер при этом не знает, что работает с заглушкой.
 *
 * 4. Разделение ответственности (документация vs реализация):
 *    Интерфейс — это публичный контракт (ЧТО делает сервис).
 *    Реализация — это детали (КАК делает). Разработчику достаточно посмотреть
 *    на интерфейс, чтобы понять возможности сервиса без погружения в код.
 */
public interface PatientService {

    /**
     * Регистрирует нового пациента в системе.
     *
     * Ожидаемое поведение реализации:
     * - Замаппить CreatePatientRequest в сущность Patient через PatientMapper.
     * - Установить начальный статус (например, REGISTERED).
     * - Сохранить через patientRepository.save().
     * - Вернуть PatientResponse через маппер.
     *
     * @param request DTO с персональными данными пациента (ФИО, дата рождения, СНИЛС и т.д.)
     * @return PatientResponse — созданный пациент с присвоенным id и датой регистрации
     */
    PatientResponse create(CreatePatientRequest request);

    /**
     * Возвращает данные конкретного пациента по его идентификатору.
     *
     * Ожидаемое поведение: если пациент не найден или не активен —
     * выбрасывается исключение (например, EntityNotFoundException или ResourceNotFoundException).
     *
     * @param id первичный ключ пациента в БД
     * @return PatientResponse — полные данные пациента включая текущего врача и палату
     */
    PatientResponse getById(Long id);

    /**
     * Возвращает постраничный список всех активных пациентов.
     *
     * Используется Spring Data пагинация: Pageable содержит номер страницы, размер страницы
     * и параметры сортировки. Клиент передаёт их как query-параметры:
     *   GET /patients?page=0&size=20&sort=lastName,asc
     *
     * PageResponse<PatientResponse> — обёртка над Page<T> от Spring Data, которая
     * сериализуется в удобный JSON с полями content, totalElements, totalPages и т.д.
     *
     * @param pageable параметры пагинации и сортировки от Spring Data
     * @return страница с пациентами и метаданными пагинации
     */
    PageResponse<PatientResponse> getAll(Pageable pageable);

    /**
     * Обновляет редактируемые данные пациента (PATCH-семантика).
     *
     * Реализация должна:
     * - Найти пациента по id (или выбросить исключение).
     * - Применить изменения через PatientMapper.updateFromRequest() с @MappingTarget.
     * - Неизменяемые поля (birthDate, snils, gender) игнорируются маппером.
     * - Hibernate автоматически сохранит изменения через dirty checking при завершении транзакции.
     *
     * @param id      идентификатор пациента для обновления
     * @param request DTO с новыми значениями (null-поля означают "не изменять")
     * @return PatientResponse — пациент с обновлёнными данными
     */
    PatientResponse update(Long id, UpdatePatientRequest request);

    /**
     * Мягкое удаление (soft delete) пациента — деактивация без физического удаления из БД.
     *
     * Паттерн Soft Delete: вместо DELETE FROM patients WHERE id = ? устанавливается флаг active = false.
     * Зачем так делать:
     * - Сохраняется история (медицинские записи нельзя удалять физически).
     * - Возможно восстановление пациента.
     * - Ссылочная целостность не нарушается (другие таблицы могут ссылаться на пациента).
     *
     * После softDelete пациент не должен появляться в результатах getAll() и search().
     * Для этого в репозитории используется фильтрация: WHERE active = true.
     *
     * @param id идентификатор пациента для деактивации
     */
    void softDelete(Long id);

    /**
     * Назначает лечащего врача пациенту.
     *
     * Это отдельный эндпоинт, а не часть update(), потому что назначение врача —
     * самостоятельная бизнес-операция с собственными правилами:
     * - Врач должен быть активным.
     * - Врач должен принадлежать нужному отделению.
     * - Пациент должен быть в статусе ADMITTED или REGISTERED.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  идентификатор врача для назначения
     * @return PatientResponse — пациент с обновлённым полем currentDoctor
     */
    PatientResponse assignDoctor(Long patientId, Long doctorId);

    /**
     * Возвращает список платных услуг, назначенных конкретному пациенту.
     *
     * Каждый элемент списка — PatientPaidServiceResponse — содержит информацию
     * об услуге (название, цена) и статусе оплаты (оплачена или нет).
     *
     * @param patientId идентификатор пациента
     * @return список всех платных услуг пациента (назначенных и/или оплаченных)
     */
    List<PatientPaidServiceResponse> getServices(Long patientId);

    /**
     * Поиск пациентов по поисковой строке и/или статусу с пагинацией.
     *
     * Поисковая строка q обычно ищется по ФИО или СНИЛС пациента.
     * Фильтр status позволяет ограничить выборку пациентами с конкретным статусом
     * (REGISTERED, ADMITTED, DISCHARGED, TRANSFERRED).
     * Оба параметра опциональны — если null, фильтрация по ним не применяется.
     *
     * @param q        строка поиска (часть имени, СНИЛС и т.д.), может быть null
     * @param status   статус пациента для фильтрации, может быть null
     * @param pageable параметры пагинации и сортировки
     * @return страница с найденными пациентами и метаданными пагинации
     */
    PageResponse<PatientResponse> search(String q, PatientStatus status, Pageable pageable);
}
