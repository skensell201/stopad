package com.skensel.stopad;

import java.io.*;
import java.security.*;
import java.util.*;

/** Single atomic envelope contains source bytes, fetch time and digest; no separate metadata can drift. */
final class BlocklistSnapshot {
    private static final String MAGIC = "STOPAD-ADS-1";
    final long fetchedAt;
    final String sha256;
    final Set<String> domains;
    final DomainRules rules;
    private final byte[] raw;

    private BlocklistSnapshot(byte[] raw, long fetchedAt) throws IOException {
        if (fetchedAt < 0) throw new IOException("Invalid timestamp");
        this.raw = raw.clone(); this.fetchedAt = fetchedAt;
        this.domains = HostsBlocklist.parse(this.raw); this.sha256 = digest(this.raw);
        this.rules = DomainRules.validated(domains);
        if (domains.size() < 500) throw new IOException("List unexpectedly small");
    }
    static BlocklistSnapshot create(byte[] raw, long fetchedAt) throws IOException {
        return new BlocklistSnapshot(raw, fetchedAt);
    }
    static String digest(byte[] raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    void encode(OutputStream stream) throws IOException {
        DataOutputStream out = new DataOutputStream(stream);
        out.writeUTF(MAGIC); out.writeLong(fetchedAt); out.writeUTF(sha256); out.writeInt(raw.length); out.write(raw); out.flush();
    }
    static BlocklistSnapshot decode(InputStream stream) throws IOException {
        DataInputStream input = new DataInputStream(stream);
        if (!input.readUTF().equals(MAGIC)) throw new IOException("Unsupported list cache");
        long fetchedAt = input.readLong(); String expected = input.readUTF(); int length = input.readInt();
        if (length <= 0 || length > HostsBlocklist.MAX_BYTES) throw new IOException("Invalid cached size");
        byte[] raw = new byte[length]; input.readFully(raw);
        if (input.read() != -1 || !digest(raw).equals(expected)) throw new IOException("Invalid cached digest");
        return create(raw, fetchedAt);
    }
}
