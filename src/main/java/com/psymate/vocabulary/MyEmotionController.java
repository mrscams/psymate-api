package com.psymate.vocabulary;

import com.psymate.vocabulary.EmotionService.MergeView;
import com.psymate.vocabulary.api.EmotionView;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Émotions personnalisées du praticien courant. Sera protégé par la sécurité de M0-04. */
@RestController
@RequestMapping("/api/me/emotions")
class MyEmotionController {

  record CreateEmotionRequest(String labelFr, String family) {}

  record RenameEmotionRequest(String labelFr) {}

  record MergeEmotionRequest(String targetCode) {}

  private final EmotionService emotions;

  MyEmotionController(EmotionService emotions) {
    this.emotions = emotions;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  EmotionView create(@RequestBody CreateEmotionRequest request) {
    return emotions.create(request.labelFr(), request.family());
  }

  @PatchMapping("/{code}")
  EmotionView rename(@PathVariable String code, @RequestBody RenameEmotionRequest request) {
    return emotions.rename(code, request.labelFr());
  }

  @PostMapping("/{code}/merge")
  MergeView merge(@PathVariable String code, @RequestBody MergeEmotionRequest request) {
    return emotions.merge(code, request.targetCode());
  }

  @PostMapping("/merges/{id}/undo")
  MergeView undo(@PathVariable UUID id) {
    return emotions.undo(id);
  }
}
