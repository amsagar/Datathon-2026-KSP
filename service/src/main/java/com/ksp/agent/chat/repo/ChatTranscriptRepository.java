package com.ksp.agent.chat.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import org.springframework.ai.chat.messages.Message;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Direct JDBC access to Spring AI's {@code SPRING_AI_CHAT_MEMORY} transcript table for operations
 * the framework repository does not offer:
 * <ul>
 *   <li><b>append</b> — Spring AI's {@code JdbcChatMemoryRepository.saveAll} is a full
 *       DELETE-then-reINSERT of the whole conversation (O(n²) writes over a session's life).
 *       {@link #append} inserts only the new rows, using the exact same wire format Spring AI
 *       writes ({@code content} = message text, {@code type} = {@code MessageType} name,
 *       {@code "timestamp"} = an epoch-second-seeded sequence, one increment per message, stored
 *       as milliseconds) so reads through the framework repository are unchanged.</li>
 *   <li><b>counts / last row</b> — cheap SQL aggregates so callers do not have to load and
 *       deserialize the full transcript just to count messages or peek at the tail.</li>
 * </ul>
 */
@Repository
public class ChatTranscriptRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public ChatTranscriptRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    /** Content + type of the transcript's last row (by timestamp), if any. */
    public record LastMessage(String content, String type) {}

    /**
     * Append messages to a conversation's transcript without touching existing rows. Ordering
     * matches Spring AI's own {@code saveAll}: timestamps start at the current epoch second and
     * increment by one second per message, so appended rows always sort after earlier ones.
     */
    public void append(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String sql = sqlQueryLoader.getQuery("CHAT.MEMORY.APPEND");
        long sequenceStart = Instant.now().getEpochSecond();
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Message message = messages.get(i);
                ps.setString(1, conversationId);
                ps.setString(2, message.getText() == null ? "" : message.getText());
                ps.setString(3, message.getMessageType().name());
                ps.setTimestamp(4, new Timestamp((sequenceStart + i) * 1000L));
            }

            @Override
            public int getBatchSize() {
                return messages.size();
            }
        });
    }

    /** Total number of transcript rows for a conversation. */
    public long countMessages(String conversationId) {
        String sql = sqlQueryLoader.getQuery("CHAT.MEMORY.COUNT");
        Long count = jdbcTemplate.queryForObject(sql, Long.class, conversationId);
        return count == null ? 0L : count;
    }

    /** Number of transcript rows of one {@code MessageType} (e.g. {@code ASSISTANT}). */
    public long countByType(String conversationId, String type) {
        String sql = sqlQueryLoader.getQuery("CHAT.MEMORY.COUNT_BY_TYPE");
        Long count = jdbcTemplate.queryForObject(sql, Long.class, conversationId, type);
        return count == null ? 0L : count;
    }

    /** The most recent transcript row, used for consecutive-duplicate detection on append. */
    public Optional<LastMessage> findLast(String conversationId) {
        String sql = sqlQueryLoader.getQuery("CHAT.MEMORY.FIND_LAST");
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new LastMessage(rs.getString("content"), rs.getString("type")),
                conversationId).stream().findFirst();
    }
}
