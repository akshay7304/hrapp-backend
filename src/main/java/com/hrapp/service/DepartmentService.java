package com.hrapp.service;

import com.hrapp.dto.request.DepartmentRequest;
import com.hrapp.dto.response.DepartmentResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Department;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.DepartmentRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        Long companyId = requireCallerCompanyId();

        if (departmentRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new ConflictException("Department with this name already exists");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        Department department = Department.builder()
                .company(company)
                .name(request.getName())
                .build();
        department = departmentRepository.save(department);

        log.info("Created department id={} name='{}' in company={}", department.getId(), department.getName(), companyId);
        return toResponse(department);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments() {
        Long companyId = requireCallerCompanyId();
        return departmentRepository.findByCompanyId(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        Long companyId = requireCallerCompanyId();

        Department department = departmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        if (!department.getName().equalsIgnoreCase(request.getName())
                && departmentRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new ConflictException("Department with this name already exists");
        }

        department.setName(request.getName());
        department = departmentRepository.save(department);

        log.info("Updated department id={} name='{}'", department.getId(), department.getName());
        return toResponse(department);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Long companyId = requireCallerCompanyId();

        Department department = departmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        departmentRepository.delete(department);
        log.info("Deleted department id={}", id);
    }

    private Long requireCallerCompanyId() {
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new BadRequestException("Caller is not bound to a company");
        }
        return companyId;
    }

    private DepartmentResponse toResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .companyId(department.getCompany() != null ? department.getCompany().getId() : null)
                .createdAt(department.getCreatedAt())
                .build();
    }
}
