package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StreamQueryService {

    private final StreamService streamService;
    private final AnalysisRepository analysisRepository;

    public List<StreamResponse> getBrowseList() {
        return streamService.getAllStreams().stream()
            .map(s -> {
                boolean isAnalyzing = !analysisRepository.findRecentSignals(s.channelId(), 1).isEmpty();

                return new StreamResponse(
                    s.channelId(),
                    s.channelName(),
                    s.liveTitle(),
                    s.thumbnailUrl(),
                    s.categoryName(),
                    isAnalyzing ? "ANALYZING" : "LIVE"
                );
            })
            .sorted(Comparator.comparing(StreamResponse::status))
            .toList();
    }
}
