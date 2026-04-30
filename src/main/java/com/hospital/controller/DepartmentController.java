package com.hospital.controller;

import com.hospital.dto.request.CreateDepartmentRequest;
import com.hospital.dto.request.UpdateDepartmentRequest;
import com.hospital.dto.response.DepartmentResponse;
import com.hospital.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер для управления отделениями ветеринарной клиники.
 *
 * <p>Отделение (Department) — справочная сущность верхнего уровня иерархии:
 * отделение содержит палаты, в которых размещаются пациенты.
 * CRUD операций здесь меньше, чем у пациентов, так как количество отделений
 * невелико и пагинация не нужна.</p>
 *
 * <p><b>Почему здесь List, а не Page?</b>
 * Отделений в клинике обычно единицы (5-20). Загружать их все сразу
 * совершенно нормально — накладные расходы на пагинацию в этом случае
 * превысили бы выгоду. Правило: пагинация нужна там, где записей может быть
 * сотни и тысячи (пациенты, логи). Для небольших справочников — {@code List}.</p>
 *
 * <p><b>Полный CRUD в REST:</b>
 * <pre>
 *   POST   /api/departments       — Create  (HTTP 201)
 *   GET    /api/departments/{id}  — Read    (HTTP 200)
 *   GET    /api/departments       — Read All (HTTP 200)
 *   PUT    /api/departments/{id}  — Update  (HTTP 200)
 *   DELETE /api/departments/{id}  — Delete  (HTTP 204)
 * </pre></p>
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Department management API")
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * Создаёт новое отделение.
     *
     * <p><b>@PostMapping + @RequestBody + @Valid</b> — стандартная тройка для
     * создания ресурса:
     * <ol>
     *   <li>Клиент отправляет {@code POST /api/departments} с JSON в теле.</li>
     *   <li>{@code @RequestBody} — Jackson читает JSON и заполняет DTO.</li>
     *   <li>{@code @Valid} — Bean Validation проверяет DTO перед вызовом метода.</li>
     *   <li>Сервис сохраняет отделение и возвращает DTO с присвоенным {@code id}.</li>
     *   <li>Возвращается HTTP 201 Created с телом.</li>
     * </ol></p>
     *
     * <p><b>Зачем возвращать созданный объект?</b> Клиент получает сервер-генерированные
     * поля: {@code id}, дату создания. Без этого клиент не узнает {@code id}
     * созданного ресурса и не сможет на него сослаться.</p>
     *
     * @param request DTO с названием отделения и другими данными
     * @return созданное отделение с присвоенным ID, HTTP 201 Created
     */
    @PostMapping
    @Operation(summary = "Create a new department")
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.create(request));
    }

    /**
     * Возвращает отделение по идентификатору.
     *
     * <p><b>@GetMapping("/{id}")</b> — GET-запрос на конкретный ресурс.
     * GET — безопасный и идемпотентный метод: не изменяет состояние сервера,
     * можно кешировать на уровне HTTP-прокси и браузера.</p>
     *
     * <p><b>HTTP статусы для GET по ID:</b>
     * <ul>
     *   <li><b>200 OK</b> — ресурс найден, возвращается в теле.</li>
     *   <li><b>404 Not Found</b> — ресурса с таким ID не существует.
     *       Генерируется глобальным {@code @ExceptionHandler} при исключении
     *       в сервисе.</li>
     * </ul></p>
     *
     * @param id идентификатор отделения
     * @return данные отделения, HTTP 200 OK
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID")
    public ResponseEntity<DepartmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getById(id));
    }

    /**
     * Возвращает список всех отделений.
     *
     * <p><b>@GetMapping без пути</b> — обрабатывает GET на базовый URL
     * {@code /api/departments}. Возвращает все отделения без фильтрации
     * и пагинации — их количество заведомо мало.</p>
     *
     * <p><b>ResponseEntity&lt;List&lt;DepartmentResponse&gt;&gt;</b>:
     * если отделений нет вообще, метод вернёт пустой список {@code []}
     * со статусом 200 OK — это правильное поведение. Не нужно возвращать
     * 404 для пустой коллекции: ресурс (коллекция) существует, просто он пуст.</p>
     *
     * @return список всех отделений (может быть пустым), HTTP 200 OK
     */
    @GetMapping
    @Operation(summary = "Get all departments")
    public ResponseEntity<List<DepartmentResponse>> getAll() {
        return ResponseEntity.ok(departmentService.getAll());
    }

    /**
     * Обновляет данные отделения.
     *
     * <p><b>Паттерн обновления (Update Pattern):</b>
     * <ol>
     *   <li>Сервис загружает отделение из БД по {@code id}.</li>
     *   <li>Обновляет поля из DTO.</li>
     *   <li>Сохраняет и возвращает обновлённый объект.</li>
     * </ol>
     * Если отделения с таким {@code id} нет — бросается исключение → HTTP 404.</p>
     *
     * <p><b>@Valid на @RequestBody</b> — гарантирует, что переименовать отделение
     * в пустую строку не получится: Bean Validation отклонит запрос с HTTP 400
     * до вызова сервиса.</p>
     *
     * @param id      идентификатор отделения для обновления
     * @param request DTO с новыми данными отделения
     * @return обновлённое отделение, HTTP 200 OK
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update department")
    public ResponseEntity<DepartmentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(departmentService.update(id, request));
    }

    /**
     * Удаляет отделение.
     *
     * <p><b>Hard Delete vs Soft Delete:</b> в отличие от пациентов, отделение
     * удаляется физически из БД. Это возможно только если нет связанных палат
     * и пациентов — иначе нарушится ссылочная целостность. Сервис должен
     * проверять это условие и бросать исключение при наличии зависимостей.</p>
     *
     * <p><b>HTTP 204 No Content</b> — стандарт для успешного DELETE:
     * операция выполнена, возвращать нечего. Клиент должен удалить ресурс
     * из своего кеша при получении 204.</p>
     *
     * <p><b>Идемпотентность DELETE:</b> по спецификации HTTP, повторный DELETE
     * уже удалённого ресурса должен возвращать либо 204, либо 404.
     * Второй вызов обычно бросает исключение → 404.</p>
     *
     * @param id идентификатор отделения для удаления
     * @return пустой ответ, HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete department")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
