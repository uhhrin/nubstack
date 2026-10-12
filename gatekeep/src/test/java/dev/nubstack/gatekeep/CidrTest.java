package dev.nubstack.gatekeep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CidrTest {

    @Test
    void ipv4RangeMatchesInsideAndRejectsOutside() {
        Cidr range = Cidr.parse("172.16.0.0/12");
        assertTrue(range.contains(Cidr.address("172.17.0.5")));
        assertTrue(range.contains(Cidr.address("172.31.255.255")));
        assertFalse(range.contains(Cidr.address("172.32.0.1")));
        assertFalse(range.contains(Cidr.address("10.0.0.1")));
    }

    @Test
    void bareAddressIsHostRoute() {
        Cidr host = Cidr.parse("127.0.0.1");
        assertTrue(host.contains(Cidr.address("127.0.0.1")));
        assertFalse(host.contains(Cidr.address("127.0.0.2")));
    }

    @Test
    void ipv6RangeMatches() {
        Cidr range = Cidr.parse("2001:db8:abcd::/48");
        assertTrue(range.contains(Cidr.address("2001:db8:abcd:1::7")));
        assertFalse(range.contains(Cidr.address("2001:db8:abce::1")));
        assertTrue(Cidr.parse("::1/128").contains(Cidr.address("::1")));
    }

    @Test
    void familiesDoNotCrossMatch() {
        assertFalse(Cidr.parse("0.0.0.0/0").contains(Cidr.address("2001:db8::1")));
        assertFalse(Cidr.parse("::/0").contains(Cidr.address("1.2.3.4")));
    }

    @Test
    void ipv4MappedIpv6IsTreatedAsIpv4() {
        assertTrue(Cidr.parse("127.0.0.0/8").contains(Cidr.address("::ffff:127.0.0.1")));
    }

    @Test
    void zeroPrefixMatchesEverythingInFamily() {
        assertTrue(Cidr.parse("0.0.0.0/0").contains(Cidr.address("8.8.8.8")));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("not-an-ip"));
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("10.0.0.0/-1"));
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("::1/129"));
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("10.0.0.0/abc"));
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("localhost"));
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse(""));
    }
}
