package com.hospital.service.strategy;

import com.hospital.entity.Patient;

/**
 * Strategy pattern: different discharge behaviours depending on the clinical context.
 */
public interface DischargeStrategy {

    DischargeType getType();

    /** Applies discharge-specific business logic to the patient before it is persisted. */
    void discharge(Patient patient);
}
