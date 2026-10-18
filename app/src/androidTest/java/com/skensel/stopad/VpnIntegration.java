package com.skensel.stopad;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Run only with the isolated emulator probes installed. No production backdoor or exported test API. */
public final class VpnIntegration extends Instrumentation {
    private Context context;
    private final String game = "com.skensel.stopad.probe.game", browser = "com.skensel.stopad.probe.browser";
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Set<String> original = Collections.emptySet();
        boolean ok = false;
        try {
            context = getTargetContext();
            original = new HashSet<>(context.getSharedPreferences("games", 0).getStringSet("selected", Collections.emptySet()));
            require(!AdVpnService.active(), "Start test with VPN off");
            startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            require(VpnService.prepare(context) == null, "Grant emulator VPN app-op before test");
            require(GameCatalog.load(context).stream().anyMatch(a -> a.packageName.equals(game)), "Game visible");
            require(GameCatalog.load(context).stream().noneMatch(a -> a.packageName.equals(browser)), "Misclassified browser excluded");
            Bundle baseline = probe(game);
            require(baseline.getBoolean("adResolves") && baseline.getBoolean("normalResolves") && baseline.getBoolean("directTcp"), "Baseline network: " + baseline);
            select(Set.of(browser));
            require(GameCatalog.selected(context).isEmpty(), "Browser-only selection rejected");
            launchVpn(); Thread.sleep(1000);
            require(!AdVpnService.active(), "Empty allowed list must not establish VPN");
            select(Set.of(game)); launchVpn();
            awaitActive(true);
            Bundle filtered = probe(game);
            android.util.Log.i("StopAdTest", "filtered=" + filtered + " blocked=" + AdVpnService.blocked() + " forwarded=" + AdVpnService.forwarded() + " failed=" + AdVpnService.failed());
            require(!filtered.getBoolean("adResolves") && filtered.getBoolean("normalResolves") && filtered.getBoolean("directTcp"), "Selected game: " + filtered);
            Bundle bypass = probe(browser);
            require(bypass.getBoolean("adResolves") && bypass.getBoolean("normalResolves") && bypass.getBoolean("directTcp"), "Browser bypass: " + bypass);
            require(AdVpnService.blocked() > 0 && AdVpnService.forwarded() > 0, "Real packet counters");
            result.putString("counters", "blocked=" + AdVpnService.blocked() + ", forwarded=" + AdVpnService.forwarded() + ", failed=" + AdVpnService.failed());
            context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP)); awaitActive(false);
            // netd caches NXDOMAIN briefly. Fresh probe process alone does not clear its network cache;
            // disconnect changes the network and should restore the physical resolver.
            Bundle restored = probe(game);
            require(restored.getBoolean("adResolves") && restored.getBoolean("normalResolves"), "Network restored: " + restored);
            launchVpn(); awaitActive(true);
            context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP)); awaitActive(false);
            result.putString("stream", "PASS: empty list, browser exclusion, DNS block/forward, direct TCP, stop/restore, restart");
            ok = true;
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure);
            android.util.Log.e("StopAdTest", "Integration failed", failure);
        } finally {
            if (context != null) {
                context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP));
                select(original);
            }
        }
        finish(ok ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void select(Set<String> packages) { context.getSharedPreferences("games", 0).edit().putStringSet("selected", new HashSet<>(packages)).commit(); }
    private void launchVpn() {
        startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        runOnMainSync(() -> context.startForegroundService(new Intent(context, AdVpnService.class).setAction(AdVpnService.START)));
    }
    private void awaitActive(boolean active) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (AdVpnService.active() != active && System.currentTimeMillis() < deadline) Thread.sleep(50);
        require(AdVpnService.active() == active, "VPN state " + active + ": " + AdVpnService.message);
    }
    private Bundle probe(String packageName) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Bundle[] result = new Bundle[1];
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override protected void onReceiveResult(int code, Bundle data) { result[0] = data; done.countDown(); }
        };
        Parcel parcel = Parcel.obtain();
        receiver.writeToParcel(parcel, 0); parcel.setDataPosition(0);
        ResultReceiver remote = ResultReceiver.CREATOR.createFromParcel(parcel); parcel.recycle();
        context.startActivity(new Intent().setClassName(packageName, "com.skensel.stopad.probe.ProbeActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("receiver", remote));
        require(done.await(25, TimeUnit.SECONDS), "Probe timeout: " + packageName);
        return result[0];
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
