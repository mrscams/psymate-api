package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.testsupport.SharedPostgres;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class AppIT {

  @Autowired private DataSource dataSource;
  @Autowired private MockMvc mockMvc;

  @Test
  void connectsToPgvectorPostgres() throws Exception {
    var jdbc = new JdbcTemplate(dataSource);
    assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from pg_available_extensions where name = 'vector'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void healthIsUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
