package com.hospital.service.strategy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ФАБРИКА СТРАТЕГИЙ ВЫПИСКИ (Factory + Strategy паттерны).
 *
 * Связывает DischargeType (ключ) с нужной реализацией DischargeStrategy (значение).
 * Хранит все стратегии в Map для O(1) поиска по типу.
 *
 * КАК SPRING АВТОМАТИЧЕСКИ ВНЕДРЯЕТ ВСЕ СТРАТЕГИИ:
 *   Spring видит конструктор с параметром List<DischargeStrategy>.
 *   Он находит ВСЕ бины, реализующие интерфейс DischargeStrategy
 *   (NormalDischargeStrategy, ForcedDischargeStrategy, TransferDischargeStrategy)
 *   и собирает их в список.
 *   Конструктор фабрики получает этот список и строит Map.
 *
 * Преимущество: добавление нового типа выписки = только новый @Component класс.
 * Этот класс даже не нужно редактировать — Spring сам найдёт новую стратегию.
 *
 * EnumMap — специализированная реализация Map для enum-ключей.
 * Работает быстрее HashMap (использует массив по ordinal()) и требует меньше памяти.
 */
@Component
public class DischargeStrategyFactory {

    // Map: тип выписки → реализация стратегии
    private final Map<DischargeType, DischargeStrategy> strategies = new EnumMap<>(DischargeType.class);

    /**
     * Конструктор получает список ВСЕХ бинов-стратегий от Spring.
     * strategyList.forEach(s -> strategies.put(s.getType(), s)) — заполняем карту:
     * каждая стратегия сама сообщает, за какой тип она отвечает (через getType()).
     */
    public DischargeStrategyFactory(List<DischargeStrategy> strategyList) {
        strategyList.forEach(s -> strategies.put(s.getType(), s));
    }

    /**
     * Возвращает стратегию выписки для заданного типа.
     *
     * @param type тип выписки (NORMAL, FORCED, TRANSFER)
     * @return реализация стратегии
     * @throws IllegalArgumentException если стратегия для данного типа не зарегистрирована.
     *         Это программная ошибка — значит добавили новый DischargeType,
     *         но забыли создать соответствующую стратегию.
     */
    public DischargeStrategy getStrategy(DischargeType type) {
        DischargeStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No discharge strategy found for type: " + type);
        }
        return strategy;
    }
}
