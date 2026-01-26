package io.slice.stream.engine.analysis.presentation;

import io.slice.stream.engine.analysis.application.ChatAnalysisService;
import io.slice.stream.engine.analysis.domain.ChatAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatAnalysisController {

    private final ChatAnalysisService chatAnalysisService;

    @GetMapping("/api/v1/analysis/{streamId}")
    public ResponseEntity<ChatAnalysisResult> getChatAnalysisResult(@PathVariable String streamId) {
        return chatAnalysisService.getChatAnalysisResult(streamId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
