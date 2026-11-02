package com.psymate.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.vocabulary.MergeResolver.EmotionEdge;
import com.psymate.vocabulary.api.EmotionResolution;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MergeResolverTest {

  @Test
  void anActiveMergeResolvesTheSourceToItsTarget() {
    List<EmotionResolution> r =
        MergeResolver.emotions(List.of(new EmotionEdge("c_a", "sadness", false)), "c_a");

    assertThat(r)
        .containsExactly(new EmotionResolution("c_a", "sadness", Set.of("c_a", "sadness")));
  }

  @Test
  void anUndoneMergeResolvesTheSourceToItselfAndKeepsItsHistory() {
    List<EmotionResolution> r =
        MergeResolver.emotions(List.of(new EmotionEdge("c_a", "sadness", true)), "c_a");

    assertThat(r).containsExactly(new EmotionResolution("c_a", "c_a", Set.of("c_a", "sadness")));
  }

  @Test
  void chainedMergesResolveToTheLastActiveTarget() {
    List<EmotionEdge> merges =
        List.of(new EmotionEdge("c_a", "c_b", false), new EmotionEdge("c_b", "joy", false));

    assertThat(MergeResolver.emotions(merges, "c_b"))
        .containsExactly(
            new EmotionResolution("c_a", "joy", Set.of("c_a", "c_b", "joy")),
            new EmotionResolution("c_b", "joy", Set.of("c_b", "joy")));
  }

  @Test
  void undoingTheFirstLinkOfAChainBringsItsSourceBack() {
    List<EmotionEdge> merges =
        List.of(new EmotionEdge("c_a", "c_b", true), new EmotionEdge("c_b", "joy", false));

    assertThat(MergeResolver.emotions(merges, "c_a"))
        .containsExactly(new EmotionResolution("c_a", "c_a", Set.of("c_a", "c_b", "joy")));
  }

  @Test
  void onlySourcesWhoseHistoryContainsTheCodeAreReturned() {
    List<EmotionEdge> merges =
        List.of(new EmotionEdge("c_a", "sadness", false), new EmotionEdge("c_x", "joy", false));

    assertThat(MergeResolver.emotions(merges, "c_x"))
        .extracting(EmotionResolution::sourceCode)
        .containsExactly("c_x");
    assertThat(MergeResolver.emotions(merges, "anger")).isEmpty();
  }

  @Test
  void aReMergeAfterAnUndoFollowsTheNewTarget() {
    List<EmotionEdge> merges =
        List.of(new EmotionEdge("c_a", "sadness", true), new EmotionEdge("c_a", "joy", false));

    assertThat(MergeResolver.emotions(merges, "c_a"))
        .containsExactly(new EmotionResolution("c_a", "joy", Set.of("c_a", "sadness", "joy")));
  }

  @Test
  void aCycleStopsInsteadOfLooping() {
    List<EmotionEdge> merges =
        List.of(new EmotionEdge("c_a", "c_b", false), new EmotionEdge("c_b", "c_a", false));

    assertThat(MergeResolver.emotions(merges, "c_a")).hasSize(2);
  }

  @Test
  void tagChainsResolveToTheirLastTarget() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();

    assertThat(MergeResolver.tags(Map.of(a, b, b, c))).isEqualTo(Map.of(a, c, b, c));
    assertThat(MergeResolver.tags(Map.of())).isEmpty();
  }
}
