package com.skensel.stopad;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Tests updater failure boundaries against app-private storage, plus one real fixed-source HTTPS request. */
final class ListIntegration {
    private final Instrumentation runner;
    ListIntegration(Instrumentation runner) { this.runner = runner; }
    private Context context;
    private BlocklistSnapshot original;
    private byte[] oldCache;
    private Path target;
    private Set<String> oldSelection;
    private String oldMessage;
    void run() {
        Bundle result = new Bundle(); boolean ok = false;
        try {
            context = runner.getTargetContext();
            target = context.getNoBackupFilesDir().toPath().resolve("ads.snapshot");
            oldCache = Files.exists(target) ? read(target) : null;
            oldSelection = new HashSet<>(context.getSharedPreferences("games", 0).getStringSet("selected", Collections.emptySet()));
            long initialStart = SystemClock.elapsedRealtime();
            runner.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            awaitIdle();
            android.util.Log.i("StopAdListTest", "Initial list ready in " + (SystemClock.elapsedRealtime() - initialStart) + " ms");
            original = BlocklistRepository.snapshot;
            oldMessage = BlocklistRepository.message;
            require(original != null && original.domains.size() > 100000, "Bundled list ready");
            long before = SystemClock.elapsedRealtime();
            BlocklistRepository.update(context); awaitIdle();
            require(BlocklistRepository.message.contains("актуален") || BlocklistRepository.message.contains("обновлён"), "Fixed HTTPS source: " + BlocklistRepository.message);
            BlocklistSnapshot good = BlocklistRepository.snapshot;
            require(new BlocklistStore(context.getNoBackupFilesDir().toPath()).load().sha256.equals(good.sha256), "Persisted downloaded snapshot");
            byte[] goodCache = read(target);
            for (BlocklistRepository.Download failure : new BlocklistRepository.Download[]{
                    () -> { throw new IOException("Simulated network interruption"); },
                    () -> "<html>gateway error</html>".getBytes(StandardCharsets.UTF_8),
                    () -> fixture(600)}) {
                BlocklistRepository.update(context, failure); awaitIdle();
                require(BlocklistRepository.snapshot == good && Arrays.equals(goodCache, read(target)), "Previous list retained after rejected update");
                require(BlocklistRepository.message.contains("не удалось"), "Failure visible to UI");
            }
            Files.write(target, new byte[]{1, 2, 3});
            Properties metadata = new Properties();
            try (InputStream input = context.getAssets().open("ads-snapshot.properties")) { metadata.load(input); }
            require(BlocklistRepository.load(context).sha256.equals(metadata.getProperty("sha256")), "Corrupt cache falls back to bundled list");
            Files.write(target, goodCache);
            // The test fixture is a game, and app-op consent is granted only by the isolated emulator script.
            context.getSharedPreferences("games", 0).edit().putStringSet("selected", Set.of("com.skensel.stopad.probe.game")).commit();
            runner.runOnMainSync(() -> context.startForegroundService(new Intent(context, AdVpnService.class).setAction(AdVpnService.START)));
            awaitVpn(true);
            AdVpnService.Session session = AdVpnService.current;
            require(!session.rules.blocks("ads.stopad.test"), "Marker not initially blocked");
            byte[] raw;
            try (InputStream input = context.getAssets().open("ads-hosts.txt")) { raw = BlocklistRepository.bounded(input); }
            ByteArrayOutputStream extended = new ByteArrayOutputStream(); extended.write(raw);
            extended.write("\n0.0.0.0 ads.stopad.test\n".getBytes(StandardCharsets.UTF_8));
            byte[] update = extended.toByteArray();
            BlocklistRepository.update(context, () -> update); awaitIdle();
            require(BlocklistRepository.snapshot.rules.blocks("ads.stopad.test"), "Updated marker installed");
            require(AdVpnService.current == session && !session.rules.blocks("ads.stopad.test")
                    && !session.listVersion.equals(BlocklistRepository.snapshot.sha256), "Active session keeps previous rules");
            context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP)); awaitVpn(false);
            runner.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            runner.runOnMainSync(() -> context.startForegroundService(new Intent(context, AdVpnService.class).setAction(AdVpnService.START)));
            awaitVpn(true);
            require(AdVpnService.current.rules.blocks("ads.stopad.test"), "Restart applies updated rules");
            result.putString("stream", "PASS: " + good.domains.size() + " domains; HTTPS update, failed/malformed/shrunken update retention, corrupt-cache fallback, deferred activation, restart (" + (SystemClock.elapsedRealtime() - before) + " ms)");
            ok = true;
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error);
            android.util.Log.e("StopAdListTest", "List test failed", error);
        } finally {
            if (context != null) {
                context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP));
                if (oldSelection != null) context.getSharedPreferences("games", 0).edit().putStringSet("selected", oldSelection).commit();
                if (original != null) { BlocklistRepository.snapshot = original; BlocklistRepository.message = oldMessage; }
                if (target != null) try {
                    if (oldCache == null) Files.deleteIfExists(target); else Files.write(target, oldCache);
                } catch (IOException restoreFailed) { result.putString("stream", "FAIL: cache restoration " + restoreFailed); ok = false; }
            }
        }
        runner.finish(ok ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private static byte[] fixture(int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append("0.0.0.0 ads").append(i).append(".example.net\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] read(Path file) throws IOException { return Files.readAllBytes(file); }
    private static void awaitIdle() throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 60000;
        while (BlocklistRepository.busy && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
        require(!BlocklistRepository.busy, "List worker timeout");
    }
    private static void awaitVpn(boolean expected) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (AdVpnService.active() != expected && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
        require(AdVpnService.active() == expected, "VPN state: " + AdVpnService.message);
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
