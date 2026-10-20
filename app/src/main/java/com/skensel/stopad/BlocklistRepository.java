package com.skensel.stopad;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** All I/O and validation runs on one worker. An active VPN keeps its immutable rules until restarted. */
final class BlocklistRepository {
    static final String SOURCE = "https://raw.githubusercontent.com/blocklistproject/Lists/main/ads.txt";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Object lock = new Object();
    static volatile BlocklistSnapshot snapshot;
    static volatile boolean busy;
    static volatile String message = "";
    private static boolean requested;

    static void initialize(Context context) {
        Context app = context.getApplicationContext();
        synchronized (lock) {
            if (requested) return;
            requested = true; busy = true;
        }
        worker.execute(() -> {
            try {
                try { store(app).cleanupStaging(); } catch (IOException ignored) { }
                snapshot = load(app);
            }
            catch (IOException e) { message = app.getString(R.string.list_read_failed); }
            finally { busy = false; }
        });
    }
    interface Download { byte[] fetch() throws IOException; }
    static void update(Context context) { update(context, BlocklistRepository::download); }
    static void update(Context context, Download download) {
        Context app = context.getApplicationContext();
        synchronized (lock) {
            if (busy) return;
            busy = true; message = "";
        }
        worker.execute(() -> {
            try {
                BlocklistSnapshot previous = snapshot != null ? snapshot : load(app);
                BlocklistSnapshot next = BlocklistSnapshot.create(download.fetch(), System.currentTimeMillis());
                if (next.domains.size() < previous.domains.size() / 2) throw new IOException("Unexpected list shrink");
                store(app).install(next);
                snapshot = next;
                message = previous.sha256.equals(next.sha256) ? app.getString(R.string.list_current) : app.getString(R.string.list_updated);
            } catch (IOException e) { message = app.getString(R.string.list_update_failed); }
            finally { busy = false; }
        });
    }
    static BlocklistSnapshot load(Context context) throws IOException {
        try { return store(context).load(); }
        catch (IOException corruptOrMissing) {
            Properties metadata = new Properties();
            try (InputStream input = context.getAssets().open("ads-snapshot.properties")) { metadata.load(input); }
            byte[] data;
            try (InputStream input = context.getAssets().open("ads-hosts.txt")) { data = bounded(input); }
            long fetchedAt;
            try { fetchedAt = Long.parseLong(metadata.getProperty("fetchedAt")); }
            catch (RuntimeException badMetadata) { throw new IOException("Invalid bundled metadata", badMetadata); }
            BlocklistSnapshot bundled = BlocklistSnapshot.create(data, fetchedAt);
            if (!bundled.sha256.equals(metadata.getProperty("sha256"))) throw new IOException("Invalid bundled digest");
            return bundled;
        }
    }
    static Set<String> seed(Context context) throws IOException {
        Set<String> names = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("blocked-domains.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                line = line.trim(); if (!line.isEmpty() && !line.startsWith("#")) names.add(line);
            }
        }
        return names;
    }
    private static BlocklistStore store(Context context) { return new BlocklistStore(context.getNoBackupFilesDir().toPath()); }
    private static byte[] download() throws IOException {
        URL url = new URL(SOURCE);
        AdVpnService.Session session = AdVpnService.current;
        HttpURLConnection connection = (HttpURLConnection) (session != null && session.network != null
                ? session.network.openConnection(url) : url.openConnection());
        try {
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "text/plain");
            if (connection.getResponseCode() != 200) throw new IOException("List server unavailable");
            if (connection.getContentLengthLong() > HostsBlocklist.MAX_BYTES) throw new IOException("List too large");
            try (InputStream input = connection.getInputStream()) { return bounded(input); }
        } finally { connection.disconnect(); }
    }
    static byte[] bounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        for (int n; (n = input.read(buffer)) != -1;) {
            if (output.size() + n > HostsBlocklist.MAX_BYTES || System.nanoTime() > deadline) throw new IOException("List transfer limit exceeded");
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }
}
