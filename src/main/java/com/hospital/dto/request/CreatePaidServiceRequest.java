package com.hospital.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO для создания новой платной услуги (POST /api/paid-services).
 *
 * <p>Платная услуга — это справочная запись (например, «УЗИ брюшной полости»,
 * «Консультация кардиолога»). После создания услуга может назначаться
 * пациентам через {@code PatientPaidService}.
 *
 * <p>Цена хранится в типе {@code BigDecimal}, а не {@code double} / {@code float},
 * потому что денежные суммы требуют точных вычислений без ошибок округления
 * с плавающей точкой. {@code BigDecimal} гарантирует точность до копеек.
 */
@Data
public class CreatePaidServiceRequest {

    /**
     * Название платной услуги.
     *
     * <p><b>@NotBlank</b> — название обязательно; нельзя создать услугу
     * без понятного наименования.
     */
    @NotBlank(message = "Service name is required")
    private String name;

    /**
     * Стоимость услуги в рублях.
     *
     * <p><b>@NotNull</b> — цена обязательна; бесплатных «платных» услуг
     * не бывает.<br>
     * <b>@DecimalMin(value = "0.01")</b> — минимальная допустимая цена
     * составляет одну копейку. Это исключает нулевую и отрицательную цену.
     * {@code @DecimalMin} работает с {@code BigDecimal}, {@code BigInteger},
     * {@code String} и числовыми типами, в отличие от {@code @Min},
     * который работает только с целыми числами.
     */
    @NotNull
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    /**
     * Описание услуги — что включает, показания, длительность и т.п.
     * Необязательное поле; при отсутствии сохраняется как {@code null}.
     */
    private String description;
}
