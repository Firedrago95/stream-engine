package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeHighlightEventRepository implements JpaHighlightEventRepository {

    private final Map<Long, HighlightEventEntity> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public Optional<HighlightEventEntity> findFirstByStreamIdAndStatusOrderByStartTimeDesc(String streamId, String status) {
        return storage.values().stream()
            .filter(h -> Objects.equals(h.getStreamId(), streamId) && Objects.equals(h.getStatus(), status))
            .max(Comparator.comparing(HighlightEventEntity::getStartTime));
    }

    @Override
    public List<HighlightEventEntity> findAllByStreamIdAndSessionId(String streamId, String sessionId) {
        return storage.values().stream()
            .filter(h -> Objects.equals(h.getStreamId(), streamId) && Objects.equals(h.getSessionId(), sessionId))
            .toList();
    }

    @Override
    public List<HighlightEventEntity> findAllByStreamIdAndSessionIdIn(String streamId, Collection<String> sessionIds) {
        return storage.values().stream()
            .filter(h -> Objects.equals(h.getStreamId(), streamId) && sessionIds.contains(h.getSessionId()))
            .toList();
    }

    @Override
    public List<HighlightEventEntity> findZombieSessions(Instant threshold) {
        return storage.values().stream()
            .filter(h -> "ONGOING".equals(h.getStatus()) && h.getLastPeakTime().isBefore(threshold))
            .toList();
    }

    @Override
    public int deleteExceptTop(String sessionId, int retentionLimit) {
        return 0;
    }

    @Override
    public int compressOldHighlightsExceptTop(Instant threshold, int retentionLimit) {
        return 0;
    }

    @Override
    public int deleteExpiredHighlights(Instant expiredThreshold) {
        return 0;
    }

    @Override
    public int deleteAllBySessionIds(List<String> sessionIds) {
        List<Long> toRemove = storage.values().stream()
            .filter(h -> sessionIds.contains(h.getSessionId()))
            .map(HighlightEventEntity::getId)
            .filter(Objects::nonNull)
            .toList();
        toRemove.forEach(storage::remove);
        return toRemove.size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends HighlightEventEntity> S save(S entity) {
        Long id = entity.getId();
        if (id == null) {
            id = idGenerator.getAndIncrement();
            ReflectionTestUtils.setField(entity, "id", id);
        }
        storage.put(id, entity);
        return entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends HighlightEventEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public Optional<HighlightEventEntity> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    @Override
    public List<HighlightEventEntity> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<HighlightEventEntity> findAllById(Iterable<Long> ids) {
        List<HighlightEventEntity> result = new ArrayList<>();
        for (Long id : ids) {
            HighlightEventEntity entity = storage.get(id);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void deleteById(Long id) {
        storage.remove(id);
    }

    @Override
    public void delete(HighlightEventEntity entity) {
        if (entity.getId() != null) {
            storage.remove(entity.getId());
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(storage::remove);
    }

    @Override
    public void deleteAll(Iterable<? extends HighlightEventEntity> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void flush() {}

    @Override
    public <S extends HighlightEventEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends HighlightEventEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<HighlightEventEntity> entities) {
        deleteAll(entities);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<Long> ids) {
        deleteAllById(ids);
    }

    @Override
    public void deleteAllInBatch() {
        deleteAll();
    }

    @Override
    public HighlightEventEntity getOne(Long id) {
        return storage.get(id);
    }

    @Override
    public HighlightEventEntity getById(Long id) {
        return storage.get(id);
    }

    @Override
    public HighlightEventEntity getReferenceById(Long id) {
        return storage.get(id);
    }

    @Override
    public <S extends HighlightEventEntity> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends HighlightEventEntity> List<S> findAll(Example<S> example) {
        return List.of();
    }

    @Override
    public <S extends HighlightEventEntity> List<S> findAll(Example<S> example, Sort sort) {
        return List.of();
    }

    @Override
    public <S extends HighlightEventEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public <S extends HighlightEventEntity> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends HighlightEventEntity> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends HighlightEventEntity, R> R findBy(Example<S> example, Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<HighlightEventEntity> findAll(Sort sort) {
        return List.of();
    }

    @Override
    public Page<HighlightEventEntity> findAll(Pageable pageable) {
        return Page.empty();
    }
}
