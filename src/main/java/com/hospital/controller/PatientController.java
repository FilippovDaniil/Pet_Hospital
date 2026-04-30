package com.hospital.controller;

import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.dto.request.UpdatePatientRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.PatientStatus;
import com.hospital.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер для управления пациентами ветеринарной клиники.
 *
 * <p>Реализует полный CRUD (Create, Read, Update, Delete) для сущности Patient,
 * а также дополнительные операции: назначение врача и просмотр платных услуг пациента.</p>
 *
 * <p><b>Архитектурная роль контроллера (HTTP-слой):</b>
 * Контроллер намеренно остаётся "тонким" — он не содержит бизнес-логики.
 * Его единственная ответственность:
 * <ul>
 *   <li>Принять HTTP-запрос и десериализовать входные данные.</li>
 *   <li>Передать данные в {@link PatientService}.</li>
 *   <li>Упаковать результат сервиса в {@link ResponseEntity} с правильным HTTP-статусом.</li>
 * </ul>
 * Такое разделение упрощает тестирование: сервис тестируется unit-тестами независимо
 * от HTTP, а контроллер проверяется интеграционными тестами (MockMvc).</p>
 *
 * <p><b>@RequestMapping("/api/patients")</b> — базовый путь. Все методы наследуют
 * этот префикс. Итоговые URL строятся как: базовый путь + путь метода.</p>
 *
 * <p><b>@Tag</b> (Swagger/OpenAPI 3) — группирует эндпоинты в интерактивной
 * документации Swagger UI под разделом "Patients". Swagger UI доступен по
 * адресу {@code /swagger-ui.html} после запуска приложения.</p>
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "Patients", description = "Patient management API")
public class PatientController {

    private final PatientService patientService;

