package com.hospital.service.strategy;

/**
 * Перечисление типов выписки пациента.
 *
 * Используется как ключ для выбора стратегии в DischargeStrategyFactory.
 * Клиент REST API передаёт тип выписки в запросе (строкой, Spring автоматически
 * конвертирует её в enum через StringToEnumConverter).
 *
 * Пример HTTP-запроса:
 *   POST /api/admin/patients/5/discharge?type=TRANSFER
 */
public enum DischargeType {
    /** Обычная плановая выписка — пациент выписан домой. Статус → DISCHARGED. */
    NORMAL,

    /** Административная выписка по требованию или против воли пациента. Статус → DISCHARGED. */
    FORCED,

    /** Перевод в другое лечебное учреждение. Статус → TRANSFERRED. */
    TRANSFER
}
