package com.psymate.directory.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.directory.model.PublicProfileDetail;
import com.psymate.shared.web.IpAddresses;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicProfileAssemblerTest {

  private static PublicProfileDetail detail(
      boolean showPhone, boolean showEmail, boolean showAddress, boolean showNumber) {
    return new PublicProfileDetail(
        UUID.randomUUID(),
        "alice-martin",
        "Alice Martin",
        null,
        showNumber,
        null,
        "bio",
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "Lyon",
        null,
        "FR",
        "0102030405",
        "alice@example.test",
        "https://alice.example",
        "1 rue de la Paix",
        showPhone,
        showEmail,
        showAddress,
        null,
        null,
        "accepting",
        null,
        null);
  }

  @Test
  void hidesEverythingNotOptedIn() {
    PublicProfile p =
        PublicProfileAssembler.toPublic(
            detail(false, false, false, false), "psychologist", true, "ADELI-1");
    assertThat(p.phone()).isNull();
    assertThat(p.email()).isNull();
    assertThat(p.address()).isNull();
    assertThat(p.professionalNumber()).isNull();
    assertThat(p.websiteUrl()).isEqualTo("https://alice.example");
    assertThat(p.verified()).isTrue();
  }

  @Test
  void showsOnlyOptedInFields() {
    PublicProfile p =
        PublicProfileAssembler.toPublic(detail(true, false, true, true), "coach", false, "ADELI-1");
    assertThat(p.phone()).isEqualTo("0102030405");
    assertThat(p.email()).isNull();
    assertThat(p.address()).isEqualTo("1 rue de la Paix");
    assertThat(p.professionalNumber()).isEqualTo("ADELI-1");
    assertThat(p.verified()).isFalse();
  }

  @Test
  void excerptCutsAtLastSpace() {
    assertThat(PublicProfileAssembler.excerpt("court")).isEqualTo("court");
    String cut = PublicProfileAssembler.excerpt("abcdefghi ".repeat(30));
    assertThat(cut).endsWith("abcdefghi…").hasSizeLessThanOrEqualTo(201);
  }

  @Test
  void ipv6IsGroupedBySlash64() {
    assertThat(IpAddresses.subnetKey("2001:db8:1:2:0:0:0:1"))
        .isEqualTo(IpAddresses.subnetKey("2001:db8:1:2:ffff:0:0:9"));
    assertThat(IpAddresses.subnetKey("2001:db8:1:3::1"))
        .isNotEqualTo(IpAddresses.subnetKey("2001:db8:1:2::1"));
    assertThat(IpAddresses.subnetKey("10.0.0.1")).isEqualTo("10.0.0.1");
  }
}
