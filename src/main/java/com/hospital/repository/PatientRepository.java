package com.hospital.repository;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link Patient} (пациент).
 *
 * <p>Расширяет {@link JpaRepository}, который предоставляет готовые методы CRUD:
 * {@code save}, {@code findById}, {@code findAll}, {@code delete} и другие.
 * Первый generic-параметр — тип сущности, второй — тип первичного ключа.</p>
 *
 * <p>Spring Data JPA автоматически создаёт реализацию этого интерфейса во время
 * запуска приложения (через {@code SimpleJpaRepository}), поэтому писать
 * имплементацию вручную не нужно.</p>
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /**
     * Возвращает страницу активных пациентов (у которых поле {@code active = true}).
     *
     * <p><b>Как Spring Data JPA строит запрос по имени метода:</b><br>
     * {@code findAll} — SELECT ... FROM patients<br>
     * {@code By} — WHERE<br>
     * {@code ActiveTrue} — active = true<br>
     * Итого: {@code SELECT * FROM patients WHERE active = true}</p>
     *
     * <p><b>Pageable и Page&lt;T&gt;:</b><br>
     * {@link Pageable} — объект, содержащий номер страницы, размер страницы
     * и параметры сортировки. Передаётся из сервисного слоя, например:
     * {@code PageRequest.of(0, 10, Sort.by("fullName"))}.<br>
     * {@link Page}&lt;T&gt; — результат, содержащий:
     * <ul>
     *   <li>список сущностей текущей страницы ({@code getContent()})</li>
     *   <li>общее количество записей ({@code getTotalElements()})</li>
     *   <li>общее количество страниц ({@code getTotalPages()})</li>
     *   <li>мета-информацию о текущей странице</li>
     * </ul>
     * Под капотом Spring генерирует два запроса: один с LIMIT/OFFSET для данных,
     * второй — COUNT для подсчёта общего числа записей.</p>
     *
     * @param pageable параметры пагинации и сортировки
     * @return страница активных пациентов
     */
    Page<Patient> findAllByActiveTrue(Pageable pageable);

    /**
     * Ищет активного пациента по идентификатору.
     *
     * <p><b>Построение запроса по имени:</b><br>
     * {@code findBy} — SELECT ... FROM patients WHERE<br>
     * {@code Id} — id = :id<br>
     * {@code And} — AND<br>
     * {@code ActiveTrue} — active = true<br>
     * Итого: {@code SELECT * FROM patients WHERE id = ? AND active = true}</p>
     *
     * <p><b>Optional&lt;T&gt;:</b><br>
     * Возвращает {@link Optional}, а не {@code null}, чтобы явно сигнализировать
     * об отсутствии результата. Вызывающий код должен обработать оба случая:
     * {@code optional.orElseThrow()} или {@code optional.ifPresent(...)}.<br>
     * Это позволяет избежать {@code NullPointerException} и делает код читаемее.</p>
     *
     * <p>Метод используется для "мягкого удаления" (soft delete): пациент остаётся
     * в БД, но помечается флагом {@code active = false}, и запросы его не находят.</p>
     *
     * @param id идентификатор пациента
     * @return {@link Optional} с пациентом, если он найден и активен
     */
    Optional<Patient> findByIdAndActiveTrue(Long id);

    /**
     * Проверяет, существует ли активный пациент с указанным СНИЛС.
     *
     * <p><b>Построение запроса по имени:</b><br>
     * {@code existsBy} — SELECT COUNT(*) > 0 FROM patients WHERE<br>
     * {@code Snils} — snils = :snils<br>
     * {@code AndActiveTrue} — AND active = true<br>
     * Итого: {@code SELECT CASE WHEN COUNT(*)>0 THEN true ELSE false END
     * FROM patients WHERE snils = ? AND active = true}</p>
     *
     * <p>Используется при создании нового пациента для проверки уникальности
     * СНИЛС (дубликаты недопустимы).</p>
     *
     * @param snils номер СНИЛС
     * @return {@code true}, если активный пациент с таким СНИЛС уже существует
     */
    boolean existsBySnilsAndActiveTrue(String snils);

    /**
     * Подсчитывает количество активных пациентов на лечении у указанного врача.
     *
     * <p><b>JPQL-запрос:</b><br>
     * {@code SELECT COUNT(p)} — считаем количество объектов Patient<br>
     * {@code FROM Patient p} — из таблицы patients (через JPA-сущность)<br>
     * {@code WHERE p.currentDoctor.id = :doctorId} — у которых текущий врач
     * имеет указанный id (Spring Data JPA сгенерирует JOIN с таблицей doctors)<br>
     * {@code AND p.active = true} — только активные (не удалённые)<br>
     * {@code AND p.status = 'TREATMENT'} — только те, кто сейчас лечится</p>
     *
     * <p>Используется для ограничения нагрузки на врача: например, не более
     * 10 активных пациентов одновременно.</p>
     *
     * @param doctorId идентификатор врача
     * @return количество активных пациентов в статусе TREATMENT у данного врача
     */
    @Query("SELECT COUNT(p) FROM Patient p WHERE p.currentDoctor.id = :doctorId AND p.active = true AND p.status = 'TREATMENT'")
    long countActivePatientsByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Возвращает страницу активных пациентов, прикреплённых к указанному врачу.
     *
     * <p><b>JPQL-запрос:</b><br>
     * {@code SELECT p FROM Patient p} — выбираем объекты Patient<br>
     * {@code WHERE p.currentDoctor.id = :doctorId} — фильтр по врачу
     * (JOIN с таблицей doctors выполняется автоматически)<br>
     * {@code AND p.active = true} — только активные пациенты</p>
     *
     * <p>Второй параметр {@link Pageable} позволяет получить результат постранично,
     * что критично при большом числе пациентов у врача.</p>
     *
     * @param doctorId идентификатор врача
     * @param pageable параметры пагинации и сортировки
     * @return страница активных пациентов данного врача
     */
    @Query("SELECT p FROM Patient p WHERE p.currentDoctor.id = :doctorId AND p.active = true")
    Page<Patient> findByDoctorId(@Param("doctorId") Long doctorId, Pageable pageable);

    /**
     * Возвращает список пациентов, находящихся в указанной палате прямо сейчас.
     *
     * <p><b>JPQL-запрос:</b><br>
     * {@code SELECT p FROM Patient p} — выбираем сущности Patient<br>
     * {@code WHERE p.currentWard.id = :wardId} — пациенты, чья текущая палата
     * совпадает с указанной (неявный JOIN с таблицей wards)<br>
     * {@code AND p.active = true} — не удалённые<br>
     * {@code AND p.status = 'TREATMENT'} — только те, кто сейчас на лечении
     * (а не выписанные или переведённые)</p>
     *
     * <p>Метод используется, например, для отображения текущей занятости палаты
     * или при выписке пациента, когда нужно обновить счётчик занятых мест.</p>
     *
     * @param wardId идентификатор палаты
     * @return список пациентов, находящихся в палате в данный момент
     */
    @Query("SELECT p FROM Patient p WHERE p.currentWard.id = :wardId AND p.active = true AND p.status = 'TREATMENT'")
    java.util.List<Patient> findCurrentPatientsInWard(@Param("wardId") Long wardId);

    /**
     * Выполняет постраничный поиск пациентов с опциональными фильтрами по имени и статусу.
     *
     * <p><b>JPQL-запрос:</b><br>
     * {@code SELECT p FROM Patient p WHERE p.active = true} — только активные<br>
     * {@code AND (cast(:q as String) IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', cast(:q as String), '%')))}
     * — если строка поиска передана, ищем её вхождение в полное имя (без учёта регистра)<br>
     * {@code AND (:status IS NULL OR p.status = :status)} — если статус передан, фильтруем по нему</p>
     *
     * <p><b>Почему {@code cast(:q as String)}, а не просто {@code :q}?</b><br>
     * Это особенность совместимости <b>Hibernate 6 + PostgreSQL</b>.<br>
     * В Hibernate 6 изменился механизм вывода типов параметров (type inference).
     * Когда параметр {@code :q} передаётся как {@code null}, Hibernate не может
     * определить его тип и генерирует SQL вида {@code cast(null as oid)}.
     * PostgreSQL воспринимает {@code oid} как идентификатор объекта БД, а не строку,
     * что приводит к ошибке {@code operator does not exist: character varying = oid}.<br>
     * Явный {@code cast(:q as String)} говорит Hibernate: "всегда трактуй этот
     * параметр как {@code varchar}", что генерирует корректный SQL:
     * {@code cast(? as varchar) IS NULL OR LOWER(fullName) LIKE LOWER(CONCAT('%', cast(? as varchar), '%'))}.<br>
     * Альтернативой было бы написать два отдельных метода или использовать
     * {@code Specification}, но этот приём элегантнее для простых случаев.</p>
     *
     * <p><b>Паттерн "опциональный фильтр":</b><br>
     * Конструкция {@code (:param IS NULL OR условие)} — стандартный способ
     * сделать фильтр необязательным в одном JPQL-запросе.
     * Если параметр равен {@code null}, условие всегда истинно и запись не фильтруется.</p>
     *
     * @param q      строка поиска по имени (может быть {@code null} — тогда фильтр не применяется)
     * @param status статус пациента (может быть {@code null} — тогда фильтр не применяется)
     * @param pageable параметры пагинации и сортировки
     * @return страница пациентов, соответствующих критериям поиска
     */
    @Query("SELECT p FROM Patient p WHERE p.active = true " +
           "AND (cast(:q as String) IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', cast(:q as String), '%'))) " +
           "AND (:status IS NULL OR p.status = :status)")
    Page<Patient> search(@Param("q") String q, @Param("status") PatientStatus status, Pageable pageable);
}
