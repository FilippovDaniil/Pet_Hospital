package com.hospital.controller;

import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.dto.request.UpdateDoctorRequest;
import com.hospital.dto.response.DoctorResponse;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.Specialty;
import com.hospital.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST-контроллер для управления врачами ветеринарной клиники.
 *
 * <p>Реализует CRUD-операции для сущности Doctor и предоставляет
 * возможность просматривать пациентов врача с пагинацией.</p>
 *
 * <p><b>Структура URL-пространства этого контроллера:</b>
 * <pre>
 *   POST   /api/doctors                    — создать врача
 *   GET    /api/doctors/{id}               — получить врача по ID
 *   GET    /api/doctors?specialty=SURGEON  — список врачей (с фильтром)
 *   PUT    /api/doctors/{id}               — обновить врача
 *   DELETE /api/doctors/{id}               — мягко удалить врача
 *   GET    /api/doctors/{id}/patients      — список пациентов врача
 * </pre></p>
 *
 * <p><b>Принцип единственной ответственности (SRP):</b> каждый контроллер
 * отвечает за один ресурс. Логика работы с врачами сосредоточена здесь,
 * а не размазана по другим контроллерам. {@link DoctorService} содержит
 * всю бизнес-логику, контроллер — только HTTP-координацию.</p>
 */
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctors", description = "Doctor management API")
public class DoctorController {

    private final DoctorService doctorService;

