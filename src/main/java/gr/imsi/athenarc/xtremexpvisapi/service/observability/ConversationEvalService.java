package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Observation;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Score;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ScoreCreateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Trace;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TraceDetail;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TracesResponse;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.Conversation;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.EvaluateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.EvaluateResponse;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.MetricInfo;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.MetricResult;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.Turn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Conversation-level evaluation of one run (a Langfuse session). Reads the conversation's turns,
 * judges the whole transcript on the same rubrics as the SDK's {@code CONVERSATION_METRICS}, and
 * stores the results where the SDK's {@code ctx.evaluate_conversation()} would: an MLflow
 * {@code eval_<name>} metric on the run and a {@code judge_<name>} verdict on the trace.
 */
@Service
public class ConversationEvalService {

  private static final Logger log = LoggerFactory.getLogger(ConversationEvalService.class);

  /** The SDK's RUBRIC_PROMPT, so a score means the same whether it came from code or the UI. */
  private static final String RUBRIC_PROMPT =
      """
      You are an evaluation judge. Rate the case below on one criterion.

      %s

      Criterion: %s
      Scale:
      %s

      Respond with ONLY a JSON object, no other text: {"score": <integer 1-5>, "reason": "one sentence explanation"}""";

  private static final String SYSTEM_PROMPT =
      "You are a strict evaluation judge. Reply with one JSON object only.";

  private record Rubric(MetricInfo info, String criterion, String scale) {}

  // Same criteria and scales as experimentlens.evals.CONVERSATION_METRICS: change both together.
  private static final Map<String, Rubric> RUBRICS = new LinkedHashMap<>();

  static {
    add("conversation_relevance", "Each reply fits what the user just said, given the conversation so far.", false,
        "Is every assistant reply relevant to the user's latest message, given everything said before it?",
        "5 = every reply fits the conversation\n3 = some replies drift or ignore what the user just said\n1 = most replies are off topic");
    add("knowledge_retention", "The assistant remembers details from earlier turns instead of re-asking or contradicting them.", false,
        "Does the assistant remember what the user told it in earlier turns, without asking for it again or contradicting it?",
        "5 = uses earlier details correctly throughout\n3 = forgets or re-asks for some details\n1 = repeatedly forgets or contradicts what the user said");
    add("conversation_completeness", "By the end, everything the user asked for was handled.", false,
        "By the end of the conversation, has the assistant done everything the user asked for?",
        "5 = every request was handled\n3 = some requests were left unanswered\n1 = the user's main request was not handled");
    add("role_adherence", "The assistant stays within its role (scope, tone, rules) in every turn.", true,
        "Does the assistant stay within the role described above in every turn (its scope, tone and rules)?",
        "5 = stays in role throughout\n3 = slips out of role once or twice\n1 = ignores the role");
  }

  private static void add(String name, String description, boolean needsRole, String criterion, String scale) {
    RUBRICS.put(name, new Rubric(new MetricInfo(name, description, needsRole, 0.5), criterion, scale));
  }

  /** Run tag the SDK writes with the role text role_adherence was judged against. */
  private static final String ROLE_TAG = "experimentlens.conversation_role";

  private record RunData(Map<String, Double> metrics, Map<String, String> tags) {}

  private static final Pattern DIGIT = Pattern.compile("\\b([1-5])\\b");

  private final ObservabilityService observabilityService;
  private final LlmClient llmClient;
  private final RestTemplate restTemplate;
  private final String mlflowUrl;
  private final String mlflowToken;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ConversationEvalService(
      ObservabilityServiceFactory observabilityServiceFactory,
      LlmClient llmClient,
      RestTemplate restTemplate,
      @Value("${mlflow.tracking.url}") String mlflowUrl,
      @Value("${mlflow.tracking.token:}") String mlflowToken) {
    this.observabilityService = observabilityServiceFactory.getObservabilityService();
    this.llmClient = llmClient;
    this.restTemplate = restTemplate;
    this.mlflowUrl = mlflowUrl.endsWith("/") ? mlflowUrl.substring(0, mlflowUrl.length() - 1) : mlflowUrl;
    this.mlflowToken = mlflowToken;
  }

