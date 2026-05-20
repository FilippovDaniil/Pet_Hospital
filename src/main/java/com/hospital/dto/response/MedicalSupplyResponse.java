package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO ответа для позиции склада.
 *
 * Два поля типа:
 *   - category      ("MEDICINE")   — machine-readable для логики фронтенда.
 *   - categoryLabel ("Медикамент") — human-readable для отображения в таблице UI.
 *
 * lowStock вычисляется в NurseServiceImpl.toSupplyResponse() как quantity <= minQuantity.
 * Не хранится в БД — всегда актуален.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalSupplyResponse {

    /** Первичный ключ позиции. */
    private Long id;

    /** Название позиции: «Аспирин», «Шприц 5мл». */
    private String name;

    /** Enum-ключ категории: "MEDICINE" | "CONSUMABLE" | "EQUIPMENT". */
    private String category;

    /** Русский ярлык категории: «Медикамент» | «Расходник» | «Оборудование». */
    private String categoryLabel;

    /** Текущий остаток в единицах хранения. */
    private int quantity;

    /** Единица измерения: «таб.», «шт.», «мл.». */
    private String unit;

    /** Описание: состав, показания, инструкция. Может быть null. */
    private String description;

    /** Порог предупреждения о нехватке. */
    private int minQuantity;

    /**
     * true, если quantity <= minQuantity.
     * Используется в дашборде медсестры для подсветки строк красным.
     */
    private boolean lowStock;
}
