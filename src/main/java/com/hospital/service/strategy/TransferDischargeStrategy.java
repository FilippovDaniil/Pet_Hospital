package com.hospital.service.strategy;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Стратегия выписки с переводом в другое медицинское учреждение.
 *
 * Применяется когда пациент переводится в другую больницу, специализированную клинику
 * или реабилитационный центр.
 *
 * Ключевое отличие от NormalDischargeStrategy:
 *   - Статус устанавливается TRANSFERRED, а не DISCHARGED.
 *   Это позволяет в отчётах различать пациентов "выписан домой" и "переведён".
 *
 * В расширенной версии системы здесь можно добавить:
 *   - Указание принимающего учреждения
 *   - Автоматическое создание выписки (discharge summary)
 *   - Уведомление принимающего учреждения через внешний API
 */
@Component
@Slf4j
public class TransferDischargeStrategy implements DischargeStrategy {

    @Override
    public DischargeType getType() {
        return DischargeType.TRANSFER;
    }

    @Override
    public void discharge(Patient patient) {
        log.info("Transfer discharge for patient id={}", patient.getId());
        // TRANSFERRED отличается от DISCHARGED: пациент ушёл не домой, а в другое учреждение
        patient.setStatus(PatientStatus.TRANSFERRED);
        patient.setCurrentDoctor(null); // врач нашей клиники больше не ведёт пациента
    }
}
