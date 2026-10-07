package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.LocalDate;
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

public class FakeStreamerFollowerSnapshotRepository implements StreamerFollowerSnapshotRepository {

    private final Map<Long, StreamerFollowerSnapshotEntity> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    public void addSnapshot(StreamerFollowerSnapshotEntity snapshot) {
        save(snapshot);
    }

    public void addSnapshots(Collection<StreamerFollowerSnapshotEntity> snapshots) {
        snapshots.forEach(this::addSnapshot);
    }

    @Override
    public Optional<StreamerFollowerSnapshotEntity> findByStreamIdAndSnapshotDate(String streamId, LocalDate snapshotDate) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getStreamId(), streamId) && Objects.equals(s.getSnapshotDate(), snapshotDate))
            .findFirst();
    }

    @Override
    public List<StreamerFollowerSnapshotEntity> findAllByStreamIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
        String streamId,
        LocalDate startDate
    ) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getStreamId(), streamId) && !s.getSnapshotDate().isBefore(startDate))
            .sorted(Comparator.comparing(StreamerFollowerSnapshotEntity::getSnapshotDate))
            .toList();
    }

    @Override
    public Optional<StreamerFollowerSnapshotEntity> findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(
        String streamId,
        LocalDate snapshotDate
    ) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getStreamId(), streamId) && s.getSnapshotDate().isBefore(snapshotDate))
            .max(Comparator.comparing(StreamerFollowerSnapshotEntity::getSnapshotDate));
    }

    @Override
    public List<StreamerFollowerSnapshotEntity> findAllBySnapshotDateAndStreamIdIn(
        LocalDate snapshotDate,
        Collection<String> streamIds
    ) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getSnapshotDate(), snapshotDate) && streamIds.contains(s.getStreamId()))
            .toList();
    }

    @Override
    public int deleteExpiredSnapshots(LocalDate cutoffDate) {
        int initialSize = storage.size();
        storage.entrySet().removeIf(entry -> entry.getValue().getSnapshotDate().isBefore(cutoffDate));
        return initialSize - storage.size();
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> S save(S entity) {
        long id = idGenerator.getAndIncrement();
        storage.put(id, entity);
        return entity;
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    public Optional<StreamerFollowerSnapshotEntity> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    @Override
    public List<StreamerFollowerSnapshotEntity> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<StreamerFollowerSnapshotEntity> findAllById(Iterable<Long> ids) {
        List<StreamerFollowerSnapshotEntity> result = new ArrayList<>();
        for (Long id : ids) {
            StreamerFollowerSnapshotEntity found = storage.get(id);
            if (found != null) {
                result.add(found);
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
    public void delete(StreamerFollowerSnapshotEntity entity) {
        storage.values().remove(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(storage::remove);
    }

    @Override
    public void deleteAll(Iterable<? extends StreamerFollowerSnapshotEntity> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void flush() {}

    @Override
    public <S extends StreamerFollowerSnapshotEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<StreamerFollowerSnapshotEntity> entities) {
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
    public StreamerFollowerSnapshotEntity getOne(Long id) {
        return storage.get(id);
    }

    @Override
    public StreamerFollowerSnapshotEntity getById(Long id) {
        return storage.get(id);
    }

    @Override
    public StreamerFollowerSnapshotEntity getReferenceById(Long id) {
        return storage.get(id);
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> List<S> findAll(Example<S> example) {
        return List.of();
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> List<S> findAll(Example<S> example, Sort sort) {
        return List.of();
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends StreamerFollowerSnapshotEntity, R> R findBy(Example<S> example, Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<StreamerFollowerSnapshotEntity> findAll(Sort sort) {
        return findAll();
    }

    @Override
    public Page<StreamerFollowerSnapshotEntity> findAll(Pageable pageable) {
        return Page.empty();
    }
}
