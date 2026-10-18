package com.skensel.stopad;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
public final class UpstreamCheck {
    static final class Sockets implements DnsUpstream.Sockets {
        final Set<Closeable> opened = new HashSet<>();
        public DatagramSocket udp() throws IOException { DatagramSocket s = new DatagramSocket(); opened.add(s); return s; }
        public Socket tcp() { Socket s = new Socket(); opened.add(s); return s; }
        public void release(Closeable socket) { opened.remove(socket); }
    }
    static byte[] exchange(DnsPacket packet, byte[] udpReply, byte[] tcpReply, boolean shortBody) throws Exception {
        return exchange(packet,udpReply,tcpReply,shortBody,false);
    }
    static byte[] exchange(DnsPacket packet, byte[] udpReply, byte[] tcpReply, boolean shortBody, boolean fullTcp) throws Exception {
        InetAddress loop = InetAddress.getLoopbackAddress();
        try (ServerSocket tcp = new ServerSocket(0, 1, loop); DatagramSocket udp = new DatagramSocket(tcp.getLocalPort(), loop)) {
            tcp.setSoTimeout(3000); udp.setSoTimeout(3000);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> server = executor.submit(() -> {
                try {
                    if (!fullTcp) {
                        byte[] data = new byte[4096]; DatagramPacket request = new DatagramPacket(data, data.length); udp.receive(request);
                        udp.send(new DatagramPacket(udpReply, udpReply.length, request.getSocketAddress()));
                    }
                    if (tcpReply != null) try (Socket socket = tcp.accept()) {
                        DataInputStream input = new DataInputStream(socket.getInputStream());
                        byte[] q = new byte[input.readUnsignedShort()]; input.readFully(q);
                        if (!Arrays.equals(q, packet.query)) throw new AssertionError("TCP query changed");
                        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                        output.writeShort(tcpReply.length + (shortBody ? 4 : 0)); output.write(tcpReply); output.flush();
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            Sockets sockets = new Sockets();
            try {
                DnsUpstream upstream = new DnsUpstream(new InetSocketAddress(loop, tcp.getLocalPort()));
                byte[] result = fullTcp ? upstream.resolveTcp(packet,sockets) : upstream.resolve(packet,sockets);
                server.get(4, TimeUnit.SECONDS); return result;
            } finally {
                executor.shutdownNow();
                if (!sockets.opened.isEmpty()) throw new AssertionError("Socket leak");
            }
        }
    }
    public static void main(String[] args) throws Exception {
        byte[] raw = PacketCheck.ip(PacketCheck.query("example.com"));
        DnsPacket packet = DnsPacket.parse(raw, raw.length);
        PacketCheck.check(packet.udpLimit() == 512);
        byte[] full = packet.error(0);
        PacketCheck.check(Arrays.equals(exchange(packet, full, null, false), full));
        byte[] truncated = full.clone(); truncated[2] |= 2;
        PacketCheck.check(Arrays.equals(exchange(packet, truncated, full, false), full));
        byte[] large = Arrays.copyOf(full, 600);
        byte[] limited = exchange(packet, truncated, large, false);
        PacketCheck.check((limited[2] & 2) != 0 && (limited[3] & 15) == 0 && limited.length == packet.questionEnd);
        try { exchange(packet, truncated, full, true); throw new AssertionError("Short TCP body accepted"); }
        catch (EOFException expected) { }
        byte[] wrong = full.clone(); wrong[0] ^= 1;
        try { exchange(packet, wrong, null, false); throw new AssertionError("Wrong UDP transaction accepted"); }
        catch (IOException expected) { }
        byte[] edns = Arrays.copyOf(packet.query, packet.query.length + 11);
        DnsPacket.put16(edns, 10, 1); DnsPacket.put16(edns, packet.query.length + 1, 41);
        DnsPacket.put16(edns, packet.query.length + 3, 1232);
        byte[] ednsRaw = PacketCheck.ip(edns); DnsPacket opt = DnsPacket.parse(ednsRaw, ednsRaw.length);
        PacketCheck.check(opt.udpLimit() == 1232);
        PacketCheck.check(exchange(opt, truncated, large, false).length == 600);
        byte[] huge = Arrays.copyOf(full, 6000);
        PacketCheck.check(Arrays.equals(exchange(packet, truncated, huge, false, true),huge));
        System.out.println("DnsUpstream: UDP, TCP retry/framing, EDNS limits, truncation and socket cleanup passed");
    }
}
