package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.Turn;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationEvalServiceTest {

  // Same expected text as the SDK's test_transcript_numbers_turns_and_names_speakers, so a score
  // means the same whether the conversation was judged from code or from the UI.
  @Test
  void transcriptMatchesTheSdk() {
    List<Turn> turns = List.of(
        new Turn(1, "Hi, I'm Ana. My booking is LH123.", "Hello Ana, how can I help with LH123?", "t", "o1"),
        new Turn(2, "Can I move it to Friday?", "Yes, LH123 is now on Friday.", "t", "o2"));

    assertEquals(
        "Turn 1\nUser: Hi, I'm Ana. My booking is LH123.\nAssistant: Hello Ana, how can I help with LH123?\n\n"
            + "Turn 2\nUser: Can I move it to Friday?\nAssistant: Yes, LH123 is now on Friday.",
        ConversationEvalService.transcript(turns));
  }
}
