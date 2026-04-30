package com.hospital.mapper;

import com.hospital.dto.request.CreatePaidServiceRequest;
import com.hospital.dto.response.PaidServiceResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.entity.PaidService;
import com.hospital.entity.PatientPaidService;
import org.mapstruct.*;

/**
 * Маппер для работы с платными услугами и связями "пациент — услуга".
 *
 * --- Два типа маппинга в одном интерфейсе ---
 * Этот маппер работает с двумя разными сущностями:
 *   1. PaidService      — справочник платных услуг (название, цена).
 *   2. PatientPaidService — связь "пациент получил услугу" (промежуточная таблица ManyToMany
 *                          с дополнительными полями: дата, статус оплаты).
 *
 * Такой подход нормален: маппер группируется по домену (платные услуги), а не по сущности.
 *
 * --- componentModel = "spring" ---
 * Сгенерированный PaidServiceMapperImpl будет Spring Bean (@Component),
 * доступным для внедрения в PaidServiceServiceImpl через конструктор.
 *
 * --- Нет nullValuePropertyMappingStrategy ---
 * Как и WardMapper, этот маппер использует стратегию по умолчанию (SET_TO_NULL),
 * поскольку здесь нет операций частичного обновления — платные услуги только создаются
 * и читаются, а связи с пациентами только добавляются и обновляются статусом оплаты.
 */
@Mapper(componentModel = "spring")
public interface PaidServiceMapper {

    /**
     * Создаёт справочную сущность PaidService из HTTP-запроса.
     *
     * Игнорируемые поля:
     *   - id     — генерируется БД.
     *   - active — при создании услуга активна по умолчанию; значение устанавливает сервис.
     *
     * Автоматически маппятся: name, description, price (имена совпадают в DTO и сущности).
     *
     * @param request DTO с данными новой платной услуги (название, описание, цена)
     * @return сущность PaidService, готовая к сохранению в справочник
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    PaidService toEntity(CreatePaidServiceRequest request);

    /**
     * Преобразует сущность PaidService в DTO для ответа клиенту.
     *
     * Все поля маппятся автоматически по именам (id, name, description, price, active).
     * Явных @Mapping не требуется — MapStruct справится сам.
     *
     * Этот метод используется при запросе справочника услуг (GET /paid-services).
     *
     * @param paidService сущность из справочника платных услуг
     * @return PaidServiceResponse — DTO для JSON-ответа
     */
    PaidServiceResponse toResponse(PaidService paidService);

    /**
     * Преобразует промежуточную сущность PatientPaidService в DTO ответа.
     *
     * PatientPaidService — это JPA-сущность, представляющая связь ManyToMany между
     * Patient и PaidService с дополнительными полями (дата назначения, статус оплаты).
     * Она содержит ссылки на оба связанных объекта.
     *
     * Маппинг из двух вложенных объектов одновременно:
     *
     * @Mapping(target = "patientId",   source = "patient.id")
     * @Mapping(target = "patientName", source = "patient.fullName")
     *   — достаёт данные о пациенте из вложенного объекта Patient.
     *
     * @Mapping(target = "serviceId",   source = "paidService.id")
     * @Mapping(target = "serviceName", source = "paidService.name")
     * @Mapping(target = "price",       source = "paidService.price")
     *   — достаёт данные об услуге из вложенного объекта PaidService.
     *
     * Это демонстрирует возможность MapStruct разворачивать несколько вложенных объектов
     * одновременно в один плоский DTO — вся логика генерируется в compile-time.
     *
     * Остальные поля (id, assignedAt, paid и т.д.) маппятся по именам автоматически.
     *
     * @param link сущность PatientPaidService с заполненными связями patient и paidService
     * @return PatientPaidServiceResponse — плоский DTO со всеми нужными полями
     */
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientName", source = "patient.fullName")
    @Mapping(target = "serviceId", source = "paidService.id")
    @Mapping(target = "serviceName", source = "paidService.name")
    @Mapping(target = "price", source = "paidService.price")
    PatientPaidServiceResponse toLinkResponse(PatientPaidService link);
}
