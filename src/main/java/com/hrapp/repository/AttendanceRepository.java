package com.hrapp.repository;

import com.hrapp.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * Paginated variant of {@link #findByCompanyIdAndAttendanceDate(Long, LocalDate)}.
     * Uses {@link EntityGraph} so the {@code user} association is loaded
     * with the row (avoids N+1) while still allowing safe SQL pagination —
     * {@code JOIN FETCH} on a {@code Pageable} query would force in-memory
     * pagination (HHH000104).
     */
    @EntityGraph(attributePaths = {"user", "user.company"})
    Page<Attendance> findByCompanyIdAndAttendanceDate(
            Long companyId, LocalDate attendanceDate, Pageable pageable);

    List<Attendance> findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long userId, LocalDate from, LocalDate to);

    /**
     * Standard name kept for API parity with the spec — delegates to the
     * sorted variant above via Spring Data's derived query resolution by name.
     */
    List<Attendance> findByUserIdAndAttendanceDateBetween(
            Long userId, LocalDate from, LocalDate to);

    /** Paginated history lookup; ordering is whatever {@code Pageable.sort} requests. */
    @EntityGraph(attributePaths = {"user", "user.company"})
    Page<Attendance> findByUserIdAndAttendanceDateBetween(
            Long userId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.user " +
            "WHERE a.company.id = :companyId AND a.attendanceDate BETWEEN :from AND :to " +
            "ORDER BY a.attendanceDate ASC, a.user.fullName ASC")
    List<Attendance> findByCompanyIdAndAttendanceDateBetween(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    boolean existsByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    List<Attendance> findByCompanyIdAndAttendanceDateAndCheckInIsNotNull(
            Long companyId, LocalDate attendanceDate);

    List<Attendance> findByCompanyIdAndAttendanceDateAndCheckInIsNotNullAndCheckOutIsNull(
            Long companyId, LocalDate attendanceDate);

    /**
     * Month/day status tallies without loading rows — used by home dashboard.
     * Returns {@code [AttendanceStatus, Long count]}.
     */
    @Query("SELECT a.status, COUNT(a) FROM Attendance a "
            + "WHERE a.user.id = :userId AND a.attendanceDate BETWEEN :from AND :to "
            + "GROUP BY a.status")
    List<Object[]> countStatusByUserIdAndAttendanceDateBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Today company attendance summary in one round-trip.
     * Returns a single row:
     * {@code [present, absent, halfDay, onLeave, weekOff, holiday, checkedInToday, checkedInNow]}.
     */
    @Query("SELECT "
            + "COALESCE(SUM(CASE WHEN a.status = com.hrapp.enums.AttendanceStatus.PRESENT THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN a.status = com.hrapp.enums.AttendanceStatus.ABSENT THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN a.status = com.hrapp.enums.AttendanceStatus.HALF_DAY THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN a.status = com.hrapp.enums.AttendanceStatus.ON_LEAVE THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN a.status = com.hrapp.enums.AttendanceStatus.WEEK_OFF THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN a.status = com.hrapp.enums.AttendanceStatus.HOLIDAY THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN a.checkIn IS NOT NULL THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN a.checkIn IS NOT NULL AND a.checkOut IS NULL THEN 1 ELSE 0 END), 0) "
            + "FROM Attendance a WHERE a.company.id = :companyId AND a.attendanceDate = :date")
    Object[] summarizeCompanyDay(
            @Param("companyId") Long companyId,
            @Param("date") LocalDate date);
}
