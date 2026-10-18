package com.skensel.stopad;

import java.io.*;
import java.net.*;
import java.util.*;

/** Socket creation is supplied by the VPN service so all UDP and TCP sockets are protected/bound. */
final class DnsUpstream {
    interface Sockets {
        DatagramSocket udp() throws IOException;
        Socket tcp() throws IOException;
        void release(Closeable socket);
    }
    private final InetSocketAddress server;
    DnsUpstream(InetSocketAddress server) { this.server = server; }

    byte[] resolveTcp(DnsPacket packet, Sockets sockets) throws IOException {
        Socket tcp = sockets.tcp();
        try (tcp) {
            tcp.connect(server, 2000); tcp.setSoTimeout(2000);
            DataOutputStream output = new DataOutputStream(tcp.getOutputStream());
            output.writeShort(packet.query.length); output.write(packet.query); output.flush();
            DataInputStream input = new DataInputStream(tcp.getInputStream());
            int size = input.readUnsignedShort();
            if (size < 12) throw new IOException("Short TCP DNS response");
            byte[] answer = new byte[size]; input.readFully(answer);
            if (!packet.accepts(answer) || (answer[2] & 2) != 0) throw new IOException("Invalid TCP DNS response");
            return answer;
        } finally { sockets.release(tcp); }
    }

    byte[] resolve(DnsPacket packet, Sockets sockets) throws IOException {
        byte[] answer;
        DatagramSocket udp = sockets.udp();
        try (udp) {
            udp.connect(server); udp.setSoTimeout(2000);
            udp.send(new DatagramPacket(packet.query, packet.query.length));
            byte[] buffer = new byte[65535];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            udp.receive(reply); answer = Arrays.copyOf(buffer, reply.getLength());
            if (!packet.accepts(answer)) throw new IOException("Invalid UDP DNS response");
        } finally { sockets.release(udp); }
        if ((answer[2] & 2) != 0) answer = resolveTcp(packet,sockets);
        // UDP callers retry over the local TCP endpoint. Signal truncation, never a fake SERVFAIL,
        // when the complete response cannot fit the caller's advertised UDP size and TUN MTU.
        if (answer.length > Math.min(1472, packet.udpLimit())) {
            answer = packet.error(0); answer[2] |= 2;
        }
        return answer;
    }
}