  public List<MetricInfo> metrics() {
    return RUBRICS.values().stream().map(Rubric::info).toList();
  }

  /** The session's conversation, or one with no turns when it isn't a multi-turn run. */
  public Conversation getConversation(String sessionId) {
    List<TraceDetail> traces = loadTraces(sessionId);
    List<Turn> turns = new ArrayList<>();
    String source = "none";
    TraceDetail primary = traces.isEmpty() ? null : traces.get(0);

    // SDK shape: one trace per run, one "turn N" span per turn (metadata.turn = N).
    for (TraceDetail trace : traces) {
      List<Turn> spanTurns = turnSpans(trace);
      if (spanTurns.size() > turns.size()) {
        turns = spanTurns;
        primary = trace;
        source = "turn-spans";
      }
    }
    // Other tools' shape: one trace per turn in the session.
    if (turns.isEmpty() && traces.size() > 1) {
      for (TraceDetail trace : traces) {
        turns.add(new Turn(turns.size() + 1, text(trace.getInput(), "question", "message", "input"),
            text(trace.getOutput(), "answer", "output"), trace.getId(), null));
      }
      primary = traces.get(traces.size() - 1);
      source = "traces";
    }

    String experimentId = primary == null ? null : experimentIdOf(primary);
    RunData run = runData(sessionId);
    String role = primary == null ? null : stringMeta(primary, "system_prompt");
    if (role == null) role = run.tags().get(ROLE_TAG);
    List<MetricResult> results = primary == null ? List.of() : existingResults(primary, run.metrics());

    return new Conversation(sessionId, experimentId, primary == null ? null : primary.getId(), source, role,
        turns, results, metrics());
  }

  public EvaluateResponse evaluate(String sessionId, EvaluateRequest request) {
    Conversation conversation = getConversation(sessionId);
    if (conversation.turns().isEmpty()) {
      throw new IllegalArgumentException("This run has no conversation turns to evaluate.");
    }
    List<String> names = request.metrics() == null || request.metrics().isEmpty()
        ? new ArrayList<>(RUBRICS.keySet())
        : request.metrics();
    String role = request.role() == null || request.role().isBlank() ? null : request.role().trim();
    for (String name : names) {
      Rubric rubric = RUBRICS.get(name);
      if (rubric == null) {
        throw new IllegalArgumentException("Unknown conversation metric: " + name);
      }
      if (rubric.info().needsRole() && role == null) {
        throw new IllegalArgumentException(name + " needs the assistant's role (usually its system prompt).");
      }
    }

    String model = request.model() == null || request.model().isBlank()
        ? llmClient.getDefaultJudgeModel()
        : request.model().trim();
    String transcript = transcript(conversation.turns());

    List<MetricResult> results = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    for (String name : names) {
      Rubric rubric = RUBRICS.get(name);
      try {
        MetricResult result = judge(rubric, transcript, role, model);
        results.add(result);
        saveVerdict(conversation.traceId(), name, result, model);
      } catch (Exception e) {
        log.warn("Conversation eval {} failed for session {}: {}", name, sessionId, e.getMessage());
        failed.add(name);
      }
    }

    boolean saved = !results.isEmpty() && saveToRun(conversation.experimentId(), sessionId, results, role);
    return new EvaluateResponse(model, results, failed, saved);
  }

  /** "Turn 1\nUser: ...\nAssistant: ..." blocks, exactly as the SDK formats them. */
  static String transcript(List<Turn> turns) {
    List<String> blocks = new ArrayList<>();
    for (Turn turn : turns) {
      blocks.add("Turn " + turn.index() + "\nUser: " + nullToEmpty(turn.user()) + "\nAssistant: "
          + nullToEmpty(turn.assistant()));
    }
    return String.join("\n\n", blocks);
  }

