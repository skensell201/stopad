package com.skensel.stopad;

import java.net.IDN;
import java.util.*;

/** Immutable per-session rules: user exceptions override block rules, including subdomains. */
final class DomainPolicy {
    private final DomainRules blocked, allowed;
    DomainPolicy(Collection<String> blocked, Collection<String> allowed) {
        this.blocked = new DomainRules(blocked);
        this.allowed = new DomainRules(allowed);
    }
    boolean blocks(String domain) {
        try { return !allowed.blocks(domain) && blocked.blocks(domain); }
        catch (IllegalArgumentException nonHostName) { return false; } // Valid DNS SRV labels can contain underscores.
    }
    static Set<String> parseExceptions(String text) {
        Set<String> domains = new TreeSet<>();
        if (text.length() > 65536) throw new IllegalArgumentException("Слишком длинный список");
        for (String raw : text.split("\\R")) {
            raw = raw.trim(); if (raw.isEmpty()) continue;
            if (raw.endsWith(".")) raw = raw.substring(0, raw.length() - 1);
            String name = IDN.toASCII(raw, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (name.length() > 253 || !name.contains(".") || name.matches("[0-9.]+"))
                throw new IllegalArgumentException("Нужен домен, например ads.example.com");
            domains.add(name);
            if (domains.size() > 256) throw new IllegalArgumentException("Максимум 256 доменов");
        }
        return domains;
    }
}
