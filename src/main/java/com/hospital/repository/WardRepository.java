package com.hospital.repository;

import com.hospital.entity.Ward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий для работы с сущностью {@link Ward} (палата).
 *
 * <p>Палата принадлежит отделению ({@code Department}) и имеет вместимость
 * ({@code capacity}) и текущую заполненность ({@code currentOccupancy}).
 * Репозиторий содержит как методы с автогенерацией запросов по имени,
 * так и кастомные JPQL-запросы с {@code @Query}.</p>
 */
public interface WardRepository extends JpaRepository<Ward, Long> {

    /**
     * Возвращает страницу палат, принадлежащих указанному отделению.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM wards WHERE department_id = ? LIMIT ? OFFSET ?}</p>
     *
     * <p>Spring Data JPA "понимает" {@code departmentId} как поле вложенного
     * объекта: в сущности {@code Ward} есть поле {@code department} типа
     * {@code Department}, а у {@code Department} есть поле {@code id}.
     * Spring автоматически добавляет JOIN или использует внешний ключ
     * {@code department_id} в таблице {@code wards}.</p>
     *
     * <p>Используется для вывода палат в конкретном отделении (например,
     * в карточке отделения на фронтенде).</p>
     *
     * @param departmentId идентификатор отделения
     * @param pageable     параметры пагинации и сортировки
     * @return страница палат данного отделения
     */
    Page<Ward> findByDepartmentId(Long departmentId, Pageable pageable);

    /**
     * Находит палаты указанного отделения, в которых есть свободные места.
     *
     * <p><b>JPQL-запрос:</b><br>
     * {@code SELECT w FROM Ward w} — выбираем все объекты Ward<br>
     * {@code WHERE w.department.id = :deptId} — фильтр по отделению<br>
     * {@code AND w.currentOccupancy < w.capacity} — только палаты,
     * где текущая занятость меньше вместимости (есть хотя бы одно свободное место)</p>
     *
     * <p>Используется при госпитализации пациента: сервис вызывает этот метод,
     * чтобы предложить только те палаты, куда можно поместить нового пациента.
     * Возвращает {@link List}, а не {@link Page}, так как список доступных палат
     * обычно невелик и пагинация не нужна.</p>
     *
     * @param departmentId идентификатор отделения
     * @return список палат со свободными местами в данном отделении
     */
    @Query("SELECT w FROM Ward w WHERE w.department.id = :deptId AND w.currentOccupancy < w.capacity")
    List<Ward> findAvailableWardsByDepartment(@Param("deptId") Long departmentId);

    /**
     * Возвращает все палаты вместе с данными об их отделениях (JOIN FETCH).
     *
     * <p><b>JPQL-запрос:</b><br>
     * {@code SELECT w FROM Ward w} — выбираем объекты Ward<br>
     * {@code LEFT JOIN FETCH w.department} — загружаем связанный объект
     * {@code Department} одним запросом (жадная загрузка)<br>
     * {@code ORDER BY w.department.id, w.wardNumber} — сортировка сначала
     * по отделению, затем по номеру палаты внутри отделения</p>
     *
     * <p><b>Проблема N+1 и JOIN FETCH:</b><br>
     * Если бы связь {@code Ward -> Department} была ленивой ({@code LAZY}),
     * то при обходе списка палат Hibernate отправлял бы отдельный SELECT
     * для каждого отделения: 1 запрос за всеми палатами + N запросов за
     * каждым отделением = проблема N+1.<br>
     * {@code JOIN FETCH} решает проблему: Hibernate выполняет один JOIN-запрос
     * и загружает и палаты, и их отделения сразу.
     * Итоговый SQL выглядит примерно так:
     * {@code SELECT w.*, d.* FROM wards w LEFT JOIN departments d ON w.department_id = d.id
     * ORDER BY d.id, w.ward_number}</p>
     *
     * <p>{@code LEFT JOIN} (а не {@code INNER JOIN}) гарантирует, что палаты без
     * отделения (если такие есть) тоже попадут в результат.</p>
     *
     * @return список всех палат с предзагруженными отделениями
     */
    @Query("SELECT w FROM Ward w LEFT JOIN FETCH w.department ORDER BY w.department.id, w.wardNumber")
    List<Ward> findAllWithDepartment();
}