    /**
     * Регистрирует нового пациента в системе.
     *
     * <p><b>@PostMapping</b> — обрабатывает HTTP POST {@code /api/patients}.
     * POST семантически означает "создать новый ресурс". Данные передаются
     * в теле запроса (не в URL), что позволяет отправить произвольный объём
     * данных без ограничений длины URL.</p>
     *
     * <p><b>@Valid @RequestBody</b> — два механизма валидации работают вместе:
     * <ol>
     *   <li>{@code @RequestBody} — Jackson десериализует JSON из тела запроса
     *       в объект {@link CreatePatientRequest}.</li>
     *   <li>{@code @Valid} — Bean Validation проверяет ограничения на полях DTO
     *       (например, {@code @NotBlank}, {@code @Size}). При ошибке — HTTP 400.</li>
     * </ol></p>
     *
     * <p><b>HTTP 201 Created</b> — стандартный статус при успешном создании ресурса.
     * По спецификации HTTP ответ на POST-создание должен возвращать 201, а не 200.
     * Дополнительно можно добавить заголовок {@code Location} с URL созданного ресурса.</p>
     *
     * @param request DTO с данными нового пациента
     * @return созданный пациент, HTTP 201 Created
     */
    @PostMapping
    @Operation(summary = "Register a new patient")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientService.create(request));
    }

    /**
     * Возвращает пациента по его идентификатору.
     *
     * <p><b>@GetMapping("/{id}")</b> — обрабатывает HTTP GET {@code /api/patients/123}.
     * GET — идемпотентный метод: повторный вызов не меняет состояние сервера.
     * Фигурные скобки {@code {id}} обозначают шаблонную переменную пути.</p>
     *
     * <p><b>@PathVariable Long id</b> — извлекает значение {@code {id}} из URL
     * и автоматически конвертирует строку "123" в тип {@code Long}.
     * Spring поддерживает конвертацию в примитивы, обёртки, {@code UUID},
     * {@code LocalDate} и другие типы через {@code ConversionService}.</p>
     *
     * <p><b>ResponseEntity.ok(...)</b> — возвращает HTTP 200 OK с телом ответа.
     * Если сервис выбрасывает исключение (пациент не найден), глобальный
     * {@code @ExceptionHandler} перехватит его и вернёт HTTP 404 Not Found.</p>
     *
     * @param id идентификатор пациента из URL-пути
     * @return данные пациента, HTTP 200 OK; HTTP 404 если не найден
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get patient by ID")
    public ResponseEntity<PatientResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.getById(id));
    }

    /**
     * Возвращает постраничный список активных пациентов.
     *
     * <p><b>Пагинация (постраничный вывод)</b> — критически важна при работе
     * с большими наборами данных. Вместо возврата всех записей сразу, клиент
     * запрашивает конкретную страницу определённого размера.</p>
     *
     * <p><b>@RequestParam(defaultValue = "0") int page</b> — считывает параметр
     * из строки запроса URL: {@code /api/patients?page=2&size=10}.
     * Параметры {@code required = false} и {@code defaultValue} делают их
     * необязательными — если клиент не передаёт их, используются значения по
     * умолчанию ({@code page=0}, {@code size=20}).
     * Spring автоматически конвертирует строку в {@code int}.</p>
     *
     * <p><b>PageRequest.of(page, size, Sort.by("id"))</b> — создаёт объект
     * {@link org.springframework.data.domain.Pageable}, который Spring Data JPA
     * использует для генерации SQL с {@code LIMIT}, {@code OFFSET} и {@code ORDER BY}.
     * Сортировка по {@code id} гарантирует стабильный порядок результатов.</p>
     *
     * <p><b>PageResponse&lt;PatientResponse&gt;</b> — кастомная обёртка над
     * {@code Page<T>} из Spring Data. Содержит данные страницы, общее количество
     * записей, номер текущей страницы и признак последней страницы.
     * Это позволяет фронтенду отрисовать навигацию по страницам.</p>
     *
     * @param page номер страницы (начиная с 0), по умолчанию 0
     * @param size количество записей на странице, по умолчанию 20
     * @return страница с пациентами и метаданными пагинации, HTTP 200 OK
     */
    @GetMapping
    @Operation(summary = "Get all active patients (paginated)")
    public ResponseEntity<PageResponse<PatientResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(patientService.getAll(PageRequest.of(page, size, Sort.by("id"))));
    }

    /**
     * Ищет пациентов по имени и/или статусу с пагинацией.
     *
     * <p><b>Поиск через @RequestParam(required = false)</b> — параметры {@code q}
     * (строка поиска) и {@code status} не обязательны. Если оба не указаны,
     * вернётся список всех пациентов. Примеры URL:
     * <ul>
     *   <li>{@code /api/patients/search?q=Барсик} — поиск по имени</li>
     *   <li>{@code /api/patients/search?status=ADMITTED} — фильтр по статусу</li>
     *   <li>{@code /api/patients/search?q=Мурзик&status=DISCHARGED&page=1}</li>
     * </ul></p>
     *
     * <p><b>PatientStatus status</b> — Spring автоматически конвертирует строку
     * из URL (например, "ADMITTED") в значение enum {@link PatientStatus}
     * благодаря встроенному {@code StringToEnumConverter}.
     * Если строка не соответствует ни одному значению enum — HTTP 400.</p>
     *
     * <p><b>Отдельный эндпоинт /search vs параметры на GET /</b>:
     * Оба подхода валидны. Отдельный путь {@code /search} делает намерение
     * очевидным и упрощает маршрутизацию в сложных случаях.</p>
     *
     * @param q      строка поиска по имени (необязательно)
     * @param status фильтр по статусу пациента (необязательно)
     * @param page   номер страницы, по умолчанию 0
     * @param size   размер страницы, по умолчанию 20
     * @return страница с найденными пациентами, HTTP 200 OK
     */
    @GetMapping("/search")
    @Operation(summary = "Search patients by name and/or status")
    public ResponseEntity<PageResponse<PatientResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PatientStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(patientService.search(q, status,
                PageRequest.of(page, size, Sort.by("id"))));
    }

    /**
     * Обновляет данные существующего пациента.
     *
     * <p><b>@PutMapping("/{id}")</b> — обрабатывает HTTP PUT {@code /api/patients/123}.
     * PUT означает полную замену ресурса: клиент передаёт все поля объекта.
     * Отличие от PATCH: PATCH — частичное обновление (только изменённые поля).
     * В этом проекте используется PUT, хотя семантически некоторые операции
     * ближе к PATCH.</p>
     *
     * <p><b>Комбинация @PathVariable + @RequestBody</b> — типичный паттерн обновления:
     * {@code id} приходит из URL (кого обновляем), а новые данные — из тела запроса
     * (что обновляем). Spring связывает их независимо друг от друга.</p>
     *
     * <p><b>HTTP 200 OK</b> — возвращаем обновлённый объект. Клиент получает
     * актуальное состояние ресурса после сохранения (с обновлёнными полями).</p>
     *
     * @param id      идентификатор пациента для обновления
     * @param request DTO с новыми данными пациента
     * @return обновлённый пациент, HTTP 200 OK
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update patient data")
    public ResponseEntity<PatientResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePatientRequest request) {
        return ResponseEntity.ok(patientService.update(id, request));
    }

    /**
     * Мягко удаляет пациента (soft delete — помечает как неактивного).
     *
     * <p><b>@DeleteMapping("/{id}")</b> — обрабатывает HTTP DELETE {@code /api/patients/123}.
     * DELETE — идемпотентный метод: повторный вызов даёт тот же результат.</p>
     *
     * <p><b>Soft Delete (мягкое удаление)</b> — пациент не удаляется физически
     * из БД, а помечается как неактивный (флаг {@code active = false}).
     * Преимущества:
     * <ul>
     *   <li>Данные можно восстановить при ошибке.</li>
     *   <li>Сохраняется история и целостность связанных записей.</li>
     *   <li>Аудиторские требования (медицинские данные нельзя удалять).</li>
     * </ul></p>
     *
     * <p><b>HTTP 204 No Content</b> — стандартный статус для успешного DELETE.
     * Тело ответа пустое (нечего возвращать после удаления).
     * {@code ResponseEntity<Void>} явно указывает, что тела нет.
     * {@code .noContent().build()} создаёт ответ со статусом 204 без тела.</p>
     *
     * @param id идентификатор пациента для удаления
     * @return пустой ответ, HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete patient")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Назначает врача пациенту.
     *
     * <p><b>Два @PathVariable в одном методе</b> — Spring парсит оба значения
     * из шаблона пути {@code /{patientId}/assign-doctor/{doctorId}}:
     * например, {@code /api/patients/5/assign-doctor/3} → patientId=5, doctorId=3.
     * Имена переменных в аннотации должны совпадать с именами параметров метода.</p>
     *
     * <p><b>PUT для операции связывания</b> — назначение врача пациенту является
     * обновлением ресурса пациента (меняется поле doctor), поэтому логично
     * использовать PUT. Альтернатива — отдельный POST на ресурс связи.</p>
     *
     * <p><b>Семантика URL</b>: {@code /patients/{patientId}/assign-doctor/{doctorId}}
     * читается как "у пациента {patientId} назначить врача {doctorId}".
     * Это RESTful стиль — ресурсы и их отношения отражены в URL.</p>
     *
     * @param patientId идентификатор пациента
     * @param doctorId  идентификатор врача для назначения
     * @return обновлённый пациент с назначенным врачом, HTTP 200 OK
     */
    @PutMapping("/{patientId}/assign-doctor/{doctorId}")
    @Operation(summary = "Assign a doctor to patient")
    public ResponseEntity<PatientResponse> assignDoctor(
            @PathVariable Long patientId,
            @PathVariable Long doctorId) {
        return ResponseEntity.ok(patientService.assignDoctor(patientId, doctorId));
    }

    /**
     * Возвращает список всех платных услуг, назначенных конкретному пациенту.
     *
     * <p><b>Вложенный ресурс (nested resource)</b>: URL {@code /patients/{patientId}/services}
     * отражает иерархию — услуги принадлежат пациенту. Это стандартная
     * RESTful практика для отношений "один ко многим".</p>
     *
     * <p><b>List vs Page</b>: здесь возвращается полный список ({@code List<>})
     * без пагинации. Это приемлемо, когда количество записей заведомо небольшое
     * (услуги одного пациента). Для потенциально больших коллекций используй
     * {@code Page<>} с пагинацией.</p>
     *
     * <p><b>PatientPaidServiceResponse</b> — DTO для связи пациент-услуга,
     * содержащий информацию о самой услуге, дате назначения и статусе оплаты.
     * Использование отдельного DTO (а не entity) защищает от утечки внутренней
     * структуры БД наружу.</p>
     *
     * @param patientId идентификатор пациента
     * @return список платных услуг пациента, HTTP 200 OK
     */
    @GetMapping("/{patientId}/services")
    @Operation(summary = "Get all paid services for patient")
    public ResponseEntity<List<PatientPaidServiceResponse>> getServices(@PathVariable Long patientId) {
        return ResponseEntity.ok(patientService.getServices(patientId));
    }
}
