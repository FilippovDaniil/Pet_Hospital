package com.hospital.service;

import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.dto.request.UpdatePatientRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.dto.response.PatientResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PatientService {

    PatientResponse create(CreatePatientRequest request);

    PatientResponse getById(Long id);

    PageResponse<PatientResponse> getAll(Pageable pageable);

    PatientResponse update(Long id, UpdatePatientRequest request);

    void softDelete(Long id);

    PatientResponse assignDoctor(Long patientId, Long doctorId);

    List<PatientPaidServiceResponse> getServices(Long patientId);
}
