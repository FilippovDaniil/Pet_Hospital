package com.hospital.repository;

import com.hospital.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для работы с сущностью {@link Department} (отделение больницы).
 *
 * <p>Не содержит дополнительных методов, поскольку для работы с отделениями
 * достаточно стандартных операций, предоставляемых {@link JpaRepository}:
 * <ul>
 *   <li>{@code findAll()} — получить список всех отделений</li>
 *   <li>{@code findById(Long id)} — найти отделение по ID, возвращает {@code Optional<Department>}</li>
 *   <li>{@code save(Department)} — создать или обновить отделение</li>
 *   <li>{@code deleteById(Long id)} — удалить отделение по ID</li>
 *   <li>{@code existsById(Long id)} — проверить существование отделения</li>
 *   <li>{@code count()} — получить общее количество отделений</li>
 * </ul>
 * </p>
 *
 * <p><b>Когда стоит добавлять методы в репозиторий?</b><br>
 * Только когда стандартных CRUD-операций недостаточно, например:
 * нужна фильтрация, поиск по нескольким полям, агрегация или JOIN.
 * Для справочников (таких как отделения) стандартных методов, как правило,
 * достаточно — список отделений получается через {@code findAll()},
 * а конкретное отделение — через {@code findById()}.</p>
 *
 * <p>Реализация этого интерфейса создаётся Spring Data JPA автоматически
 * через механизм динамических прокси (AOP + рефлексия) во время старта
 * Spring-контекста.</p>
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