  private MetricResult judge(Rubric rubric, String transcript, String role, String model) throws Exception {
    String inputs = "Conversation: " + transcript + (rubric.info().needsRole() ? "\nRole: " + role : "");
    String prompt = String.format(RUBRIC_PROMPT, inputs, rubric.criterion(), rubric.scale());
    String reply = llmClient.chatJson(SYSTEM_PROMPT, prompt, model);

    Integer raw = null;
    String reason = reply;
    try {
      JsonNode node = objectMapper.readTree(reply);
      raw = (int) Math.round(node.path("score").asDouble(Double.NaN));
      reason = node.path("reason").asText(reply);
    } catch (Exception ignored) {
      Matcher digit = DIGIT.matcher(reply);
      if (digit.find()) raw = Integer.parseInt(digit.group(1));
    }
    if (raw == null || raw < 1) {
      throw new IllegalStateException("Unreadable judge output: " + reply);
    }
    double score = (Math.min(raw, 5) - 1) / 4.0;
    double threshold = rubric.info().threshold();
    return new MetricResult(rubric.info().name(), score, score >= threshold, threshold, reason);
  }

  private void saveVerdict(String traceId, String metric, MetricResult result, String model) {
    if (traceId == null) return;
    ScoreCreateRequest score = new ScoreCreateRequest();
    score.setTraceId(traceId);
    score.setName("judge_" + metric);
    score.setDataType("BOOLEAN");
    score.setValue(Boolean.TRUE.equals(result.passed()) ? 1.0 : 0.0);
    score.setComment(String.format("%s (score %.2f, judged by %s in ExperimentLens)", result.reason(), result.score(), model));
    observabilityService.createScore(score);
  }

  /** Logs eval_<name> on the MLflow run and declares the metric, like the SDK. False if MLflow refused. */
  private boolean saveToRun(String experimentId, String runId, List<MetricResult> results, String role) {
    try {
      long now = System.currentTimeMillis();
      if (role != null) {
        mlflow("/api/2.0/mlflow/runs/set-tag", Map.of(
            "run_id", runId, "key", ROLE_TAG, "value", role.length() > 5000 ? role.substring(0, 5000) : role));
      }
      for (MetricResult result : results) {
        mlflow("/api/2.0/mlflow/runs/log-metric", Map.of(
            "run_id", runId, "key", "eval_" + result.metric(), "value", result.score(), "timestamp", now, "step", 0));
        if (experimentId != null) {
          String definition = String.format(
              "{\"pass\": %s, \"direction\": \"maximize\", \"kind\": \"quality\"}", result.threshold());
          mlflow("/api/2.0/mlflow/experiments/set-experiment-tag", Map.of(
              "experiment_id", experimentId, "key", "experimentlens.metric.eval_" + result.metric(), "value", definition));
        }
      }
      return true;
    } catch (Exception e) {
      log.warn("Could not log conversation eval metrics to MLflow run {}: {}", runId, e.getMessage());
      return false;
    }
  }

  private void mlflow(String path, Map<String, Object> body) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (mlflowToken != null && !mlflowToken.isBlank()) headers.setBearerAuth(mlflowToken);
    // Sent as a ready JSON string: a streamed (chunked) body reaches MLflow empty ("Missing run_id").
    String json = objectMapper.writeValueAsString(body);
    restTemplate.exchange(mlflowUrl + path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
  }

