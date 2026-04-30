package com.hospital.service.strategy;

import com.hospital.entity.Patient;

/**
 * ПАТТЕРН СТРАТЕГИЯ (Strategy Pattern) — интерфейс для алгоритма выписки пациента.
 *
 * Паттерн Strategy позволяет выбирать алгоритм в рантайме без изменения кода,
 * который этот алгоритм использует.
 *
 * ПРОБЛЕМА без паттерна:
 *   В AdminServiceImpl был бы такой код:
 *     if (type == NORMAL)   { patient.setStatus(DISCHARGED); ... }
 *     else if (type == FORCED) { patient.setStatus(DISCHARGED); ... другая логика }
 *     else if (type == TRANSFER) { patient.setStatus(TRANSFERRED); ... }
 *   Добавить новый тип выписки = изменить существующий класс (нарушение Open/Closed Principle).
 *
 * РЕШЕНИЕ с паттерном Strategy:
 *   Каждый тип выписки — отдельный класс, реализующий этот интерфейс.
 *   AdminServiceImpl не знает о конкретных типах: strategyFactory.getStrategy(type).discharge(patient).
 *   Новый тип выписки = новый класс, существующий код не трогаем.
 *
 * Три реализации:
 *   NormalDischargeStrategy   — обычная выписка домой (статус DISCHARGED)
 *   ForcedDischargeStrategy   — административная выписка (статус DISCHARGED, с предупреждением)
 *   TransferDischargeStrategy — перевод в другое учреждение (статус TRANSFERRED)
 */
public interface DischargeStrategy {

    /**
     * Возвращает тип выписки, который реализует эта стратегия.
     * DischargeStrategyFactory использует этот метод для построения карты: тип → стратегия.
     */
    DischargeType getType();

    /**
     * Применяет бизнес-логику выписки к сущности пациента.
     * Метод ИЗМЕНЯЕТ переданный объект (устанавливает статус, обнуляет врача и т.д.).
     * Сохранение изменённого пациента в БД — ответственность вызывающего кода (AdminServiceImpl).
     *
     * @param patient сущность пациента — будет изменена "на месте"
     */
    void discharge(Patient patient);
}
