package com.hrapp.repository;

import com.hrapp.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * Returns mappings for one user with the {@code role} eagerly fetched so
     * callers can read role names without an N+1 lookup.
     */
    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role WHERE ur.user.id = :userId")
    List<UserRole> findByUserId(@Param("userId") Long userId);

    /**
     * Batch variant of {@link #findByUserId(Long)} — fetches all mappings for
     * a set of users in a single query.
     */
    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role WHERE ur.user.id IN :userIds")
    List<UserRole> findByUserIdIn(@Param("userIds") Collection<Long> userIds);
}
