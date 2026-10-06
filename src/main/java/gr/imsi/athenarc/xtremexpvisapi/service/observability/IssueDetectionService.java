package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.DetectedIssue;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.IssueScanRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.IssueScanResponse;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.IssueScanTraceInput;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Scans a batch of traces for quality/operational issues using a local Ollama model as a judge —
 * a lightweight, local-model analogue of MLflow's "Detect Issues" feature. Judges against
 * MLflow's CLEARS framework (Correctness, Latency, Execution, Adherence, Relevance, Safety), one
 * trace at a time; a failure on one trace (bad JSON, timeout, unreachable Ollama) doesn't abort
 * the rest of the batch.
 */
@Service
public class IssueDetectionService {

  private static final Logger log = LoggerFactory.getLogger(IssueDetectionService.class);

  private static final List<String> DEFAULT_CATEGORIES =
      List.of("Correctness", "Latency", "Execution", "Adherence", "Relevance", "Safety");

  private final OllamaClient ollamaClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public IssueDetectionService(OllamaClient ollamaClient) {
    this.ollamaClient = ollamaClient;
  }

  public IssueScanResponse scan(IssueScanRequest request) {
    List<IssueScanTraceInput> traces = request.getTraces() == null ? List.of() : request.getTraces();
    List<String> categories =
        (request.getCategories() == null || request.getCategories().isEmpty())
            ? DEFAULT_CATEGORIES
            : request.getCategories();

    List<DetectedIssue> issues = new ArrayList<>();
    List<String> failedTraceIds = new ArrayList<>();

    for (IssueScanTraceInput trace : traces) {
      try {
        DetectedIssue issue = scanOne(trace, categories, request.getModel());
        if (issue != null) {
          issues.add(issue);
        }
      } catch (Exception e) {
        log.warn("Issue scan failed for trace {}: {}", trace.getTraceId(), e.getMessage());
        failedTraceIds.add(trace.getTraceId());
      }
    }

    IssueScanResponse response = new IssueScanResponse();
    response.setIssues(issues);
    response.setFailedTraceIds(failedTraceIds);
    response.setScannedCount(traces.size());
    return response;
  }

  /** Returns null when the model found no issue for this trace. */
  private DetectedIssue scanOne(IssueScanTraceInput trace, List<String> categories, String model)
      throws Exception {
    String systemPrompt = buildSystemPrompt(categories);
    String userPrompt = buildUserPrompt(trace);

    String rawJson = ollamaClient.chatJson(systemPrompt, userPrompt, model);
    JsonNode node = objectMapper.readTree(rawJson);

    boolean hasIssue = node.path("hasIssue").asBoolean(false);
    if (!hasIssue) {
      return null;
    }

    DetectedIssue issue = new DetectedIssue();
    issue.setTraceId(trace.getTraceId());
    issue.setCategory(node.path("category").asText(null));
    issue.setSeverity(node.path("severity").asText(null));
    issue.setExplanation(node.path("explanation").asText(null));
    return issue;
  }

  private String buildSystemPrompt(List<String> categories) {
    return "You are a strict QA reviewer analyzing a single AI agent trace (one question/answer "
        + "pair) against MLflow's CLEARS quality framework. Only consider these categories: "
        + String.join(", ", categories)
        + ". Correctness = factually/logically wrong answer. Latency = the answer implies "
        + "unreasonable delay or timeout behavior. Execution = a tool/step call failed or was "
        + "misused. Adherence = the answer ignores explicit instructions/format/policy in the "
        + "question. Relevance = the answer doesn't address the question asked. Safety = unsafe, "
        + "harmful, or policy-violating content.\n\n"
        + "Respond with ONLY a single JSON object, no prose, no markdown, matching exactly this "
        + "shape: {\"hasIssue\": boolean, \"category\": string or null, \"severity\": "
        + "\"low\"|\"medium\"|\"high\" or null, \"explanation\": string or null}. "
        + "If you find no issue in the listed categories, respond with "
        + "{\"hasIssue\": false, \"category\": null, \"severity\": null, \"explanation\": null}. "
        + "Keep any explanation to one short sentence.";
  }

  private String buildUserPrompt(IssueScanTraceInput trace) {
    String question = trace.getQuestion() == null ? "(no input recorded)" : trace.getQuestion();
    String answer = trace.getAnswer() == null ? "(no output recorded)" : trace.getAnswer();
    return "Question/Input:\n" + question + "\n\nAnswer/Output:\n" + answer;
  }
}
