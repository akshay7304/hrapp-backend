package com.hrapp.service;

import com.hrapp.dto.request.DesignationRequest;
import com.hrapp.dto.response.DesignationResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Designation;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.DesignationRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignationService {

    private final DesignationRepository designationRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public DesignationResponse createDesignation(DesignationRequest request) {
        Long companyId = requireCallerCompanyId();

        if (designationRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new ConflictException("Designation with this name already exists");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        Designation designation = Designation.builder()
                .company(company)
                .name(request.getName())
                .build();
        designation = designationRepository.save(designation);

        log.info("Created designation id={} name='{}' in company={}", designation.getId(), designation.getName(), companyId);
        return toResponse(designation);
    }

    @Transactional(readOnly = true)
    public List<DesignationResponse> getAllDesignations() {
        Long companyId = requireCallerCompanyId();
        return designationRepository.findByCompanyId(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DesignationResponse updateDesignation(Long id, DesignationRequest request) {
        Long companyId = requireCallerCompanyId();

        Designation designation = designationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found"));

        if (!designation.getName().equalsIgnoreCase(request.getName())
                && designationRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new ConflictException("Designation with this name already exists");
        }

        designation.setName(request.getName());
        designation = designationRepository.save(designation);

        log.info("Updated designation id={} name='{}'", designation.getId(), designation.getName());
        return toResponse(designation);
    }

    @Transactional
    public void deleteDesignation(Long id) {
        Long companyId = requireCallerCompanyId();

        Designation designation = designationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found"));

        designationRepository.delete(designation);
        log.info("Deleted designation id={}", id);
    }

    private Long requireCallerCompanyId() {
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new BadRequestException("Caller is not bound to a company");
        }
        return companyId;
    }

    private DesignationResponse toResponse(Designation designation) {
        return DesignationResponse.builder()
                .id(designation.getId())
                .name(designation.getName())
                .companyId(designation.getCompany() != null ? designation.getCompany().getId() : null)
                .createdAt(designation.getCreatedAt())
                .build();
    }
}
