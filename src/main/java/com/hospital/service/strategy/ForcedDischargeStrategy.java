package com.hospital.service.strategy;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Administrative forced discharge — patient may leave against medical advice.
 */
@Component
@Slf4j
public class ForcedDischargeStrategy implements DischargeStrategy {

    @Override
    public DischargeType getType() {
        return DischargeType.FORCED;
    }

    @Override
    public void discharge(Patient patient) {
        log.warn("FORCED discharge applied to patient id={}", patient.getId());
        patient.setStatus(PatientStatus.DISCHARGED);
        patient.setCurrentDoctor(null);
    }
}
