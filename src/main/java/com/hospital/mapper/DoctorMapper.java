package com.hospital.mapper;

import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.dto.request.UpdateDoctorRequest;
import com.hospital.dto.response.DoctorResponse;
import com.hospital.entity.Doctor;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DoctorMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "active", ignore = true)
    Doctor toEntity(CreateDoctorRequest request);

    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    DoctorResponse toResponse(Doctor doctor);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateFromRequest(@MappingTarget Doctor doctor, UpdateDoctorRequest request);
}
