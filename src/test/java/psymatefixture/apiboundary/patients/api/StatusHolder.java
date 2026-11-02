package psymatefixture.apiboundary.patients.api;

import org.springframework.http.HttpStatusCode;

/** Incorrect : un type d'{@code api} expose un type {@code org.springframework.http}. */
public record StatusHolder(HttpStatusCode status) {}
