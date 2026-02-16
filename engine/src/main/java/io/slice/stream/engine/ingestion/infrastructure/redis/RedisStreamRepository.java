package io.slice.stream.engine.ingestion.infrastructure.redis;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.core.redis.Rediskeys;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    public StreamUpdateResults update(List<StreamTarget> streamTargets) {
        List<String> args = makeArguments(streamTargets);
        return executeStreamUpdate(args);
    }

    private List<String> makeArguments(List<StreamTarget> streamTargets) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(streamTargets.size()));

        for (StreamTarget target : streamTargets) {
            args.add(target.channelId());
        }

        for (StreamTarget target : streamTargets) {
            args.add(target.channelId());
            args.add(serialize(target));
        }
        return args;
    }

    private StreamUpdateResults executeStreamUpdate(List<String> args) {
        try {
            List<?> rawResult = redisTemplate.execute(
                updateStreamScript,
                List.of(
                    Rediskeys.STREAM_TARGETS,     // KEYS[1]
                    Rediskeys.STREAM_LIVE_PREFIX, // KEYS[2]
                    Rediskeys.ANALYSIS_INDEX      // KEYS[3]
                ),
                args.toArray(new String[0])
            );

            if (rawResult == null || rawResult.size() < 2 || !(rawResult.get(0) instanceof List) || !(rawResult.get(1) instanceof List)) {
                log.warn("Redis 스크립트 실행 결과가 비정상적입니다. rawResult: {}", rawResult);
                return new StreamUpdateResults(new HashSet<>(), new HashSet<>());
            }

            List<String> newStreamTargetsJson = (List<String>) rawResult.get(0);
            List<String> closedStreamIds = (List<String>) rawResult.get(1);

            Set<StreamTarget> newStreamTargets = newStreamTargetsJson.stream()
                .map(this::deserialize)
                .collect(java.util.stream.Collectors.toSet());

            return new StreamUpdateResults(
                newStreamTargets,
                new HashSet<>(closedStreamIds)
            );
        } catch (Exception e) {
            log.error("Redis 방송 정보 업데이트 실패 오류", e);
            throw new IngestionException(ErrorCode.INTERNAL_SERVER_ERROR, "Redis 스크립트 실행 중 오류가 발생했습니다.");
        }
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
