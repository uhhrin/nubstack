package dev.nubstack.gatekeep;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Pattern;

/** IPv4 or IPv6 network in CIDR notation. A bare address is a host route. */
public final class Cidr {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]+");

    private final byte[] network;
    private final int prefix;

    private Cidr(byte[] network, int prefix) {
        this.network = network;
        this.prefix = prefix;
    }

    public static Cidr parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty address range");
        }
        String trimmed = text.trim();
        int slash = trimmed.indexOf('/');
        byte[] bytes = address(slash < 0 ? trimmed : trimmed.substring(0, slash)).getAddress();
        int max = bytes.length * 8;
        int prefix = max;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(trimmed.substring(slash + 1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid prefix length in '" + trimmed + "'");
            }
            if (prefix < 0 || prefix > max) {
                throw new IllegalArgumentException("Prefix out of range in '" + trimmed + "'");
            }
        }
        return new Cidr(mask(bytes, prefix), prefix);
    }

    /** Parses an IP literal. Never performs a DNS lookup. */
    public static InetAddress address(String literal) {
        if (literal == null) {
            throw new IllegalArgumentException("Empty address");
        }
        String text = literal.trim();
        boolean valid = text.indexOf(':') >= 0 ? IPV6.matcher(text).matches() : IPV4.matcher(text).matches();
        if (!valid) {
            throw new IllegalArgumentException("Not an IP address: '" + literal + "'");
        }
        try {
            return InetAddress.getByName(text);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Not an IP address: '" + literal + "'");
        }
    }

    public boolean contains(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == network.length && Arrays.equals(mask(bytes, prefix), network);
    }

    private static byte[] mask(byte[] bytes, int prefix) {
        byte[] out = bytes.clone();
        for (int i = 0; i < out.length; i++) {
            int bitsKept = Math.max(0, Math.min(8, prefix - i * 8));
            out[i] &= (byte) (0xFF << (8 - bitsKept));
        }
        return out;
    }
}
