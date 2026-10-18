package com.skensel.stopad.probe;
import android.app.Activity;
import android.os.*;
import android.widget.TextView;
import java.net.*;
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
            if (receiver != null) receiver.send(0, result);
            runOnUiThread(() -> { text.setText(result.toString()); finish(); });
        }, "network-probe").start();
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
