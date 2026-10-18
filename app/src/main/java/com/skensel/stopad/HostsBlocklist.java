package com.skensel.stopad;

import java.io.*;
import java.net.IDN;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.Pattern;

/** Strict parser for The Block List Project hosts format; never interpret HTML or arbitrary IP mappings as rules. */
final class HostsBlocklist {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    static final int MAX_BYTES = 12 * 1024 * 1024, MAX_DOMAINS = 350000;
    private static String canonical(String value) throws IOException {
        boolean ascii = true;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) > 127) { ascii = false; break; }
        try { value = ascii ? value.toLowerCase(Locale.ROOT) : IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid host in list", e); }
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        if (value.length() > 253 || value.indexOf('.') < 1) throw new IOException("Invalid host in list");
        int label = 0; boolean numeric = true;
        for (int i = 0; i <= value.length(); i++) {
            char c = i == value.length() ? '.' : value.charAt(i);
            if (c == '.') {
                if (label < 1 || label > 63 || value.charAt(i - label) == '-' || value.charAt(i - 1) == '-') throw new IOException("Invalid DNS label");
                label = 0;
            } else {
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) throw new IOException("Invalid DNS label");
                if (c < '0' || c > '9') numeric = false;
                label++;
            }
        }
        if (numeric) throw new IOException("IP is not a domain");
        return value;
    }
    static Set<String> parse(byte[] raw) throws IOException {
        if (raw.length == 0 || raw.length > MAX_BYTES) throw new IOException("Invalid list size");
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        Set<String> names = new HashSet<>();
        int unsupported = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(raw), decoder))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.length() > 4096) throw new IOException("List line too long");
                int comment = line.indexOf('#');
                if (comment >= 0) line = line.substring(0, comment);
                line = line.trim(); if (line.isEmpty()) continue;
                String[] fields = WHITESPACE.split(line);
                if (fields.length < 2 || !(fields[0].equals("127.0.0.1") || fields[0].equals("0.0.0.0")
                        || fields[0].equals("::1") || fields[0].equals("::"))) throw new IOException("Unsupported hosts record");
                for (int i = 1; i < fields.length; i++) {
                    if (fields[i].equalsIgnoreCase("localhost") || fields[i].equalsIgnoreCase("localhost.localdomain")) continue;
                    if (fields[i].contains("_")) {
                        if (++unsupported > 1000) throw new IOException("Too many unsupported hosts");
                        continue;
                    }
                    String name = canonical(fields[i]);
                    names.add(name);
                    if (names.size() > MAX_DOMAINS) throw new IOException("Too many domains");
                }
            }
        }
        for (String essential : new String[]{"example.com", "github.com", "raw.githubusercontent.com", "google.com", "cloudflare.com"})
            if (names.contains(essential)) throw new IOException("List blocks an essential domain");
        return Collections.unmodifiableSet(names);
    }
}
