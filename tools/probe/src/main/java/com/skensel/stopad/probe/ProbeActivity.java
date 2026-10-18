package com.skensel.stopad.probe;
import android.app.Activity;
import android.os.*;
import android.widget.TextView;
import java.net.*;
import java.io.*;
import android.net.DnsResolver;
import android.net.Network;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.util.List;
import java.util.concurrent.*;
public final class ProbeActivity extends Activity {
    private Network dnsNetwork;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this); text.setText("StopAd network probe…"); setContentView(text);
        ResultReceiver receiver = getIntent().getParcelableExtra("receiver");
        new Thread(() -> {
            Bundle result = new Bundle();
            boolean expectedVpn = getIntent().getBooleanExtra("expectVpn", false);
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline) {
                Network network = cm.getActiveNetwork();
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == expectedVpn) {
                    dnsNetwork = network; break;
                }
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            result.putBoolean("networkReady", dnsNetwork != null);
            result.putBoolean("adResolves", resolves("doubleclick.net"));
            result.putBoolean("normalResolves", resolves("example.com"));
            result.putBoolean("expandedResolves", resolves("s0.2mdn.net"));
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("1.1.1.1", 443), 4000);
                result.putBoolean("directTcp", true);
            } catch (Exception failure) { result.putBoolean("directTcp", false); result.putString("tcpError", failure.toString()); }
            if (expectedVpn && dnsNetwork != null) { tcpDns(result); largeDns(result); }
            if (receiver != null) receiver.send(0, result);
            runOnUiThread(() -> { text.setText(result.toString()); finish(); });
        }, "network-probe").start();
    }
    // Real kernel TCP through the selected app's VPN; pipelined DNS frames in one reusable connection.
    private void tcpDns(Bundle result) {
        try (Socket socket = dnsNetwork.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress("10.111.0.2", 53), 2000); socket.setSoTimeout(4000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            for (String domain : new String[]{"doubleclick.net", "example.com", "org"}) {
                ByteArrayOutputStream query = new ByteArrayOutputStream();
                query.write(new byte[]{0x45,0x67,1,0,0,1,0,0,0,0,0,0});
                for (String label : domain.split("\\.")) { query.write(label.length()); query.write(label.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
                query.write(new byte[]{0,0,(byte)(domain.equals("org")?48:1),0,1}); byte[] bytes = query.toByteArray();
                output.writeShort(bytes.length); output.write(bytes);
            }
            output.flush();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            byte[] blocked = new byte[input.readUnsignedShort()]; input.readFully(blocked);
            byte[] normal = new byte[input.readUnsignedShort()]; input.readFully(normal);
            result.putBoolean("tcpDnsBlocked", blocked.length >= 12 && (blocked[3] & 15) == 3);
            result.putBoolean("tcpDnsNormal", normal.length >= 12 && (normal[3] & 15) == 0 && ((normal[6] & 255) * 256 + (normal[7] & 255)) > 0);
            byte[] large=new byte[input.readUnsignedShort()];input.readFully(large);
            result.putInt("tcpDnsLargeBytes",large.length);
            result.putBoolean("tcpDnsLarge",large.length>512 && (large[3]&15)==0 && (large[2]&2)==0);
        } catch (Exception failure) { result.putString("tcpDnsError", failure.toString()); }
    }
    private void largeDns(Bundle result) {
        // No EDNS: org DNSKEY currently exceeds 512 bytes and exercises netd UDP -> TCP retry.
        byte[] query={0x32,0x10,1,0,0,1,0,0,0,0,0,0,3,111,114,103,0,0,48,0,1};
        CountDownLatch done=new CountDownLatch(1);
        DnsResolver.getInstance().rawQuery(dnsNetwork,query,DnsResolver.FLAG_NO_CACHE_LOOKUP|DnsResolver.FLAG_NO_CACHE_STORE,
                Runnable::run,null,new DnsResolver.Callback<byte[]>() {
                    @Override public void onAnswer(byte[] answer,int code) {
                        result.putInt("fallbackBytes",answer.length);
                        result.putBoolean("udpTcpFallback",code==0 && answer.length>512 && (answer[2]&2)==0);
                        done.countDown();
                    }
                    @Override public void onError(DnsResolver.DnsException error) { result.putString("fallbackError",error.toString());done.countDown(); }
                });
        try { if(!done.await(10,TimeUnit.SECONDS))result.putString("fallbackError","Timeout"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    private boolean resolves(String domain) {
        if (dnsNetwork == null) return false;
        CountDownLatch done = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        DnsResolver.getInstance().query(dnsNetwork, domain, DnsResolver.TYPE_A,
                DnsResolver.FLAG_NO_CACHE_LOOKUP | DnsResolver.FLAG_NO_CACHE_STORE,
                Runnable::run, null, new DnsResolver.Callback<List<InetAddress>>() {
                    @Override public void onAnswer(List<InetAddress> answers, int code) {
                        result[0] = code == 0 && !answers.isEmpty(); done.countDown();
                    }
                    @Override public void onError(DnsResolver.DnsException error) { done.countDown(); }
                });
        try { return done.await(10, TimeUnit.SECONDS) && result[0]; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
}
