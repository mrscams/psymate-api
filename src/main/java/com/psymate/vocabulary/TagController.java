package com.psymate.vocabulary;

import com.psymate.vocabulary.api.TagView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tags du praticien courant. Sera protégé par la sécurité de M0-04 (pas encore présente). */
@RestController
@RequestMapping("/api/tags")
class TagController {

  record TagRequest(String label) {}

  record MergeTagRequest(UUID targetId) {}

  private final TagService tags;

  TagController(TagService tags) {
    this.tags = tags;
  }

  @GetMapping
  List<TagView> list() {
    return tags.tagsOfCurrentPractitioner();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TagView create(@RequestBody TagRequest request) {
    return tags.create(request.label());
  }

  @PatchMapping("/{id}")
  TagView rename(@PathVariable UUID id, @RequestBody TagRequest request) {
    return tags.rename(id, request.label());
  }

  @PostMapping("/{id}/merge")
  TagView merge(@PathVariable UUID id, @RequestBody MergeTagRequest request) {
    return tags.merge(id, request.targetId());
  }
}
