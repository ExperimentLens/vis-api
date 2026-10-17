package gr.imsi.athenarc.xtremexpvisapi.controller;

import gr.imsi.athenarc.xtremexpvisapi.service.observability.LlmClient;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Read-only view of the connections vis-api is configured with (from env / application.properties)
 * plus a connection test per service. Secrets are never returned, only whether they are set.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

  private final LlmClient llmClient;
  private final RestTemplate restTemplate;
  private final String mlflowUrl;
  private final String langfuseUrl;
  private final String ollamaUrl;

  public SettingsController(
      LlmClient llmClient,
      RestTemplateBuilder restTemplateBuilder,
      @Value("${mlflow.tracking.url}") String mlflowUrl,
      @Value("${langfuse.api.url}") String langfuseUrl,
      @Value("${ollama.api.url:http://localhost:11434}") String ollamaUrl) {
    this.llmClient = llmClient;
    this.restTemplate =
        restTemplateBuilder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(10)).build();
    this.mlflowUrl = stripSlash(mlflowUrl);
    this.langfuseUrl = stripSlash(langfuseUrl);
    this.ollamaUrl = stripSlash(ollamaUrl);
  }

  @GetMapping
  @Operation(summary = "Configured connections and the default judge model")
  public ResponseEntity<Map<String, Object>> getSettings() {
    Map<String, Object> connections = new LinkedHashMap<>();
    connections.put("mlflow", Map.of("url", mlflowUrl, "env", "MLFLOW_TRACKING_URL"));
    connections.put("langfuse", Map.of("url", langfuseUrl, "env", "LANGFUSE_API_URL"));
    connections.put("ollama", Map.of("url", ollamaUrl, "env", "OLLAMA_API_URL"));
    connections.put(
        "anthropic", Map.of("configured", llmClient.isAnthropicConfigured(), "env", "ANTHROPIC_API_KEY"));
    connections.put(
        "openai",
        Map.of(
            "configured", llmClient.isOpenaiConfigured(),
            "url", llmClient.getOpenaiUrl(),
            "env", "OPENAI_API_KEY"));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("connections", connections);
    body.put("defaultJudgeModel", llmClient.getDefaultJudgeModel());
    body.put("defaultJudgeModelEnv", "LLM_JUDGE_MODEL");
    return ResponseEntity.ok(body);
  }

  @GetMapping("/test/{service}")
  @Operation(summary = "Check that vis-api can reach a configured service")
  public ResponseEntity<Map<String, Object>> test(@PathVariable String service) {
    long start = System.currentTimeMillis();
    try {
      switch (service) {
        case "mlflow" -> restTemplate.getForObject(mlflowUrl + "/health", String.class);
        case "langfuse" -> restTemplate.getForObject(langfuseUrl + "/api/public/health", String.class);
        case "ollama" -> restTemplate.getForObject(ollamaUrl + "/api/tags", String.class);
        case "anthropic", "openai" -> llmClient.ping(service);
        default -> {
          return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Unknown service " + service));
        }
      }
      return ResponseEntity.ok(Map.of("ok", true, "latencyMs", System.currentTimeMillis() - start));
    } catch (Exception e) {
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      return ResponseEntity.ok(Map.of("ok", false, "error", message));
    }
  }

  private static String stripSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
