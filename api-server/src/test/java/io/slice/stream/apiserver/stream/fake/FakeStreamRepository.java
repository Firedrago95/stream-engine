package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
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

public class FakeStreamRepository implements JpaStreamRepository {

    private final Map<String, StreamEntity> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    public void addStream(StreamEntity stream) {
        save(stream);
    }

    public void addStreams(Collection<StreamEntity> streams) {
        streams.forEach(this::addStream);
    }

    @Override
    public List<StreamEntity> findAllByStreamIdIn(List<String> streamIds) {
        return streamIds.stream()
            .map(storage::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public Optional<StreamEntity> findByStreamId(String streamId) {
        return Optional.ofNullable(storage.get(streamId));
    }

    @Override
    public int markAllOfflineBefore(Instant threshold) {
        return 0;
    }

    @Override
    public List<StreamEntity> findActiveStreams(Instant threshold) {
        return storage.values().stream()
            .filter(StreamEntity::isLive)
            .toList();
    }

    @Override
    public void upsertStream(StreamEntity s, Instant currentTime) {
        storage.put(s.getStreamId(), s);
    }

    @Override
    public List<StreamEntity> searchByStreamerName(String keyword, Instant threshold) {
        return storage.values().stream()
            .filter(s -> s.getStreamerName() != null && s.getStreamerName().contains(keyword))
            .toList();
    }

    @Override
    public List<String> findTopStreamIdsByConcurrentUserCount(Instant since, Pageable pageable) {
        return storage.values().stream()
            .filter(s -> !s.getLastUpdateAt().isBefore(since))
            .sorted(Comparator.comparing(StreamEntity::getConcurrentUserCount, Comparator.reverseOrder()))
            .map(StreamEntity::getStreamId)
            .toList();
    }

    @Override
    public List<StreamEntity> findActiveStreamsByStreamIds(List<String> streamIds, Instant threshold) {
        return streamIds.stream()
            .map(storage::get)
            .filter(Objects::nonNull)
            .filter(StreamEntity::isLive)
            .toList();
    }

    @Override
    public List<StreamEntity> findAllStreamersForLeaderboard(Instant since, Pageable pageable) {
        return List.of();
    }

    @Override
    public List<StreamEntity> searchAllStreamersForLeaderboard(String keyword, Instant since, Pageable pageable) {
        return List.of();
    }

    @Override
    public List<StreamerLeaderboardProjection> findTopStreamersWith30dAvg(Instant since, int minDays, int limit) {
        return List.of();
    }

    @Override
    public List<StreamerLeaderboardProjection> searchTopStreamersWith30dAvg(String keyword, Instant since, int limit) {
        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends StreamEntity> S save(S entity) {
        storage.put(entity.getStreamId(), entity);
        return entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends StreamEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public Optional<StreamEntity> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public boolean existsById(Long id) {
        return false;
    }

    @Override
    public List<StreamEntity> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<StreamEntity> findAllById(Iterable<Long> ids) {
        return List.of();
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void deleteById(Long id) {
        storage.values().removeIf(s -> Objects.equals(s.getId(), id));
    }

    @Override
    public void delete(StreamEntity entity) {
        storage.remove(entity.getStreamId());
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        for (Long id : ids) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends StreamEntity> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void flush() {}

    @Override
    public <S extends StreamEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends StreamEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<StreamEntity> entities) {
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
    public StreamEntity getOne(Long id) {
        return null;
    }

    @Override
    public StreamEntity getById(Long id) {
        return null;
    }

    @Override
    public StreamEntity getReferenceById(Long id) {
        return null;
    }

    @Override
    public <S extends StreamEntity> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends StreamEntity> List<S> findAll(Example<S> example) {
        return List.of();
    }

    @Override
    public <S extends StreamEntity> List<S> findAll(Example<S> example, Sort sort) {
        return List.of();
    }

    @Override
    public <S extends StreamEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public <S extends StreamEntity> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends StreamEntity> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends StreamEntity, R> R findBy(Example<S> example, Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<StreamEntity> findAll(Sort sort) {
        return List.of();
    }

    @Override
    public Page<StreamEntity> findAll(Pageable pageable) {
        return Page.empty();
    }
}
