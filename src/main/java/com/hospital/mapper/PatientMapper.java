package com.hospital.mapper;

import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.Patient;
import org.mapstruct.*;

/**
 * Маппер для преобразования объектов пациента между слоями приложения.
 *
 * --- Что такое MapStruct? ---
 * MapStruct — это генератор кода на основе аннотаций (Annotation Processor).
 * Он работает во время КОМПИЛЯЦИИ (не в рантайме), читает этот интерфейс и автоматически
 * создаёт реализацию — класс PatientMapperImpl.java в папке target/generated-sources/.
 *
 * Главное отличие от рефлексии (например, ModelMapper):
 *   - Рефлексия: маппинг происходит во время выполнения программы через java.lang.reflect,
 *     что медленно и может вызывать ошибки только в рантайме.
 *   - MapStruct: генерирует обычный Java-код с геттерами/сеттерами. Это так же быстро,
 *     как написанный вручную маппинг. Ошибки видны на этапе компиляции.
 *
 * --- Параметры @Mapper ---
 * componentModel = "spring" — говорит MapStruct добавить аннотацию @Component к сгенерированному
 *   классу, чтобы Spring автоматически нашёл его и зарегистрировал как Spring Bean.
 *   После этого маппер можно внедрять через @Autowired или через конструктор в любой сервис.
 *
 * nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE — глобальная стратегия
 *   для всего маппера: если поле в источнике равно null, не перезаписывать поле в цели.
 *   Особенно полезно при частичных обновлениях (PATCH-запросы).
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PatientMapper {

    /**
     * Преобразует запрос на создание пациента в JPA-сущность Patient.
     *
     * Поля, которые MapStruct проигнорирует (не будет трогать в сгенерированном коде):
     *   - id         — генерируется базой данных (@GeneratedValue), задавать его вручную нельзя.
     *   - registrationDate — устанавливается автоматически (например, через @PrePersist или в сервисе).
     *   - status     — начальный статус задаётся бизнес-логикой сервиса, не из запроса.
     *   - currentDoctor — связь назначается отдельным эндпоинтом, не при создании.
     *   - currentWard   — аналогично: размещение в палате — отдельная операция.
     *   - active     — по умолчанию true, управляется логикой soft-delete.
     *
     * @Mapping(target = "...", ignore = true) означает: "пропусти это поле полностью,
     * не вызывай для него сеттер в сгенерированном коде".
     *
     * @param request DTO с данными из HTTP-запроса (имя, дата рождения, СНИЛС и т.д.)
     * @return новая сущность Patient с заполненными полями из запроса
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registrationDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentDoctor", ignore = true)
    @Mapping(target = "currentWard", ignore = true)
    @Mapping(target = "active", ignore = true)
    Patient toEntity(CreatePatientRequest request);

    /**
     * Преобразует JPA-сущность Patient в PatientResponse для возврата клиенту.
     *
     * Здесь демонстрируется мощная возможность MapStruct — маппинг вложенных объектов
     * через точечную нотацию (dot notation):
     *
     * @Mapping(target = "currentDoctorId", source = "currentDoctor.id")
     *   — MapStruct сгенерирует код:
     *     if (patient.getCurrentDoctor() != null) {
     *         response.setCurrentDoctorId(patient.getCurrentDoctor().getId());
     *     }
     *   То есть он автоматически обрабатывает null-safety для вложенного объекта.
     *
     * @Mapping(target = "currentDoctorName", source = "currentDoctor.fullName")
     *   — аналогично достаёт fullName из вложенного объекта Doctor.
     *
     * Остальные поля (firstName, lastName, birthDate, snils и т.д.) маппятся автоматически
     * по совпадению имён между Patient и PatientResponse — MapStruct находит их сам.
     *
     * @param patient JPA-сущность из базы данных
     * @return PatientResponse — плоский DTO для JSON-ответа (без вложенных объектов)
     */
    @Mapping(target = "currentDoctorId", source = "currentDoctor.id")
    @Mapping(target = "currentDoctorName", source = "currentDoctor.fullName")
    @Mapping(target = "currentWardId", source = "currentWard.id")
    @Mapping(target = "currentWardNumber", source = "currentWard.wardNumber")
    PatientResponse toResponse(Patient patient);

    /**
     * Частичное обновление сущности Patient из запроса на обновление.
     *
     * Ключевая особенность этого метода — аннотация @MappingTarget:
     *   @MappingTarget Patient patient — говорит MapStruct, что не нужно создавать новый объект,
     *   а нужно ИЗМЕНИТЬ уже существующий объект patient, переданный как параметр.
     *   Сгенерированный код будет вызывать сеттеры на существующем объекте.
     *
     * @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
     *   — локальная стратегия для этого конкретного метода: если поле в request равно null,
     *   НЕ перезаписывать соответствующее поле в patient. Это реализация семантики PATCH:
     *   клиент присылает только те поля, которые хочет изменить, остальные остаются как есть.
     *
     * Поля, заблокированные от изменения через этот метод:
     *   - id, registrationDate, snils, birthDate, gender — неизменяемые атрибуты пациента.
     *   - status, currentDoctor, currentWard, active — управляются отдельной бизнес-логикой.
     *
     * Возвращаемый тип void означает, что изменения применяются к переданному объекту напрямую,
     * новый объект не создаётся — это эффективно, т.к. объект уже привязан к JPA-сессии.
     *
     * @param patient  существующая сущность из БД, которую нужно обновить (изменяется на месте)
     * @param request  DTO с полями для обновления (null-поля игнорируются)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "birthDate", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "snils", ignore = true)
    @Mapping(target = "registrationDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentDoctor", ignore = true)
    @Mapping(target = "currentWard", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateFromRequest(@MappingTarget Patient patient,
                           com.hospital.dto.request.UpdatePatientRequest request);
}
