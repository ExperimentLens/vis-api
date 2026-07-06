package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Crude lexical diffing between an original and a counterfactual LLM output. */
public final class DiffUtils {

  private DiffUtils() {}

  /** Jaccard distance over whitespace tokens: 0 = identical, 1 = fully different. */
  public static double tokenDiffRatio(String a, String b) {
    Set<String> tokensA = new HashSet<>(Arrays.asList(a.split("\\s+")));
    Set<String> tokensB = new HashSet<>(Arrays.asList(b.split("\\s+")));
    Set<String> union = new HashSet<>(tokensA);
    union.addAll(tokensB);
    if (union.isEmpty()) {
      return 0.0;
    }
    Set<String> intersection = new HashSet<>(tokensA);
    intersection.retainAll(tokensB);
    return 1.0 - ((double) intersection.size() / union.size());
  }
}
