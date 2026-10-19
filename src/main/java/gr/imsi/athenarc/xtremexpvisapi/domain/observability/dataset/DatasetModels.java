package gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset;

import java.util.List;
import java.util.Map;

/**
 * Backend-neutral shapes for test datasets. The trace store (Langfuse today) is mapped onto these,
 * so the frontend never sees the store's own payloads.
 */
public final class DatasetModels {

  private DatasetModels() {}

  public record Dataset(
      String name,
      String description,
      Map<String, Object> metadata,
      String createdAt,
      String updatedAt,
      Integer itemCount) {}

  /** One test case. {@code input} and {@code expectedOutput} are free-form JSON. */
  public record DatasetItem(
      String id,
      Object input,
      Object expectedOutput,
      Map<String, Object> metadata,
      String sourceTraceId,
      String sourceObservationId,
      String status,
      String createdAt,
      String updatedAt) {}

  /** A named run over the dataset, e.g. one experiment variant (written by the SDK). */
  public record DatasetRun(
      String id, String name, Map<String, Object> metadata, String createdAt) {}

  public record DatasetDetail(Dataset dataset, List<DatasetItem> items, List<DatasetRun> runs) {}

  public record DatasetCreateRequest(String name, String description) {}

  /**
   * Adds or updates one item. With {@code caseId} set, the item gets the same id the SDK's
   * {@code upload_dataset} would give that case, and {@code caseId} is stored as {@code input.id},
   * so the SDK and the UI edit the same item instead of duplicating it.
   */
  public record DatasetItemRequest(
      String caseId,
      Object input,
      Object expectedOutput,
      Map<String, Object> metadata,
      String sourceTraceId,
      String sourceObservationId,
      String status) {}
}
