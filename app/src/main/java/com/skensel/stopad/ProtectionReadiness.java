package com.skensel.stopad;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;

/** Local prerequisites only; READY does not mean a VPN is established or upstream is reachable. */
final class ProtectionReadiness {
    enum State { READY, NO_GAMES, LIST_LOADING, NO_NETWORK, PRIVATE_DNS }
    private ProtectionReadiness() { }
    static State check(Context context, boolean hasGames, boolean listReady) {
        if (!hasGames) return State.NO_GAMES;
        if (!listReady) return State.LIST_LOADING;
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        Network network = cm.getActiveNetwork();
        if (network == null) return State.NO_NETWORK;
        LinkProperties link = cm.getLinkProperties(network);
        if (Build.VERSION.SDK_INT >= 28 && link != null && link.isPrivateDnsActive()) return State.PRIVATE_DNS;
        return State.READY;
    }
}
