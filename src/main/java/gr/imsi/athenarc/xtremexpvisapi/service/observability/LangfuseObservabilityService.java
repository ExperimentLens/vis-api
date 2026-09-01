package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Score;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoreCreateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoresResponse;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TraceDetail;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TracesResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Service("langfuse")
public class LangfuseObservabilityService implements ObservabilityService {

  private final RestTemplate restTemplate;
  private final String apiUrl;

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
            .queryParam("projectId", projectId)
            .queryParam("fields", "observations,scores");

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
        UriComponentsBuilder.fromHttpUrl(apiUrl).path("/api/public/scores").queryParam("projectId", projectId);

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
