package com.hospital.service.strategy;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Стратегия обычной плановой выписки пациента домой.
 *
 * Это наиболее распространённый тип выписки: плановое завершение лечения.
 * Пациент выписывается в удовлетворительном состоянии, курс лечения завершён.
 *
 * @Component — Spring зарегистрирует этот бин, DischargeStrategyFactory его найдёт
 * и добавит в карту стратегий с ключом DischargeType.NORMAL.
 */
@Component
@Slf4j
public class NormalDischargeStrategy implements DischargeStrategy {

    /**
     * Идентификатор стратегии для DischargeStrategyFactory.
     * Фабрика вызывает этот метод при построении карты стратегий.
     */
    @Override
    public DischargeType getType() {
        return DischargeType.NORMAL;
    }

    /**
     * Применяет логику обычной выписки:
     * 1. Устанавливает статус DISCHARGED — пациент выписан.
     * 2. Обнуляет текущего врача — связь с лечащим врачом разрывается.
     *
     * Обратите внимание: this method НЕ сохраняет пациента в БД.
     * Сохранение — ответственность AdminServiceImpl, который вызывает эту стратегию.
     * Разделение ответственности: стратегия только изменяет объект, а не управляет транзакцией.
     */
    @Override
    public void discharge(Patient patient) {
        log.info("Normal discharge for patient id={}", patient.getId());
        patient.setStatus(PatientStatus.DISCHARGED);
        patient.setCurrentDoctor(null); // врач больше не ведёт выписанного пациента
    }
}
