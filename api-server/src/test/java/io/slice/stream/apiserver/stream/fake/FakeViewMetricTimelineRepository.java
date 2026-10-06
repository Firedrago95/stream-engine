package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.stream.infrastructure.JpaViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import java.util.ArrayList;
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

public class FakeViewMetricTimelineRepository implements JpaViewMetricTimelineRepository {

    private final Map<Long, ViewMetricTimelineEntity> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    private final Map<String, Double> averageViewerCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> peakViewerCounts = new ConcurrentHashMap<>();

    public void setAverageViewerCount(String sessionId, Double average) {
        averageViewerCounts.put(sessionId, average);
    }

    public void setPeakViewerCount(String sessionId, Integer peak) {
        peakViewerCounts.put(sessionId, peak);
    }

    @Override
    public List<ViewMetricTimelineEntity> findBySessionIdOrderByTimestampAsc(String sessionId) {
        return storage.values().stream()
            .filter(v -> Objects.equals(v.getSessionId(), sessionId))
            .sorted(Comparator.comparing(ViewMetricTimelineEntity::getTimestamp))
            .toList();
    }

    @Override
    public Double findAverageViewerCountBySessionId(String sessionId) {
        if (averageViewerCounts.containsKey(sessionId)) {
            return averageViewerCounts.get(sessionId);
        }
        return storage.values().stream()
            .filter(v -> Objects.equals(v.getSessionId(), sessionId))
            .mapToInt(ViewMetricTimelineEntity::getViewerCount)
            .average()
            .orElse(0.0);
    }

    @Override
    public Integer findPeakViewerCountBySessionId(String sessionId) {
        if (peakViewerCounts.containsKey(sessionId)) {
            return peakViewerCounts.get(sessionId);
        }
        return storage.values().stream()
            .filter(v -> Objects.equals(v.getSessionId(), sessionId))
            .mapToInt(ViewMetricTimelineEntity::getViewerCount)
            .max()
            .orElse(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends ViewMetricTimelineEntity> S save(S entity) {
        Long id = entity.getId();
        if (id == null) {
            id = idGenerator.getAndIncrement();
        }
        storage.put(id, entity);
        return entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends ViewMetricTimelineEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public Optional<ViewMetricTimelineEntity> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    @Override
    public List<ViewMetricTimelineEntity> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<ViewMetricTimelineEntity> findAllById(Iterable<Long> ids) {
        List<ViewMetricTimelineEntity> result = new ArrayList<>();
        for (Long id : ids) {
            ViewMetricTimelineEntity entity = storage.get(id);
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
    public void delete(ViewMetricTimelineEntity entity) {
        if (entity.getId() != null) {
            storage.remove(entity.getId());
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(storage::remove);
    }

    @Override
    public void deleteAll(Iterable<? extends ViewMetricTimelineEntity> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        storage.clear();
        averageViewerCounts.clear();
        peakViewerCounts.clear();
    }

    @Override
    public void flush() {}

    @Override
    public <S extends ViewMetricTimelineEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends ViewMetricTimelineEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<ViewMetricTimelineEntity> entities) {
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
    public ViewMetricTimelineEntity getOne(Long id) {
        return storage.get(id);
    }

    @Override
    public ViewMetricTimelineEntity getById(Long id) {
        return storage.get(id);
    }

    @Override
    public ViewMetricTimelineEntity getReferenceById(Long id) {
        return storage.get(id);
    }

    @Override
    public <S extends ViewMetricTimelineEntity> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends ViewMetricTimelineEntity> List<S> findAll(Example<S> example) {
        return List.of();
    }

    @Override
    public <S extends ViewMetricTimelineEntity> List<S> findAll(Example<S> example, Sort sort) {
        return List.of();
    }

    @Override
    public <S extends ViewMetricTimelineEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public <S extends ViewMetricTimelineEntity> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends ViewMetricTimelineEntity> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends ViewMetricTimelineEntity, R> R findBy(Example<S> example, Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<ViewMetricTimelineEntity> findAll(Sort sort) {
        return List.of();
    }

    @Override
    public Page<ViewMetricTimelineEntity> findAll(Pageable pageable) {
        return Page.empty();
    }
}
