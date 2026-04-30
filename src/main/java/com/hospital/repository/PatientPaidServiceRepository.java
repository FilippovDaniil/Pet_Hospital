package com.hospital.repository;

import com.hospital.entity.PatientPaidService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Репозиторий для работы с сущностью {@link PatientPaidService} —
 * связью "пациент — назначенная платная услуга".
 *
 * <p>{@code PatientPaidService} — это join-таблица с дополнительными полями
 * (например, дата назначения). Она связывает пациента ({@code Patient})
 * с конкретной платной услугой ({@code PaidService}), которая ему была оказана.</p>
 *
 * <p>Репозиторий содержит методы для:
 * <ul>
 *   <li>получения списка услуг конкретного пациента</li>
 *   <li>подсчёта суммарной стоимости услуг пациента</li>
 *   <li>получения всех услуг, назначенных пациентам данного врача</li>
 * </ul>
 * </p>
 */
public interface PatientPaidServiceRepository extends JpaRepository<PatientPaidService, Long> {

    /**
     * Возвращает список всех платных услуг, назначенных указанному пациенту.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM patient_paid_services WHERE patient_id = ?}</p>
     *
     * <p><b>Важно про N+1:</b><br>
     * Если сущность {@code PatientPaidService} имеет LAZY-связи с {@code PaidService}
     * и {@code Patient}, то обращение к этим полям при итерации по списку
     * вызовет дополнительные SELECT-запросы (по одному на каждый элемент).
     * Для отображения истории услуг пациента с минимальным числом запросов
     * лучше использовать JOIN FETCH (см. метод {@link #findByDoctorId}).</p>
     *
     * @param patientId идентификатор пациента
     * @return список назначенных услуг пациента
     */
    List<PatientPaidService> findByPatientId(Long patientId);

    /**
     * Подсчитывает суммарную стоимость всех услуг, оказанных указанному пациенту.
     *
     * <p><b>JPQL-запрос:</b><br>
     * {@code SELECT COALESCE(SUM(pps.paidService.price), 0)} — суммируем цены
     * всех связанных услуг; {@code COALESCE} возвращает {@code 0}, если услуг нет
     * (иначе {@code SUM} вернул бы {@code null})<br>
     * {@code FROM PatientPaidService pps} — из таблицы назначений<br>
     * {@code WHERE pps.patient.id = :patientId} — только для данного пациента</p>
     *
     * <p>Hibernate транслирует {@code pps.paidService.price} в JOIN:
     * {@code INNER JOIN paid_services ps ON pps.paid_service_id = ps.id}
     * и берёт поле {@code ps.price}.</p>
     *
     * <p>Используется для отображения итоговой суммы счёта пациента
     * (например, при выписке или в личном кабинете).</p>
     *
     * @param patientId идентификатор пациента
     * @return суммарная стоимость оказанных услуг; {@code 0}, если услуг нет
     */
    @Query("SELECT COALESCE(SUM(pps.paidService.price), 0) FROM PatientPaidService pps WHERE pps.patient.id = :patientId")
    BigDecimal sumPriceByPatientId(@Param("patientId") Long patientId);

    /**
     * Возвращает список услуг, назначенных пациентам указанного врача,
     * с предзагрузкой связанных сущностей (JOIN FETCH).
     *
     * <p><b>JPQL-запрос (текстовый блок, Java 15+):</b><br>
     * {@code SELECT pps FROM PatientPaidService pps} — выбираем записи назначений<br>
     * {@code JOIN FETCH pps.paidService} — загружаем данные об услуге
     * (название, цена) одним JOIN, избегая проблемы N+1<br>
     * {@code JOIN FETCH pps.patient p} — загружаем данные о пациенте
     * тоже одним JOIN<br>
     * {@code WHERE p.currentDoctor.id = :doctorId} — фильтруем: только пациенты,
     * у которых текущий лечащий врач — это указанный врач</p>
     *
     * <p><b>Проблема N+1 здесь была бы критична:</b><br>
     * Без JOIN FETCH Hibernate сначала загрузил бы все {@code PatientPaidService},
     * затем для каждой записи отдельно загружал бы {@code PaidService} и
     * {@code Patient} — это 1 + 2*N запросов. При 50 назначениях это 101 запрос
     * вместо одного.<br>
     * {@code JOIN FETCH} сводит всё к одному SQL-запросу с JOIN.</p>
     *
     * <p>Используется в дашборде врача для отображения истории услуг
     * по всем его пациентам.</p>
     *
     * @param doctorId идентификатор врача
     * @return список назначений услуг с предзагруженными данными об услуге и пациенте
     */
    @Query("""
            SELECT pps FROM PatientPaidService pps
            JOIN FETCH pps.paidService
            JOIN FETCH pps.patient p
            WHERE p.currentDoctor.id = :doctorId
            """)
    List<PatientPaidService> findByDoctorId(@Param("doctorId") Long doctorId);
}
