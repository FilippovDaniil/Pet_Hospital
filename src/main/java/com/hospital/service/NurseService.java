package com.hospital.service;

import com.hospital.dto.request.AdjustSupplyRequest;
import com.hospital.dto.request.CreateAssignmentRequest;
import com.hospital.dto.request.CreateSupplyRequest;
import com.hospital.dto.response.MedicalSupplyResponse;
import com.hospital.dto.response.NurseAssignmentResponse;

import java.util.List;

public interface NurseService {

    List<MedicalSupplyResponse> getAllSupplies();
    MedicalSupplyResponse createSupply(CreateSupplyRequest request);
    MedicalSupplyResponse updateSupply(Long id, CreateSupplyRequest request);
    MedicalSupplyResponse adjustSupply(Long id, AdjustSupplyRequest request);
    void deleteSupply(Long id);

    List<NurseAssignmentResponse> getAllAssignments(String status);
    NurseAssignmentResponse createAssignment(String nurseUsername, CreateAssignmentRequest request);
    NurseAssignmentResponse updateAssignmentStatus(Long id, String status);
    void deleteAssignment(Long id);

    List<NurseAssignmentResponse> getClientAssignments(Long clientUserId);
}
