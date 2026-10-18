package com.skensel.stopad;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.net.Network;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Run only with the isolated emulator probes installed. No production backdoor or exported test API. */
public final class VpnIntegration extends Instrumentation {
    private Context context;
    private boolean networkTests, listTests, uiTests, lifecycleTests, largeFontTests;
    private final String game = "com.skensel.stopad.probe.game", browser = "com.skensel.stopad.probe.browser", manualGame = "com.skensel.stopad.probe.manual";
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); largeFontTests=arguments!=null && "true".equals(arguments.getString("largeFonts")); lifecycleTests = arguments != null && "true".equals(arguments.getString("lifecycleTests")); uiTests = arguments != null && "true".equals(arguments.getString("uiTests")); listTests = arguments != null && "true".equals(arguments.getString("listTests")); networkTests = arguments != null && "true".equals(arguments.getString("networkTests")); start(); }
    @Override public void callActivityOnCreate(Activity activity, Bundle state) {
        if (uiTests) {
            activity.setTurnScreenOn(true); activity.setShowWhenLocked(true);
            activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        super.callActivityOnCreate(activity, state);
    }
    @Override public void onStart() {
        if (uiTests) { new UiIntegration(this).run(largeFontTests); return; }
        if (listTests) { new ListIntegration(this).run(); return; }
        Bundle result = new Bundle();
        Set<String> original = Collections.emptySet();
        Set<String> oldManual = Collections.emptySet(), oldExceptions = Collections.emptySet();
        boolean ok = false;
        try {
            context = getTargetContext();
            original = new HashSet<>(context.getSharedPreferences("games", 0).getStringSet("selected", Collections.emptySet()));
            oldManual = new HashSet<>(context.getSharedPreferences("games", 0).getStringSet("manual", Collections.emptySet()));
            oldExceptions = new HashSet<>(context.getSharedPreferences("games", 0).getStringSet("domain_exceptions", Collections.emptySet()));
            context.getSharedPreferences("games", 0).edit().remove("manual").remove("domain_exceptions").commit();
            require(!AdVpnService.active(), "Start test with VPN off");
            startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long listDeadline = SystemClock.elapsedRealtime() + 30000;
            while (BlocklistRepository.snapshot == null && BlocklistRepository.busy && SystemClock.elapsedRealtime() < listDeadline) Thread.sleep(50);
            require(BlocklistRepository.snapshot != null, "List ready: " + BlocklistRepository.message);
            require(VpnService.prepare(context) == null, "Grant emulator VPN app-op before test");
            require(GameCatalog.load(context).stream().anyMatch(a -> a.packageName.equals(game)), "Game visible");
            require(GameCatalog.load(context).stream().noneMatch(a -> a.packageName.equals(browser)), "Misclassified browser excluded");
            require(GameCatalog.candidates(context).stream().anyMatch(a -> a.packageName.equals(manualGame)), "Uncategorized game candidate visible");
            require(GameCatalog.load(context).stream().noneMatch(a -> a.packageName.equals(manualGame)), "Uncategorized app not automatically classified as game");
            Bundle baseline = probe(game);
            require(baseline.getBoolean("expandedResolves") && baseline.getBoolean("adResolves") && baseline.getBoolean("normalResolves") && baseline.getBoolean("directTcp"), "Baseline network: " + baseline);
            select(Set.of(browser));
            require(GameCatalog.selected(context).isEmpty(), "Browser-only selection rejected");
            launchVpn(); Thread.sleep(1000);
            require(!AdVpnService.active(), "Empty allowed list must not establish VPN");
            select(Set.of(game)); launchVpn();
            awaitActive(true);
            Bundle filtered = probe(game);
            android.util.Log.i("StopAdTest", "filtered=" + filtered + " blocked=" + AdVpnService.blocked() + " forwarded=" + AdVpnService.forwarded() + " failed=" + AdVpnService.failed());
            require(!filtered.getBoolean("expandedResolves") && !filtered.getBoolean("adResolves") && filtered.getBoolean("normalResolves") && filtered.getBoolean("directTcp"), "Selected game: " + filtered);
            require(filtered.getBoolean("tcpDnsBlocked") && filtered.getBoolean("tcpDnsNormal"), "DNS/TCP block and pipeline forward: " + filtered);
            require(filtered.getBoolean("tcpDnsLarge") && filtered.getBoolean("udpTcpFallback"), "Large TCP answer and native UDP-to-TCP fallback: " + filtered);
            android.util.Log.i("StopAdTest","TCP DNS bytes="+filtered.getInt("tcpDnsLargeBytes")+", native fallback bytes="+filtered.getInt("fallbackBytes"));
            Bundle bypass = probe(browser);
            require(bypass.getBoolean("expandedResolves") && bypass.getBoolean("adResolves") && bypass.getBoolean("normalResolves") && bypass.getBoolean("directTcp"), "Browser bypass: " + bypass);
            require(AdVpnService.blocked() > 0 && AdVpnService.forwarded() > 0, "Real packet counters");
            result.putString("counters", "blocked=" + AdVpnService.blocked() + ", forwarded=" + AdVpnService.forwarded() + ", failed=" + AdVpnService.failed());
            context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP)); awaitActive(false);
            // netd caches NXDOMAIN briefly. Fresh probe process alone does not clear its network cache;
            // disconnect changes the network and should restore the physical resolver.
            Bundle restored = probe(game);
            require(restored.getBoolean("adResolves") && restored.getBoolean("normalResolves"), "Network restored: " + restored);
            context.getSharedPreferences("games", 0).edit().putStringSet("domain_exceptions", Set.of("doubleclick.net")).commit();
            launchVpn(); awaitActive(true);
            Bundle exception = probe(game);
            require(exception.getBoolean("adResolves") && exception.getBoolean("normalResolves"), "Exception overrides block: " + exception);
            context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP)); awaitActive(false);
            context.getSharedPreferences("games", 0).edit().remove("domain_exceptions").putStringSet("manual", Set.of(manualGame, browser)).commit();
            require(GameCatalog.load(context).stream().noneMatch(a -> a.packageName.equals(browser)), "Manual browser still excluded");
            select(Set.of(manualGame)); launchVpn(); awaitActive(true);
            Bundle manual = probe(manualGame);
            require(!manual.getBoolean("adResolves") && manual.getBoolean("normalResolves"), "Manually added game filtered: " + manual);
            if (networkTests) networkTransitions();
            if (lifecycleTests) screenOffProbe();
            context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP)); awaitActive(false);
            result.putString("stream", "PASS: empty list, browser exclusion, DNS block/forward including expanded list, direct TCP, DNS/TCP pipeline and large native fallback, stop/restore, exceptions, manual game, restart" + (networkTests ? ", Wi-Fi/mobile transitions, Private DNS stop" : "") + (lifecycleTests ? ", screen-off background UDP/TCP" : ""));
            ok = true;
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure);
            android.util.Log.e("StopAdTest", "Integration failed", failure);
        } finally {
            if (context != null) {
                context.startService(new Intent(context, AdVpnService.class).setAction(AdVpnService.STOP));
                select(original);
                context.getSharedPreferences("games", 0).edit().putStringSet("manual", oldManual).putStringSet("domain_exceptions", oldExceptions).commit();
            }
        }
        finish(ok ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void screenOffProbe() throws Exception {
        PowerManager power=context.getSystemService(PowerManager.class);
        boolean interactive=power.isInteractive();
        try {
            shell("cmd power sleep");
            long deadline=SystemClock.elapsedRealtime()+5000;
            while(power.isInteractive() && SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
            require(!power.isInteractive(),"Emulator screen must be off before background test");
            Thread.sleep(1000);
            CountDownLatch done=new CountDownLatch(1);Bundle[] answer=new Bundle[1];
            ResultReceiver receiver=new ResultReceiver(new Handler(Looper.getMainLooper())) {
                @Override protected void onReceiveResult(int code,Bundle data) { answer[0]=data;done.countDown(); }
            };
            Parcel parcel=Parcel.obtain();receiver.writeToParcel(parcel,0);parcel.setDataPosition(0);
            ResultReceiver remote=ResultReceiver.CREATOR.createFromParcel(parcel);parcel.recycle();
            context.sendBroadcast(new Intent().setClassName(manualGame,"com.skensel.stopad.probe.BackgroundProbe").putExtra("receiver",remote));
            require(done.await(12,TimeUnit.SECONDS),"Background probe timed out");Bundle result=answer[0];
            require(result.getBoolean("screenOff") && !power.isInteractive(),"DNS probe must not wake screen: "+result);
            require(result.getBoolean("blocked") && result.getBoolean("forwarded") && result.getBoolean("tcpBlocked"),"Background DNS with screen off: "+result);
            require(AdVpnService.active(),"VPN must remain active with screen off");
        } finally { if(interactive)shell("cmd power wakeup"); }
    }
    private void networkTransitions() throws Exception {
        String wifi = shell("settings get global wifi_on").trim();
        String dnsMode = shell("settings get global private_dns_mode").trim();
        String dnsName = shell("settings get global private_dns_specifier").trim();
        try {
            // Only used by --network on a disposable emulator; caller explicitly selects an emulator serial.
            shell("settings put global private_dns_mode off");
            shell("svc wifi enable");
            awaitTransport(NetworkCapabilities.TRANSPORT_WIFI);
            if (!AdVpnService.active()) { launchVpn(); awaitActive(true); }
            shell("svc wifi disable");
            awaitTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            Bundle cellular = probe(manualGame);
            require(!cellular.getBoolean("adResolves") && cellular.getBoolean("normalResolves") && cellular.getBoolean("directTcp"), "Mobile transition: " + cellular);
            shell("svc wifi enable");
            awaitTransport(NetworkCapabilities.TRANSPORT_WIFI);
            Bundle wifiResult = probe(manualGame);
            require(!wifiResult.getBoolean("adResolves") && wifiResult.getBoolean("normalResolves") && wifiResult.getBoolean("directTcp"), "Wi-Fi return: " + wifiResult);
            shell("settings put global private_dns_specifier dns.google");
            shell("settings put global private_dns_mode hostname");
            long deadline = System.currentTimeMillis() + 20000;
            while (AdVpnService.active() && System.currentTimeMillis() < deadline) Thread.sleep(100);
            require(!AdVpnService.active() && AdVpnService.message.contains("Private DNS"), "Stop when Private DNS enabled: " + AdVpnService.message);
            require(ProtectionReadiness.check(context, true, true) == ProtectionReadiness.State.PRIVATE_DNS,
                    "Preflight detects active Private DNS");
            launchVpn(); Thread.sleep(1000);
            require(!AdVpnService.active() && AdVpnService.message.contains("Private DNS"), "Private DNS must prevent establishment");
        } finally {
            restoreSetting("private_dns_specifier", dnsName);
            restoreSetting("private_dns_mode", dnsMode);
            shell("svc wifi " + ("1".equals(wifi) ? "enable" : "disable"));
        }
    }
    private void awaitTransport(int transport) throws Exception {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        long deadline = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < deadline) {
            AdVpnService.Session session = AdVpnService.current;
            NetworkCapabilities caps = session == null ? null : cm.getNetworkCapabilities(session.network);
            if (caps != null && caps.hasTransport(transport) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Default transport did not change: " + transport + ": " + AdVpnService.message);
    }
    private String shell(String command) throws Exception {
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            for (int count; (count = input.read(buffer)) != -1;) bytes.write(buffer, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
    private void restoreSetting(String name, String value) throws Exception {
        // Values come from Android settings; accept only the known alphabet before constructing a shell command.
        if (value.equals("null") || value.isEmpty()) shell("settings delete global " + name);
        else if (value.matches("[a-zA-Z0-9._-]+")) shell("settings put global " + name + " " + value);
        else throw new IllegalArgumentException("Unexpected test setting value");
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
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("receiver", remote).putExtra("expectVpn", AdVpnService.active() && GameCatalog.selected(context).contains(packageName)));
        require(done.await(45, TimeUnit.SECONDS), "Probe timeout: " + packageName);
        return result[0];
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
