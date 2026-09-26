package io.slice.stream.engine.ingestion.fake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

public class FakeApplicationEventPublisher implements ApplicationEventPublisher {

    private final List<Object> publishedEvents = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        if (event != null) {
            publishedEvents.add(event);
        }
    }

    public List<Object> getPublishedEvents() {
        return Collections.unmodifiableList(publishedEvents);
    }

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
