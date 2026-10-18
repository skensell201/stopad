package com.skensel.stopad;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class PacketCheck {
    static byte[] query(String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0});
        for (String label : name.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII); out.write(bytes.length); out.writeBytes(bytes);
        }
        out.writeBytes(new byte[]{0, 0, 1, 0, 1}); return out.toByteArray();
    }
    static byte[] ip(byte[] dns) {
        byte[] result = new byte[28 + dns.length];
        result[0] = 0x45; DnsPacket.put16(result, 2, result.length);
        result[8] = 64; result[9] = 17;
        result[12] = 10; result[13] = 111; result[15] = 1;
        result[16] = 10; result[17] = 111; result[19] = 2;
        DnsPacket.put16(result, 20, 54321); DnsPacket.put16(result, 22, 53);
        DnsPacket.put16(result, 24, dns.length + 8);
        System.arraycopy(dns, 0, result, 28, dns.length);
        DnsPacket.put16(result, 10, DnsPacket.checksum(result, 0, 20)); return result;
    }
    public static void main(String[] args) {
        byte[] raw = ip(query("ads.example.com"));
        DnsPacket p = DnsPacket.parse(raw, raw.length);
        check(p != null && p.domain.equals("ads.example.com"));
        byte[] denied = p.error(3);
        check(p.accepts(denied) && (denied[3] & 15) == 3 && DnsPacket.u16(denied, 6) == 0);
        byte[] reply = p.reply(denied);
        check(DnsPacket.checksum(reply, 0, 20) == 0 && reply[12] == raw[16] && reply[19] == raw[15]);
        check(DnsPacket.u16(reply, 22) == 54321 && DnsPacket.u16(reply, 24) == denied.length + 8);
        denied[0] ^= 1; check(!p.accepts(denied));
        byte[] compressed = query("a.example.com"); compressed[12] = (byte) 0xc0;
        check(DnsPacket.parse(ip(compressed), compressed.length + 28) == null);
        for (int n = 0; n < raw.length; n++) check(DnsPacket.parse(raw, n) == null);
        byte[] fragment = raw.clone(); fragment[6] = 0x20; fragment[10] = fragment[11] = 0;
        DnsPacket.put16(fragment, 10, DnsPacket.checksum(fragment, 0, 20));
        check(DnsPacket.parse(fragment, fragment.length) == null);
        Random random = new Random(42);
        for (int i = 0; i < 20000; i++) {
            byte[] malformed = new byte[random.nextInt(256)]; random.nextBytes(malformed);
            DnsPacket.parse(malformed, malformed.length);
        }
        System.out.println("DnsPacket: response, bounds, fragmentation and 20000 malformed packets passed");
    }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
