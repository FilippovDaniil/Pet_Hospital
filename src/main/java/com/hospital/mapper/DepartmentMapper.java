package com.hospital.mapper;

import com.hospital.dto.request.CreateDepartmentRequest;
import com.hospital.dto.request.UpdateDepartmentRequest;
import com.hospital.dto.response.DepartmentResponse;
import com.hospital.entity.Department;
import org.mapstruct.*;

/**
 * Маппер для преобразования сущности Department между слоями приложения.
 *
 * --- Как MapStruct находит совпадающие поля? ---
 * По умолчанию MapStruct сопоставляет поля по ИМЕНИ (case-sensitive).
 * Если в Department есть поле "name", а в DepartmentResponse тоже есть "name" —
 * маппинг произойдёт автоматически: MapStruct вызовет getName() и setName().
 *
 * --- Когда нужен явный @Mapping? ---
 * 1. Имена полей различаются (source != target).
 * 2. Нужно достать поле из вложенного объекта (source = "headDoctor.id").
 * 3. Нужно игнорировать поле при маппинге (ignore = true).
 * 4. Нужно использовать Java-выражение для вычисления значения (expression = "java(...)").
 *
 * --- componentModel = "spring" ---
 * Сгенерированный DepartmentMapperImpl будет аннотирован @Component.
 * Spring добавит его в контекст, и он будет доступен для внедрения в DepartmentServiceImpl.
 *
 * --- nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE ---
 * При маппинге null-значений из источника в цель — пропускать их.
 * Это обеспечивает корректную работу частичного обновления.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DepartmentMapper {

    /**
     * Создаёт сущность Department из HTTP-запроса на создание отделения.
     *
     * Игнорируемые поля:
     *   - id          — первичный ключ, генерируется БД (@GeneratedValue).
     *   - headDoctor  — заведующий отделением назначается отдельной операцией;
     *                   при создании отделение не обязано сразу иметь заведующего.
     *
     * Поля, которые маппятся автоматически: name, description и другие текстовые поля,
     * если их имена совпадают в DTO и сущности.
     *
     * @param request DTO с данными нового отделения из тела HTTP-запроса
     * @return сущность Department, готовая к сохранению через repository.save()
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "headDoctor", ignore = true)
    Department toEntity(CreateDepartmentRequest request);

    /**
     * Преобразует сущность Department в плоский DTO DepartmentResponse.
     *
     * Разворачивание вложенного объекта Doctor (заведующего):
     *
     * @Mapping(target = "headDoctorId", source = "headDoctor.id")
     *   — MapStruct генерирует null-safe код:
     *     if (department.getHeadDoctor() != null) {
     *         response.setHeadDoctorId(department.getHeadDoctor().getId());
     *     }
     *   Если заведующий ещё не назначен (headDoctor == null), поле в ответе будет null.
     *
     * @Mapping(target = "headDoctorName", source = "headDoctor.fullName")
     *   — аналогично извлекает полное имя заведующего.
     *
     * Такой "плоский" DTO удобен для фронтенда: не нужно разбирать вложенные объекты.
     * JSON-ответ будет выглядеть как:
     *   { "id": 1, "name": "Хирургия", "headDoctorId": 3, "headDoctorName": "Иванов И.И." }
     *
     * @param department JPA-сущность из базы данных
     * @return DepartmentResponse — DTO для HTTP-ответа
     */
    @Mapping(target = "headDoctorId", source = "headDoctor.id")
    @Mapping(target = "headDoctorName", source = "headDoctor.fullName")
    DepartmentResponse toResponse(Department department);

    /**
     * Применяет изменения из запроса на обновление к существующей сущности Department.
     *
     * @MappingTarget Department department — MapStruct не создаёт новый объект,
     * а обновляет переданный. Объект department уже находится в JPA persistence context,
     * поэтому грязное чтение (dirty checking) Hibernate автоматически зафиксирует
     * изменения в БД при завершении транзакции — без явного вызова repository.save().
     *
     * @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
     * — если поле в UpdateDepartmentRequest равно null, соответствующее поле в Department
     * не перезаписывается. Это поведение PATCH: обновляются только переданные поля.
     *
     * Заблокированные поля:
     *   - id         — первичный ключ неизменяем.
     *   - headDoctor — назначение заведующего — отдельная привилегированная операция
     *                  с дополнительными проверками (например, только активные врачи).
     *
     * @param department существующая сущность Department (изменяется напрямую)
     * @param request    DTO с новыми значениями (null-поля игнорируются)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "headDoctor", ignore = true)
    void updateFromRequest(@MappingTarget Department department, UpdateDepartmentRequest request);
}
