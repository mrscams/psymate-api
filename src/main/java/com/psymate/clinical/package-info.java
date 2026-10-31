@ApplicationModule(
    allowedDependencies = {
      "identity :: api",
      "patients :: api",
      "vocabulary :: api",
      "events :: api",
      "shared"
    })
package com.psymate.clinical;

import org.springframework.modulith.ApplicationModule;
