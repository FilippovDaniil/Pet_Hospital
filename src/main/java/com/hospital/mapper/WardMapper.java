package com.hospital.mapper;

import com.hospital.dto.request.CreateWardRequest;
import com.hospital.dto.response.WardResponse;
import com.hospital.entity.Ward;
import org.mapstruct.*;

/**
 * Маппер для преобразования сущности Ward (палата) между слоями приложения.
 *
 * --- Особенность этого маппера: expression = "java(...)" ---
 * В отличие от других маппперов, здесь используется Java-выражение для вычисления
 * производного поля freeSlots (количество свободных мест в палате).
 * Это поле НЕ хранится в БД — оно вычисляется на лету из capacity и currentOccupancy.
 *
 * --- componentModel = "spring" без nullValuePropertyMappingStrategy ---
 * Обратите внимание: у этого маппера нет nullValuePropertyMappingStrategy.
 * Это означает использование стратегии по умолчанию — SET_TO_NULL:
 * если source-поле null, то target-поле будет установлено в null.
 * Это нормально для WardMapper, т.к. метода частичного обновления (updateFromRequest) нет —
 * палата создаётся и обновляется через целостные операции (assign/discharge patient).
 */
@Mapper(componentModel = "spring")
public interface WardMapper {

    /**
     * Создаёт сущность Ward из HTTP-запроса на создание палаты.
     *
     * Игнорируемые поля:
     *   - id               — первичный ключ, генерируется БД.
     *   - currentOccupancy — начальное значение 0 устанавливается бизнес-логикой или @PrePersist;
     *                        нельзя позволить клиенту задать начальную заполняемость.
     *   - department       — связь с отделением устанавливается в сервисе по departmentId:
     *                        нужно найти объект Department в БД и установить связь вручную.
     *
     * Автоматически маппятся: wardNumber, capacity и другие поля с совпадающими именами.
     *
     * @param request DTO с параметрами новой палаты
     * @return сущность Ward, готовая к сохранению
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentOccupancy", ignore = true)
    @Mapping(target = "department", ignore = true)
    Ward toEntity(CreateWardRequest request);

    /**
     * Преобразует сущность Ward в WardResponse с вычисляемым полем freeSlots.
     *
     * --- @Mapping с source (вложенный объект) ---
     * @Mapping(target = "departmentId", source = "department.id")
     *   — извлекает id отделения из вложенного объекта Department.
     *   MapStruct генерирует null-safe код с проверкой на null для ward.getDepartment().
     *
     * @Mapping(target = "departmentName", source = "department.name")
     *   — извлекает название отделения.
     *
     * --- @Mapping с expression = "java(...)" ---
     * @Mapping(target = "freeSlots", expression = "java(ward.freeSlots())")
     *   — это мощный инструмент MapStruct для случаев, когда нельзя обойтись автоматикой.
     *   Выражение вычисляется прямо в сгенерированном коде:
     *     response.setFreeSlots(ward.freeSlots());
     *   Метод freeSlots() определён в сущности Ward и возвращает (capacity - currentOccupancy).
     *
     *   Когда использовать expression:
     *     - Вычисляемые поля (производные значения).
     *     - Преобразования типов, не поддерживаемые встроенными конвертерами MapStruct.
     *     - Вызов статических утилитарных методов.
     *   Важно: внутри expression можно использовать импортированные классы через
     *   @Mapper(imports = {MyUtil.class}).
     *
     * @param ward JPA-сущность палаты из репозитория
     * @return WardResponse — DTO с плоской структурой и вычисленным freeSlots
     */
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "freeSlots", expression = "java(ward.freeSlots())")
    WardResponse toResponse(Ward ward);
}
