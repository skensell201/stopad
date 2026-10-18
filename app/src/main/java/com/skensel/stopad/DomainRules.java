package com.skensel.stopad;

import java.net.IDN;
import java.util.*;

/** Exact domains and their subdomains; never substring matching. */
public final class DomainRules {
    private final Set<String> blocked = new HashSet<>();
    public DomainRules(Collection<String> domains) {
        for (String domain : domains) blocked.add(normalize(domain));
    }
    public boolean blocks(String domain) {
        String candidate = normalize(domain);
        while (!candidate.isEmpty()) {
            if (blocked.contains(candidate)) return true;
            int dot = candidate.indexOf('.');
            if (dot < 0) return false;
            candidate = candidate.substring(dot + 1);
        }
        return false;
    }
    private static String normalize(String domain) {
        String value = Objects.requireNonNull(domain).trim();
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) throw new IllegalArgumentException("Empty domain");
        return IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    }
}
