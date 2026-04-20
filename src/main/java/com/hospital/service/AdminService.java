package com.hospital.service;

import com.hospital.dto.response.PaidServiceSummaryReport;
import com.hospital.dto.response.PatientResponse;
import com.hospital.dto.response.WardOccupancyReport;
import com.hospital.service.strategy.DischargeType;

import java.util.List;

public interface AdminService {

    List<WardOccupancyReport> getWardOccupancyReport();

    PaidServiceSummaryReport getPaidServicesSummary();

    PatientResponse dischargePatient(Long patientId, DischargeType dischargeType);
}
