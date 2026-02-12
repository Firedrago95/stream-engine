package io.slice.stream.apiserver.aggregation.presentation;

import io.slice.stream.apiserver.aggregation.application.ChatAggregationService;
import io.slice.stream.apiserver.aggregation.domain.ChatAggregationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatAggregationController {

    private final ChatAggregationService chatAggregationService;

    @GetMapping("/api/v1/aggregation/{streamId}")
    public ResponseEntity<ChatAggregationResult> getChatAggregationResult(@PathVariable String streamId) {
        return chatAggregationService.getChatAggregationResult(streamId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
