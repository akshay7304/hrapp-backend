package com.hrapp.repository;

import com.hrapp.entity.ShiftRotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftRotationRepository extends JpaRepository<ShiftRotation, Long> {

    List<ShiftRotation> findByCompanyIdAndIsActiveTrueOrderByNameAsc(Long companyId);

    List<ShiftRotation> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<ShiftRotation> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);
}
