package com.psymate.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
  }

  @Test
  void unparsableValuesBecomeNull() {
    assertThat(IpAddresses.normalize(null)).isNull();
    assertThat(IpAddresses.normalize("")).isNull();
    assertThat(IpAddresses.normalize("unknown")).isNull();
    assertThat(IpAddresses.normalize("example.org")).isNull();
    assertThat(IpAddresses.normalize("999.1.1.1")).isNull();
    assertThat(IpAddresses.normalize("fe80::zz")).isNull();
    assertThat(IpAddresses.normalize("%eth0")).isNull();
  }
}
