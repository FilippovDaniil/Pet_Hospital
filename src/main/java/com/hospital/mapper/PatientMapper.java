package com.hospital.mapper;

import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.Patient;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PatientMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registrationDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentDoctor", ignore = true)
    @Mapping(target = "currentWard", ignore = true)
    @Mapping(target = "active", ignore = true)
    Patient toEntity(CreatePatientRequest request);

    @Mapping(target = "currentDoctorId", source = "currentDoctor.id")
    @Mapping(target = "currentDoctorName", source = "currentDoctor.fullName")
    @Mapping(target = "currentWardId", source = "currentWard.id")
    @Mapping(target = "currentWardNumber", source = "currentWard.wardNumber")
    PatientResponse toResponse(Patient patient);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "birthDate", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "snils", ignore = true)
    @Mapping(target = "registrationDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentDoctor", ignore = true)
    @Mapping(target = "currentWard", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateFromRequest(@MappingTarget Patient patient,
                           com.hospital.dto.request.UpdatePatientRequest request);
}
