package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Routes LLM calls (judge, replay) to a provider by the model's prefix, using the same
 * {@code <provider>/<model>} naming as the SDK (litellm): {@code anthropic/claude-sonnet-5-5},
 * {@code openai/gpt-4o-mini}, {@code ollama/llama3.2}. A bare name is treated as an Ollama tag.
 * {@code openai/} works with any OpenAI-compatible server via {@code openai.api.url}.
 */
@Component
public class LlmClient {

  private static final String ANTHROPIC_VERSION = "2023-06-01";

  private final OllamaClient ollamaClient;
  private final RestTemplate restTemplate;
  private final String defaultJudgeModel;
  private final String anthropicUrl;
  private final String anthropicKey;
  private final String openaiUrl;
  private final String openaiKey;

  public LlmClient(
      OllamaClient ollamaClient,
      RestTemplateBuilder restTemplateBuilder,
      @Value("${llm.judge.model:}") String judgeModel,
      @Value("${ollama.api.model:llama3.2}") String ollamaModel,
      @Value("${anthropic.api.url:https://api.anthropic.com}") String anthropicUrl,
      @Value("${anthropic.api.key:${ANTHROPIC_API_KEY:}}") String anthropicKey,
      @Value("${openai.api.url:https://api.openai.com/v1}") String openaiUrl,
      @Value("${openai.api.key:${OPENAI_API_KEY:}}") String openaiKey) {
    this.ollamaClient = ollamaClient;
    this.restTemplate =
        restTemplateBuilder
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(120))
            .build();
    this.defaultJudgeModel = judgeModel.isBlank() ? "ollama/" + ollamaModel : judgeModel;
    this.anthropicUrl = stripSlash(anthropicUrl);
    this.anthropicKey = anthropicKey;
    this.openaiUrl = stripSlash(openaiUrl);
    this.openaiKey = openaiKey;
  }

  public String getDefaultJudgeModel() {
    return defaultJudgeModel;
  }

  public boolean isAnthropicConfigured() {
    return !anthropicKey.isBlank();
  }

  public boolean isOpenaiConfigured() {
    return !openaiKey.isBlank();
  }

  public String getOpenaiUrl() {
    return openaiUrl;
  }

  /** Plain completion, used for counterfactual replay. */
  public String generate(String prompt, String model) {
    String resolved = model == null || model.isBlank() ? defaultJudgeModel : model;
    return switch (provider(resolved)) {
      case "anthropic" -> anthropic(null, prompt, bareName(resolved));
      case "openai" -> openai(null, prompt, bareName(resolved), false);
      default -> ollamaClient.generate(prompt, resolved);
    };
  }

  /** System + user call whose reply must be one JSON object (judge-style calls). */
  public String chatJson(String systemPrompt, String userPrompt, String model) {
    String resolved = model == null || model.isBlank() ? defaultJudgeModel : model;
    return switch (provider(resolved)) {
      case "anthropic" -> extractJsonObject(anthropic(systemPrompt, userPrompt, bareName(resolved)));
      case "openai" -> extractJsonObject(openai(systemPrompt, userPrompt, bareName(resolved), true));
      default -> ollamaClient.chatJson(systemPrompt, userPrompt, resolved);
    };
  }

  /** Lists models to prove the key/URL work; throws on failure. */
  public void ping(String provider) {
    switch (provider) {
      case "anthropic" -> {
        requireKey(anthropicKey, "ANTHROPIC_API_KEY");
        restTemplate.exchange(
            anthropicUrl + "/v1/models", HttpMethod.GET, new HttpEntity<>(anthropicHeaders()), Map.class);
      }
      case "openai" -> {
        requireKey(openaiKey, "OPENAI_API_KEY");
        restTemplate.exchange(
            openaiUrl + "/models", HttpMethod.GET, new HttpEntity<>(openaiHeaders()), Map.class);
      }
      default -> throw new IllegalArgumentException("Unknown provider " + provider);
    }
  }

  private String anthropic(String system, String user, String model) {
    requireKey(anthropicKey, "ANTHROPIC_API_KEY");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("max_tokens", 1024);
    if (system != null) {
      body.put("system", system);
    }
    body.put("messages", List.of(Map.of("role", "user", "content", user)));

    @SuppressWarnings("unchecked")
    Map<String, Object> response =
        restTemplate.postForObject(
            anthropicUrl + "/v1/messages", new HttpEntity<>(body, anthropicHeaders()), Map.class);

    Object content = response == null ? null : response.get("content");
    if (content instanceof List<?> blocks) {
      StringBuilder text = new StringBuilder();
      for (Object block : blocks) {
        if (block instanceof Map<?, ?> b && "text".equals(b.get("type"))) {
          text.append(b.get("text"));
        }
      }
      return text.toString();
    }
    throw new IllegalStateException("Anthropic returned no content for model " + model);
  }

  private String openai(String system, String user, String model, boolean json) {
    requireKey(openaiKey, "OPENAI_API_KEY");
    List<Map<String, String>> messages =
        system == null
            ? List.of(Map.of("role", "user", "content", user))
            : List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("messages", messages);
    if (json) {
      body.put("response_format", Map.of("type", "json_object"));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> response =
        restTemplate.postForObject(
            openaiUrl + "/chat/completions", new HttpEntity<>(body, openaiHeaders()), Map.class);

    if (response != null
        && response.get("choices") instanceof List<?> choices
        && !choices.isEmpty()
        && choices.get(0) instanceof Map<?, ?> choice
        && choice.get("message") instanceof Map<?, ?> message
        && message.get("content") != null) {
      return String.valueOf(message.get("content"));
    }
    throw new IllegalStateException("OpenAI-compatible server returned no message for model " + model);
  }

  private HttpHeaders anthropicHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("x-api-key", anthropicKey);
    headers.set("anthropic-version", ANTHROPIC_VERSION);
    return headers;
  }

  private HttpHeaders openaiHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(openaiKey);
    return headers;
  }

  static String provider(String model) {
    int slash = model.indexOf('/');
    return slash < 0 ? "ollama" : model.substring(0, slash).toLowerCase();
  }

  private static String bareName(String model) {
    return model.substring(model.indexOf('/') + 1);
  }

  // Hosted models without a strict JSON mode sometimes wrap the object in prose or a code fence.
  static String extractJsonObject(String text) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    return start >= 0 && end > start ? text.substring(start, end + 1) : text;
  }

  private static void requireKey(String key, String envName) {
    if (key == null || key.isBlank()) {
      throw new IllegalStateException(envName + " is not set on vis-api");
    }
  }

  private static String stripSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
