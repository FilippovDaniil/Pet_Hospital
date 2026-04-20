package com.hospital.mapper;

import com.hospital.dto.request.CreateWardRequest;
import com.hospital.dto.response.WardResponse;
import com.hospital.entity.Ward;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface WardMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentOccupancy", ignore = true)
    @Mapping(target = "department", ignore = true)
    Ward toEntity(CreateWardRequest request);

    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "freeSlots", expression = "java(ward.freeSlots())")
    WardResponse toResponse(Ward ward);
}
