package com.skensel.stopad;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.*;
import android.system.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** DNS-only split VPN. It never routes the Internet default route. */
public final class AdVpnService extends VpnService {
    static final String START = "com.skensel.stopad.START", STOP = "com.skensel.stopad.STOP";
    static volatile Session current;
    static volatile String message = "";
    private ConnectivityManager.NetworkCallback networkMonitor;
    private final Handler main = new Handler(Looper.getMainLooper());

    static boolean active() { Session s = current; return s != null && !s.closed.get(); }
    static boolean online() { Session s = current; return s != null && s.network != null; }
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
            if (games.isEmpty()) throw new IOException(getString(R.string.vpn_no_games));
            if (VpnService.prepare(this) != null) throw new IOException(getString(R.string.vpn_permission_required));
            var cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = cm.getActiveNetwork();
            if (network == null) throw new IOException(getString(R.string.vpn_no_network));
            var link = cm.getLinkProperties(network);
            if (Build.VERSION.SDK_INT >= 28 && link != null && link.isPrivateDnsActive())
                throw new IOException(getString(R.string.vpn_private_dns));
            BlocklistSnapshot list = BlocklistRepository.snapshot;
            if (list == null) {
                BlocklistRepository.initialize(this);
                throw new IOException(getString(R.string.vpn_list_loading));
            }
            DomainPolicy rules = new DomainPolicy(list.rules, BlocklistRepository.seed(this),
                    getSharedPreferences("games", MODE_PRIVATE).getStringSet("domain_exceptions", Collections.emptySet()));
            Builder builder = new Builder().setSession(getString(R.string.vpn_session))
                    .addAddress("10.111.0.1", 32).addDnsServer("10.111.0.2")
                    .addRoute("10.111.0.2", 32).allowFamily(OsConstants.AF_INET6)
                    .setMtu(1500).setBlocking(false).setUnderlyingNetworks(new Network[]{network})
                    .setConfigureIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            for (String game : games) builder.addAllowedApplication(game);
            ParcelFileDescriptor tunnel = builder.establish();
            if (tunnel == null) throw new IOException(getString(R.string.vpn_establish_failed));
            Session session;
            try { session = new Session(tunnel, rules, network, list.sha256, this); }
            catch (IOException e) { tunnel.close(); throw e; }
            message = "";
            current = session; // Only report active after establish and stream initialization succeed.
            watchNetwork(session, cm);
            getSystemService(NotificationManager.class).notify(1, notification(true));
            new Thread(() -> pump(session), "stopad-dns").start();
        } catch (Exception e) {
            stopSession(e.getMessage() == null ? getString(R.string.start_failed) : e.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification notification(boolean running) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("protection", getString(R.string.ui_protection), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, AdVpnService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "protection").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(running ? getString(R.string.vpn_notification_running) : getString(R.string.vpn_notification_starting))
                .setContentText(running && !online() ? getString(R.string.waiting_network) : getString(R.string.vpn_notification_body))
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.vpn_notification_stop), stop).build()).build();
    }

    private void pump(Session s) {
        byte[] buffer = new byte[32767];
        StructPollfd poll = new StructPollfd();
        try {
            poll.fd = s.input.getFD(); poll.events = (short) OsConstants.POLLIN;
            while (!s.closed.get()) {
                s.tcp.tick(SystemClock.elapsedRealtime());
                int ready = Os.poll(new StructPollfd[]{poll}, 500);
                if (s.closed.get()) break;
                if (ready == 0) continue;
                if ((poll.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0)
                    throw new IOException(getString(R.string.vpn_interface_closed));
                if ((poll.revents & OsConstants.POLLIN) == 0) continue;
                int n = s.input.read(buffer);
                if (n < 0) throw new EOFException();
                if(n>9 && buffer[9]==6) { s.tcp.accept(buffer,n,SystemClock.elapsedRealtime());continue; }
                DnsPacket packet = DnsPacket.parse(buffer, n);
                if (packet == null) continue;
                if (s.rules.blocks(packet.domain)) {
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
                if (current == s) { stopSession(getString(R.string.vpn_interface_error)); stopSelf(); }
            });
        }
    }

    private void watchNetwork(Session s, ConnectivityManager cm) {
        networkMonitor = new ConnectivityManager.NetworkCallback() {
            final Map<Network, NetworkCapabilities> physical = new HashMap<>();
            { if (s.network != null) physical.put(s.network, cm.getNetworkCapabilities(s.network)); }
            private void reconcile() {
                if (current != s || s.closed.get()) return;
                Network preferred = cm.getActiveNetwork();
                NetworkCapabilities defaultCaps = cm.getNetworkCapabilities(preferred);
                if (!physical.containsKey(preferred) || defaultCaps == null || !defaultCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    // The VPN owner can receive its own VPN as default. Never bind upstream sockets to it.
                    preferred = null;
                    int best = -1;
                    for (var entry : physical.entrySet()) {
                        NetworkCapabilities caps = entry.getValue();
                        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue;
                        int score = (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 100 : 0)
                                + (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? 30
                                : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? 20 : 10);
                        if (score > best || (score == best && entry.getKey().equals(s.network))) {
                            best = score; preferred = entry.getKey();
                        }
                    }
                }
                LinkProperties link = preferred == null ? null : cm.getLinkProperties(preferred);
                if (Build.VERSION.SDK_INT >= 28 && link != null && link.isPrivateDnsActive()) {
                    stopSession(getString(R.string.vpn_private_dns_enabled)); stopSelf(); return;
                }
                s.switchNetwork(preferred);
                setUnderlyingNetworks(preferred == null ? new Network[0] : new Network[]{preferred});
                getSystemService(NotificationManager.class).notify(1, notification(true));
            }
            @Override public void onAvailable(Network network) {
                physical.put(network, cm.getNetworkCapabilities(network)); reconcile();
            }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                physical.put(network, caps); reconcile();
            }
            @Override public void onLinkPropertiesChanged(Network network, LinkProperties link) { reconcile(); }
            @Override public void onLost(Network network) { physical.remove(network); reconcile(); }
        };
        cm.registerNetworkCallback(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networkMonitor, main);
    }

    private void resolveTcp(Session s, byte[] query, Consumer<byte[]> reply) {
        DnsPacket packet=DnsPacket.fromTcp(query);
        if(packet==null) { reply.accept(null);return; }
        if(s.rules.blocks(packet.domain)) {
            s.blocked.incrementAndGet();reply.accept(packet.error(3));return;
        }
        try { s.workers.execute(()->resolve(s,packet,reply)); }
        catch(RejectedExecutionException overloaded) { s.failed.incrementAndGet();reply.accept(packet.error(2)); }
    }
    private void resolve(Session s, DnsPacket packet) { resolve(s,packet,null); }
    private void resolve(Session s, DnsPacket packet, Consumer<byte[]> tcpReply) {
        byte[] response = null;
        Network network = s.network;
        try {
            if (network == null) throw new IOException("Offline");
            DnsUpstream upstream = new DnsUpstream(new InetSocketAddress("1.1.1.1", 53));
            DnsUpstream.Sockets sockets = new DnsUpstream.Sockets() {
                @Override public DatagramSocket udp() throws IOException {
                    DatagramSocket socket = new DatagramSocket();
                    try {
                        s.track(socket, network);
                        if (!protect(socket)) throw new IOException("Cannot protect DNS socket");
                        network.bindSocket(socket); return socket;
                    } catch (IOException e) { socket.close(); s.sockets.remove(socket); throw e; }
                }
                @Override public Socket tcp() throws IOException {
                    Socket socket = new Socket();
                    try {
                        // Android protect(Socket) needs an allocated native descriptor before connect.
                        socket.bind(new InetSocketAddress(0));
                        s.track(socket, network);
                        if (!protect(socket)) throw new IOException("Cannot protect DNS socket");
                        network.bindSocket(socket); return socket;
                    } catch (IOException e) { socket.close(); s.sockets.remove(socket); throw e; }
                }
                @Override public void release(Closeable socket) { s.sockets.remove(socket); }
            };
            response = tcpReply==null ? upstream.resolve(packet,sockets) : upstream.resolveTcp(packet,sockets);
        } catch (IOException e) { s.failed.incrementAndGet(); }
        try {
            if (response == null) response = packet.error(2);
            else s.forwarded.incrementAndGet();
            if(tcpReply==null) s.send(packet.reply(response)); else tcpReply.accept(response);
        } catch (IOException | UncheckedIOException e) {
            if (!s.closed.get()) main.post(() -> {
                if (current == s) { stopSession(getString(R.string.vpn_reply_failed)); stopSelf(); }
            });
        }
    }

    private void stopSession(String reason) {
        Session s = current;
        current = null; message = reason;
        if (networkMonitor != null) {
            try { ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkMonitor); }
            catch (IllegalArgumentException ignored) { }
            networkMonitor = null;
        }
        if (s != null) s.close();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onRevoke() { main.post(() -> { stopSession(getString(R.string.vpn_revoked)); stopSelf(); }); }
    @Override public void onDestroy() { stopSession(message); super.onDestroy(); }

    static final class Session {
        final DomainPolicy rules;
        final TcpDnsTransport tcp;
        final String listVersion;
        volatile Network network;
        final Set<Closeable> sockets = ConcurrentHashMap.newKeySet();
        final ParcelFileDescriptor.AutoCloseInputStream input;
        final ParcelFileDescriptor.AutoCloseOutputStream output;
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicLong blocked = new AtomicLong(), forwarded = new AtomicLong(), failed = new AtomicLong();
        final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
        Session(ParcelFileDescriptor tunnel, DomainPolicy rules, Network network, String listVersion, AdVpnService owner) throws IOException {
            this.rules = rules; this.network = network; this.listVersion = listVersion;
            ParcelFileDescriptor duplicate = ParcelFileDescriptor.dup(tunnel.getFileDescriptor());
            input = new ParcelFileDescriptor.AutoCloseInputStream(tunnel);
            output = new ParcelFileDescriptor.AutoCloseOutputStream(duplicate);
            tcp=new TcpDnsTransport(bytes->{
                try { send(bytes); } catch(IOException e) { throw new UncheckedIOException(e); }
            },(query,reply)->owner.resolveTcp(this,query,reply));
        }
        synchronized void track(Closeable socket, Network expected) throws IOException {
            if (closed.get() || !Objects.equals(network, expected)) throw new IOException("Network changed");
            sockets.add(socket);
        }
        void switchNetwork(Network replacement) {
            synchronized(this) {
                if (Objects.equals(network, replacement)) return;
                network = replacement;
                closeSockets();
            }
            // Never take the TCP monitor while holding Session: TCP output takes these locks in reverse.
            tcp.reset();
        }
        private void closeSockets() {
            for (Closeable socket : sockets) try { socket.close(); } catch (IOException ignored) { }
            sockets.clear();
        }
        synchronized void send(byte[] data) throws IOException {
            if (!closed.get()) output.write(data);
        }
        void close() {
            tcp.reset();
            synchronized(this) {
            if (!closed.compareAndSet(false, true)) return;
            workers.shutdownNow();
            closeSockets();
            try { input.close(); } catch (IOException ignored) { }
            try { output.close(); } catch (IOException ignored) { }
            }
        }
    }
}
