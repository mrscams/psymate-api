package com.psymate.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class UploadSizeExceptionHandlerTest {

  @RestController
  static class Failing {
    @PostMapping("/api/me/verification/documents")
    void upload() {
      throw new MaxUploadSizeExceededException(26L * 1024 * 1024);
    }
  }

  @Test
  void multipartSizeErrorBecomesProblemJson413() throws Exception {
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new Failing())
            .setControllerAdvice(new UploadSizeExceptionHandler())
            .build();
    mvc.perform(post("/api/me/verification/documents"))
        .andExpect(status().is(413))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
  }
}
