package io.slice.stream.engine.sampler.presentation;

import io.slice.stream.engine.sampler.application.ChatSamplerService;
import io.slice.stream.engine.sampler.application.dto.SamplingStatusResponse;
import io.slice.stream.engine.sampler.application.dto.StartSamplingRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/chat-sampler")
public class ChatSamplerInternalController {

    private final ChatSamplerService chatSamplerService;

    public ChatSamplerInternalController(ChatSamplerService chatSamplerService) {
        this.chatSamplerService = chatSamplerService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSampling(@RequestBody StartSamplingRequest request) {
        chatSamplerService.startSampling(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "채팅 샘플링이 시작되었습니다."
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopSampling() {
        chatSamplerService.stopAll("USER_API_REQUEST");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "모든 채팅 샘플링이 중단되었습니다."
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<SamplingStatusResponse> getStatus() {
        SamplingStatusResponse status = chatSamplerService.getStatus();
        return ResponseEntity.ok(status);
    }
}
