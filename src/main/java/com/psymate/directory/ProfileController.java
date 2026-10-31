package com.psymate.directory;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Profil public du praticien connecté (écran 10). */
@RestController
@RequestMapping("/api/me/profile")
class ProfileController {

  private final ProfileService service;

  ProfileController(ProfileService service) {
    this.service = service;
  }

  @GetMapping
  ProfileDto get() {
    return service.get();
  }

  @PutMapping
  ProfileDto update(@RequestBody UpdateProfileRequest request) {
    return service.update(request);
  }

  @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ProfileDto uploadPhoto(@RequestPart("file") MultipartFile file) throws IOException {
    return service.uploadPhoto(file.getBytes());
  }

  @DeleteMapping("/photo")
  ResponseEntity<Void> deletePhoto() {
    service.deletePhoto();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/publish")
  ResponseEntity<Void> publish() {
    service.publish();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/unpublish")
  ResponseEntity<Void> unpublish() {
    service.unpublish();
    return ResponseEntity.noContent().build();
  }
}
