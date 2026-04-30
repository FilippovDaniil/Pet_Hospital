package com.hospital.repository;

import com.hospital.entity.Doctor;
import com.hospital.entity.Specialty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link Doctor} (врач).
 *
 * <p>Все методы используют соглашение об именовании Spring Data JPA —
 * фреймворк разбирает название метода и строит JPQL/SQL автоматически.
 * Ключевые ключевые слова:
 * <ul>
 *   <li>{@code findBy} — SELECT ... WHERE</li>
 *   <li>{@code ActiveTrue} — AND active = true</li>
 *   <li>{@code existsBy} — SELECT COUNT(*) > 0 WHERE</li>
 *   <li>{@code And} — AND в условии</li>
 * </ul>
 * </p>
 *
 * <p>Реализация интерфейса создаётся Spring Data JPA автоматически через
 * прокси-класс {@code SimpleJpaRepository} при старте контекста приложения.</p>
 */
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    /**
     * Возвращает страницу активных врачей (у которых {@code active = true}).
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM doctors WHERE active = true LIMIT ? OFFSET ?}<br>
     * + отдельный запрос {@code SELECT COUNT(*) FROM doctors WHERE active = true}
     * для заполнения мета-информации объекта {@link Page}.</p>
     *
     * <p><b>Зачем возвращать Page, а не List?</b><br>
     * При большом количестве врачей возврат всего списка сразу создаёт
     * нагрузку на БД и память. {@link Page} позволяет клиенту запросить
     * нужную страницу и получить информацию о пагинации (totalPages, totalElements)
     * для построения навигации на фронтенде.</p>
     *
     * @param pageable параметры пагинации (номер страницы, размер, сортировка)
     * @return страница активных врачей
     */
    Page<Doctor> findAllByActiveTrue(Pageable pageable);

    /**
     * Ищет активного врача по его идентификатору.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM doctors WHERE id = ? AND active = true}</p>
     *
     * <p><b>Паттерн soft delete (мягкое удаление):</b><br>
     * Врач не удаляется физически из базы данных. Вместо этого выставляется
     * флаг {@code active = false}. Это позволяет сохранить историческую
     * привязку врача к пациентам и записям, не нарушая ссылочную целостность.<br>
     * Метод вернёт {@link Optional#empty()}, если врач либо не существует,
     * либо был "удалён" (active = false).</p>
     *
     * @param id идентификатор врача
     * @return {@link Optional} с врачом, если он найден и активен
     */
    Optional<Doctor> findByIdAndActiveTrue(Long id);

    /**
     * Возвращает страницу активных врачей указанной специальности.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM doctors WHERE specialty = ? AND active = true LIMIT ? OFFSET ?}</p>
     *
     * <p>{@link Specialty} — это enum, хранимый в БД как строка (обычно через
     * {@code @Enumerated(EnumType.STRING)}). Spring Data JPA автоматически
     * преобразует enum в строковое значение при формировании запроса.</p>
     *
     * <p>Используется при назначении врача пациенту: можно отфильтровать
     * только врачей нужной специальности и выбрать подходящего.</p>
     *
     * @param specialty специальность (например, THERAPIST, SURGEON)
     * @param pageable  параметры пагинации и сортировки
     * @return страница активных врачей с указанной специальностью
     */
    Page<Doctor> findBySpecialtyAndActiveTrue(Specialty specialty, Pageable pageable);
}
