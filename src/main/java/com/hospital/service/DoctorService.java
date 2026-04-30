package com.hospital.service;

import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.dto.request.UpdateDoctorRequest;
import com.hospital.dto.response.DoctorResponse;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.Specialty;
import org.springframework.data.domain.Pageable;

/**
 * Контракт сервисного слоя для работы с врачами.
 *
 * --- Принцип разделения через интерфейс ---
 * Интерфейс DoctorService определяет ЧТО умеет делать сервис врачей.
 * Конкретная реализация DoctorServiceImpl определяет КАК это делается.
 *
 * Такое разделение позволяет:
 *   - Писать тесты для DoctorController, подменяя DoctorService на Mock:
 *       @MockBean DoctorService doctorService;
 *       when(doctorService.getById(1L)).thenReturn(mockResponse);
 *   - Менять реализацию (например, добавить кэширование) без изменения контроллера.
 *   - Spring AOP: аспекты (логирование, транзакции через @Transactional) применяются
 *     к методам через прокси, который создаётся для Bean, реализующего этот интерфейс.
 *
 * --- Dependency Injection для этого интерфейса ---
 * DoctorController зависит от DoctorService:
 *   private final DoctorService doctorService; // инициализируется Spring
 * Spring IoC Container видит, что DoctorServiceImpl реализует DoctorService,
 * и автоматически внедряет его в конструктор контроллера.
 */
public interface DoctorService {

    /**
     * Регистрирует нового врача в системе.
     *
     * Реализация должна:
     * - Замаппить CreateDoctorRequest в Doctor через DoctorMapper.toEntity().
     * - Найти Department по departmentId из запроса и установить связь.
     * - Установить active = true.
     * - Сохранить через doctorRepository.save().
     *
     * @param request DTO с данными врача (ФИО, специальность, номер лицензии, id отделения)
     * @return DoctorResponse — созданный врач с присвоенным id и данными отделения
     */
    DoctorResponse create(CreateDoctorRequest request);

    /**
     * Возвращает данные врача по идентификатору.
     *
     * Если врач не найден или неактивен — выбрасывается исключение,
     * которое обрабатывается глобальным @ControllerAdvice и возвращает HTTP 404.
     *
     * @param id первичный ключ врача
     * @return DoctorResponse — данные врача с информацией об отделении
     */
    DoctorResponse getById(Long id);

    /**
     * Возвращает постраничный список всех активных врачей.
     *
     * Pageable позволяет клиенту управлять:
     *   - Номером страницы (page=0 — первая страница, нумерация с нуля).
     *   - Размером страницы (size=10 — 10 записей на странице).
     *   - Сортировкой (sort=fullName,asc — по ФИО в алфавитном порядке).
     *
     * В URL это выглядит так: GET /doctors?page=0&size=10&sort=fullName,asc
     *
     * @param pageable параметры пагинации от Spring Data
     * @return постраничный список врачей с метаданными (totalPages, totalElements)
     */
    PageResponse<DoctorResponse> getAll(Pageable pageable);

    /**
     * Возвращает постраничный список врачей с указанной специализацией.
     *
     * Specialty — enum (перечисление) возможных специализаций врача.
     * Spring автоматически конвертирует строку из query-параметра в значение enum:
     *   GET /doctors?specialty=SURGEON → Specialty.SURGEON
     *
     * Это полезно для поиска врача нужного профиля при направлении пациента.
     *
     * @param specialty специализация врача (SURGEON, THERAPIST, CARDIOLOGIST и т.д.)
     * @param pageable  параметры пагинации
     * @return постраничный список врачей выбранной специализации
     */
    PageResponse<DoctorResponse> getBySpecialty(Specialty specialty, Pageable pageable);

    /**
     * Обновляет данные врача (PATCH-семантика).
     *
     * Применяет только те поля, которые явно переданы в запросе.
     * Поля department и active не изменяются через этот метод.
     *
     * @param id      идентификатор врача
     * @param request DTO с новыми значениями (null — не изменять поле)
     * @return DoctorResponse — врач с обновлёнными данными
     */
    DoctorResponse update(Long id, UpdateDoctorRequest request);

    /**
     * Мягкое удаление врача — установка флага active = false.
     *
     * Физически запись в таблице doctors остаётся.
     * Врач пропадает из всех списков (getAll, getBySpecialty).
     * Связанные пациенты (currentDoctor) сохраняют ссылку — это позволяет
     * восстановить историю назначений даже после деактивации врача.
     *
     * @param id идентификатор врача для деактивации
     */
    void softDelete(Long id);

    /**
     * Возвращает постраничный список пациентов, закреплённых за данным врачом.
     *
     * Используется, например, в личном кабинете врача для просмотра своих пациентов.
     * Метод находится в DoctorService (а не PatientService), потому что речь идёт
     * о контексте врача — это его пациенты, и запрос делается от лица врача.
     *
     * @param doctorId идентификатор врача
     * @param pageable параметры пагинации и сортировки
     * @return постраничный список пациентов этого врача
     */
    PageResponse<PatientResponse> getPatients(Long doctorId, Pageable pageable);
}
