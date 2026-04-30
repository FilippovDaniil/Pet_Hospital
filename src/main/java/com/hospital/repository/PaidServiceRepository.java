package com.hospital.repository;

import com.hospital.entity.PaidService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link PaidService} (платная услуга).
 *
 * <p>Платные услуги — это справочник услуг (например, "УЗИ брюшной полости",
 * "Консультация кардиолога") с ценами. Услуги поддерживают мягкое удаление:
 * неактуальные услуги помечаются флагом {@code active = false}, а не
 * удаляются из базы (это необходимо для сохранения истории назначенных услуг).</p>
 */
public interface PaidServiceRepository extends JpaRepository<PaidService, Long> {

    /**
     * Возвращает страницу активных платных услуг.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM paid_services WHERE active = true LIMIT ? OFFSET ?}<br>
     * + {@code SELECT COUNT(*) FROM paid_services WHERE active = true}</p>
     *
     * <p>Используется в интерфейсе назначения услуг пациенту: врач видит
     * только актуальный прайс-лист (active = true), а архивные услуги скрыты.
     * Пагинация важна, если прайс-лист содержит сотни позиций.</p>
     *
     * @param pageable параметры пагинации и сортировки (например, по названию или цене)
     * @return страница активных платных услуг
     */
    Page<PaidService> findAllByActiveTrue(Pageable pageable);

    /**
     * Ищет активную платную услугу по идентификатору.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM paid_services WHERE id = ? AND active = true}</p>
     *
     * <p>Используется при добавлении услуги пациенту: сервисный слой проверяет,
     * что услуга с данным ID существует и не была деактивирована.
     * Если услуга деактивирована или не найдена — метод вернёт
     * {@link Optional#empty()}, и сервис должен выбросить соответствующее
     * исключение (например, {@code ResourceNotFoundException}).</p>
     *
     * @param id идентификатор услуги
     * @return {@link Optional} с услугой, если она найдена и активна
     */
    Optional<PaidService> findByIdAndActiveTrue(Long id);
}
