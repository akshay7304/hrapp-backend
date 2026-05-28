package com.hrapp.repository;

import com.hrapp.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findByCompanyId(Long companyId);

    Optional<LeaveType> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndName(Long companyId, String name);
}
