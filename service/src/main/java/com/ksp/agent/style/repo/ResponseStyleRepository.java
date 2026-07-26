package com.ksp.agent.style.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.style.entity.ResponseStyle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class ResponseStyleRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public ResponseStyleRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(ResponseStyle s, long now) {
        String sql = sqlQueryLoader.getQuery("STYLE.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, s.getAssistantId());
            ps.setString(2, s.getName());
            ps.setString(3, s.getDescription());
            ps.setString(4, s.getInstructions());
            ps.setBoolean(5, s.isDefaultStyle());
            ps.setLong(6, now);
            ps.setLong(7, now);
            return ps;
        }, keyHolder);
        return String.valueOf(keyHolder.getKeys().get("id"));
    }

    public List<ResponseStyle> findByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("STYLE.FIND_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    public Optional<ResponseStyle> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("STYLE.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    public int update(ResponseStyle s, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("STYLE.UPDATE"),
                s.getName(), s.getDescription(), s.getInstructions(), now, s.getId());
    }

    public int delete(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("STYLE.DELETE"), id);
    }

    public Optional<ResponseStyle> findDefaultByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("STYLE.FIND_DEFAULT_BY_ASSISTANT"),
                        rowMapper(), assistantId)
                .stream()
                .findFirst();
    }

    public void clearDefaultForAssistant(String assistantId) {
        jdbcTemplate.update(sqlQueryLoader.getQuery("STYLE.CLEAR_DEFAULT_FOR_ASSISTANT"), assistantId);
    }

    public void setDefault(String id, long now) {
        jdbcTemplate.update(sqlQueryLoader.getQuery("STYLE.SET_DEFAULT"), now, id);
    }

    private RowMapper<ResponseStyle> rowMapper() {
        return (rs, rowNum) -> new ResponseStyle(
                rs.getString("id"),
                rs.getString("assistant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("instructions"),
                rs.getBoolean("is_default"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
