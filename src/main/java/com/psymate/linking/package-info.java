@ApplicationModule(
    allowedDependencies = {
      "clinical :: api",
      "vocabulary :: api",
      "events :: api",
      "identity :: api",
      "patients :: api",
      "shared"
    })
package com.psymate.linking;

import org.springframework.modulith.ApplicationModule;
