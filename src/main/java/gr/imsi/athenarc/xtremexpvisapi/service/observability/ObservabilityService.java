package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Score;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoreCreateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoresResponse;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TraceDetail;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TracesResponse;

public interface ObservabilityService {
  TracesResponse getTraces(String projectId, String sessionId, String userId);

  TraceDetail getTrace(String traceId);

  /** Creates a human annotation (score) on a trace, or on one observation within it. */
  Score createScore(ScoreCreateRequest request);

  /** Lists scores for a project, optionally narrowed to one trace and/or score name. */
  ScoresResponse getScores(String projectId, String traceId, String name, Integer page, Integer limit);
}
