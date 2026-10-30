package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Meta;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Observation;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Score;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoreCreateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoresResponse;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Trace;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TraceDetail;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TracesResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service("langfuse")
public class LangfuseObservabilityService implements ObservabilityService {

  private final RestTemplate restTemplate;
  private final String apiUrl;
  // Bounded so one experiment's page fan-out can't flood Langfuse.
  private final ExecutorService pageFetcher = Executors.newFixedThreadPool(8);

  public LangfuseObservabilityService(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${langfuse.api.url}") String apiUrl,
      @Value("${langfuse.api.publicKey}") String publicKey,
      @Value("${langfuse.api.secretKey}") String secretKey) {
    this.apiUrl = apiUrl;
    this.restTemplate = restTemplateBuilder.basicAuthentication(publicKey, secretKey).build();
  }

  @Override
  public TracesResponse getTraces(String projectId, String sessionId, String userId) {
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> entity = new HttpEntity<>(headers);

    UriComponentsBuilder uriBuilder =
        UriComponentsBuilder.fromHttpUrl(apiUrl)
            .path("/api/public/traces")
            .queryParam("fields", "observations,scores");

    if (projectId != null && !projectId.isEmpty()) {
      uriBuilder.queryParam("projectId", projectId);
    }

    if (sessionId != null && !sessionId.isEmpty()) {
      uriBuilder.queryParam("sessionId", sessionId);
    }
    if (userId != null && !userId.isEmpty()) {
      uriBuilder.queryParam("userId", userId);
    }

    ResponseEntity<TracesResponse> response =
        restTemplate.exchange(
            uriBuilder.toUriString(), HttpMethod.GET, entity, TracesResponse.class);

    return response.getBody();
  }

  @Override
  public TraceDetail getTrace(String traceId) {
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> entity = new HttpEntity<>(headers);

    String url =
        UriComponentsBuilder.fromHttpUrl(apiUrl)
            .path("/api/public/traces/{traceId}")
            .buildAndExpand(traceId)
            .toUriString();

    ResponseEntity<TraceDetail> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, TraceDetail.class);

