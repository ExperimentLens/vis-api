package gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation;

import java.util.List;

/** A run's multi-turn conversation and its conversation-level evaluation. */
public final class ConversationModels {

  private ConversationModels() {}

  public record Turn(int index, String user, String assistant, String traceId, String observationId) {}

  /** One conversation metric as shown in the UI; {@code needsRole} means it reads the role text. */
  public record MetricInfo(String name, String description, boolean needsRole, double threshold) {}

  /** The latest result for one metric: score 0..1 (higher is better) and the judge's reason. */
  public record MetricResult(String metric, Double score, Boolean passed, Double threshold, String reason) {}

  /**
   * @param traceId the trace that holds the turns (scores are written there)
   * @param suggestedRole the run's system prompt when the trace carries one, to prefill role_adherence
   * @param results earlier results for the conversation metrics, empty when none were run
   */
  public record Conversation(
      String sessionId,
      String experimentId,
      String traceId,
      String source,
      String suggestedRole,
      List<Turn> turns,
      List<MetricResult> results,
      List<MetricInfo> metrics) {}

  public record EvaluateRequest(List<String> metrics, String model, String role) {}

  public record EvaluateResponse(
      String model, List<MetricResult> results, List<String> failed, boolean savedToRun) {}
}
