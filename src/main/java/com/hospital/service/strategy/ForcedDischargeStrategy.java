package com.hospital.service.strategy;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Стратегия принудительной административной выписки.
 *
 * Используется в случаях:
 *   - Пациент отказывается от лечения (АМА — against medical advice)
 *   - Административное решение о выписке
 *   - Грубое нарушение правил учреждения
 *
 * Отличие от NormalDischargeStrategy: здесь используется log.warn (WARN, не INFO),
 * чтобы этот факт всегда попадал в логи и был заметен при аудите.
 * В реальной системе здесь можно добавить создание специальной аудит-записи,
 * уведомление главного врача и т.д.
 *
 * Статус пациента после выписки — тот же DISCHARGED, что и при обычной,
 * но логика применения (и потенциально дополнительные действия) отличается.
 */
@Component
@Slf4j
public class ForcedDischargeStrategy implements DischargeStrategy {

    @Override
    public DischargeType getType() {
        return DischargeType.FORCED;
    }

    /**
     * WARN-уровень логирования намеренно — принудительная выписка нетипична
     * и должна привлечь внимание при просмотре логов.
     */
    @Override
    public void discharge(Patient patient) {
        log.warn("FORCED discharge applied to patient id={}", patient.getId());
        patient.setStatus(PatientStatus.DISCHARGED);
        patient.setCurrentDoctor(null);
    }
}