  /** Latest score per conversation metric: the number from the MLflow run, the reason from the trace. */
  private List<MetricResult> existingResults(TraceDetail trace, Map<String, Double> runMetrics) {
    Map<String, Score> verdicts = new LinkedHashMap<>();
    if (trace.getScores() != null) {
      trace.getScores().stream()
          .filter(s -> s.getName() != null && s.getName().startsWith("judge_"))
          .sorted(Comparator.comparing(Score::getTimestamp, Comparator.nullsFirst(Comparator.naturalOrder())))
          .forEach(s -> verdicts.put(s.getName().substring("judge_".length()), s));
    }

    List<MetricResult> results = new ArrayList<>();
    for (Rubric rubric : RUBRICS.values()) {
      String name = rubric.info().name();
      Double score = runMetrics.get("eval_" + name);
      Score verdict = verdicts.get(name);
      if (score == null && verdict == null) continue;
      Boolean passed = score != null ? score >= rubric.info().threshold()
          : verdict.getValue() != null ? verdict.getValue() >= 0.5 : null;
      results.add(new MetricResult(name, score, passed, rubric.info().threshold(),
          verdict == null ? null : verdict.getComment()));
    }
    return results;
  }

  @SuppressWarnings("unchecked")
  private RunData runData(String runId) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    Map<String, String> tags = new LinkedHashMap<>();
    try {
      HttpHeaders headers = new HttpHeaders();
      if (mlflowToken != null && !mlflowToken.isBlank()) headers.setBearerAuth(mlflowToken);
      Map<String, Object> body = restTemplate.exchange(mlflowUrl + "/api/2.0/mlflow/runs/get?run_id=" + runId,
          HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
      Map<String, Object> run = (Map<String, Object>) body.get("run");
      Map<String, Object> data = (Map<String, Object>) run.get("data");
      for (Map<String, Object> metric : (List<Map<String, Object>>) data.getOrDefault("metrics", List.of())) {
        metrics.put((String) metric.get("key"), ((Number) metric.get("value")).doubleValue());
      }
      for (Map<String, Object> tag : (List<Map<String, Object>>) data.getOrDefault("tags", List.of())) {
        tags.put((String) tag.get("key"), (String) tag.get("value"));
      }
    } catch (Exception e) {
      log.debug("No MLflow run {} for conversation scores: {}", runId, e.getMessage());
    }
    return new RunData(metrics, tags);
  }

  private List<TraceDetail> loadTraces(String sessionId) {
    TracesResponse response = observabilityService.getTraces(null, sessionId, null);
    List<TraceDetail> traces = new ArrayList<>();
    if (response == null || response.getData() == null) return traces;
    response.getData().stream()
        .sorted(Comparator.comparing(Trace::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
        .forEach(t -> traces.add(observabilityService.getTrace(t.getId())));
    return traces;
  }

  private static List<Turn> turnSpans(TraceDetail trace) {
    List<Turn> turns = new ArrayList<>();
    if (trace.getObservations() == null) return turns;
    for (Observation o : trace.getObservations()) {
      if (o.getMetadata() instanceof Map<?, ?> meta && meta.get("turn") instanceof Number n) {
        turns.add(new Turn(n.intValue(), text(o.getInput(), "message", "question", "input"),
            text(o.getOutput(), "answer", "output"), trace.getId(), o.getId()));
      }
    }
    turns.sort(Comparator.comparingInt(Turn::index));
    return turns;
  }

  /** The first of {@code keys} found in a JSON object, the value itself if it is text, else its JSON. */
  private static String text(Object value, String... keys) {
    if (value == null) return null;
    if (value instanceof String s) return s;
    if (value instanceof Map<?, ?> map) {
      for (String key : keys) {
        if (map.get(key) instanceof String s) return s;
      }
    }
    try {
      return new ObjectMapper().writeValueAsString(value);
    } catch (Exception e) {
      return String.valueOf(value);
    }
  }

  private static String experimentIdOf(TraceDetail trace) {
    String fromMeta = stringMeta(trace, "mlflow_experiment_id");
    return fromMeta != null ? fromMeta : trace.getUserId();
  }

  private static String stringMeta(TraceDetail trace, String key) {
    Object value = trace.getMetadata() == null ? null : trace.getMetadata().get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