    return response.getBody();
  }

  // Langfuse caps every list endpoint at 100 items a page.
  private static final int PAGE_SIZE = 100;

  static class Page<T> {
    public List<T> data;
    public Meta meta;
  }

  /** A row of the trace list: observations and scores are ids only, latency and cost are included. */
  static class TraceRow extends Trace {
    public Double latency;
    public Double totalCost;
  }

  private static final ParameterizedTypeReference<Page<TraceRow>> TRACE_PAGE = new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Page<Observation>> OBSERVATION_PAGE = new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Page<Score>> SCORE_PAGE = new ParameterizedTypeReference<>() {};

  /**
   * Reads the user's traces, observations and scores through the three list endpoints
   * (a few dozen pages, fetched in parallel) and joins them by trace, instead of one
   * detail request per trace.
   */
  @Override
  public List<TraceDetail> getTraceDetails(String projectId, String userId) {
    CompletableFuture<Page<TraceRow>> firstTraces = fetchPageAsync("traces", TRACE_PAGE, projectId, userId, 1);
    CompletableFuture<Page<Observation>> firstObservations = fetchPageAsync("observations", OBSERVATION_PAGE, projectId, userId, 1);
    CompletableFuture<Page<Score>> firstScores = fetchPageAsync("scores", SCORE_PAGE, projectId, userId, 1);

    CompletableFuture<List<TraceRow>> traces = restOfPages("traces", TRACE_PAGE, projectId, userId, firstTraces.join());
    CompletableFuture<List<Observation>> observations = restOfPages("observations", OBSERVATION_PAGE, projectId, userId, firstObservations.join());
    CompletableFuture<List<Score>> scores = restOfPages("scores", SCORE_PAGE, projectId, userId, firstScores.join());

    Map<String, List<Observation>> observationsByTrace = observations.join().stream()
        .collect(Collectors.groupingBy(Observation::getTraceId));
    // Session-level scores carry no trace id.
    Map<String, List<Score>> scoresByTrace = scores.join().stream()
        .filter(score -> score.getTraceId() != null)
        .collect(Collectors.groupingBy(Score::getTraceId));

    return traces.join().stream().map(row -> {
      TraceDetail detail = new TraceDetail();
      detail.setId(row.getId());
      detail.setTimestamp(row.getTimestamp());
      detail.setName(row.getName());
      detail.setUserId(row.getUserId());
      detail.setSessionId(row.getSessionId());
      detail.setRelease(row.getRelease());
      detail.setVersion(row.getVersion());
      detail.setMetadata(row.getMetadata());
      detail.setTags(row.getTags());
      detail.setPublic(row.isPublic());
      detail.setInput(row.getInput());
      detail.setOutput(row.getOutput());
      detail.setLatency(row.latency);
      detail.setTotalCost(row.totalCost);
      detail.setObservations(observationsByTrace.getOrDefault(row.getId(), List.of()));
      detail.setScores(scoresByTrace.getOrDefault(row.getId(), List.of()));
      return detail;
    }).toList();
  }

  /** The first page's items followed by every other page's, the others fetched in parallel. */
  private <T> CompletableFuture<List<T>> restOfPages(
      String resource, ParameterizedTypeReference<Page<T>> type, String projectId, String userId, Page<T> first) {
    int totalPages = first.meta == null ? 1 : first.meta.getTotalPages();
    List<CompletableFuture<Page<T>>> rest = IntStream.rangeClosed(2, totalPages)
        .mapToObj(page -> fetchPageAsync(resource, type, projectId, userId, page))
        .toList();

    return CompletableFuture.allOf(rest.toArray(CompletableFuture[]::new)).thenApply(done -> {
      List<T> items = new ArrayList<>(first.data == null ? List.of() : first.data);
      rest.forEach(page -> {
        List<T> data = page.join().data;
        if (data != null) items.addAll(data);
      });
      return items;
    });
  }

  private <T> CompletableFuture<Page<T>> fetchPageAsync(
      String resource, ParameterizedTypeReference<Page<T>> type, String projectId, String userId, int page) {
    UriComponentsBuilder uriBuilder =
        UriComponentsBuilder.fromHttpUrl(apiUrl)
            .path("/api/public/" + resource)
            .queryParam("userId", userId)
            .queryParam("page", page)
            .queryParam("limit", PAGE_SIZE);

    if (projectId != null && !projectId.isEmpty()) {
      uriBuilder.queryParam("projectId", projectId);
    }

    String url = uriBuilder.toUriString();

    return CompletableFuture.supplyAsync(
        () -> restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type).getBody(), pageFetcher);
  }

  @Override
  public Score createScore(ScoreCreateRequest request) {
    // Built as a plain map rather than serializing ScoreCreateRequest directly:
    // Langfuse's "value" field is numeric for NUMERIC/BOOLEAN scores but a
    // string for CATEGORICAL ones, and null fields should be omitted rather
    // than sent as null.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("traceId", request.getTraceId());
    if (request.getObservationId() != null && !request.getObservationId().isEmpty()) {
      body.put("observationId", request.getObservationId());
    }
    body.put("name", request.getName());
    if (request.getDataType() != null && !request.getDataType().isEmpty()) {
      body.put("dataType", request.getDataType());
    }
    if ("CATEGORICAL".equalsIgnoreCase(request.getDataType())) {
      body.put("value", request.getStringValue());
    } else {
      body.put("value", request.getValue());
    }
    if (request.getComment() != null && !request.getComment().isEmpty()) {
      body.put("comment", request.getComment());
    }

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body);

    String url = UriComponentsBuilder.fromHttpUrl(apiUrl).path("/api/public/scores").toUriString();

    ResponseEntity<Score> response = restTemplate.exchange(url, HttpMethod.POST, entity, Score.class);

    return response.getBody();
  }

  @Override
  public ScoresResponse getScores(
      String projectId, String traceId, String name, Integer page, Integer limit) {
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> entity = new HttpEntity<>(headers);

    UriComponentsBuilder uriBuilder =
        UriComponentsBuilder.fromHttpUrl(apiUrl).path("/api/public/scores");

    if (projectId != null && !projectId.isEmpty()) {
      uriBuilder.queryParam("projectId", projectId);
    }

    if (traceId != null && !traceId.isEmpty()) {
      uriBuilder.queryParam("traceId", traceId);
    }
    if (name != null && !name.isEmpty()) {
      uriBuilder.queryParam("name", name);
    }
    if (page != null) {
      uriBuilder.queryParam("page", page);
    }
    if (limit != null) {
      uriBuilder.queryParam("limit", limit);
    }

    ResponseEntity<ScoresResponse> response =
        restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.GET, entity, ScoresResponse.class);

    return response.getBody();
  }
}
