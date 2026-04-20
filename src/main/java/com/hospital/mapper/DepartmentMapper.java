package com.hospital.mapper;

import com.hospital.dto.request.CreateDepartmentRequest;
import com.hospital.dto.request.UpdateDepartmentRequest;
import com.hospital.dto.response.DepartmentResponse;
import com.hospital.entity.Department;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DepartmentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "headDoctor", ignore = true)
    Department toEntity(CreateDepartmentRequest request);

    @Mapping(target = "headDoctorId", source = "headDoctor.id")
    @Mapping(target = "headDoctorName", source = "headDoctor.fullName")
    DepartmentResponse toResponse(Department department);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "headDoctor", ignore = true)
    void updateFromRequest(@MappingTarget Department department, UpdateDepartmentRequest request);
}
