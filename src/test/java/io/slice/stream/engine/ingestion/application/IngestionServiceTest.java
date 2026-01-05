package io.slice.stream.engine.ingestion.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.event.StreamStartedEvent;
import io.slice.stream.engine.ingestion.domain.event.StreamStoppedEvent;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class IngestionServiceTest {

    @Mock
    StreamDiscoveryClient streamDiscoveryClient;

    @Mock
    StreamRepository streamRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    IngestionService ingestionService;

    @BeforeEach
    void setup() {
        ingestionService = new IngestionService(
            streamDiscoveryClient,
            streamRepository,
            eventPublisher
        );
    }

    @Test
    void 새로운_스트림을_성공적으로_수집해야_한다() {
        // given
        List<StreamTarget> newStreams = List.of(
            new StreamTarget("ch1", "chName1", 123L, "title1", 10)
        );
        StreamUpdateResults results = new StreamUpdateResults(Set.of("ch1"), Collections.emptySet());
        when(streamDiscoveryClient.fetchTopLiveStreams(any(int.class))).thenReturn(newStreams);
        when(streamRepository.update(newStreams)).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(streamRepository).update(newStreams);
        verify(eventPublisher).publishEvent(any(StreamStartedEvent.class));
    }

    @Test
    void 스트림_업데이트를_올바르게_처리해야_한다() {
        // given
        List<StreamTarget> liveStreams = List.of(
            new StreamTarget("ch1", "chName1", 1L, "title1", 10)
        );
        StreamUpdateResults results = new StreamUpdateResults(Set.of("ch1"), Set.of("ch2"));
        when(streamDiscoveryClient.fetchTopLiveStreams(any(int.class))).thenReturn(liveStreams);
        when(streamRepository.update(liveStreams)).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(eventPublisher).publishEvent(any(StreamStartedEvent.class));
        verify(eventPublisher).publishEvent(any(StreamStoppedEvent.class));
    }

    @Test
    void 스트림_시작_시_StreamStartedEvent를_발행해야_한다() {
        // given
        List<StreamTarget> newStreams = List.of(
            new StreamTarget("ch1", "chName1", 1L, "title1", 10),
            new StreamTarget("ch2", "chName2", 2L, "title2", 20)
        );
        StreamUpdateResults results = new StreamUpdateResults(Set.of("ch1", "ch2"),
            Collections.emptySet());
        when(streamDiscoveryClient.fetchTopLiveStreams(any(int.class))).thenReturn(newStreams);
        when(streamRepository.update(newStreams)).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        ArgumentCaptor<StreamStartedEvent> captor = ArgumentCaptor.forClass(
            StreamStartedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
    }

    @Test
    void 스트림_중지_시_StreamStoppedEvent를_발행해야_한다() {
        // given
        List<StreamTarget> liveStreams = Collections.emptyList();
        StreamUpdateResults results = new StreamUpdateResults(Collections.emptySet(),
            Set.of("ch1", "ch2"));
        when(streamDiscoveryClient.fetchTopLiveStreams(any(int.class))).thenReturn(liveStreams);
        when(streamRepository.update(liveStreams)).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        ArgumentCaptor<StreamStoppedEvent> captor = ArgumentCaptor.forClass(
            StreamStoppedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
    }

    @Test
    void 변경되지_않은_스트림에_대해서는_이벤트를_발행하지_않아야_한다() {
        // given
        List<StreamTarget> liveStreams = List.of(
            new StreamTarget("ch1", "chName1", 1L, "title1", 10)
        );
        StreamUpdateResults results = new StreamUpdateResults(Collections.emptySet(),
            Collections.emptySet());
        when(streamDiscoveryClient.fetchTopLiveStreams(any(int.class))).thenReturn(liveStreams);
        when(streamRepository.update(liveStreams)).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 스트림_탐색_중_오류를_정상적으로_처리해야_한다() {
        // given
        when(streamDiscoveryClient.fetchTopLiveStreams(any(int.class))).thenThrow(
            new RuntimeException("API Error"));

        // when & then
        assertThrows(RuntimeException.class, () -> ingestionService.ingest());
    }

    @Test
    void 저장소의_스트림_상태를_업데이트해야_한다() {
        // given
        List<StreamTarget> liveStreams = List.of(
            new StreamTarget("ch1", "chName1", 1L, "title1", 10),
            new StreamTarget("ch2", "chName2", 2L, "title2", 20)
        );
        when(streamDiscoveryClient.fetchTopLiveStreams(any(int.class))).thenReturn(liveStreams);
        when(streamRepository.update(liveStreams)).thenReturn(
            new StreamUpdateResults(Collections.emptySet(), Collections.emptySet()));

        // when
        ingestionService.ingest();

        // then
        verify(streamRepository).update(liveStreams);
    }
}
