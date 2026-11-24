package com.hrapp.repository;

import com.hrapp.entity.AttendancePunch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendancePunchRepository extends JpaRepository<AttendancePunch, Long> {

    List<AttendancePunch> findByAttendanceId(Long attendanceId);

    List<AttendancePunch> findByAttendanceIdOrderByPunchTimeAsc(Long attendanceId);

    @Query("SELECT p FROM AttendancePunch p "
            + "WHERE p.attendance.id IN :attendanceIds "
            + "ORDER BY p.attendance.id ASC, p.punchTime ASC")
    List<AttendancePunch> findByAttendanceIdInOrderByPunchTimeAsc(
            @Param("attendanceIds") Collection<Long> attendanceIds);

    List<AttendancePunch> findByUserIdAndPunchTimeBetween(
            Long userId, LocalDateTime from, LocalDateTime to);

    long countByAttendanceId(Long attendanceId);

    Optional<AttendancePunch> findFirstByAttendanceIdOrderByPunchTimeDesc(Long attendanceId);

    void deleteByAttendanceId(Long attendanceId);
}
