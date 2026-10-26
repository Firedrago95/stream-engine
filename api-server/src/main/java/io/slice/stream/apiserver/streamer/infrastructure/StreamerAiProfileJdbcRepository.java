package io.slice.stream.apiserver.streamer.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerAiProfileRepository;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerAiProfileDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class StreamerAiProfileJdbcRepository implements StreamerAiProfileRepository {

    private static final String FIND_BY_STREAM_ID_SQL = """
        SELECT stream_id, summary_line, broadcasting_style, core_games,
               fandom_traits, popular_memes, analyzed_session_count, updated_at
        FROM streamer_ai_profiles
        WHERE stream_id = :streamId
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<StreamerAiProfileDto> findByStreamId(String streamId) {
        MapSqlParameterSource params = new MapSqlParameterSource("streamId", streamId);
        try {
            StreamerAiProfileDto profile = jdbcTemplate.queryForObject(
                FIND_BY_STREAM_ID_SQL,
                params,
                this::mapRowToDto
            );
            return Optional.ofNullable(profile);
        } catch (EmptyResultDataAccessException e) {
            log.debug("스트리머 AI 프로필 미존재: streamId={}", streamId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("스트리머 AI 프로필 조회 중 오류 발생: streamId={}", streamId, e);
            return Optional.empty();
        }
    }

    private StreamerAiProfileDto mapRowToDto(ResultSet rs, int rowNum) throws SQLException {
        String summaryLine = rs.getString("summary_line");
        String broadcastingStyle = rs.getString("broadcasting_style");
        String fandomTraits = rs.getString("fandom_traits");
        int analyzedSessionCount = rs.getInt("analyzed_session_count");

        Timestamp updatedAtTimestamp = rs.getTimestamp("updated_at");
        Instant updatedAt = updatedAtTimestamp != null ? updatedAtTimestamp.toInstant() : Instant.now();

        List<String> coreGames = parseJsonStringList(rs.getString("core_games"));
        List<String> popularMemes = parseJsonStringList(rs.getString("popular_memes"));

        return new StreamerAiProfileDto(
            summaryLine,
            broadcastingStyle,
            coreGames,
            fandomTraits,
            popularMemes,
            analyzedSessionCount,
            updatedAt
        );
    }

    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON 파싱 실패, 빈 리스트 반환: json={}", json);
            return Collections.emptyList();
        }
    }
}
