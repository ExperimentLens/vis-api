package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

/** One trace's condensed input/output, as sent from the frontend for a local-LLM issue scan. */
public class IssueScanTraceInput {
  private String traceId;
  private String question;
  private String answer;

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public String getAnswer() {
    return answer;
  }

  public void setAnswer(String answer) {
    this.answer = answer;
  }
}
