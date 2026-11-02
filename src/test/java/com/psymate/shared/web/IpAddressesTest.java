package com.psymate.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class IpAddressesTest {

  @Test
  void ipv6ZoneIsRemoved() {
    assertThat(IpAddresses.normalize("fe80::1%eth0")).isEqualTo("fe80::1");
    assertThat(IpAddresses.normalize("fe80::1%12")).isEqualTo("fe80::1");
  }

  @Test
  void plainAddressesAreKept() {
    assertThat(IpAddresses.normalize("127.0.0.1")).isEqualTo("127.0.0.1");
    assertThat(IpAddresses.normalize("::1")).isEqualTo("::1");
    assertThat(IpAddresses.normalize(" 2001:db8::ff00:42:8329 "))
        .isEqualTo("2001:db8::ff00:42:8329");
    // Forme textuelle gardée telle quelle, pas canonisée.
    assertThat(IpAddresses.normalize("0:0:0:0:0:0:0:1")).isEqualTo("0:0:0:0:0:0:0:1");
  }

  @Test
  void unparsableValuesBecomeNull() {
    assertThat(IpAddresses.normalize(null)).isNull();
    assertThat(IpAddresses.normalize("")).isNull();
    assertThat(IpAddresses.normalize("   ")).isNull();
    assertThat(IpAddresses.normalize("unknown")).isNull();
    assertThat(IpAddresses.normalize("example.org")).isNull();
    assertThat(IpAddresses.normalize("999.1.1.1")).isNull();
    assertThat(IpAddresses.normalize("1.2.3")).isNull();
    assertThat(IpAddresses.normalize("fe80::zz")).isNull();
    assertThat(IpAddresses.normalize("%eth0")).isNull();
    assertThat(IpAddresses.normalize(":")).isNull();
    assertThat(IpAddresses.normalize("[::1]")).isNull();
  }

  @Test
  void aForwardedForListIsNotAnAddress() {
    // La valeur d'un X-Forwarded-For à plusieurs sauts n'est jamais découpée ici : elle est
    // rejetée.
    assertThat(IpAddresses.normalize("203.0.113.7, 10.0.0.1")).isNull();
    assertThat(IpAddresses.normalize("2001:db8::1, 2001:db8::2")).isNull();
    assertThat(IpAddresses.parse("203.0.113.7,10.0.0.1")).isNull();
  }

  @Test
  void overlongIpv6IsRejected() {
    // Java accepte des zéros de tête sans limite ; au-delà de 45 caractères (la plus longue forme
    // textuelle d'une IPv6), ce n'est pas une adresse que donne un conteneur servlet : rejetée.
    String padded = "0".repeat(50) + "::1";
    assertThat(IpAddresses.normalize(padded)).isNull();
    assertThat(IpAddresses.parse(padded)).isNull();
    String longest = "ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255";
    assertThat(longest).hasSize(45);
    assertThat(IpAddresses.normalize(longest)).isEqualTo(longest);
  }

  @Test
  void parseGivesTheAddressWithoutZone() throws Exception {
    assertThat(IpAddresses.parse("127.0.0.1")).isEqualTo(InetAddress.getByName("127.0.0.1"));
    assertThat(IpAddresses.parse(" fe80::1%eth0 ")).isEqualTo(InetAddress.getByName("fe80::1"));
    assertThat(IpAddresses.parse(null)).isNull();
    assertThat(IpAddresses.parse("example.org")).isNull();
  }

  @Test
  void subnetKeyGroupsIpv6BySlash64() {
    assertThat(IpAddresses.subnetKey("2001:db8:1:2:0:0:0:1"))
        .isEqualTo(IpAddresses.subnetKey("2001:db8:1:2:ffff:0:0:9"))
        .isEqualTo("20010db800010002/64");
    assertThat(IpAddresses.subnetKey("2001:db8:1:3::1"))
        .isNotEqualTo(IpAddresses.subnetKey("2001:db8:1:2::1"));
    assertThat(IpAddresses.subnetKey("10.0.0.1")).isEqualTo("10.0.0.1");
    // IPv4 mappée en IPv6 : Java la rend en IPv4.
    assertThat(IpAddresses.subnetKey("::ffff:10.0.0.1")).isEqualTo("10.0.0.1");
    assertThat(IpAddresses.subnetKey(null)).isEqualTo("unknown");
    assertThat(IpAddresses.subnetKey("fe80::zz")).isEqualTo("fe80::zz");
  }
}
