package io.slice.stream.apiserver.stream.targeting;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${analysis.internal-prefix}/targets")
@RequiredArgsConstructor
public class TargetStreamerInternalController {

    private final TargetStreamerService targetStreamerService;

    @GetMapping
    public ResponseEntity<List<String>> getTargets() {
        log.info("[Internal API] 엔진으로부터 타겟 채널 목록 요청 수신");
        List<String> targetChannelIds = targetStreamerService.getActiveTargetChannelIds();
        log.info("[Internal API] 타겟 채널 목록 반환 완료 (총 {}개)", targetChannelIds.size());
        return ResponseEntity.ok(targetChannelIds);
    }
}
