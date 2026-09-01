package gr.imsi.athenarc.xtremexpvisapi.controller;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ReplayRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ReplayResult;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Score;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoreCreateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoresResponse;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TraceDetail;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TracesResponse;
import gr.imsi.athenarc.xtremexpvisapi.service.observability.CounterfactualReplayService;
import gr.imsi.athenarc.xtremexpvisapi.service.observability.ObservabilityService;
import gr.imsi.athenarc.xtremexpvisapi.service.observability.ObservabilityServiceFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

  private final ObservabilityService observabilityService;
  private final CounterfactualReplayService counterfactualReplayService;

  public ObservabilityController(
      ObservabilityServiceFactory observabilityServiceFactory,
      CounterfactualReplayService counterfactualReplayService) {
    this.observabilityService = observabilityServiceFactory.getObservabilityService();
    this.counterfactualReplayService = counterfactualReplayService;
  }

  @GetMapping("/traces")
  @Operation(summary = "Get traces", description = "Get traces from the observability service.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved traces"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<TracesResponse> getTraces(
      @Parameter(description = "The project ID", required = true) @RequestParam String projectId,
      @Parameter(description = "The session ID") @RequestParam(required = false) String sessionId,
      @Parameter(description = "The user ID") @RequestParam(required = false) String userId) {
    TracesResponse traces = observabilityService.getTraces(projectId, sessionId, userId);
    return ResponseEntity.ok(traces);
  }

  @GetMapping("/traces/{traceId}")
  @Operation(
      summary = "Get a specific trace by ID",
      description =
          "Get a full trace from the observability service, including observations and scores.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the trace"),
        @ApiResponse(responseCode = "404", description = "Trace not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<TraceDetail> getTrace(
      @Parameter(description = "The ID of the trace", required = true) @PathVariable
          String traceId) {
    TraceDetail trace = observabilityService.getTrace(traceId);
    return ResponseEntity.ok(trace);
  }

  @PostMapping("/traces/{traceId}/counterfactual")
  @Operation(
      summary = "Run a counterfactual replay of one LLM observation in a trace",
      description =
          "Re-runs a single GENERATION observation's prompt (merged with the supplied overrides) "
              + "against the local Ollama model, so the original and counterfactual outputs can be "
              + "compared. This replays reasoning only; it does not re-execute training/evaluation.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully ran the counterfactual replay"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request or non-replayable observation"),
        @ApiResponse(responseCode = "404", description = "Trace or observation not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<ReplayResult> runCounterfactual(
      @Parameter(description = "The ID of the trace", required = true) @PathVariable String traceId,
      @RequestBody ReplayRequest request) {
    ReplayResult result = counterfactualReplayService.runCounterfactual(traceId, request);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/scores")
  @Operation(
      summary = "Create a human annotation",
      description =
          "Attaches a score (thumbs/rating + comment) to a trace, or to one observation within it "
              + "when observationId is set.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully created the annotation"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<Score> createScore(@RequestBody ScoreCreateRequest request) {
    Score score = observabilityService.createScore(request);
    return ResponseEntity.ok(score);
  }

  @GetMapping("/scores")
  @Operation(
      summary = "List annotations",
      description =
          "Lists scores for a project, optionally narrowed to one trace and/or score name — "
              + "the source for an 'all annotations across the experiment' view.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved annotations"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<ScoresResponse> getScores(
      @Parameter(description = "The project ID", required = true) @RequestParam String projectId,
      @Parameter(description = "The trace ID") @RequestParam(required = false) String traceId,
      @Parameter(description = "The score name") @RequestParam(required = false) String name,
      @Parameter(description = "Page number (1-indexed)") @RequestParam(required = false) Integer page,
      @Parameter(description = "Page size") @RequestParam(required = false) Integer limit) {
    ScoresResponse scores = observabilityService.getScores(projectId, traceId, name, page, limit);
    return ResponseEntity.ok(scores);
  }
}
