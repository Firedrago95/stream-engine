package io.slice.stream.apiserver.admin.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;

public class FakeSystemConfigRepository implements SystemConfigRepository {

    private final Map<String, SystemConfigEntity> storage = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public List<SystemConfigEntity> findAllByOrderByCategoryAscConfigKeyAsc() {
        return storage.values().stream()
            .sorted(Comparator.comparing(SystemConfigEntity::getCategory)
                .thenComparing(SystemConfigEntity::getConfigKey))
            .toList();
    }

    @Override
    public <S extends SystemConfigEntity> S save(S entity) {
        storage.put(entity.getConfigKey(), entity);
        return entity;
    }

    @Override
    public <S extends SystemConfigEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    public Optional<SystemConfigEntity> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }

    @Override
    public List<SystemConfigEntity> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<SystemConfigEntity> findAllById(Iterable<String> ids) {
        List<SystemConfigEntity> result = new ArrayList<>();
        for (String id : ids) {
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
    public void deleteById(String id) {
        storage.remove(id);
    }

    @Override
    public void delete(SystemConfigEntity entity) {
        if (entity.getConfigKey() != null) {
            storage.remove(entity.getConfigKey());
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        for (String id : ids) {
            storage.remove(id);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends SystemConfigEntity> entities) {
        for (SystemConfigEntity entity : entities) {
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
    public <S extends SystemConfigEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends SystemConfigEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<SystemConfigEntity> entities) {
        deleteAll(entities);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<String> ids) {
        deleteAllById(ids);
    }

    @Override
    public void deleteAllInBatch() {
        deleteAll();
    }

    @Override
    public SystemConfigEntity getOne(String id) {
        return storage.get(id);
    }

    @Override
    public SystemConfigEntity getById(String id) {
        return storage.get(id);
    }

    @Override
    public SystemConfigEntity getReferenceById(String id) {
        return storage.get(id);
    }

    @Override
    public <S extends SystemConfigEntity> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends SystemConfigEntity> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends SystemConfigEntity> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends SystemConfigEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends SystemConfigEntity> long count(Example<S> example) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends SystemConfigEntity> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends SystemConfigEntity, R> R findBy(
        Example<S> example,
        Function<FetchableFluentQuery<S>, R> queryFunction
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<SystemConfigEntity> findAll(Sort sort) {
        return findAll();
    }

    @Override
    public Page<SystemConfigEntity> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }
}
