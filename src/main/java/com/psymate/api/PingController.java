package com.psymate.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PingController {

	@GetMapping("/api/public/ping")
	Map<String, String> ping() {
		return Map.of("status", "ok", "service", "psymate-api");
	}

}
