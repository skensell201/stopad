package com.skensel.stopad;
import java.util.*;
public final class PolicyCheck {
    public static void main(String[] args) {
        var names = DomainPolicy.parseExceptions(" Ads.Example.com.\nреклама.рф\nads.example.com\n");
        check(names.size() == 2 && names.contains("ads.example.com"));
        var policy = new DomainPolicy(List.of("example.com"), names);
        check(!policy.blocks("video.ads.example.com"));
        check(policy.blocks("other.example.com"));
        check(policy.blocks("example.com"));
        check(!policy.blocks("_service._tcp.example.com"));
        check(DomainPolicy.parseExceptions("\n").isEmpty());
        for (String invalid : new String[]{"https://ads.example.com", "example.com/a", "*.example.com", "com", "1.2.3.4", "a..example.com", ".example.com", "foo.example:53"}) {
            try { DomainPolicy.parseExceptions(invalid); throw new AssertionError("Accepted: " + invalid); }
            catch (IllegalArgumentException expected) { }
        }
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 257; i++) huge.append("a").append(i).append(".example.com\n");
        try { DomainPolicy.parseExceptions(huge.toString()); throw new AssertionError("No size limit"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("DomainPolicy: precedence, IDN, validation and limits passed");
    }
    static void check(boolean value) { if (!value) throw new AssertionError(); }
}
