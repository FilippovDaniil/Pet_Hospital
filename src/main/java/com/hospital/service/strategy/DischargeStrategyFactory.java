package com.hospital.service.strategy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory that selects the correct DischargeStrategy by DischargeType.
 * All strategy beans are injected automatically by Spring.
 */
@Component
public class DischargeStrategyFactory {

    private final Map<DischargeType, DischargeStrategy> strategies = new EnumMap<>(DischargeType.class);

    public DischargeStrategyFactory(List<DischargeStrategy> strategyList) {
        strategyList.forEach(s -> strategies.put(s.getType(), s));
    }

    public DischargeStrategy getStrategy(DischargeType type) {
        DischargeStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No discharge strategy found for type: " + type);
        }
        return strategy;
    }
}
