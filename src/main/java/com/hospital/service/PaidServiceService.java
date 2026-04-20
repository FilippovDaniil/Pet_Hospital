package com.hospital.service;

import com.hospital.dto.request.CreatePaidServiceRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PaidServiceResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import org.springframework.data.domain.Pageable;

public interface PaidServiceService {

    PaidServiceResponse create(CreatePaidServiceRequest request);

    PaidServiceResponse getById(Long id);

    PageResponse<PaidServiceResponse> getAll(Pageable pageable);

    PatientPaidServiceResponse assignToPatient(Long patientId, Long serviceId);

    PatientPaidServiceResponse markPaid(Long patientId, Long linkId);
}
