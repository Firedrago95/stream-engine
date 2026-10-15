package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.fake.FakeStringRedisTemplate;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreamSyncDirtyCheckerTest {

    private FakeStringRedisTemplate redisTemplate;
    private StreamSyncDirtyChecker dirtyChecker;

    @BeforeEach
    void setUp() {
        redisTemplate = new FakeStringRedisTemplate();
        dirtyChecker = new StreamSyncDirtyChecker(redisTemplate);
    }

    @Test
    @DisplayName("이전 스냅샷이 없는 신규 방송은 Dirty로 판정되어야 한다")
    void newStreamIsDirty() {
        StreamSyncRequest req = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "첫 방송", "img.png", 1000, "소통", Instant.now()
        );

        List<StreamSyncRequest> dirty = dirtyChecker.filterDirtyRequests(List.of(req));

        assertThat(dirty).hasSize(1);
        assertThat(dirty.get(0).streamId()).isEqualTo("ch1");
    }

    @Test
    @DisplayName("스냅샷과 동일한 정보의 방송은 Clean으로 판정되어 제외되어야 한다")
    void identicalStreamIsClean() {
        StreamSyncRequest req = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "제목", "img.png", 1000, "소통", Instant.now()
        );
        dirtyChecker.updateSnapshots(List.of(req));

        List<StreamSyncRequest> dirty = dirtyChecker.filterDirtyRequests(List.of(req));

        assertThat(dirty).isEmpty();
    }

    @Test
    @DisplayName("제목이나 카테고리가 변경되면 Dirty로 판정되어야 한다")
    void metadataChangeIsDirty() {
        StreamSyncRequest initial = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "원래 제목", "img.png", 1000, "소통", Instant.now()
        );
        dirtyChecker.updateSnapshots(List.of(initial));

        StreamSyncRequest changedTitle = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "변경된 제목", "img.png", 1000, "소통", Instant.now()
        );
        List<StreamSyncRequest> dirtyTitle = dirtyChecker.filterDirtyRequests(List.of(changedTitle));
        assertThat(dirtyTitle).hasSize(1);

        StreamSyncRequest changedCategory = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "원래 제목", "img.png", 1000, "게임", Instant.now()
        );
        List<StreamSyncRequest> dirtyCategory = dirtyChecker.filterDirtyRequests(List.of(changedCategory));
        assertThat(dirtyCategory).hasSize(1);
    }

    @Test
    @DisplayName("시청자 수가 미세하게 변경(30명 미만 및 10% 미만)되면 Clean으로 판정되어야 한다")
    void minorViewerChangeIsClean() {
        StreamSyncRequest initial = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "제목", "img.png", 1000, "소통", Instant.now()
        );
        dirtyChecker.updateSnapshots(List.of(initial));

        StreamSyncRequest slightChange = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "제목", "img.png", 1010, "소통", Instant.now()
        );
        List<StreamSyncRequest> dirty = dirtyChecker.filterDirtyRequests(List.of(slightChange));

        assertThat(dirty).isEmpty();
    }

    @Test
    @DisplayName("시청자 수가 유의미하게 변경(30명 이상 및 10% 이상)되면 Dirty로 판정되어야 한다")
    void significantViewerChangeIsDirty() {
        StreamSyncRequest initial = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "제목", "img.png", 1000, "소통", Instant.now()
        );
        dirtyChecker.updateSnapshots(List.of(initial));

        StreamSyncRequest bigChange = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "제목", "img.png", 1200, "소통", Instant.now()
        );
        List<StreamSyncRequest> dirty = dirtyChecker.filterDirtyRequests(List.of(bigChange));

        assertThat(dirty).hasSize(1);
    }

    @Test
    @DisplayName("방제에 파이프(|)나 특수문자가 포함되어도 직렬화 오류 없이 동일하면 Clean이어야 한다")
    void titleWithSpecialCharactersDoesNotBreakParsing() {
        StreamSyncRequest specialTitleReq = new StreamSyncRequest(
            "ch1", "live1", "침착맨", "롤 | 솔랭 | 배치고사 [1일차] (마이크 on/off)", "img.png", 1000, "게임", Instant.now()
        );
        dirtyChecker.updateSnapshots(List.of(specialTitleReq));

        List<StreamSyncRequest> dirty = dirtyChecker.filterDirtyRequests(List.of(specialTitleReq));

        assertThat(dirty).isEmpty();
    }

    @Test
    @DisplayName("성인 방송이나 유료 프로모션 플래그가 변경되면 Dirty로 판정되어야 한다")
    void adultOrPaidFlagChangeIsDirty() {
        Instant now = Instant.now();
        StreamSyncRequest initial = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "img.png", 1000, "소통", now, false, false);
        dirtyChecker.updateSnapshots(List.of(initial));

        StreamSyncRequest changedAdult = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "img.png", 1000, "소통", now, false, true);
        List<StreamSyncRequest> dirtyAdult = dirtyChecker.filterDirtyRequests(List.of(changedAdult));
        assertThat(dirtyAdult).hasSize(1);

        StreamSyncRequest changedPaid = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "img.png", 1000, "소통", now, true, false);
        List<StreamSyncRequest> dirtyPaid = dirtyChecker.filterDirtyRequests(List.of(changedPaid));
        assertThat(dirtyPaid).hasSize(1);
    }

    @Test
    @DisplayName("스트리머 닉네임만 변경되어도 Dirty로 판정되어야 한다")
    void streamerNameChangeIsDirty() {
        Instant now = Instant.now();
        StreamSyncRequest initial = new StreamSyncRequest("ch1", "live1", "이전닉네임", "제목", "img.png", 1000, "소통", now);
        dirtyChecker.updateSnapshots(List.of(initial));

        StreamSyncRequest changedName = new StreamSyncRequest("ch1", "live1", "새로운닉네임", "제목", "img.png", 1000, "소통", now);
        List<StreamSyncRequest> dirty = dirtyChecker.filterDirtyRequests(List.of(changedName));

        assertThat(dirty).hasSize(1);
        assertThat(dirty.get(0).streamerName()).isEqualTo("새로운닉네임");
    }

    @Test
    @DisplayName("프로필 이미지만 변경되어도 Dirty로 판정되어야 한다")
    void profileImageUrlChangeIsDirty() {
        Instant now = Instant.now();
        StreamSyncRequest initial = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "https://old-image.png", 1000, "소통", now);
        dirtyChecker.updateSnapshots(List.of(initial));

        StreamSyncRequest changedImage = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "https://new-image.png", 1000, "소통", now);
        List<StreamSyncRequest> dirty = dirtyChecker.filterDirtyRequests(List.of(changedImage));

        assertThat(dirty).hasSize(1);
        assertThat(dirty.get(0).profileImageUrl()).isEqualTo("https://new-image.png");
    }

    @Test
    @DisplayName("null 또는 빈 리스트 입력 시 예외 없이 빈 목록을 반환해야 한다")
    void emptyOrNullRequestsReturnEmptyList() {
        assertThat(dirtyChecker.filterDirtyRequests(null)).isEmpty();
        assertThat(dirtyChecker.filterDirtyRequests(List.of())).isEmpty();
    }
}
