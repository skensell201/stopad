package com.skensel.stopad;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class BlocklistCheck {
    static byte[] fixture(int count, String prefix) {
        StringBuilder out = new StringBuilder("# fixture\n127.0.0.1 localhost\n::1 localhost\n");
        for (int i = 0; i < count; i++) out.append("0.0.0.0 ").append(prefix).append(i).append(".example.net\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        byte[] raw = fixture(600, "ads");
        BlocklistSnapshot first = BlocklistSnapshot.create(raw, 1000);
        PacketCheck.check(first.domains.size() == 600 && first.rules.blocks("sub.ads0.example.net"));
        raw[0] = '!'; // The snapshot must retain its own bytes.
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(); first.encode(encoded);
        byte[] envelope = encoded.toByteArray();
        BlocklistSnapshot loaded = BlocklistSnapshot.decode(new ByteArrayInputStream(envelope));
        PacketCheck.check(loaded.sha256.equals(first.sha256) && loaded.fetchedAt == 1000 && loaded.domains.equals(first.domains));
        envelope[envelope.length - 2] ^= 1;
        expectInvalid(() -> BlocklistSnapshot.decode(new ByteArrayInputStream(envelope)));
        expectInvalid(() -> BlocklistSnapshot.create(fixture(10, "ads"), 0));
        for (String bad : new String[]{"<html>error</html>", "1.2.3.4 ads.example.net", "0.0.0.0 *.example.net", "0.0.0.0 com", "0.0.0.0 1.2.3.4", "0.0.0.0 google.com"})
            expectInvalid(() -> HostsBlocklist.parse(bad.getBytes(StandardCharsets.UTF_8)));
        expectInvalid(() -> HostsBlocklist.parse(new byte[]{(byte) 0xc3, (byte) 0x28}));
        expectInvalid(() -> HostsBlocklist.parse(new byte[HostsBlocklist.MAX_BYTES + 1]));
        Set<String> normalized = HostsBlocklist.parse("127.0.0.1 Ads.Example.net. ads.example.net\n0.0.0.0 under_score.example.net\n".getBytes(StandardCharsets.UTF_8));
        PacketCheck.check(normalized.equals(Set.of("ads.example.net")));
        Path folder = Files.createTempDirectory("stopad-list-check-");
        try {
            BlocklistStore store = new BlocklistStore(folder); store.install(first);
            BlocklistSnapshot second = BlocklistSnapshot.create(fixture(601, "new"), 2000);
            BlocklistStore failure = new BlocklistStore(folder, (from, to) -> { throw new IOException("Simulated commit failure"); });
            expectInvalid(() -> failure.install(second));
            PacketCheck.check(store.load().sha256.equals(first.sha256));
            try (var files = Files.list(folder)) { PacketCheck.check(files.count() == 1); }
            Files.write(folder.resolve("ads-update-abandoned.tmp"), new byte[]{1, 2, 3});
            store.cleanupStaging();
            PacketCheck.check(store.load().sha256.equals(first.sha256));
            store.install(second); PacketCheck.check(store.load().sha256.equals(second.sha256));
        } finally {
            try (var files = Files.list(folder)) { for (Path file : files.toList()) Files.delete(file); }
            Files.delete(folder);
        }
        Properties metadata = new Properties();
        try (InputStream input = Files.newInputStream(Path.of("app/src/main/assets/ads-snapshot.properties"))) { metadata.load(input); }
        byte[] bundledRaw = Files.readAllBytes(Path.of("app/src/main/assets/ads-hosts.txt"));
        long begin = System.nanoTime();
        BlocklistSnapshot bundled = BlocklistSnapshot.create(bundledRaw, Long.parseLong(metadata.getProperty("fetchedAt")));
        PacketCheck.check(bundled.sha256.equals(metadata.getProperty("sha256")));
        PacketCheck.check(bundled.domains.size() > 100000 && bundled.rules.blocks("s0.2mdn.net") && !bundled.rules.blocks("example.com"));
        System.out.println("Blocklist: parser, integrity, rollback, atomic replace and bundled " + bundled.domains.size() + " domains passed (" + (System.nanoTime() - begin) / 1000000 + " ms)");
    }
    interface Action { void run() throws Exception; }
    static void expectInvalid(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid list accepted"); }
        catch (IOException expected) { }
    }
}
