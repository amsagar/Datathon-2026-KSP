package com.ksp.agent.auth.local.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.auth.local.entity.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class AppUserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public AppUserRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public Optional<AppUser> findByUsername(String username) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("USER.FIND_BY_USERNAME"), rowMapper(), username)
                .stream().findFirst();
    }

    public List<AppUser> findAll() {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("USER.FIND_ALL"), rowMapper());
    }

    public Optional<AppUser> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("USER.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    /**
     * Legacy create used by {@code AuthController.register}: minimal columns, user is enabled and
     * not forced to change password.
     */
    public void create(String username, String passwordHash, String displayName, String email,
                       String roles, long now) {
        create(username, passwordHash, displayName, email, roles, null, null, null, null, true, false, now);
    }

    /**
     * Full create covering all managed columns. Returns the generated id.
     */
    public String create(String username, String passwordHash, String displayName, String email,
                         String roles, LocalDate dateOfBirth, String phone, String designation,
                         String department, boolean enabled, boolean mustChangePassword, long now) {
        String sql = sqlQueryLoader.getQuery("USER.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, displayName);
            ps.setString(4, email);
            ps.setString(5, roles);
            if (dateOfBirth != null) {
                ps.setDate(6, Date.valueOf(dateOfBirth));
            } else {
                ps.setNull(6, Types.DATE);
            }
            ps.setString(7, phone);
            ps.setString(8, designation);
            ps.setString(9, department);
            ps.setBoolean(10, enabled);
            ps.setBoolean(11, mustChangePassword);
            ps.setLong(12, now);
            ps.setLong(13, now);
            return ps;
        }, keyHolder);
        return String.valueOf(keyHolder.getKeys().get("id"));
    }

    public int updateProfileAndRoles(String id, String displayName, String email, String roles,
                                     LocalDate dateOfBirth, String phone, String designation,
                                     String department, boolean enabled, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("USER.UPDATE_PROFILE_AND_ROLES"),
                displayName, email, roles,
                dateOfBirth != null ? Date.valueOf(dateOfBirth) : null,
                phone, designation, department, enabled, now, id);
    }

    public int updatePassword(String id, String passwordHash, boolean mustChangePassword) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("USER.UPDATE_PASSWORD"),
                passwordHash, mustChangePassword, System.currentTimeMillis(), id);
    }

    public int setEnabled(String id, boolean enabled) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("USER.SET_ENABLED"),
                enabled, System.currentTimeMillis(), id);
    }

    public int updatePhoto(String id, byte[] bytes, String contentType) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("USER.UPDATE_PHOTO"),
                bytes, contentType, System.currentTimeMillis(), id);
    }

    public Optional<UserPhoto> findPhoto(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("USER.FIND_PHOTO"),
                (rs, rowNum) -> new UserPhoto(rs.getBytes("photo"), rs.getString("photo_content_type")), id)
                .stream().findFirst();
    }

    public int touchLogin(String id, long ts) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("USER.TOUCH_LOGIN"), ts, ts, id);
    }

    public int delete(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("USER.DELETE"), id);
    }

    public int countEnabledAdmins() {
        Integer count = jdbcTemplate.queryForObject(
                sqlQueryLoader.getQuery("USER.COUNT_ENABLED_ADMINS"), Integer.class);
        return count == null ? 0 : count;
    }

    /** Photo payload: bytes may be null when no photo is stored. */
    public record UserPhoto(byte[] bytes, String contentType) {
    }

    private RowMapper<AppUser> rowMapper() {
        return (rs, rowNum) -> {
            AppUser user = new AppUser();
            user.setId(rs.getString("id"));
            user.setUsername(rs.getString("username"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setDisplayName(rs.getString("display_name"));
            user.setEmail(rs.getString("email"));
            user.setRoles(rs.getString("roles"));
            Date dob = rs.getDate("date_of_birth");
            user.setDateOfBirth(dob != null ? dob.toLocalDate() : null);
            user.setPhone(rs.getString("phone"));
            user.setDesignation(rs.getString("designation"));
            user.setDepartment(rs.getString("department"));
            user.setPhotoContentType(rs.getString("photo_content_type"));
            user.setEnabled(rs.getBoolean("enabled"));
            user.setMustChangePassword(rs.getBoolean("must_change_password"));
            long lastLogin = rs.getLong("last_login_at");
            user.setLastLoginAt(rs.wasNull() ? null : lastLogin);
            user.setCreatedAt(rs.getLong("created_at"));
            long updated = rs.getLong("updated_at");
            user.setUpdatedAt(rs.wasNull() ? null : updated);
            return user;
        };
    }
}
