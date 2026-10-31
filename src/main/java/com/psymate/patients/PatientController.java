package com.psymate.patients;

import com.psymate.patients.PatientDtos.CreatePatientRequest;
import com.psymate.patients.PatientDtos.FlagRequest;
import com.psymate.patients.PatientDtos.PatientDto;
import com.psymate.patients.PatientDtos.PatientPage;
import com.psymate.patients.PatientDtos.TagsRequest;
import com.psymate.patients.PatientDtos.VersionDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Patients du praticien courant (fiches 01 et 16). */
@RestController
@RequestMapping("/api/patients")
class PatientController {

  private final PatientService patients;

  PatientController(PatientService patients) {
    this.patients = patients;
  }

  @GetMapping
  PatientPage listPatients(
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return patients.list(q, archived, tag, sort, cursor, limit);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  PatientDto createPatient(@RequestBody CreatePatientRequest request) {
    return patients.create(request);
  }

  @GetMapping("/{id}")
  PatientDto getPatient(@PathVariable UUID id) {
    return patients.get(id);
  }

  @PatchMapping("/{id}")
  PatientDto patchPatient(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return patients.patch(id, body);
  }

  @GetMapping("/{id}/versions")
  List<VersionDto> listPatientVersions(@PathVariable UUID id) {
    return patients.versions(id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void archivePatient(@PathVariable UUID id) {
    patients.archive(id);
  }

  @PostMapping("/{id}/restore")
  PatientDto restorePatient(@PathVariable UUID id) {
    return patients.restore(id);
  }

  @PostMapping("/{id}/flags")
  @ResponseStatus(HttpStatus.CREATED)
  PatientDto addPatientFlag(@PathVariable UUID id, @RequestBody FlagRequest request) {
    return patients.addFlag(id, request);
  }

  @DeleteMapping("/{id}/flags/{kind}")
  PatientDto clearPatientFlag(@PathVariable UUID id, @PathVariable String kind) {
    return patients.clearFlag(id, kind);
  }

  @PutMapping("/{id}/tags")
  PatientDto replacePatientTags(@PathVariable UUID id, @RequestBody TagsRequest request) {
    return patients.replaceTags(id, request.tags());
  }
}
