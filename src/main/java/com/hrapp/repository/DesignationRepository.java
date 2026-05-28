package com.hrapp.repository;

import com.hrapp.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DesignationRepository extends JpaRepository<Designation, Long> {

    List<Designation> findByCompanyId(Long companyId);

    Optional<Designation> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndName(Long companyId, String name);
}
