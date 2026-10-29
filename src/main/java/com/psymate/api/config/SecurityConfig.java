package com.psymate.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	// Placeholder until real authentication (e.g. OIDC/JWT) is chosen: only health/info and
	// /api/public/** are open; everything else requires authentication.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/info", "/api/public/**").permitAll()
				.anyRequest().authenticated())
			.httpBasic(Customizer.withDefaults());
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${psymate.cors.allowed-origins}") List<String> origins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

}
