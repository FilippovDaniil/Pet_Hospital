package com.hospital.service;

import com.hospital.dto.request.CreateWardRequest;
import com.hospital.dto.response.WardResponse;

import java.util.List;

public interface WardService {

    WardResponse create(CreateWardRequest request);

    WardResponse getById(Long id);

    List<WardResponse> getAll();

    WardResponse admitPatient(Long wardId, Long patientId);

    WardResponse dischargePatientFromWard(Long wardId, Long patientId);
}
