package com.hospital.repository;

import com.hospital.entity.MedicalSupply;
import com.hospital.entity.SupplyCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий склада медикаментов.
 *
 * Наследует JpaRepository — получаем стандартные CRUD-методы (save, findById,
 * deleteById, findAll и т.д.) бесплатно.
 *
 * Оба метода ниже используют Spring Data Query Derivation — JPA сам генерирует
 * SQL из имени метода, без необходимости писать @Query вручную.
 */
public interface MedicalSupplyRepository extends JpaRepository<MedicalSupply, Long> {

    /**
     * Возвращает все позиции, отсортированные сначала по категории (A→Z),
     * затем по имени (A→Z) внутри каждой категории.
     *
     * Сгенерированный SQL:
     *   SELECT * FROM medical_supply ORDER BY category ASC, name ASC
     *
     * Используется в getAllSupplies() — основной список для таблицы медсестры.
     */
    List<MedicalSupply> findAllByOrderByCategoryAscNameAsc();

    /**
     * Фильтрует позиции по категории с сортировкой по имени.
     *
     * Сгенерированный SQL:
     *   SELECT * FROM medical_supply WHERE category = ? ORDER BY name ASC
     *
     * Зарезервирован для будущей фильтрации в UI (вкладки MEDICINE / CONSUMABLE / EQUIPMENT).
     * В текущей версии не вызывается из сервисного слоя напрямую.
     */
    List<MedicalSupply> findByCategoryOrderByNameAsc(SupplyCategory category);
}
