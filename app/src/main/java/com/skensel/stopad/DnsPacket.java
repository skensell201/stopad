package com.skensel.stopad;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Minimal defensive IPv4/UDP DNS codec. TCP and fragmented packets are deliberately unsupported. */
final class DnsPacket {
    final byte[] ip, query;
    final String domain;
    final int headerLength, questionEnd;

    private DnsPacket(byte[] ip, int headerLength, byte[] query, Question question) {
        this.ip = ip; this.headerLength = headerLength; this.query = query;
        this.domain = question.domain; this.questionEnd = question.end;
    }
    static DnsPacket parse(byte[] packet, int length) {
        if (length < 40 || length > packet.length || (packet[0] & 0xf0) != 0x40) return null;
        int h = (packet[0] & 15) * 4;
        if (h < 20 || h + 20 > length || u16(packet, 2) != length || packet[9] != 17
                || (u16(packet, 6) & 0x3fff) != 0 || checksum(packet, 0, h) != 0) return null;
        // Only the synthetic DNS address 10.111.0.2 and UDP/53 are routed into this prototype.
        if (packet[16] != 10 || packet[17] != 111 || packet[18] != 0 || packet[19] != 2
                || u16(packet, h + 2) != 53 || u16(packet, h + 4) != length - h) return null;
        byte[] query = Arrays.copyOfRange(packet, h + 8, length);
        if ((query[2] & 0xf8) != 0 || (query[3] & 0x0f) != 0 || u16(query, 4) != 1
                || u16(query, 6) != 0 || u16(query, 8) != 0) return null;
        Question question = question(query);
        if (question == null) return null;
        return new DnsPacket(Arrays.copyOf(packet, length), h, query, question);
    }
    private record Question(String domain, int end) { }
    private static Question question(byte[] data) {
        if (data.length < 17) return null;
        int p = 12;
        StringBuilder name = new StringBuilder();
        while (p < data.length) {
            int n = data[p++] & 255;
            if (n == 0) {
                if (name.length() == 0 || p + 4 > data.length || u16(data, p + 2) != 1) return null;
                return new Question(name.toString(), p + 4);
            }
            if (n > 63 || p + n >= data.length || name.length() + n + 1 > 254) return null;
            for (int i = p; i < p + n; i++) {
                int c = data[i] & 255;
                if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9') || c == '-' || c == '_')) return null;
            }
            if (name.length() > 0) name.append('.');
            name.append(new String(data, p, n, StandardCharsets.US_ASCII));
            p += n;
        }
        return null;
    }
    byte[] error(int rcode) {
        byte[] result = Arrays.copyOf(query, questionEnd);
        result[2] = (byte) (0x80 | (query[2] & 1)); // QR, original RD; no AA/TC/AD.
        result[3] = (byte) (0x80 | rcode); // RA and NXDOMAIN/SERVFAIL.
        Arrays.fill(result, 6, 12, (byte) 0);
        return result;
    }
    boolean accepts(byte[] response) {
        Question q = question(response);
        return q != null && response.length >= questionEnd && (response[2] & 0xf8) == 0x80
                && u16(response, 0) == u16(query, 0) && u16(response, 4) == 1
                && q.end == questionEnd
                && Arrays.equals(Arrays.copyOfRange(query, 12, questionEnd),
                                 Arrays.copyOfRange(response, 12, q.end));
    }
    byte[] reply(byte[] dns) {
        if (dns.length > 65507) throw new IllegalArgumentException("DNS response too large");
        byte[] result = new byte[28 + dns.length];
        result[0] = 0x45; put16(result, 2, result.length);
        result[8] = 64; result[9] = 17;
        System.arraycopy(ip, 16, result, 12, 4);
        System.arraycopy(ip, 12, result, 16, 4);
        put16(result, 10, checksum(result, 0, 20));
        put16(result, 20, 53); put16(result, 22, u16(ip, headerLength));
        put16(result, 24, dns.length + 8);
        // IPv4 UDP checksum 0 is legal. IPv6 is not routed here.
        System.arraycopy(dns, 0, result, 28, dns.length);
        return result;
    }
    static int u16(byte[] data, int p) { return ((data[p] & 255) << 8) | (data[p + 1] & 255); }
    static void put16(byte[] data, int p, int n) { data[p] = (byte) (n >> 8); data[p + 1] = (byte) n; }
    static int checksum(byte[] data, int offset, int length) {
        int sum = 0;
        for (int p = offset; p < offset + length; p += 2) {
            sum += (data[p] & 255) << 8;
            if (p + 1 < offset + length) sum += data[p + 1] & 255;
        }
        while ((sum >> 16) != 0) sum = (sum & 65535) + (sum >> 16);
        return (~sum) & 65535;
    }
}
