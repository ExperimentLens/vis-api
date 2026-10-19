package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.Dataset;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetCreateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetDetail;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetItem;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetItemRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetRun;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Datasets in Langfuse, through its public REST API. Uses the same conventions as the Python
 * SDK's {@code experimentlens.datasets}: deterministic item ids per case, {@code expected} as the
 * expected output, and archiving instead of deleting so earlier versions can still be read.
 */
@Service
public class LangfuseDatasetService implements DatasetService {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};
  private static final int PAGE_SIZE = 100;
  // uuid.NAMESPACE_URL, which the SDK uses for uuid5 item ids.
  private static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");

  private final RestTemplate restTemplate;
  private final String apiUrl;

  public LangfuseDatasetService(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${langfuse.api.url}") String apiUrl,
      @Value("${langfuse.api.publicKey}") String publicKey,
      @Value("${langfuse.api.secretKey}") String secretKey) {
    this.apiUrl = apiUrl;
    this.restTemplate = restTemplateBuilder.basicAuthentication(publicKey, secretKey).build();
  }

  @Override
  public List<Dataset> listDatasets() {
    List<Dataset> datasets = new ArrayList<>();
    for (Map<String, Object> raw : fetchAllPages(uri("api", "public", "v2", "datasets"))) {
      datasets.add(toDataset(raw, countItems((String) raw.get("name"))));
    }
    return datasets;
  }

  @Override
  public Dataset createDataset(DatasetCreateRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", request.name());
    if (request.description() != null && !request.description().isBlank()) {
      body.put("description", request.description());
    }
    body.put("metadata", Map.of("created_by", "experimentlens"));
    return toDataset(send(HttpMethod.POST, uri("api", "public", "v2", "datasets").build(), body), 0);
  }

  @Override
  public DatasetDetail getDataset(String name) {
    Map<String, Object> raw = send(HttpMethod.GET, uri("api", "public", "v2", "datasets", name).build(), null);

    List<DatasetItem> items = new ArrayList<>();
    for (Map<String, Object> item :
        fetchAllPages(uri("api", "public", "dataset-items").queryParam("datasetName", name))) {
      items.add(toItem(item));
    }

    List<DatasetRun> runs = new ArrayList<>();
    for (Map<String, Object> run : fetchAllPages(uri("api", "public", "datasets", name, "runs"))) {
      runs.add(
          new DatasetRun(
              (String) run.get("id"),
              (String) run.get("name"),
              asMap(run.get("metadata")),
              (String) run.get("createdAt")));
    }

    return new DatasetDetail(toDataset(raw, items.size()), items, runs);
  }

  @Override
  public DatasetItem upsertItem(String datasetName, String itemId, DatasetItemRequest request) {
    Object input = request.input();
    String id = itemId;
    String caseId = request.caseId() == null ? null : request.caseId().trim();

    if (caseId != null && !caseId.isEmpty()) {
      // Same shape the SDK uploads: the case id lives in the input, the item id derives from it.
      Map<String, Object> withId = new LinkedHashMap<>();
      withId.put("id", caseId);
      if (input instanceof Map<?, ?> map) {
        map.forEach((k, v) -> withId.put(String.valueOf(k), v));
        withId.put("id", caseId);
      } else if (input != null) {
        withId.put("input", input);
      }
      input = withId;
      if (id == null) {
        id = sdkItemId(datasetName, caseId);
      }
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("datasetName", datasetName);
    if (id != null) body.put("id", id);
    body.put("input", input);
    body.put("expectedOutput", request.expectedOutput());
    if (request.metadata() != null) body.put("metadata", request.metadata());
    if (request.sourceTraceId() != null) body.put("sourceTraceId", request.sourceTraceId());
    if (request.sourceObservationId() != null) body.put("sourceObservationId", request.sourceObservationId());
    if (request.status() != null) body.put("status", request.status());

    return toItem(send(HttpMethod.POST, uri("api", "public", "dataset-items").build(), body));
  }

  @Override
  public List<String> datasetsContainingTrace(String traceId) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (Map<String, Object> item :
        fetchAllPages(uri("api", "public", "dataset-items").queryParam("sourceTraceId", traceId))) {
      if (!"ARCHIVED".equals(item.get("status")) && item.get("datasetName") != null) {
        names.add((String) item.get("datasetName"));
      }
    }
    return new ArrayList<>(names);
  }

  /** uuid5(NAMESPACE_URL, "experimentlens-dataset:{dataset}:{case}") — identical to the SDK. */
  static String sdkItemId(String datasetName, String caseId) {
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      ByteBuffer ns = ByteBuffer.allocate(16);
      ns.putLong(NAMESPACE_URL.getMostSignificantBits()).putLong(NAMESPACE_URL.getLeastSignificantBits());
      sha1.update(ns.array());
      sha1.update(("experimentlens-dataset:" + datasetName + ":" + caseId).getBytes(StandardCharsets.UTF_8));
      byte[] hash = sha1.digest();
      hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
      hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
      ByteBuffer bb = ByteBuffer.wrap(hash, 0, 16);
      return new UUID(bb.getLong(), bb.getLong()).toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private int countItems(String datasetName) {
    URI uri = uri("api", "public", "dataset-items")
        .queryParam("datasetName", datasetName)
        .queryParam("limit", 1)
        .build();
    Map<String, Object> meta = asMap(send(HttpMethod.GET, uri, null).get("meta"));
    Object total = meta.get("totalItems");
    return total instanceof Number n ? n.intValue() : 0;
  }

  private List<Map<String, Object>> fetchAllPages(UriBuilderLike base) {
    List<Map<String, Object>> all = new ArrayList<>();
    int page = 1;
    while (true) {
      URI uri = base.queryParam("page", page).queryParam("limit", PAGE_SIZE).build();
      Map<String, Object> response = send(HttpMethod.GET, uri, null);
      Object data = response.get("data");
      if (data instanceof List<?> list) {
        list.forEach(entry -> all.add(asMap(entry)));
      }
      Object totalPages = asMap(response.get("meta")).get("totalPages");
      if (!(totalPages instanceof Number n) || page >= n.intValue()) {
        return all;
      }
      page++;
    }
  }

  private Map<String, Object> send(HttpMethod method, URI uri, Object body) {
    Map<String, Object> response =
        restTemplate.exchange(uri, method, body == null ? null : new HttpEntity<>(body), JSON).getBody();
    return response == null ? Map.of() : response;
  }

  private UriBuilderLike uri(String... segments) {
    return new UriBuilderLike(UriComponentsBuilder.fromUriString(apiUrl).pathSegment(segments));
  }

  /** Copies the builder per call, so paging one base URI doesn't pile up query params. */
  private static final class UriBuilderLike {
    private final UriComponentsBuilder builder;

    UriBuilderLike(UriComponentsBuilder builder) {
      this.builder = builder;
    }

    UriBuilderLike queryParam(String name, Object value) {
      UriComponentsBuilder copy = builder.cloneBuilder();
      copy.replaceQueryParam(name, value);
      return new UriBuilderLike(copy);
    }

    URI build() {
      return builder.encode().build().toUri();
    }
  }

  private static Dataset toDataset(Map<String, Object> raw, Integer itemCount) {
    return new Dataset(
        (String) raw.get("name"),
        (String) raw.get("description"),
        asMap(raw.get("metadata")),
        (String) raw.get("createdAt"),
        (String) raw.get("updatedAt"),
        itemCount);
  }

  private static DatasetItem toItem(Map<String, Object> raw) {
    return new DatasetItem(
        (String) raw.get("id"),
        raw.get("input"),
        raw.get("expectedOutput"),
        asMap(raw.get("metadata")),
        (String) raw.get("sourceTraceId"),
        (String) raw.get("sourceObservationId"),
        (String) raw.get("status"),
        (String) raw.get("createdAt"),
        (String) raw.get("updatedAt"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }
}
