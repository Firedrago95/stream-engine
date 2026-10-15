package io.slice.stream.apiserver.stream.fake;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

public class FakeStringRedisTemplate extends StringRedisTemplate {

    private final Map<String, String> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> listStore = new ConcurrentHashMap<>();
    private final ValueOperations<String, String> fakeValueOperations = new FakeValueOperations(memoryStore);
    @SuppressWarnings("unchecked")
    private final ListOperations<String, String> fakeListOperations = (ListOperations<String, String>) Proxy.newProxyInstance(
            ListOperations.class.getClassLoader(),
            new Class<?>[]{ListOperations.class},
            (proxy, method, args) -> {
                String methodName = method.getName();
                if ("rightPushAll".equals(methodName)) {
                    String key = (String) args[0];
                    if (args[1] instanceof Collection<?> col) {
                        List<String> list = listStore.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
                        for (Object o : col) {
                            list.add(String.valueOf(o));
                        }
                        return (long) list.size();
                    }
                }
                if ("range".equals(methodName)) {
                    String key = (String) args[0];
                    long start = (Long) args[1];
                    long end = (Long) args[2];
                    List<String> list = listStore.get(key);
                    if (list == null || list.isEmpty()) {
                        return Collections.emptyList();
                    }
                    int s = Math.max(0, (int) start);
                    int e = end == -1 ? list.size() : Math.min(list.size(), (int) end + 1);
                    if (s >= list.size() || s >= e) {
                        return Collections.emptyList();
                    }
                    return new ArrayList<>(list.subList(s, e));
                }
                if ("size".equals(methodName)) {
                    String key = (String) args[0];
                    List<String> list = listStore.get(key);
                    return list != null ? (long) list.size() : 0L;
                }
                return null;
            }
    );

    public FakeStringRedisTemplate() {
        super();
    }

    @Override
    public ValueOperations<String, String> opsForValue() {
        return fakeValueOperations;
    }

    @Override
    public ListOperations<String, String> opsForList() {
        return fakeListOperations;
    }

    @Override
    public Boolean delete(String key) {
        boolean removedMemory = memoryStore.remove(key) != null;
        boolean removedList = listStore.remove(key) != null;
        return removedMemory || removedList;
    }

    @Override
    public void rename(String oldKey, String newKey) {
        if (memoryStore.containsKey(oldKey)) {
            memoryStore.put(newKey, memoryStore.remove(oldKey));
        }
        if (listStore.containsKey(oldKey)) {
            listStore.put(newKey, listStore.remove(oldKey));
        }
    }

    @Override
    public Boolean expire(String key, Duration timeout) {
        return memoryStore.containsKey(key) || listStore.containsKey(key);
    }

    @Override
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return memoryStore.containsKey(key) || listStore.containsKey(key);
    }

    @Override
    public Set<String> keys(String pattern) {
        String regex = pattern.replace("*", ".*");
        Set<String> matched = new HashSet<>();
        for (String k : memoryStore.keySet()) {
            if (k.matches(regex)) matched.add(k);
        }
        for (String k : listStore.keySet()) {
            if (k.matches(regex)) matched.add(k);
        }
        return matched;
    }

    public void clear() {
        memoryStore.clear();
        listStore.clear();
    }

    public Map<String, String> getMemoryStore() {
        return memoryStore;
    }

    public Map<String, List<String>> getListStore() {
        return listStore;
    }

    private static class FakeValueOperations implements ValueOperations<String, String> {

        private final Map<String, String> store;

        FakeValueOperations(Map<String, String> store) {
            this.store = store;
        }

        @Override
        public void set(String key, String value) {
            store.put(key, value);
        }

        @Override
        public void set(String key, String value, Duration timeout) {
            store.put(key, value);
        }

        @Override
        public void set(String key, String value, long timeout, TimeUnit unit) {
            store.put(key, value);
        }

        @Override
        public Boolean setIfAbsent(String key, String value) {
            return store.putIfAbsent(key, value) == null;
        }

        @Override
        public Boolean setIfAbsent(String key, String value, Duration timeout) {
            return store.putIfAbsent(key, value) == null;
        }

        @Override
        public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
            return store.putIfAbsent(key, value) == null;
        }

        @Override
        public Boolean setIfPresent(String key, String value) {
            if (store.containsKey(key)) {
                store.put(key, value);
                return true;
            }
            return false;
        }

        @Override
        public Boolean setIfPresent(String key, String value, Duration timeout) {
            return setIfPresent(key, value);
        }

        @Override
        public Boolean setIfPresent(String key, String value, long timeout, TimeUnit unit) {
            return setIfPresent(key, value);
        }

        @Override
        public void multiSet(Map<? extends String, ? extends String> map) {
            store.putAll(map);
        }

        @Override
        public Boolean multiSetIfAbsent(Map<? extends String, ? extends String> map) {
            boolean allAbsent = map.keySet().stream().noneMatch(store::containsKey);
            if (allAbsent) {
                store.putAll(map);
                return true;
            }
            return false;
        }

        @Override
        public String get(Object key) {
            return store.get(key != null ? key.toString() : null);
        }

        @Override
        public String getAndDelete(String key) {
            return store.remove(key);
        }

        @Override
        public String getAndExpire(String key, Duration timeout) {
            return store.get(key);
        }

        @Override
        public String getAndExpire(String key, long timeout, TimeUnit unit) {
            return store.get(key);
        }

        @Override
        public String getAndPersist(String key) {
            return store.get(key);
        }

        @Override
        public String getAndSet(String key, String value) {
            return store.put(key, value);
        }

        @Override
        public String setGet(String key, String value, Duration timeout) {
            return store.put(key, value);
        }

        @Override
        public String setGet(String key, String value, long timeout, TimeUnit unit) {
            return store.put(key, value);
        }

        @Override
        public List<String> multiGet(Collection<String> keys) {
            return keys.stream().map(store::get).toList();
        }

        @Override
        public Long increment(String key) {
            return null;
        }

        @Override
        public Long increment(String key, long delta) {
            return null;
        }

        @Override
        public Double increment(String key, double delta) {
            return null;
        }

        @Override
        public Long decrement(String key) {
            return null;
        }

        @Override
        public Long decrement(String key, long delta) {
            return null;
        }

        @Override
        public Integer append(String key, String value) {
            String current = store.getOrDefault(key, "");
            String updated = current + value;
            store.put(key, updated);
            return updated.length();
        }

        @Override
        public String get(String key, long start, long end) {
            String val = store.get(key);
            if (val == null) return null;
            return val.substring((int) start, Math.min((int) end + 1, val.length()));
        }

        @Override
        public void set(String key, String value, long offset) {
            store.put(key, value);
        }

        @Override
        public Long size(String key) {
            String val = store.get(key);
            return val != null ? (long) val.length() : 0L;
        }

        @Override
        public Boolean setBit(String key, long offset, boolean value) {
            return false;
        }

        @Override
        public Boolean getBit(String key, long offset) {
            return false;
        }

        @Override
        public List<Long> bitField(String key, BitFieldSubCommands subCommands) {
            return List.of();
        }

        @Override
        public RedisOperations<String, String> getOperations() {
            return null;
        }
    }
}
