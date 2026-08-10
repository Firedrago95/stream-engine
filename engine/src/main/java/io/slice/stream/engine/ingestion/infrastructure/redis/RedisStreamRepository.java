package io.slice.stream.engine.ingestion.infrastructure.redis;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.core.redis.Rediskeys;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisStreamRepository implements StreamRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> updateStreamScript;
    private final JsonMapper jsonMapper;

    @Override
    public Set<String> getActiveChannelIds() {
        Set<String> members = redisTemplate.opsForSet().members(Rediskeys.STREAM_TARGETS);
        return members != null ? members : Set.of();
    }

    @Override
    public List<StreamTarget> getStreamTargets(List<String> channelIds) {
        List<Object> hashkeys = channelIds.stream()
            .map(id -> (Object) id)
            .toList();
        List<Object> values = redisTemplate.opsForHash().multiGet(Rediskeys.STREAM_LIVE_HASH, hashkeys);
        return values.stream()
            .filter(Objects::nonNull)
            .map(v -> deserialize((String) v))
            .toList();
    }

    @Override
    public void sync(Set<StreamTarget> closedStreams, List<StreamTarget> activeTargets) {
        if (activeTargets.isEmpty()) {
            if (closedStreams != null && !closedStreams.isEmpty()) {
                redisTemplate.delete(List.of(
                    Rediskeys.STREAM_TARGETS,
                    Rediskeys.STREAM_LIVE_HASH,
                    Rediskeys.ANALYSIS_INDEX
                ));
            }
            return;
        }

        List<String> args = makeArguments(closedStreams, activeTargets);
        redisTemplate.execute(
            updateStreamScript,
            List.of(Rediskeys.STREAM_TARGETS, Rediskeys.STREAM_LIVE_HASH, Rediskeys.ANALYSIS_INDEX),
            (Object[]) args.toArray(new String[0])
        );
    }

    private List<String> makeArguments(Set<StreamTarget> closedStreams, List<StreamTarget> streamTargets) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(closedStreams.size()));
        for (StreamTarget target : closedStreams) {
            args.add(target.channelId());
        }

        for (StreamTarget target : streamTargets) {
            args.add(target.channelId());
            args.add(serialize(target));
        }
        return args;
    }

    private StreamTarget deserialize(String json) {
        try {
            return jsonMapper.readValue(json, StreamTarget.class);
        } catch (Exception e) {
            throw new IngestionException(ErrorCode.INTERNAL_SERVER_ERROR, "StreamTarget 역직렬화에 실패했습니다.");
        }
    }

    private String serialize(StreamTarget target) {
        try {
            return jsonMapper.writeValueAsString(target);
        } catch (Exception e) {
            throw new IngestionException(ErrorCode.INTERNAL_SERVER_ERROR, "StreamTarget 직렬화에 실패했습니다.");
        }
    }
}
