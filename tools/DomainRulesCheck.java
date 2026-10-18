import com.skensel.stopad.DomainRules;
import java.util.List;
public class DomainRulesCheck {
    public static void main(String[] args) {
        var rules = new DomainRules(List.of("Ads.Example.com.", "реклама.рф"));
        check(rules.blocks("ADS.example.com."));
        check(rules.blocks("video.ads.example.com"));
        check(!rules.blocks("notads.example.com"));
        check(!rules.blocks("ads.example.com.safe.org"));
        check(!rules.blocks("example.com"));
        check(rules.blocks(java.net.IDN.toASCII("реклама.рф")));
        try { rules.blocks(""); throw new AssertionError("Empty domain accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("DomainRules: checks passed");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
