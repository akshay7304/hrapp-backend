package com.hrapp.jdbc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StopWatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Shared JDBC helpers (same idea as hh-api {@code JdbcAccess}).
 * Use for paginated / multi-join list queries with SQL LIMIT/OFFSET + COUNT.
 * Prefer {@link #findUsingNamedParameters} for filters and {@code IN (:ids)}.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class JdbcAccess {

    private static final long SLOW_MS = 1000L;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public <T> List<T> findUsingNamedParameters(
            String sql, MapSqlParameterSource params, RowMapper<T> rowMapper) {
        StopWatch watch = startWatch();
        int returnedRows = 0;
        try {
            List<T> results = namedParameterJdbcTemplate.query(sql, params, rowMapper);
            returnedRows = results.size();
            return results;
        } finally {
            logIfSlow("findUsingNamedParameters", sql, params.getValues(), returnedRows, watch);
        }
    }

    public <T> List<T> find(String sql, RowMapper<T> rowMapper, Object... params) {
        StopWatch watch = startWatch();
        int returnedRows = 0;
        try {
            List<T> results = jdbcTemplate.query(sql, rowMapper, params);
            returnedRows = results.size();
            return results;
        } finally {
            logIfSlow("find", sql, params, returnedRows, watch);
        }
    }

    public <T> T findOne(String sql, RowMapper<T> rowMapper, Object... params) {
        StopWatch watch = startWatch();
        try {
            return jdbcTemplate.queryForObject(sql, rowMapper, params);
        } catch (EmptyResultDataAccessException e) {
            log.debug("findOne found no row");
            return null;
        } finally {
            logIfSlow("findOne", sql, params, -1, watch);
        }
    }

    public Long findLong(String sql, Object... params) {
        StopWatch watch = startWatch();
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, params);
            return value != null ? value : 0L;
        } catch (EmptyResultDataAccessException e) {
            log.debug("findLong found no row");
            return 0L;
        } finally {
            logIfSlow("findLong", sql, params, -1, watch);
        }
    }

    public Long findLongNamed(String sql, MapSqlParameterSource params) {
        StopWatch watch = startWatch();
        try {
            Long value = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
            return value != null ? value : 0L;
        } catch (EmptyResultDataAccessException e) {
            log.debug("findLongNamed found no row");
            return 0L;
        } finally {
            logIfSlow("findLongNamed", sql, params.getValues(), -1, watch);
        }
    }

    public Integer findInteger(String sql, Object... params) {
        StopWatch watch = startWatch();
        try {
            Integer value = jdbcTemplate.queryForObject(sql, Integer.class, params);
            return value != null ? value : 0;
        } catch (EmptyResultDataAccessException e) {
            log.debug("findInteger found no row");
            return 0;
        } finally {
            logIfSlow("findInteger", sql, params, -1, watch);
        }
    }

    public String findString(String sql, Object... params) {
        StopWatch watch = startWatch();
        try {
            return jdbcTemplate.queryForObject(sql, String.class, params);
        } catch (EmptyResultDataAccessException e) {
            log.debug("findString found no row");
            return null;
        } finally {
            logIfSlow("findString", sql, params, -1, watch);
        }
    }

    public List<String> findStringList(String sql, Object... params) {
        StopWatch watch = startWatch();
        try {
            return jdbcTemplate.queryForList(sql, String.class, params);
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } finally {
            logIfSlow("findStringList", sql, params, -1, watch);
        }
    }

    public int execute(String sql, Object... params) {
        StopWatch watch = startWatch();
        int updatedRows = 0;
        try {
            updatedRows = jdbcTemplate.update(sql, params);
            return updatedRows;
        } finally {
            logIfSlow("execute", sql, params, updatedRows, watch);
        }
    }

    public int executeNamed(String sql, MapSqlParameterSource params) {
        StopWatch watch = startWatch();
        int updatedRows = 0;
        try {
            updatedRows = namedParameterJdbcTemplate.update(sql, params);
            return updatedRows;
        } finally {
            logIfSlow("executeNamed", sql, params.getValues(), updatedRows, watch);
        }
    }

    public int[] batchExecute(String sql, List<Object[]> batchArgs) {
        StopWatch watch = startWatch();
        int totalUpdatedRows = 0;
        try {
            int[] results = jdbcTemplate.batchUpdate(sql, batchArgs);
            totalUpdatedRows = Arrays.stream(results).sum();
            return results;
        } finally {
            logIfSlow("batchExecute", sql, batchArgs, totalUpdatedRows, watch);
        }
    }

    private static StopWatch startWatch() {
        StopWatch watch = new StopWatch();
        watch.start();
        return watch;
    }

    private void logIfSlow(String op, String sql, Object params, int rows, StopWatch watch) {
        if (watch.isRunning()) {
            watch.stop();
        }
        long ms = watch.getTotalTimeMillis();
        if (ms >= SLOW_MS) {
            log.info("{}|elapsedMs={}|rows={}|params={}|sql={}", op, ms, rows, params, sql);
        }
    }
}
