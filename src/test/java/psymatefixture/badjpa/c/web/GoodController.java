package psymatefixture.badjpa.c.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;
import psymatefixture.badjpa.c.GoodDto;

/** Correct : une classe web qui ne manipule que des Dto. */
@RestController
public class GoodController {
  public GoodDto get() {
    return new GoodDto(UUID.randomUUID());
  }
}
