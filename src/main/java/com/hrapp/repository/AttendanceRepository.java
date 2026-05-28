package com.hrapp.repository;

import com.hrapp.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    /**
     * Joins on user so list endpoints can include full name + emp code without
     * triggering N+1 lazy loads per row.
     */
    @Query("SELECT a FROM Attendance a JOIN FETCH a.user " +
            "WHERE a.company.id = :companyId AND a.attendanceDate = :date " +
            "ORDER BY a.user.fullName ASC")
    List<Attendance> findByCompanyIdAndAttendanceDate(
            @Param("companyId") Long companyId,
            @Param("date") LocalDate date);

    List<Attendance> findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long userId, LocalDate from, LocalDate to);

    /**
     * Standard name kept for API parity with the spec — delegates to the
     * sorted variant above via Spring Data's derived query resolution by name.
     */
    List<Attendance> findByUserIdAndAttendanceDateBetween(
            Long userId, LocalDate from, LocalDate to);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.user " +
            "WHERE a.company.id = :companyId AND a.attendanceDate BETWEEN :from AND :to " +
            "ORDER BY a.attendanceDate ASC, a.user.fullName ASC")
    List<Attendance> findByCompanyIdAndAttendanceDateBetween(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    boolean existsByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);
}
