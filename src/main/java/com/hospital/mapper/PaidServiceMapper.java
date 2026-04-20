package com.hospital.mapper;

import com.hospital.dto.request.CreatePaidServiceRequest;
import com.hospital.dto.response.PaidServiceResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.entity.PaidService;
import com.hospital.entity.PatientPaidService;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PaidServiceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    PaidService toEntity(CreatePaidServiceRequest request);

    PaidServiceResponse toResponse(PaidService paidService);

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientName", source = "patient.fullName")
    @Mapping(target = "serviceId", source = "paidService.id")
    @Mapping(target = "serviceName", source = "paidService.name")
    @Mapping(target = "price", source = "paidService.price")
    PatientPaidServiceResponse toLinkResponse(PatientPaidService link);
}
