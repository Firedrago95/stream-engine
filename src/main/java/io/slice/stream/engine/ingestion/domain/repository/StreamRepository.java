package io.slice.stream.engine.ingestion.domain.repository;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import java.util.List;

public interface StreamRepository {

    StreamUpdateResults update(List<StreamTarget> streamTargets);
}
