package com.hospital.service;

import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.dto.request.UpdateDoctorRequest;
import com.hospital.dto.response.DoctorResponse;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.Specialty;
import org.springframework.data.domain.Pageable;

public interface DoctorService {

    DoctorResponse create(CreateDoctorRequest request);

    DoctorResponse getById(Long id);

    PageResponse<DoctorResponse> getAll(Pageable pageable);

    PageResponse<DoctorResponse> getBySpecialty(Specialty specialty, Pageable pageable);

    DoctorResponse update(Long id, UpdateDoctorRequest request);

    void softDelete(Long id);

    PageResponse<PatientResponse> getPatients(Long doctorId, Pageable pageable);
}
