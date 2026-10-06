package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
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

public class FakeStreamSessionSegmentRepository implements JpaStreamSessionSegmentRepository {

    private final Map<Long, StreamSessionSegmentEntity> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    public void addSegment(StreamSessionSegmentEntity segment) {
        save(segment);
    }

    public void addSegments(Collection<StreamSessionSegmentEntity> segments) {
        segments.forEach(this::addSegment);
    }

    public List<StreamSessionSegmentEntity> getAllSegments() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Optional<StreamSessionSegmentEntity> findActiveSegment(String sessionId) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getSessionId(), sessionId) && s.getEndedAt() == null)
            .findFirst();
    }

    @Override
    public List<StreamSessionSegmentEntity> findAllActiveSegments(List<String> sessionIds) {
        return storage.values().stream()
            .filter(s -> sessionIds.contains(s.getSessionId()) && s.getEndedAt() == null)
            .toList();
    }

    @Override
    public List<StreamSessionSegmentEntity> findBySessionIdOrderByStartedAtAsc(String sessionId) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getSessionId(), sessionId))
            .sorted(Comparator.comparing(StreamSessionSegmentEntity::getStartedAt))
            .toList();
    }

    @Override
    public Optional<StreamSessionSegmentEntity> findFirstBySessionIdOrderByStartedAtDesc(String sessionId) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getSessionId(), sessionId))
            .max(Comparator.comparing(StreamSessionSegmentEntity::getStartedAt));
    }

    @Override
    public int deleteAllBySessionIds(List<String> sessionIds) {
        List<Long> keysToRemove = storage.values().stream()
            .filter(s -> sessionIds.contains(s.getSessionId()))
            .map(StreamSessionSegmentEntity::getId)
            .filter(Objects::nonNull)
            .toList();
        keysToRemove.forEach(storage::remove);
        return keysToRemove.size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends StreamSessionSegmentEntity> S save(S entity) {
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
    public <S extends StreamSessionSegmentEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public Optional<StreamSessionSegmentEntity> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    @Override
    public List<StreamSessionSegmentEntity> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<StreamSessionSegmentEntity> findAllById(Iterable<Long> ids) {
        List<StreamSessionSegmentEntity> result = new ArrayList<>();
        for (Long id : ids) {
            StreamSessionSegmentEntity entity = storage.get(id);
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
    public void delete(StreamSessionSegmentEntity entity) {
        if (entity.getId() != null) {
            storage.remove(entity.getId());
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(storage::remove);
    }

    @Override
    public void deleteAll(Iterable<? extends StreamSessionSegmentEntity> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void flush() {}

    @Override
    public <S extends StreamSessionSegmentEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends StreamSessionSegmentEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<StreamSessionSegmentEntity> entities) {
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
    public StreamSessionSegmentEntity getOne(Long id) {
        return storage.get(id);
    }

    @Override
    public StreamSessionSegmentEntity getById(Long id) {
        return storage.get(id);
    }

    @Override
    public StreamSessionSegmentEntity getReferenceById(Long id) {
        return storage.get(id);
    }

    @Override
    public <S extends StreamSessionSegmentEntity> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends StreamSessionSegmentEntity> List<S> findAll(Example<S> example) {
        return List.of();
    }

    @Override
    public <S extends StreamSessionSegmentEntity> List<S> findAll(Example<S> example, Sort sort) {
        return List.of();
    }

    @Override
    public <S extends StreamSessionSegmentEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public <S extends StreamSessionSegmentEntity> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends StreamSessionSegmentEntity> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends StreamSessionSegmentEntity, R> R findBy(Example<S> example, Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<StreamSessionSegmentEntity> findAll(Sort sort) {
        return List.of();
    }

    @Override
    public Page<StreamSessionSegmentEntity> findAll(Pageable pageable) {
        return Page.empty();
    }
}
