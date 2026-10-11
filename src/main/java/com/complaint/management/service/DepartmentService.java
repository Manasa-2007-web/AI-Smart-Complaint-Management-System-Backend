package com.complaint.management.service;

import com.complaint.management.dto.DepartmentRequest;
import com.complaint.management.dto.DepartmentResponse;
import com.complaint.management.entity.Department;
import com.complaint.management.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String name = request.name().trim();
        if (departmentRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new IllegalArgumentException("A department with this name already exists");
        }
        Department department = departmentRepository.save(new Department(name, clean(request.description())));
        return toResponse(department);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> findAll() {
        return departmentRepository.findAll().stream().map(this::toResponse).toList();
    }

    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(department.getId(), department.getName(), department.getDescription());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
