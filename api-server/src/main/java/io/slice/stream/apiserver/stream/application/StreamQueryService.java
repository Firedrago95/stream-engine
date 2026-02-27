package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StreamQueryService {

    private final StreamService streamService;
    private final AnalysisRepository analysisRepository;

    public List<StreamResponse> getBrowseList() {
        List<StreamSyncRequest> allStreams = streamService.getAllStreams();

        Set<String> channelIds = allStreams.stream()
            .map(StreamSyncRequest::streamId)
            .collect(Collectors.toSet());

        Set<String> analyzingIds = analysisRepository.findChannelsWithRecentSignals(channelIds);

        return allStreams.stream()
            .map(s -> new StreamResponse(
                s.streamId(),
                s.streamerName(),
                s.liveTitle(),
                s.thumbnailUrl(),
                s.categoryName(),
                analyzingIds.contains(s.streamId()) ? "ANALYZING" : "LIVE"
            ))
            .toList();
    }
}
