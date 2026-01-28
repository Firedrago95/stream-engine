package io.slice.stream.engine.ingestion.infrastructure.redis;

import io.slice.stream.engine.core.model.StreamTarget;
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

    private static final String STREAM_TARGET_KEY = "stream:targets";
    private static final String STREAM_LIVE_KEY_PREFIX = "stream:live:";

    @Override
    public StreamUpdateResults update(List<StreamTarget> streamTargets) {
        if (streamTargets.isEmpty()) {
            log.warn("수집된 방송이 없습니다. 외부 api 요청을 점검하세요");
            return new StreamUpdateResults(Set.of(), Set.of());
        }

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
            // execute의 반환 타입은 RedisScript<T>의 T에 따라 결정됩니다. (여기서는 List)
            List<?> rawResult = redisTemplate.execute(
                updateStreamScript,
                List.of(STREAM_TARGET_KEY, STREAM_LIVE_KEY_PREFIX),
                args.toArray(new String[0])
            );

            if (rawResult == null || rawResult.size() < 2 || !(rawResult.get(0) instanceof List) || !(rawResult.get(1) instanceof List)) {
                log.warn("Redis 스크립트 실행 결과가 비정상적입니다. rawResult: {}", rawResult);
                return new StreamUpdateResults(new HashSet<>(), new HashSet<>());
            }

            List<String> newStreamIds = (List<String>) rawResult.get(0);
            List<String> closedStreamIds = (List<String>) rawResult.get(1);

            return new StreamUpdateResults(
                new HashSet<>(newStreamIds),
                new HashSet<>(closedStreamIds)
            );
        } catch (Exception e) {
            log.error("Redis 방송 정보 업데이트 실패 오류", e);
            throw new IngestionException(ErrorCode.INTERNAL_SERVER_ERROR, "Redis 스크립트 실행 중 오류가 발생했습니다.");
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
