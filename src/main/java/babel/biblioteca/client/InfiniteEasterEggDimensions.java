package babel.biblioteca.client;

import babel.biblioteca.Compat;
import babel.biblioteca.compat.InfiniteDimensionKey;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Original book/warp strings, checked against the snapshot's 43 hard-coded generator IDs.
 * Source: https://minecraft.wiki/w/Java_Edition_20w14%E2%88%9E#Easter_egg_dimensions
 *
 * Merely listing settings must not register dimensions or call DimHash.getHash: the former
 * eagerly loads worlds at startup, and the latter changes the last-page puzzle's passphrase.
 */
public final class InfiniteEasterEggDimensions {
    public record Entry(String name, int id) {
        public InfiniteDimensionKey key() { return Compat.dimensionKey(Compat.id("_generated", Integer.toString(id))); }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("ant", 726931095),
            new Entry("basic", 236157810),
            new Entry("blacklight", 1174283440),
            new Entry("blue", 1049823113),
            new Entry("brand", 344885676),
            new Entry("bridges", 1113696725),
            new Entry("busy", 1028465021),
            new Entry("checkerboard", 741472677),
            new Entry("chess", 94341406),
            new Entry("colors", 378547252),
            new Entry("content", 1201319931),
            new Entry("credits", 1929426645),
            new Entry("custom", 484336196),
            new Entry("darkness", 1210674279),
            new Entry("decay", 1537997313),
            new Entry("fleet", 1896587401),
            new Entry("gallery", 307219718),
            new Entry("green", 1143264807),
            new Entry("holes", 894945615),
            new Entry("isolation", 1098962767),
            new Entry("library", 1902968744),
            new Entry("llama", 31674686),
            new Entry("message", 2003598857),
            new Entry("missing", 1011847535),
            new Entry("notes", 1834117187),
            new Entry("nothing", 1036032341),
            new Entry("origin", 107712651),
            new Entry("patterns", 661885389),
            new Entry("perfection", 264458659),
            new Entry("pillars", 251137100),
            new Entry("red", 2114493792),
            new Entry("retro", 1384344230),
            new Entry("rooms", 985130845),
            new Entry("shapes", 669175628),
            new Entry("skygrid", 927632079),
            new Entry("slime", 1059552697),
            new Entry("spiral", 907661935),
            new Entry("sponge", 233542201),
            new Entry("terminal", 214387762),
            new Entry("this_is_a_very_long_phrase_that_hopefully_is_not_in_any_dictionary", 1791460938),
            new Entry("tunnels", 1141490659),
            new Entry("wall", 545072168),
            new Entry("zones", 1916276638));
    private static final List<InfiniteDimensionKey> VANILLA = List.of(
            Compat.dimensionKey(Compat.id("minecraft", "overworld")),
            Compat.dimensionKey(Compat.id("minecraft", "the_nether")),
            Compat.dimensionKey(Compat.id("minecraft", "the_end")));
    private static final Map<InfiniteDimensionKey, String> NAMES = ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(Entry::key, Entry::name));
    private static final List<InfiniteDimensionKey> CREATION = Stream.concat(VANILLA.stream(), ENTRIES.stream().map(Entry::key)).toList();

    private InfiniteEasterEggDimensions() {}
    public static List<Entry> entries() { return ENTRIES; }
    public static List<InfiniteDimensionKey> forCreation() { return CREATION; }
    public static String displayName(InfiniteDimensionKey key) { return NAMES.getOrDefault(key, key.toString()); }

    public static List<InfiniteDimensionKey> ordered(List<InfiniteDimensionKey> dimensions) {
        return dimensions.stream().sorted(Comparator.<InfiniteDimensionKey>comparingInt(key -> {
            int index = VANILLA.indexOf(key);
            return index < 0 ? VANILLA.size() : index;
        }).thenComparing(InfiniteEasterEggDimensions::displayName)).toList();
    }
}
