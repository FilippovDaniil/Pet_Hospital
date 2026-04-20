package com.hospital.service;

import com.hospital.dto.request.CreateDepartmentRequest;
import com.hospital.dto.request.UpdateDepartmentRequest;
import com.hospital.dto.response.DepartmentResponse;

import java.util.List;

public interface DepartmentService {

    DepartmentResponse create(CreateDepartmentRequest request);

    DepartmentResponse getById(Long id);

    List<DepartmentResponse> getAll();

    DepartmentResponse update(Long id, UpdateDepartmentRequest request);

    void delete(Long id);
}
