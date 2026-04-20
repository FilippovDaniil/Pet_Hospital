package com.hospital.service.strategy;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Patient is transferred to another facility; status becomes TRANSFERRED and doctor is unlinked.
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
        patient.setStatus(PatientStatus.TRANSFERRED);
        patient.setCurrentDoctor(null);
    }
}
