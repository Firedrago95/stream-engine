package io.slice.stream.engine.ingestion.infrastructure.redis;

import static java.util.stream.Collectors.toSet;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.core.redis.Rediskeys;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
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
    public StreamUpdateResults update(List<StreamTarget> newStreamTargets) {
        if (newStreamTargets.isEmpty()) {
            return handleAllStreamsClosed();
        }

        Set<String> activeChannelIds = getActiveChannelIds();
        Set<StreamTarget> newStreams = filterNewStreams(newStreamTargets, activeChannelIds);
        Set<String> closedStreamIds = filterClosedStreams(newStreamTargets, activeChannelIds);
        Set<ChangedStream> changedStreams = detectChangedStreams(newStreamTargets);
        syncToRedis(closedStreamIds, newStreamTargets);
        return new StreamUpdateResults(newStreams, closedStreamIds, changedStreams);
    }

    private StreamUpdateResults handleAllStreamsClosed() {
        Set<String> activeChannelIds = getActiveChannelIds();
        if (!activeChannelIds.isEmpty()) {
            redisTemplate.delete(List.of(
                Rediskeys.STREAM_TARGETS,
                Rediskeys.STREAM_LIVE_HASH,
                Rediskeys.ANALYSIS_INDEX
            ));
        }
        return new StreamUpdateResults(Set.of(), activeChannelIds, Set.of());
    }

    private Set<String> getActiveChannelIds() {
        Set<String> activeChannelIds = redisTemplate.opsForSet().members(Rediskeys.STREAM_TARGETS);
        return activeChannelIds != null ? activeChannelIds : Set.of();
    }

    private Set<StreamTarget> filterNewStreams(List<StreamTarget> newStreamTargets, Set<String> activeChannelIds) {
        return newStreamTargets.stream()
            .filter(target -> !activeChannelIds.contains(target.channelId()))
            .collect(toSet());
    }

    private Set<String> filterClosedStreams(List<StreamTarget> newStreamTargets, Set<String> activeChannelIds) {
        Set<String> newStreamTargetChannelIds = newStreamTargets.stream()
            .map(StreamTarget::channelId)
            .collect(toSet());
        return activeChannelIds.stream()
            .filter(id -> !newStreamTargetChannelIds.contains(id))
            .collect(toSet());
    }

    private Set<ChangedStream> detectChangedStreams(List<StreamTarget> newStreamTargets) {
        List<Object> hashkeys = newStreamTargets.stream()
            .map(StreamTarget::channelId)
            .map(id -> (Object) id)
            .toList();
        List<Object> values = redisTemplate.opsForHash().multiGet(Rediskeys.STREAM_LIVE_HASH, hashkeys);
        Set<ChangedStream> changedStreams = new HashSet<>();
        for (int i = 0; i < newStreamTargets.size(); i++) {
            StreamTarget newTarget = newStreamTargets.get(i);
            String oldJson = (String) values.get(i);
            if (oldJson != null) {
                StreamTarget oldTarget = deserialize(oldJson);
                if (isMetadataChanged(oldTarget, newTarget)) {
                    changedStreams.add(createChangedStream(oldTarget, newTarget));
                }
            }
        }
        return changedStreams;
    }

    private boolean isMetadataChanged(StreamTarget oldTarget, StreamTarget newTarget) {
        return !oldTarget.liveTitle().equals(newTarget.liveTitle()) ||
            !oldTarget.categoryName().equals(newTarget.categoryName());
    }

    private ChangedStream createChangedStream(StreamTarget oldTarget, StreamTarget newTarget) {
        return new ChangedStream(
            newTarget.channelId(),
            oldTarget.liveTitle(),
            newTarget.liveTitle(),
            oldTarget.categoryName(),
            newTarget.categoryName()
        );
    }

    private void syncToRedis(Set<String> closedStreamIds, List<StreamTarget> newStreamTargets) {
        List<String> args = makeArguments(closedStreamIds, newStreamTargets);
        redisTemplate.execute(
            updateStreamScript,
            List.of(Rediskeys.STREAM_TARGETS, Rediskeys.STREAM_LIVE_HASH, Rediskeys.ANALYSIS_INDEX),
            args.toArray(new String[0])
        );
    }

    private List<String> makeArguments(Set<String> closedStreamIds, List<StreamTarget> streamTargets) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(closedStreamIds.size()));
        args.add(String.valueOf(streamTargets.size()));
        args.addAll(closedStreamIds);
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
