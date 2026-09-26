package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;

public class FakeStreamSessionRepository implements JpaStreamSessionRepository {

    private final Map<String, StreamSessionEntity> storage = new ConcurrentHashMap<>();

    public void addSession(StreamSessionEntity session) {
        storage.put(session.getSessionId(), session);
    }

    public void addSessions(Collection<StreamSessionEntity> sessions) {
        sessions.forEach(this::addSession);
    }

    @Override
    public Page<StreamSessionEntity> findByStreamIdOrderByStartedAtDesc(String streamId, Pageable pageable) {
        List<StreamSessionEntity> filtered = storage.values().stream()
            .filter(s -> Objects.equals(s.getStreamId(), streamId))
            .sorted(Comparator.comparing(StreamSessionEntity::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        return paginate(filtered, pageable);
    }

    @Override
    public Page<StreamSessionEntity> findByStreamIdAndPaidPromotionTrueOrderByStartedAtDesc(String streamId, Pageable pageable) {
        List<StreamSessionEntity> filtered = storage.values().stream()
            .filter(s -> Objects.equals(s.getStreamId(), streamId) && s.isPaidPromotion())
            .sorted(Comparator.comparing(StreamSessionEntity::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        return paginate(filtered, pageable);
    }

    private Page<StreamSessionEntity> paginate(List<StreamSessionEntity> items, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    @Override
    public Optional<StreamSessionEntity> findActiveSession(String streamId) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getStreamId(), streamId) && s.getEndedAt() == null)
            .max(Comparator.comparing(StreamSessionEntity::getStartedAt));
    }

    @Override
    public Optional<StreamSessionEntity> findBySessionId(String sessionId) {
        return Optional.ofNullable(storage.get(sessionId));
    }

    @Override
    public List<StreamSessionEntity> findAllBySessionIdIn(Collection<String> sessionIds) {
        return sessionIds.stream()
            .map(storage::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public List<StreamSessionEntity> findSessionsToClose(Instant threshold) {
        return List.of();
    }

    @Override
    public List<StreamSessionEntity> findSessionsSince(String streamId, Instant since) {
        return storage.values().stream()
            .filter(s -> Objects.equals(s.getStreamId(), streamId) && !s.getStartedAt().isBefore(since))
            .toList();
    }

    @Override
    public List<StreamSessionEntity> findRecentSessionsByStreamId(String streamId, Pageable pageable) {
        return findByStreamIdOrderByStartedAtDesc(streamId, pageable).getContent();
    }

    @Override
    public List<StreamSessionEntity> findFinishedSessionsOlderThan(Instant threshold) {
        return List.of();
    }

    @Override
    public List<StreamSessionEntity> findAllActiveSessions(List<String> streamIds) {
        return storage.values().stream()
            .filter(s -> streamIds.contains(s.getStreamId()) && s.getEndedAt() == null)
            .toList();
    }

    @Override
    public Optional<StreamSessionEntity> findActiveSession(String streamId, String sessionId) {
        return Optional.ofNullable(storage.get(sessionId))
            .filter(s -> Objects.equals(s.getStreamId(), streamId) && s.getEndedAt() == null);
    }

    @Override
    public int deleteExpiredSessions(Instant threshold) {
        return 0;
    }

    @Override
    public List<StreamSessionEntity> findSessionsOverlapping(String streamId, Instant rangeStart, Instant rangeEnd) {
        return List.of();
    }

    @Override
    public List<StreamSessionEntity> findActiveSessionsStartedBefore(Instant threshold) {
        return List.of();
    }

    @Override
    public List<String> findDistinctStreamIdsByStartedAtAfter(Instant since) {
        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends StreamSessionEntity> S save(S entity) {
        storage.put(entity.getSessionId(), entity);
        return entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends StreamSessionEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public Optional<StreamSessionEntity> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public boolean existsById(Long id) {
        return false;
    }

    @Override
    public List<StreamSessionEntity> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<StreamSessionEntity> findAllById(Iterable<Long> ids) {
        return List.of();
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void deleteById(Long id) {
    }

    @Override
    public void delete(StreamSessionEntity entity) {
        storage.remove(entity.getSessionId());
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
    }

    @Override
    public void deleteAll(Iterable<? extends StreamSessionEntity> entities) {
        for (StreamSessionEntity entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void flush() {
    }

    @Override
    public <S extends StreamSessionEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends StreamSessionEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<StreamSessionEntity> entities) {
        deleteAll(entities);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<Long> ids) {
    }

    @Override
    public void deleteAllInBatch() {
        deleteAll();
    }

    @Override
    public StreamSessionEntity getOne(Long id) {
        return null;
    }

    @Override
    public StreamSessionEntity getById(Long id) {
        return null;
    }

    @Override
    public StreamSessionEntity getReferenceById(Long id) {
        return null;
    }

    @Override
    public <S extends StreamSessionEntity> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends StreamSessionEntity> List<S> findAll(Example<S> example) {
        return List.of();
    }

    @Override
    public <S extends StreamSessionEntity> List<S> findAll(Example<S> example, Sort sort) {
        return List.of();
    }

    @Override
    public <S extends StreamSessionEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public <S extends StreamSessionEntity> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends StreamSessionEntity> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends StreamSessionEntity, R> R findBy(Example<S> example, Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<StreamSessionEntity> findAll(Sort sort) {
        return List.of();
    }

    @Override
    public Page<StreamSessionEntity> findAll(Pageable pageable) {
        return Page.empty();
    }
}
