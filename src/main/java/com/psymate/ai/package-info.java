@ApplicationModule(
    allowedDependencies = {"events :: api", "clinical :: api", "linking :: api", "shared"})
package com.psymate.ai;

import org.springframework.modulith.ApplicationModule;
