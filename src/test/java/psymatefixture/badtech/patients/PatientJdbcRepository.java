package psymatefixture.badtech.patients;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Violation : JdbcClient dans un module JPA (G Q38). */
public class PatientJdbcRepository {
  JdbcClient jdbc;
}
