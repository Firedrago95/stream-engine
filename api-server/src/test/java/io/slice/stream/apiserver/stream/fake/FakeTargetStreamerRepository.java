package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.stream.targeting.TargetStreamerEntity;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;

public class FakeTargetStreamerRepository implements TargetStreamerRepository {

    private final Map<Long, TargetStreamerEntity> storage = Collections.synchronizedMap(new LinkedHashMap<>());
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public List<TargetStreamerEntity> findAllByIsActiveTrue() {
        return storage.values().stream()
            .filter(TargetStreamerEntity::isActive)
            .toList();
    }

    @Override
    public Optional<TargetStreamerEntity> findByChannelId(String channelId) {
        return storage.values().stream()
            .filter(e -> e.getChannelId().equals(channelId))
            .findFirst();
    }

    @Override
    public boolean existsByChannelId(String channelId) {
        return storage.values().stream()
            .anyMatch(e -> e.getChannelId().equals(channelId));
    }

    @Override
    public <S extends TargetStreamerEntity> S save(S entity) {
        if (entity.getId() == null) {
            try {
                java.lang.reflect.Field idField = TargetStreamerEntity.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(entity, idGenerator.getAndIncrement());
            } catch (Exception ignored) {
            }
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public <S extends TargetStreamerEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    public Optional<TargetStreamerEntity> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    @Override
    public List<TargetStreamerEntity> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<TargetStreamerEntity> findAllById(Iterable<Long> ids) {
        List<TargetStreamerEntity> result = new ArrayList<>();
        for (Long id : ids) {
            if (storage.containsKey(id)) {
                result.add(storage.get(id));
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
    public void delete(TargetStreamerEntity entity) {
        if (entity.getId() != null) {
            storage.remove(entity.getId());
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        for (Long id : ids) {
            storage.remove(id);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends TargetStreamerEntity> entities) {
        for (TargetStreamerEntity entity : entities) {
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
    public <S extends TargetStreamerEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends TargetStreamerEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<TargetStreamerEntity> entities) {
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
    public TargetStreamerEntity getOne(Long id) {
        return storage.get(id);
    }

    @Override
    public TargetStreamerEntity getById(Long id) {
        return storage.get(id);
    }

    @Override
    public TargetStreamerEntity getReferenceById(Long id) {
        return storage.get(id);
    }

    @Override
    public <S extends TargetStreamerEntity> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends TargetStreamerEntity> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends TargetStreamerEntity> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends TargetStreamerEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends TargetStreamerEntity> long count(Example<S> example) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends TargetStreamerEntity> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends TargetStreamerEntity, R> R findBy(
        Example<S> example,
        Function<FetchableFluentQuery<S>, R> queryFunction
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TargetStreamerEntity> findAll(Sort sort) {
        return findAll();
    }

    @Override
    public Page<TargetStreamerEntity> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }
}
