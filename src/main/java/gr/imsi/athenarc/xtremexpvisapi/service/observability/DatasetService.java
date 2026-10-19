package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.Dataset;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetCreateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetDetail;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetItem;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetItemRequest;
import java.util.List;

/** Versioned test datasets, stored next to the traces they were built from. */
public interface DatasetService {

  List<Dataset> listDatasets();

  Dataset createDataset(DatasetCreateRequest request);

  /** The dataset with its items (active and archived) and the runs recorded against it. */
  DatasetDetail getDataset(String name);

  /** Creates the item, or updates it when {@code itemId} is set. */
  DatasetItem upsertItem(String datasetName, String itemId, DatasetItemRequest request);

  /** Names of the datasets that already hold an item built from this trace. */
  List<String> datasetsContainingTrace(String traceId);
}
