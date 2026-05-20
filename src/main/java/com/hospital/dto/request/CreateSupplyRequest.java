package com.hospital.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO для создания/обновления позиции склада (POST/PUT /api/nurse/supplies).
 *
 * category передаётся как строка ("MEDICINE", "CONSUMABLE", "EQUIPMENT"),
 * а не как enum, чтобы не связывать API с Java-типом: фронтенд просто
 * отправляет одно из допустимых строковых значений. Конвертация в
 * SupplyCategory.valueOf() выполняется в NurseServiceImpl.
 */
@Data
public class CreateSupplyRequest {

    /** Название позиции: «Аспирин», «Шприц 5мл». Не может быть пустым. */
    @NotBlank
    private String name;

    /** Категория: "MEDICINE" | "CONSUMABLE" | "EQUIPMENT". Обязательное поле. */
    @NotNull
    private String category;

    /** Начальный остаток. 0 — допустимо (позиция есть, но пока не пополнена). */
    @Min(0)
    private int quantity;

    /** Единица измерения: «таб.», «шт.», «мл.». Не может быть пустой. */
    @NotBlank
    private String unit;

    /** Описание: состав, показания, инструкция. Необязательное. */
    private String description;

    /**
     * Порог предупреждения о низком остатке.
     * Дефолт 5 — разумный минимум для большинства позиций.
     * @Min(0) — нельзя задать отрицательный порог.
     */
    @Min(0)
    private int minQuantity = 5;
}
