package com.psymate.identity.auth;

import java.util.Map;
import java.util.UUID;

record PractitionerRow(
    UUID id,
    UUID tenantId,
    String email,
    String passwordHash,
    String firstName,
    String lastName,
    String displayName,
    String locale,
    String profession,
    String role,
    String verificationStatus,
    Map<String, Object> preferences) {}
