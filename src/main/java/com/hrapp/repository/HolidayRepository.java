package com.hrapp.repository;

import com.hrapp.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByCompanyIdOrderByHolidayDateAsc(Long companyId);

    List<Holiday> findByCompanyId(Long companyId);

    Optional<Holiday> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndHolidayDate(Long companyId, LocalDate holidayDate);

    List<Holiday> findByCompanyIdAndHolidayDateBetween(Long companyId, LocalDate from, LocalDate to);
}
