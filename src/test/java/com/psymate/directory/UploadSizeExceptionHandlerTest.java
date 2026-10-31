package com.psymate.directory;

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
    @PostMapping("/upload")
    void upload() {
      throw new MaxUploadSizeExceededException(5L * 1024 * 1024);
    }
  }

  @Test
  void multipartSizeErrorBecomesProblemJson413() throws Exception {
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new Failing())
            .setControllerAdvice(new UploadSizeExceptionHandler())
            .build();
    mvc.perform(post("/upload"))
        .andExpect(status().is(413))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
  }
}
