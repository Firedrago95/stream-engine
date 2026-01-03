package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    }

    @Test
    void 스트림_업데이트를_올바르게_처리해야_한다() {

    }

    @Test
    void 스트림_시작_시_StreamStartedEvent를_발행해야_한다() {

    }

    @Test
    void 스트림_중지_시_StreamStoppedEvent를_발행해야_한다() {

    }

    @Test
    void 변경되지_않은_스트림에_대해서는_이벤트를_발행하지_않아야_한다() {

    }

    @Test
    void 스트림_탐색_중_오류를_정상적으로_처리해야_한다() {

    }

    @Test
    void 저장소의_스트림_상태를_업데이트해야_한다() {

    }
}
