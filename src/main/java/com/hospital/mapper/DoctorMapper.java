package com.hospital.mapper;

import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.dto.request.UpdateDoctorRequest;
import com.hospital.dto.response.DoctorResponse;
import com.hospital.entity.Doctor;
import org.mapstruct.*;

/**
 * Маппер для преобразования объектов врача между слоями приложения.
 *
 * --- Кодогенерация MapStruct ---
 * Annotation Processor Maven/Gradle запускает MapStruct во время компиляции.
 * На выходе появляется класс DoctorMapperImpl в target/generated-sources/annotations/.
 * Этот класс содержит обычные Java-вызовы геттеров и сеттеров — никакой рефлексии,
 * никаких скрытых накладных расходов в рантайме.
 *
 * --- componentModel = "spring" ---
 * MapStruct добавит @Component к DoctorMapperImpl, и Spring создаст его как Bean.
 * Внедрение в сервис выглядит так:
 *   private final DoctorMapper doctorMapper;  // Spring подставит DoctorMapperImpl
 *
 * --- nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE ---
 * Глобальная настройка для всего маппера. Если входное поле null — целевое поле
 * не трогается. Актуально для updateFromRequest, где нужен PATCH-семантика.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DoctorMapper {

    /**
     * Создаёт сущность Doctor из запроса на регистрацию нового врача.
     *
     * Игнорируемые поля:
     *   - id         — первичный ключ, генерируется БД.
     *   - department — связь с отделением задаётся отдельной логикой в сервисе
     *                  (нужно найти Department по departmentId из запроса).
     *   - active     — начальное значение true устанавливает сервис, не маппер.
     *
     * Поля, которые маппятся автоматически по именам: fullName, specialty, licenseNumber и т.д.
     *
     * @param request DTO с данными из HTTP POST-запроса
     * @return новая сущность Doctor, готовая к сохранению через doctorRepository.save()
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "active", ignore = true)
    Doctor toEntity(CreateDoctorRequest request);

    /**
     * Преобразует сущность Doctor в плоский DTO для JSON-ответа.
     *
     * Разворачивание вложенного объекта Department:
     * @Mapping(target = "departmentId", source = "department.id")
     *   — MapStruct генерирует null-safe доступ к вложенному объекту:
     *     response.setDepartmentId(doctor.getDepartment() != null
     *         ? doctor.getDepartment().getId() : null);
     *
     * @Mapping(target = "departmentName", source = "department.name")
     *   — аналогично извлекает имя отделения без дополнительного JOIN-запроса,
     *     т.к. department уже загружен вместе с доктором (EAGER или JOIN FETCH).
     *
     * Зачем плоский DTO? Клиент получает удобный JSON без вложенности:
     *   { "id": 1, "fullName": "...", "departmentId": 5, "departmentName": "Хирургия" }
     * вместо вложенного объекта Department.
     *
     * @param doctor JPA-сущность из репозитория
     * @return DoctorResponse — DTO для передачи по HTTP
     */
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    DoctorResponse toResponse(Doctor doctor);

    /**
     * Частичное обновление существующей сущности Doctor (семантика HTTP PATCH).
     *
     * @MappingTarget Doctor doctor — MapStruct не создаёт новый объект Doctor,
     * а вызывает сеттеры на уже существующем объекте. Это важно: объект уже
     * находится под управлением JPA-сессии (managed state), поэтому после выхода
     * из транзакции Spring Data автоматически сохранит изменения в БД (dirty checking).
     *
     * @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
     * — локальное переопределение (дублирует глобальную настройку для явности):
     * null-поля в UpdateDoctorRequest не затирают текущие значения в Doctor.
     * Например, если клиент не передал specialty — она останется прежней.
     *
     * Заблокированные поля:
     *   - id         — нельзя менять PK через обновление данных.
     *   - department — смена отделения — отдельная бизнес-операция с проверками.
     *   - active     — управляется через softDelete, не через обновление профиля.
     *
     * @param doctor   существующий объект Doctor из БД (изменяется на месте)
     * @param request  DTO с новыми значениями полей (null = "не менять это поле")
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateFromRequest(@MappingTarget Doctor doctor, UpdateDoctorRequest request);
}
