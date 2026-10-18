package com.skensel.stopad.probe;

import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Test APK only: DNS checks without launching an Activity or turning on the display. */
public final class BackgroundProbe extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        ResultReceiver receiver=intent.getParcelableExtra("receiver");
        PendingResult pending=goAsync();
        new Thread(()->{
            Bundle result=new Bundle();
            try {
                result.putBoolean("screenOff",!context.getSystemService(PowerManager.class).isInteractive());
                ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
                Network network=cm.getActiveNetwork();NetworkCapabilities caps=cm.getNetworkCapabilities(network);
                if(caps==null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))throw new IOException("Game has no VPN network");
                byte[] denied=udp(network,"doubleclick.net"),normal=udp(network,"example.com");
                result.putBoolean("blocked",denied.length>=12 && (denied[3]&15)==3);
                result.putBoolean("forwarded",normal.length>=12 && (normal[3]&15)==0 && ((normal[6]&255)*256+(normal[7]&255))>0);
                try(Socket socket=network.getSocketFactory().createSocket()) {
                    socket.connect(new InetSocketAddress("10.111.0.2",53),2000);socket.setSoTimeout(2000);
                    byte[] query=query("doubleclick.net");DataOutputStream output=new DataOutputStream(socket.getOutputStream());
                    output.writeShort(query.length);output.write(query);output.flush();
                    DataInputStream input=new DataInputStream(socket.getInputStream());byte[] answer=new byte[input.readUnsignedShort()];input.readFully(answer);
                    result.putBoolean("tcpBlocked",answer.length>=12 && (answer[3]&15)==3);
                }
            } catch(Exception error) { result.putString("error",error.toString()); }
            finally { if(receiver!=null)receiver.send(0,result);pending.finish(); }
        },"background-dns-probe").start();
    }
    private static byte[] udp(Network network,String domain)throws IOException {
        try(DatagramSocket socket=new DatagramSocket()) {
            network.bindSocket(socket);socket.connect(new InetSocketAddress("10.111.0.2",53));socket.setSoTimeout(2000);
            byte[] query=query(domain);socket.send(new DatagramPacket(query,query.length));
            byte[] bytes=new byte[4096];DatagramPacket answer=new DatagramPacket(bytes,bytes.length);socket.receive(answer);
            return Arrays.copyOf(bytes,answer.getLength());
        }
    }
    private static byte[] query(String domain)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[]{0x24,0x68,1,0,0,1,0,0,0,0,0,0});
        for(String label:domain.split("\\.")){bytes.write(label.length());bytes.write(label.getBytes(StandardCharsets.US_ASCII));}
        bytes.write(new byte[]{0,0,1,0,1});return bytes.toByteArray();
    }
}