    /**
     * Создаёт нового врача.
     *
     * <p><b>@PostMapping</b> без пути — срабатывает на POST {@code /api/doctors}.
     * Когда аннотация не имеет пути, метод обрабатывает запросы прямо на
     * базовый URL контроллера.</p>
     *
     * <p><b>Последовательность работы Spring при получении запроса:</b>
     * <ol>
     *   <li>DispatcherServlet получает HTTP-запрос.</li>
     *   <li>HandlerMapping находит подходящий контроллер и метод.</li>
     *   <li>Jackson десериализует JSON из тела запроса в {@link CreateDoctorRequest}.</li>
     *   <li>Bean Validation проверяет поля DTO (из-за {@code @Valid}).</li>
     *   <li>Метод выполняется, вызывает сервис.</li>
     *   <li>Jackson сериализует {@link DoctorResponse} в JSON для ответа.</li>
     * </ol></p>
     *
     * <p><b>HTTP 201 Created</b> — корректный статус для операции создания.
     * Тело содержит созданного врача со всеми серверными полями (id, timestamps).</p>
     *
     * @param request DTO с данными врача (имя, специальность, отделение)
     * @return созданный врач, HTTP 201 Created
     */
    @PostMapping
    @Operation(summary = "Create a new doctor")
    public ResponseEntity<DoctorResponse> create(@Valid @RequestBody CreateDoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.create(request));
    }

    /**
     * Возвращает врача по идентификатору.
     *
     * <p><b>@PathVariable</b> — привязывает сегмент URL к параметру метода.
     * При запросе {@code GET /api/doctors/42} Spring извлекает {@code "42"},
     * конвертирует в {@code Long} и передаёт в параметр {@code id}.
     * Если значение не конвертируется в {@code Long} (например, {@code /api/doctors/abc}),
     * Spring вернёт HTTP 400 Bad Request.</p>
     *
     * <p><b>Обработка "не найдено":</b> {@code doctorService.getById(id)} бросает
     * {@code EntityNotFoundException} если врача нет. Глобальный обработчик
     * ({@code @ControllerAdvice}) превращает это в HTTP 404 Not Found.</p>
     *
     * @param id идентификатор врача
     * @return данные врача, HTTP 200 OK
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get doctor by ID")
    public ResponseEntity<DoctorResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getById(id));
    }

    /**
     * Возвращает постраничный список врачей с опциональной фильтрацией по специальности.
     *
     * <p><b>Условная логика в контроллере</b> — единственный допустимый случай
     * бизнес-условия в контроллере: выбор между двумя вызовами сервиса
     * в зависимости от наличия параметра. Более корректное решение —
     * иметь единый метод в сервисе, принимающий {@code Optional<Specialty>}.</p>
     *
     * <p><b>@RequestParam(required = false) Specialty specialty</b>:
     * <ul>
     *   <li>Если {@code ?specialty=SURGEON} — передаётся в метод как enum-значение.</li>
     *   <li>Если параметр отсутствует — {@code specialty} будет {@code null}.</li>
     *   <li>Spring автоматически конвертирует строку "SURGEON" в {@link Specialty#SURGEON}
     *       через {@code StringToEnumConverter}.</li>
     * </ul></p>
     *
     * <p><b>Пагинация через PageRequest:</b>
     * {@code PageRequest.of(page, size, Sort.by("id"))} создаёт объект запроса
     * страницы. Параметры:
     * <ul>
     *   <li>{@code page} — номер страницы (zero-based: 0 = первая страница).</li>
     *   <li>{@code size} — количество элементов на странице.</li>
     *   <li>{@code Sort.by("id")} — сортировка по полю {@code id} по возрастанию.
     *       Для убывания: {@code Sort.by(Sort.Direction.DESC, "id")}.</li>
     * </ul>
     * Spring Data JPA транслирует это в SQL:
     * {@code SELECT ... ORDER BY id LIMIT 20 OFFSET 0}</p>
     *
     * @param specialty фильтр по специальности врача (необязательно)
     * @param page      номер страницы, по умолчанию 0
     * @param size      размер страницы, по умолчанию 20
     * @return страница врачей, HTTP 200 OK
     */
    @GetMapping
    @Operation(summary = "Get all doctors, optionally filtered by specialty")
    public ResponseEntity<PageResponse<DoctorResponse>> getAll(
            @RequestParam(required = false) Specialty specialty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id"));
        if (specialty != null) {
            return ResponseEntity.ok(doctorService.getBySpecialty(specialty, pageable));
        }
        return ResponseEntity.ok(doctorService.getAll(pageable));
    }

    /**
     * Обновляет данные врача.
     *
     * <p><b>PUT vs PATCH:</b>
     * <ul>
     *   <li>{@code PUT} — полная замена: клиент присылает всё тело объекта целиком.
     *       Отсутствующие поля обнуляются.</li>
     *   <li>{@code PATCH} — частичное обновление: клиент присылает только
     *       изменённые поля. Остальные поля сохраняют текущие значения.</li>
     * </ul>
     * В этом проекте используется {@code PUT}: DTO содержит все редактируемые
     * поля, что упрощает реализацию.</p>
     *
     * <p><b>Идемпотентность PUT:</b> повторный PUT с теми же данными
     * не меняет результат — это безопасно для retry-логики клиентов.</p>
     *
     * @param id      идентификатор врача
     * @param request DTO с обновлёнными данными
     * @return обновлённые данные врача, HTTP 200 OK
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update doctor data")
    public ResponseEntity<DoctorResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDoctorRequest request) {
        return ResponseEntity.ok(doctorService.update(id, request));
    }

    /**
     * Мягко удаляет врача (деактивация без физического удаления из БД).
     *
     * <p><b>Soft delete и ссылочная целостность:</b> если бы мы удаляли врача
     * физически, все связанные пациенты потеряли бы ссылку на врача.
     * Soft delete решает эту проблему — запись остаётся, данные сохраняются,
     * но врач не отображается в активных списках.</p>
     *
     * <p><b>HTTP 204 No Content</b>:
     * {@code ResponseEntity<Void>} — generic-тип {@code Void} явно указывает,
     * что метод не возвращает тело. {@code .noContent().build()} устанавливает
     * статус 204 и не добавляет тело в ответ. Это стандарт для DELETE.</p>
     *
     * @param id идентификатор врача для деактивации
     * @return пустой ответ, HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete doctor")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        doctorService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Возвращает постраничный список пациентов конкретного врача.
     *
     * <p><b>Вложенный ресурс с пагинацией:</b> URL {@code /api/doctors/{id}/patients}
     * — стандартная RESTful нотация для дочернего ресурса (пациенты врача).
     * Пагинация здесь важна: у популярного врача могут быть десятки пациентов.</p>
     *
     * <p><b>Переиспользование PatientResponse:</b> для списка пациентов врача
     * используется тот же DTO, что и в PatientController. Это нормально —
     * DTO отражает данные сущности, а не принадлежность к контроллеру.
     * Если бы нужно было показывать меньше полей, создали бы отдельный DTO.</p>
     *
     * @param id   идентификатор врача
     * @param page номер страницы, по умолчанию 0
     * @param size размер страницы, по умолчанию 20
     * @return страница пациентов врача, HTTP 200 OK
     */
    @GetMapping("/{id}/patients")
    @Operation(summary = "Get all patients of a doctor")
    public ResponseEntity<PageResponse<PatientResponse>> getPatients(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(doctorService.getPatients(id, PageRequest.of(page, size, Sort.by("id"))));
    }
}
