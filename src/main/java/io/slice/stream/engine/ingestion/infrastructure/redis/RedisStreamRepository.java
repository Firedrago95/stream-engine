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
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisStreamRepository implements StreamRepository {

    private static final String ACTUAL_KEY = "streams.active.id";
    private static final String INFO_KEY = "streams.info";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<List> updateStreamScript;
    private final ObjectMapper objectMapper;

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
            List<List<String>> result = stringRedisTemplate.execute(
                updateStreamScript,
                List.of(ACTUAL_KEY, INFO_KEY),
                args.toArray()
            );

            return new StreamUpdateResults(
                new HashSet<>(result.get(0)),
                new HashSet<>(result.get(1))
            );
        } catch (Exception e) {
            throw new IngestionException(ErrorCode.INTERNAL_SERVER_ERROR, "Redis 스크립트 실행 중 오류가 발생했습니다.");
        }
    }

    private String serialize(StreamTarget target) {
        try {
            return objectMapper.writeValueAsString(target);
        } catch (Exception e) {
            throw new IngestionException(ErrorCode.INTERNAL_SERVER_ERROR, "StreamTarget 직렬화에 실패했습니다.");
        }
    }
}
