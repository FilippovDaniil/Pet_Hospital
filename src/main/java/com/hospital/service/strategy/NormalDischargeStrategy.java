package com.hospital.service.strategy;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NormalDischargeStrategy implements DischargeStrategy {

    @Override
    public DischargeType getType() {
        return DischargeType.NORMAL;
    }

    @Override
    public void discharge(Patient patient) {
        log.info("Normal discharge for patient id={}", patient.getId());
        patient.setStatus(PatientStatus.DISCHARGED);
        patient.setCurrentDoctor(null);
    }
}
