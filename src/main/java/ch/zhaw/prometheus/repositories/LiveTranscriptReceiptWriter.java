package ch.zhaw.prometheus.repositories;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment;
import ch.zhaw.prometheus.model.event.LiveTranscriptReceipt;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;

/** One durable statement per receipt; caller fences local ownership and serializes reset/admission. */
@Repository
public class LiveTranscriptReceiptWriter {
    public enum Result { INSERTED, DUPLICATE, OBSOLETE }
    private final JdbcTemplate jdbc;
    private static final String CURRENT_IDENTITY = """
            from agent a where a.id = ? and a.execution_epoch = ?
              and (? is null or exists (select 1 from access_code_agent link
                  join access_code code on code.id = link.access_code_id
                  where link.agent_id = a.id and code.id = ? and code.enabled = true))
            """;

    public LiveTranscriptReceiptWriter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Result insert(UUID agentId, ExternalSpeech owner, UUID scope, Fragment fragment) {
        return jdbc.execute((ConnectionCallback<Result>) connection -> {
            // Ingress suspends ambient transactions. Do not report durability if a
            // custom pool disables autocommit, or this writer is used in a transaction.
            if (!connection.getAutoCommit())
                throw new IllegalStateException("Durable receipt insertion requires autocommit");
            // INSERT ... SELECT is atomic on MySQL/InnoDB. An absent/stale agent,
            // disabled/unlinked scope or existing receipt inserts nothing. Every
            // successful insert rechecks authorization; other errors still fail.
            // No INSERT IGNORE, upsert, buffering or automatic retry hides data loss.
            try (var statement = connection.prepareStatement("""
                    insert into live_transcript_receipt
                        (id, agent_id, session_id, epoch, provider_event_id, speaker,
                         start_ms, end_ms, received_at, arrival_sequence, transcript)
                    select ?, a.id, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    """ + CURRENT_IDENTITY + """
                      and not exists (select 1 from live_transcript_receipt r
                                      where r.session_id = ? and r.provider_event_id = ?)
                    """)) {
                statement.setBytes(1, binary(LiveTranscriptReceipt.identity(owner.sessionId(), fragment.eventId())));
                statement.setBytes(2, binary(owner.sessionId()));
                statement.setBytes(3, binary(owner.epoch()));
                statement.setString(4, fragment.eventId());
                statement.setString(5, fragment.speaker().name());
                statement.setObject(6, fragment.startMs());
                statement.setObject(7, fragment.endMs());
                statement.setTimestamp(8, Timestamp.from(Instant.ofEpochMilli(fragment.receivedMs())),
                        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
                statement.setLong(9, fragment.sequence());
                statement.setString(10, fragment.text());
                identity(statement, 11, agentId, owner, scope);
                statement.setBytes(15, binary(owner.sessionId()));
                statement.setString(16, fragment.eventId());
                if (statement.executeUpdate() == 1) return Result.INSERTED;
            }
            // Only duplicate/obsolete traffic needs a second query. Distinguish it
            // so an obsolete volatile owner is removed without altering receipts.
            try (var statement = connection.prepareStatement("select 1 " + CURRENT_IDENTITY)) {
                identity(statement, 1, agentId, owner, scope);
                try (var result = statement.executeQuery()) {
                    return result.next() ? Result.DUPLICATE : Result.OBSOLETE;
                }
            }
        });
    }

    private static void identity(java.sql.PreparedStatement statement, int offset, UUID id, ExternalSpeech owner,
            UUID scope) throws java.sql.SQLException {
        statement.setBytes(offset, binary(id));
        statement.setBytes(offset + 1, binary(owner.epoch()));
        statement.setBytes(offset + 2, scope == null ? null : binary(scope));
        statement.setBytes(offset + 3, scope == null ? null : binary(scope));
    }

    private static byte[] binary(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
