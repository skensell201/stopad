package com.skensel.stopad;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.system.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** DNS-only split VPN. It never routes the Internet default route. */
public final class AdVpnService extends VpnService {
    static final String START = "com.skensel.stopad.START", STOP = "com.skensel.stopad.STOP";
    static volatile Session current;
    static volatile String message = "";
    private final Handler main = new Handler(Looper.getMainLooper());

    static boolean active() { Session s = current; return s != null && !s.closed.get(); }
    static long blocked() { Session s = current; return s == null ? 0 : s.blocked.get(); }
    static long forwarded() { Session s = current; return s == null ? 0 : s.forwarded.get(); }
    static long failed() { Session s = current; return s == null ? 0 : s.failed.get(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !START.equals(intent.getAction())) {
            stopSession(""); stopSelf(); return START_NOT_STICKY;
        }
        if (active()) return START_NOT_STICKY;
        try {
            startForeground(1, notification(false));
            Set<String> games = GameCatalog.selected(this);
            if (games.isEmpty()) throw new IOException("Сначала выберите хотя бы одну установленную игру");
            if (VpnService.prepare(this) != null) throw new IOException("Нужно разрешение Android на VPN");
            var cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            var link = cm.getLinkProperties(cm.getActiveNetwork());
            if (Build.VERSION.SDK_INT >= 28 && link != null && link.isPrivateDnsActive())
                throw new IOException("Для DNS-прототипа выключите Private DNS в настройках Android");
            DomainRules rules;
            try (var reader = new BufferedReader(new InputStreamReader(getAssets().open("blocked-domains.txt"), StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                for (String line; (line = reader.readLine()) != null;) {
                    line = line.trim(); if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
                }
                if (lines.isEmpty()) throw new IOException("Список доменов пуст");
                rules = new DomainRules(lines);
            }
            Builder builder = new Builder().setSession("StopAd • игры")
                    .addAddress("10.111.0.1", 32).addDnsServer("10.111.0.2")
                    .addRoute("10.111.0.2", 32).allowFamily(OsConstants.AF_INET6)
                    .setMtu(1500).setBlocking(false)
                    .setConfigureIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            for (String game : games) builder.addAllowedApplication(game);
            ParcelFileDescriptor tunnel = builder.establish();
            if (tunnel == null) throw new IOException("Android не создал VPN-интерфейс");
            Session session;
            try { session = new Session(tunnel, rules); }
            catch (IOException e) { tunnel.close(); throw e; }
            message = "";
            current = session; // Only report active after establish and stream initialization succeed.
            getSystemService(NotificationManager.class).notify(1, notification(true));
            new Thread(() -> pump(session), "stopad-dns").start();
        } catch (Exception e) {
            stopSession(e.getMessage() == null ? "Не удалось запустить VPN" : e.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification notification(boolean running) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("protection", "Защита игр", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, AdVpnService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "protection").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(running ? "StopAd • фильтрация DNS игр" : "StopAd • запуск защиты")
                .setContentText("Браузеры исключены • нажмите, чтобы открыть")
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Остановить", stop).build()).build();
    }

    private void pump(Session s) {
        byte[] buffer = new byte[32767];
        StructPollfd poll = new StructPollfd();
        try {
            poll.fd = s.input.getFD(); poll.events = (short) OsConstants.POLLIN;
            while (!s.closed.get()) {
                int ready = Os.poll(new StructPollfd[]{poll}, 500);
                if (s.closed.get()) break;
                if (ready == 0) continue;
                if ((poll.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0)
                    throw new IOException("VPN-интерфейс закрыт");
                if ((poll.revents & OsConstants.POLLIN) == 0) continue;
                int n = s.input.read(buffer);
                if (n < 0) throw new EOFException();
                DnsPacket packet = DnsPacket.parse(buffer, n);
                if (packet == null) continue;
                boolean denied;
                try { denied = s.rules.blocks(packet.domain); }
                catch (IllegalArgumentException nonHostName) { denied = false; } // e.g. DNS SRV labels with underscores.
                if (denied) {
                    s.send(packet.reply(packet.error(3)));
                    s.blocked.incrementAndGet();
                } else {
                    try { s.workers.execute(() -> resolve(s, packet)); }
                    catch (RejectedExecutionException overloaded) {
                        s.failed.incrementAndGet(); s.send(packet.reply(packet.error(2)));
                    }
                }
            }
        } catch (Exception e) {
            if (!s.closed.get()) main.post(() -> {
                if (current == s) { stopSession("VPN остановлен из-за ошибки интерфейса"); stopSelf(); }
            });
        }
    }

    private void resolve(Session s, DnsPacket packet) {
        byte[] response = null;
        try (DatagramSocket socket = new DatagramSocket()) {
            if (!protect(socket)) throw new IOException("Cannot protect DNS socket");
            socket.connect(InetAddress.getByName("1.1.1.1"), 53);
            socket.setSoTimeout(2000);
            socket.send(new DatagramPacket(packet.query, packet.query.length));
            byte[] data = new byte[4096];
            DatagramPacket reply = new DatagramPacket(data, data.length);
            socket.receive(reply);
            byte[] candidate = Arrays.copyOf(data, reply.getLength());
            if (!packet.accepts(candidate)) throw new IOException("Invalid upstream response");
            if (candidate.length > 1472 || (candidate[2] & 2) != 0) {
                // UDP/IPv4 responses fit the TUN MTU. Client-side DNS/TCP is not supported yet.
                throw new IOException("DNS/TCP required");
            }
            response = candidate;
        } catch (IOException e) {
            s.failed.incrementAndGet();
        }
        try {
            if (response == null) response = packet.error(2);
            else s.forwarded.incrementAndGet();
            s.send(packet.reply(response));
        } catch (IOException e) {
            if (!s.closed.get()) main.post(() -> {
                if (current == s) { stopSession("Не удалось вернуть ответ DNS"); stopSelf(); }
            });
        }
    }

    private void stopSession(String reason) {
        Session s = current;
        current = null; message = reason;
        if (s != null) s.close();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onRevoke() { main.post(() -> { stopSession("Разрешение VPN отозвано"); stopSelf(); }); }
    @Override public void onDestroy() { stopSession(message); super.onDestroy(); }

    static final class Session {
        final DomainRules rules;
        final ParcelFileDescriptor.AutoCloseInputStream input;
        final ParcelFileDescriptor.AutoCloseOutputStream output;
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicLong blocked = new AtomicLong(), forwarded = new AtomicLong(), failed = new AtomicLong();
        final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
        Session(ParcelFileDescriptor tunnel, DomainRules rules) throws IOException {
            this.rules = rules;
            ParcelFileDescriptor duplicate = ParcelFileDescriptor.dup(tunnel.getFileDescriptor());
            input = new ParcelFileDescriptor.AutoCloseInputStream(tunnel);
            output = new ParcelFileDescriptor.AutoCloseOutputStream(duplicate);
        }
        synchronized void send(byte[] data) throws IOException {
            if (!closed.get()) output.write(data);
        }
        synchronized void close() {
            if (!closed.compareAndSet(false, true)) return;
            workers.shutdownNow();
            try { input.close(); } catch (IOException ignored) { }
            try { output.close(); } catch (IOException ignored) { }
        }
    }
}
