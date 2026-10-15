package io.slice.stream.apiserver.stream.fake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

public class FakeApplicationEventPublisher implements ApplicationEventPublisher {

    private final List<Object> publishedEvents = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publishEvent(Object event) {
        publishedEvents.add(event);
    }

    public List<Object> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getEventsOfType(Class<T> type) {
        return publishedEvents.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
    }

    public void clear() {
        publishedEvents.clear();
    }
}
