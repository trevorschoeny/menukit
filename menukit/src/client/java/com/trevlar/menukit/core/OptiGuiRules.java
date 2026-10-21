package com.trevlar.menukit.core;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Container art chosen by OptiGUI rules, per section. OptiGUI picks one texture per screen,
 * from the block used to open it, so a lime box's screen has lime art and a chest's screen has
 * none of the boxes' colours. A container region shows several containers on one screen, so
 * MenuKit evaluates the pack's rules itself, once per section, against that section's own
 * container: its block (and so its dye), its custom name, its biome and its height.
 *
 * <p><b>Only while OptiGUI is installed</b> ({@link #LOADED}). Without it, every texture
 * is returned as it was given, so nothing changes.
 *
 * <h3>What OptiGUI does, followed here</h3>
 * Ported from OptiGUI 2.3.0-beta.10+26.3's source (its {@code FilterLoader},
 * {@code OptiFineFilterLoader}, {@code OptiGuiFilterLoader}, {@code ContainerMapFilter} and
 * selectors), so a section gets what OptiGUI would give that container's own screen:
 * <ul>
 *   <li>Both formats: OptiFine {@code .properties} under {@code optifine/gui/container/}
 *       ({@code container=}, {@code texture=}, {@code texture.<path>=}, with {@code colors=},
 *       {@code name=}, {@code biomes=}, {@code heights=}, and the chest's {@code large=},
 *       {@code trapped=}, {@code ender=}, {@code christmas=}), and OptiGUI's {@code .ini}
 *       under {@code optigui:gui/} (a section per container id, {@code replacement=},
 *       {@code load.priority=} and any selector).</li>
 *   <li>Order: the {@code .ini} rules first, then the {@code .properties} ones (the order
 *       OptiGUI registers its loaders), each by file path; then sorted by priority, highest
 *       first, keeping that order between equals (a stable sort).</li>
 *   <li>Evaluation: only a texture some rule replaces is replaced at all; the first rule of
 *       the container's own whose selectors all hold wins, else the first of the rules for
 *       no container ({@code texture.<path>=}).</li>
 *   <li>A selector OptiGUI can't build (a bad biome id, a malformed range) is dropped from
 *       its rule, as OptiGUI drops it; an unknown key is ignored.</li>
 * </ul>
 *
 * <h3>Where it is used</h3>
 * {@link #forContainer} for a section that knows its container; {@link #onScreen} for one
 * that doesn't, and for the inventory panel: what OptiGUI itself resolves for the open
 * screen's own container. MenuKit's own blits never go through OptiGUI's whole-screen swap:
 * every cut piece is drawn with the float-UV {@code blit(Identifier, int x4, float x4)},
 * which OptiGUI's hook (on the 13-argument {@code blit}) never sees ({@link PackLook}).
 *
 * <h3>Cached</h3>
 * The rules are read once per resource reload, on first use; each section context's answer
 * is kept until the next reload. Nothing is read per frame.
 */
@ApiStatus.Internal
public final class OptiGuiRules {

    private OptiGuiRules() {}

    private static final Logger LOG = LoggerFactory.getLogger("MenuKit/OptiGUI");

    /** Whether OptiGUI is installed: the only case anything here changes a texture. */
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("optigui");

    // The input

    /**
     * What a rule is evaluated against: one container, drawn with {@code texture}.
     *
     * @param container the container's id, its block's ({@code minecraft:lime_shulker_box})
     * @param name      its custom name, else its screen's title (OptiGUI's name input)
     * @param biome     the biome it is in, {@code y} its height ({@code null}: unknown, so no biome or height rule holds)
     * @param large     whether it is a double chest ({@code null}: not a placed chest)
     */
    public record Input(Identifier container, Identifier texture, String name, @Nullable Identifier biome,
                        @Nullable Integer y, @Nullable Boolean large, String playerName,
                        @Nullable Identifier playerBiome, @Nullable Integer playerY, LocalDate today) {}

    /** One rule: what it replaces its container's texture with, when every selector holds. */
    record Rule(int priority, @Nullable Identifier container, Identifier replacement, Set<Identifier> replaceable,
                List<Predicate<Input>> selectors) {
        boolean holds(Input in) {
            for (Predicate<Input> s : selectors) if (!s.test(in)) return false;
            return true;
        }
    }

    /** A rule file's text, by its resource id. */
    public record Source(Identifier id, String text) {}

    /** A set of packs' rules, as OptiGUI loads them: by container, in the order they are tried. */
    public static final class Rules {
        private final Map<@Nullable Identifier, List<Rule>> byContainer;
        private final Set<Identifier> replaceable, replacements;

        Rules(Map<@Nullable Identifier, List<Rule>> byContainer, Set<Identifier> replaceable) {
            this.byContainer = byContainer;
            this.replaceable = replaceable;
            java.util.HashSet<Identifier> out = new java.util.HashSet<>();
            byContainer.values().forEach(l -> l.forEach(r -> out.add(r.replacement())));
            this.replacements = Set.copyOf(out);
        }

        /** Whether some rule swaps a container texture for {@code texture}. */
        public boolean replacesWith(Identifier texture) {
            return replacements.contains(texture);
        }

        /** The texture OptiGUI would draw for {@code in}, or {@code null} where no rule replaces it. Pure. */
        public @Nullable Identifier evaluate(Input in) {
            if (!replaceable.contains(in.texture())) return null;
            for (Rule r : byContainer.getOrDefault(in.container(), List.of())) if (r.holds(in)) return r.replacement();
            for (Rule r : byContainer.getOrDefault(null, List.of())) if (r.holds(in)) return r.replacement();
            return null;
        }

        /** How many rules were loaded, for the checks. */
        public int size() {
            return byContainer.values().stream().mapToInt(List::size).sum();
        }
    }

    // Loading, pure over the files

    /**
     * Loads the rules from {@code ini} (OptiGUI's {@code .ini} files) and {@code properties}
     * (OptiFine's), as OptiGUI does. {@code defaults} is each container's own texture (OptiGUI
     * reads it from every mod's {@code optigui:container_default_gui_textures}); {@code exists}
     * says whether a texture is in the packs; {@code modLoaded} answers {@code if.mods}.
     */
    public static Rules load(List<Source> ini, List<Source> properties, Map<Identifier, Identifier> defaults,
                             Predicate<Identifier> exists, Predicate<String> modLoaded) {
        List<Raw> raws = new ArrayList<>();
        ini.stream().sorted(Comparator.comparing(s -> s.id().toString())).forEach(s -> loadIni(s, defaults, raws));
        properties.stream().sorted(Comparator.comparing(s -> s.id().toString()))
                .forEach(s -> loadProperties(s, defaults, exists, raws));

        Map<@Nullable Identifier, List<Rule>> by = new LinkedHashMap<>();
        java.util.HashSet<Identifier> replaceable = new java.util.HashSet<>();
        for (Raw raw : raws) {
            if (skippedOnLoad(raw, modLoaded) || raw.replaceable().isEmpty() || !exists.test(raw.replacement())) continue;
            List<Predicate<Input>> selectors = new ArrayList<>();
            for (Map.Entry<String, String> e : raw.selectors().entrySet()) {
                try {
                    Predicate<Input> s = selector(e.getKey(), e.getValue());
                    if (s != null) selectors.add(s);
                } catch (RuntimeException ex) {
                    // OptiGUI logs and drops a selector it can't build; the rule stays.
                    LOG.debug("Dropping selector {}={} in {}: {}", e.getKey(), e.getValue(), raw.resource(), ex.toString());
                }
            }
            replaceable.addAll(raw.replaceable());
            by.computeIfAbsent(raw.container(), k -> new ArrayList<>())
                    .add(new Rule(raw.priority(), raw.container(), raw.replacement(), raw.replaceable(), selectors));
        }
        // Highest priority first; List.sort is stable, so equals keep their load order.
        by.values().forEach(l -> l.sort(Comparator.comparingInt(Rule::priority).reversed()));
        return new Rules(by, Set.copyOf(replaceable));
    }

    /** A rule as read, before its selectors are built. */
    private record Raw(int priority, Identifier resource, @Nullable Identifier container, Identifier replacement,
                       Set<Identifier> replaceable, Map<String, String> selectors) {}

    /** OptiGUI's load selectors: {@code if} and {@code if.mods}, which skip a rule on load. */
    private static boolean skippedOnLoad(Raw raw, Predicate<String> modLoaded) {
        for (Map.Entry<String, String> e : raw.selectors().entrySet()) {
            String v = e.getValue();
            try {
                if (e.getKey().equals("if") && !strictBoolean(v)) return true;
                if (e.getKey().equals("if.mods")) {
                    List<String> mods = split(v);
                    if (mods.isEmpty()) return true;
                    for (String m : mods) if (!modLoaded.test(m)) return true;
                }
            } catch (RuntimeException ex) {
                return true;
            }
        }
        return false;
    }

    /** OptiGUI's {@code .ini}: a section per container id (or several), {@code replacement=} beside the file. */
    private static void loadIni(Source src, Map<Identifier, Identifier> defaults, List<Raw> out) {
        for (Map.Entry<String, Map<String, String>> section : ini(src.text()).entrySet()) {
            List<Identifier> containers = new ArrayList<>();
            for (String c : split(section.getKey())) {
                if (c.startsWith("#")) continue;
                Identifier id = Identifier.tryParse(c);
                if (id != null) containers.add(id);
            }
            if (containers.isEmpty()) continue;
            Map<String, String> keys = new LinkedHashMap<>(section.getValue());
            String rep = keys.remove("replacement");
            Identifier replacement = rep == null ? null : resolvePath(rep, src.id(), null);
            if (replacement == null) continue;
            int priority = 0;
            String p = keys.remove("load.priority");
            if (p != null) {
                try { priority = Integer.parseInt(p); } catch (NumberFormatException e) { continue; }
            }
            for (Identifier container : containers) {
                Identifier tex = keys.containsKey("interaction.texture") ? Identifier.tryParse(keys.get("interaction.texture"))
                        : defaults.get(container);
                out.add(new Raw(priority, src.id(), container, replacement, tex == null ? Set.of() : Set.of(tex), keys));
            }
        }
    }

    /** OptiFine's {@code .properties}: one {@code container=}, its {@code texture=}, and any {@code texture.<path>=}. */
    private static void loadProperties(Source src, Map<Identifier, Identifier> defaults, Predicate<Identifier> exists,
                                       List<Raw> out) {
        Map<String, String> p = properties(src.text());
        if ("true".equals(p.get("optigui.ignore"))) return;
        String container = p.get("container");
        if (container == null) return;
        String texture = p.get("texture");
        if (texture != null) {
            Identifier rep = resolveTexture(texture, src.id(), exists);
            if (rep != null) for (String c : containers(container, p)) out.add(optiFine(src.id(), c, rep, p, defaults));
        }
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (!e.getKey().startsWith("texture.")) continue;
            String path = "textures/gui/" + e.getKey().substring("texture.".length());
            if (!path.endsWith(".png")) path += ".png";
            Identifier original = Identifier.tryParse(path), rep = resolveTexture(e.getValue(), src.id(), exists);
            if (original == null || rep == null) continue;
            Map<String, String> sel = new LinkedHashMap<>();
            sel.put("interaction.texture", original.toString());
            if (p.containsKey("biomes")) sel.put("player.biomes", p.get("biomes"));
            if (p.containsKey("heights")) sel.put("player.heights", p.get("heights"));
            out.add(new Raw(0, src.id(), null, rep, Set.of(original), sel));
        }
    }

    /**
     * The containers an OptiFine {@code container=} stands for, as OptiGUI expands it: a
     * shulker box per dye in {@code colors=} (the undyed box without it); a chest by
     * {@code trapped=} and {@code ender=}. Entities, the beacon and the player inventory are
     * left out: no region section is one.
     */
    // ponytail: the block containers only (OptiGUI's horse, villager, beacon and inventory rules carry selectors no section has).
    private static List<String> containers(String container, Map<String, String> p) {
        List<String> out = new ArrayList<>();
        switch (container) {
            case "anvil" -> out.addAll(List.of("anvil", "chipped_anvil", "damaged_anvil"));
            case "brewing_stand", "furnace", "hopper" -> out.add(container);
            case "crafting" -> out.add("crafting_table");
            case "enchantment" -> out.add("enchanting_table");
            case "dispenser" -> {
                String v = p.get("variants");
                if (v == null || split(v).isEmpty()) out.add("dispenser");
                else for (String s : new java.util.LinkedHashSet<>(split(v))) if (s.equals("dispenser") || s.equals("dropper")) out.add(s);
            }
            case "chest" -> {
                Boolean large = looseBoolean(p.get("large")), trapped = looseBoolean(p.get("trapped")), ender = looseBoolean(p.get("ender"));
                if (!Boolean.TRUE.equals(ender)) {
                    if (!Boolean.TRUE.equals(trapped)) out.add("chest");
                    if (!Boolean.FALSE.equals(trapped)) out.add("trapped_chest");
                }
                String name = p.get("name");
                if (!Boolean.FALSE.equals(ender) && !Boolean.TRUE.equals(large) && !Boolean.TRUE.equals(trapped)
                        && (name == null || name.isEmpty())) out.add("ender_chest");
            }
            case "shulker_box" -> {
                String colors = p.get("colors");
                List<String> dyes = colors == null ? List.of() : split(colors);
                if (colors == null || dyes.isEmpty()) out.add("shulker_box");
                else for (String d : new java.util.LinkedHashSet<>(dyes)) if (DYES.contains(d)) out.add(d + "_shulker_box");
            }
            default -> {}
        }
        return out;
    }

    private static final Set<String> DYES = Set.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** An OptiFine rule for one container: its own texture, and the selectors OptiGUI gives it. */
    private static Raw optiFine(Identifier resource, String container, Identifier rep, Map<String, String> p,
                                Map<Identifier, Identifier> defaults) {
        Identifier id = Identifier.withDefaultNamespace(container);
        Identifier original = defaults.get(id);
        // Which containers OptiGUI filters by name: the anvils, the crafting table and the
        // ender chest are not.
        boolean byName = !Set.of("anvil", "chipped_anvil", "damaged_anvil", "crafting_table", "ender_chest").contains(container);
        Map<String, String> sel = new LinkedHashMap<>();
        if (original != null) sel.put("interaction.texture", original.toString());
        if (p.containsKey("biomes")) sel.put("biomes", p.get("biomes"));
        if (p.containsKey("heights")) sel.put("heights", p.get("heights"));
        if (byName && p.containsKey("name")) {
            String n = p.get("name");
            if (n.startsWith("pattern:")) sel.put("name.wildcard", n.substring(8));
            else if (n.startsWith("ipattern:")) sel.put("name.wildcard.ignore_case", n.substring(9));
            else if (n.startsWith("regex:")) sel.put("name.regex", n.substring(6));
            else if (n.startsWith("iregex:")) sel.put("name.regex.ignore_case", n.substring(7));
            else sel.put("name", n);
        }
        if (container.equals("chest") || container.equals("trapped_chest")) {
            if (p.containsKey("large")) sel.put("chest.large", p.get("large"));
            Boolean christmas = looseBoolean(p.get("christmas"));
            if (Boolean.TRUE.equals(christmas)) sel.put("date", "dec@24-26");
            else if (Boolean.FALSE.equals(christmas)) sel.put("date", "1 2 3 4 5 6 7 8 9 spooktober 11 dec@1-23 dec@27-31");
        }
        return new Raw(0, resource, id, rep, original == null ? Set.of() : Set.of(original), sel);
    }

    // Selectors (OptiGUI's SelectorRegistry, the ones a block container can meet)

    /** OptiGUI's selectors MenuKit can't evaluate for a section: they never hold for one. */
    private static final Set<String> NEVER = Set.of("beacon.levels", "book.page.count", "book.page.current",
            "comparator.output", "donkey.has_chest", "horse.has_saddle", "horse.markings", "horse.variants",
            "llama.colors", "llama.variants", "villager.professions", "villager.type");

    /**
     * The test for selector {@code key=value}, {@code null} for a key OptiGUI doesn't know
     * (ignored). Throws where OptiGUI's would (the selector is then dropped).
     */
    static @Nullable Predicate<Input> selector(String key, String value) {
        switch (key) {
            case "interaction.texture" -> {
                Identifier t = Identifier.parse(value);
                return in -> in.texture().equals(t);
            }
            case "interaction.hand" -> {
                List<String> hands = list(value, s -> s.equals("main_hand") || s.equals("off_hand") ? s : null);
                return in -> hands.contains("main_hand");   // a section is opened as by the main hand
            }
            case "biomes" -> { List<Identifier> b = list(value, Identifier::tryParse); return in -> in.biome() != null && b.contains(in.biome()); }
            case "player.biomes" -> { List<Identifier> b = list(value, Identifier::tryParse); return in -> in.playerBiome() != null && b.contains(in.playerBiome()); }
            case "heights" -> { List<int[]> r = list(value, OptiGuiRules::range); return in -> in.y() != null && inAny(r, in.y()); }
            case "player.heights" -> { List<int[]> r = list(value, OptiGuiRules::range); return in -> in.playerY() != null && inAny(r, in.playerY()); }
            case "chest.large" -> {
                boolean want = strictBoolean(value);
                return in -> in.large() != null && in.large() == want;
            }
            case "date" -> {
                List<int[]> dates = list(value, OptiGuiRules::date);
                return in -> dates.stream().anyMatch(d -> in.today().getMonthValue() == d[0]
                        && (d[1] == Integer.MIN_VALUE || (in.today().getDayOfMonth() >= d[1] && in.today().getDayOfMonth() <= d[2])));
            }
            default -> {}
        }
        boolean player = key.startsWith("player.name");
        String nameKey = player ? key.substring("player.".length()) : key;
        Pattern pattern = switch (nameKey) {
            case "name" -> Pattern.compile(value, Pattern.LITERAL);
            case "name.wildcard" -> Pattern.compile(wildcard(unescapeJava(value)));
            case "name.wildcard.ignore_case" -> Pattern.compile(wildcard(unescapeJava(value)), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            case "name.regex" -> Pattern.compile(unescapeJava(value));
            case "name.regex.ignore_case" -> Pattern.compile(unescapeJava(value), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            default -> null;
        };
        if (pattern != null) return in -> pattern.matcher(player ? in.playerName() : in.name()).matches();
        if (NEVER.contains(key)) return in -> false;
        return null;
    }

    /** OptiGUI's wildcard: {@code *} is one or more characters, {@code ?} any run (its own reading, kept). */
    static String wildcard(String w) {
        StringBuilder b = new StringBuilder("^");
        for (char c : w.toCharArray()) {
            switch (c) {
                case '*' -> b.append(".+");
                case '?' -> b.append(".*");
                case '.', '\\', '+', '^', '$', '[', ']', '{', '}', '(', ')', '|', '/' -> b.append('\\').append(c);
                default -> b.append(c);
            }
        }
        return b.append('$').toString();
    }

    /** Commons' Java unescape, as OptiGUI applies it to wildcard and regex names (a lone backslash is dropped). */
    @SuppressWarnings("deprecation")
    private static String unescapeJava(String s) {
        return org.apache.commons.lang3.StringEscapeUtils.unescapeJava(s);
    }

    private static final Pattern RANGE = Pattern.compile("^(?:(?<start>\\d+|\\(-?\\d+\\))-(?<end>\\d+|\\(-?\\d+\\))?|(?<value>-?\\d+))$");

    /** OptiGUI's number or range ({@code 64}, {@code 0-63}, {@code (-64)-0}, {@code 100-}): {@code [from, to]}, or {@code null}. */
    static int @Nullable [] range(String s) {
        Matcher m = RANGE.matcher(s);
        if (!m.matches()) return null;
        if (m.group("value") != null) { int v = Integer.parseInt(m.group("value")); return new int[]{v, v}; }
        int start = Integer.parseInt(trimParens(m.group("start")));
        return new int[]{start, m.group("end") == null ? Integer.MAX_VALUE : Integer.parseInt(trimParens(m.group("end")))};
    }

    private static String trimParens(String s) {
        return s.replaceAll("^\\(+", "").replaceAll("\\)+$", "");
    }

    private static boolean inAny(List<int[]> ranges, int v) {
        for (int[] r : ranges) if (v >= r[0] && v <= r[1]) return true;
        return false;
    }

    /** OptiGUI's date, {@code month[@day or days]}: {@code [month, from, to]}, {@code from} MIN_VALUE for the whole month. */
    private static int @Nullable [] date(String s) {
        String[] parts = s.split("@", -1);
        if (parts.length > 2) return null;
        Month month = switch (parts[0]) {
            case "jan", "january", "1" -> Month.JANUARY;
            case "feb", "february", "2" -> Month.FEBRUARY;
            case "mar", "march", "3" -> Month.MARCH;
            case "apr", "april", "4" -> Month.APRIL;
            case "may", "5" -> Month.MAY;
            case "jun", "june", "6" -> Month.JUNE;
            case "jul", "july", "7" -> Month.JULY;
            case "aug", "augustus", "8" -> Month.AUGUST;
            case "sep", "september", "9" -> Month.SEPTEMBER;
            case "oct", "october", "spooktober", "10" -> Month.OCTOBER;
            case "nov", "november", "11" -> Month.NOVEMBER;
            case "dec", "december", "12" -> Month.DECEMBER;
            default -> null;
        };
        if (month == null) return null;
        if (parts.length == 1) return new int[]{month.getValue(), Integer.MIN_VALUE, Integer.MIN_VALUE};
        int[] days = range(parts[1]);
        // OptiGUI keeps a month whose day doesn't parse as the whole month.
        return days == null ? new int[]{month.getValue(), Integer.MIN_VALUE, Integer.MIN_VALUE} : new int[]{month.getValue(), days[0], days[1]};
    }

    /** A space- or tab-separated list, every item parsed; one that doesn't parse throws, as OptiGUI's does. */
    private static <T> List<T> list(String value, java.util.function.Function<String, @Nullable T> parse) {
        List<String> items = split(value);
        if (items.isEmpty()) throw new IllegalArgumentException("empty");
        List<T> out = new ArrayList<>();
        for (String s : items) {
            T t = parse.apply(s);
            if (t == null) throw new IllegalArgumentException("can't read " + s);
            out.add(t);
        }
        return out;
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (String part : s.split("[ \t]")) if (!part.isEmpty()) out.add(part);
        return out;
    }

    private static boolean strictBoolean(String s) {
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        throw new IllegalArgumentException("not a boolean: " + s);
    }

    private static @Nullable Boolean looseBoolean(@Nullable String s) {
        if (s == null) return null;
        String l = s.toLowerCase(java.util.Locale.ROOT);
        return l.equals("true") ? Boolean.TRUE : l.equals("false") ? Boolean.FALSE : null;
    }

    // Paths

    /** OptiGUI's path resolution: beside {@code resource}, or from {@code optifine/} for {@code ~/}, or a full id with a colon. */
    static @Nullable Identifier resolvePath(String path, Identifier resource, @Nullable String tilde) {
        boolean fromTilde = tilde != null && path.startsWith("~/");
        String rest = fromTilde ? path.substring(2) : path;
        if (rest.startsWith("/")) return null;
        long colons = rest.chars().filter(c -> c == ':').count();
        if (colons == 1) return Identifier.tryParse(rest);
        if (colons > 1) return null;
        Path root = Path.of(fromTilde ? tilde + "/." : resource.getPath());
        String resolved = root.resolveSibling(rest).normalize().toString().replace('\\', '/');
        if (resolved.contains("..")) return null;
        return Identifier.tryBuild(resource.getNamespace(), resolved);
    }

    /** An OptiFine {@code texture=}: resolved as a path, as given or with {@code .png} added, whichever is in the packs. */
    private static @Nullable Identifier resolveTexture(String texture, Identifier resource, Predicate<Identifier> exists) {
        Identifier t = resolvePath(texture, resource, "optifine");
        if (t == null) return null;
        if (exists.test(t)) return t;
        Identifier png = Identifier.tryBuild(t.getNamespace(), t.getPath() + ".png");
        return png != null && exists.test(png) ? png : null;
    }

    // The two file formats (ini4j's readings, the parts packs use)

    /** {@code key=value} (or {@code key: value}) lines, trimmed, {@code #} and {@code !} comments. */
    // ponytail: no line continuations or escapes; no published pack's rules use them.
    static Map<String, String> properties(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("!")) continue;
            int at = firstOf(l, '=', ':');
            if (at < 0) continue;
            out.put(l.substring(0, at).strip(), l.substring(at + 1).strip());
        }
        return out;
    }

    /** {@code [section]} headers, then {@code key=value} lines in it; {@code ;} and {@code #} comments. */
    static Map<String, Map<String, String>> ini(String text) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Map<String, String> cur = null;
        for (String line : text.split("\\R")) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith(";") || l.startsWith("#")) continue;
            if (l.startsWith("[") && l.endsWith("]")) {
                cur = out.computeIfAbsent(l.substring(1, l.length() - 1).strip(), k -> new LinkedHashMap<>());
                continue;
            }
            int at = firstOf(l, '=', ':');
            if (cur == null || at < 0) continue;
            cur.put(l.substring(0, at).strip(), l.substring(at + 1).strip());
        }
        return out;
    }

    private static int firstOf(String s, char a, char b) {
        int i = s.indexOf(a), j = s.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
    }

    // In the game

    private static volatile @Nullable Rules rules;
    // ponytail: one answer per section context, dropped on a reload; a screen shows a handful of containers.
    private static final Map<Key, Identifier> ANSWERS = new ConcurrentHashMap<>();

    private record Key(Identifier texture, Identifier block, String name, BlockPos pos, @Nullable Boolean large) {}

    /**
     * Whether {@code texture} is one OptiGUI may draw in place of a container screen's own
     * image (a rule's replacement): a screen the region stands in for leaves it out with
     * vanilla's own ({@code ScreenBackground}). Never without OptiGUI.
     */
    public static boolean isReplacement(Identifier texture) {
        return LOADED && Minecraft.getInstance() != null && rules().replacesWith(texture);
    }

    /** A resource reload: the rules are read again from the new packs on their next use. */
    public static void onReload() {
        rules = null;
        ANSWERS.clear();
    }

    /**
     * The texture OptiGUI's rules give a section's container: {@code block} with
     * {@code customName} (else {@code title}, as OptiGUI falls back to a screen's title), at
     * {@code pos} (else the player's position, for a container opened from an item), drawn
     * with {@code texture}. {@code texture} itself when no rule replaces it, or OptiGUI
     * isn't installed.
     */
    public static Identifier forContainer(Identifier texture, Identifier block, @Nullable String customName, String title,
                                          @Nullable BlockPos pos) {
        if (!LOADED) return texture;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return texture;
        BlockPos at = pos != null ? pos : mc.player.blockPosition();
        Boolean large = null;
        if (pos != null && mc.level.getBlockEntity(pos) instanceof ChestBlockEntity) {
            var state = mc.level.getBlockState(pos);
            if (state.hasProperty(ChestBlock.TYPE)) large = state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
        }
        String name = customName != null ? customName : title;
        Boolean isLarge = large;
        return ANSWERS.computeIfAbsent(new Key(texture, block, name, at, large), k -> {
            Identifier biome = mc.level.getBiome(at).unwrapKey().map(r -> r.identifier()).orElse(null);
            BlockPos me = mc.player.blockPosition();
            Identifier myBiome = mc.level.getBiome(me).unwrapKey().map(r -> r.identifier()).orElse(null);
            Input in = new Input(block, texture, name, biome, at.getY(), isLarge, mc.player.getName().getString(),
                    myBiome, me.getY(), LocalDate.now());
            Identifier out = rules().evaluate(in);
            return out == null ? texture : out;
        });
    }

    /** The rules of the active packs, read on first use after a reload. */
    private static Rules rules() {
        Rules r = rules;
        if (r != null) return r;
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        List<Source> ini = read(rm.listResources("gui", id -> id.getNamespace().equals("optigui") && id.getPath().endsWith(".ini")));
        List<Source> props = read(rm.listResources("optifine/gui/container",
                id -> id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) && id.getPath().endsWith(".properties")));
        r = load(ini, props, defaultTextures(), id -> rm.getResource(id).isPresent(), OptiGuiRules::modLoaded);
        LOG.debug("{} OptiGUI rules from {} files", r.size(), ini.size() + props.size());
        rules = r;
        return r;
    }

    private static List<Source> read(Map<Identifier, Resource> files) {
        List<Source> out = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> e : new TreeMap<>(files).entrySet()) {
            try (InputStream in = e.getValue().open()) {
                out.add(new Source(e.getKey(), new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                LOG.warn("Couldn't read {}: {}", e.getKey(), ex.toString());
            }
        }
        return out;
    }

    /** Each container's own texture, from every mod's {@code optigui:container_default_gui_textures}, as OptiGUI builds it. */
    private static Map<Identifier, Identifier> defaultTextures() {
        Map<Identifier, Identifier> out = new HashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (!mod.getMetadata().containsCustomValue("optigui:container_default_gui_textures")) continue;
            try {
                for (Map.Entry<String, CustomValue> e : mod.getMetadata().getCustomValue("optigui:container_default_gui_textures").getAsObject()) {
                    Identifier k = Identifier.tryParse(e.getKey()), v = Identifier.tryParse(e.getValue().getAsString());
                    if (k != null && v != null) out.putIfAbsent(k, v);
                }
            } catch (RuntimeException ex) {
                LOG.warn("Couldn't read {}'s OptiGUI container textures: {}", mod.getMetadata().getId(), ex.toString());
            }
        }
        return out;
    }

    /** {@code if.mods}: a mod id, with a version predicate after it ({@code sodium>=0.6}), loaded. */
    private static boolean modLoaded(String spec) {
        int at = -1;
        for (String op : new String[]{">=", "<=", ">", "<", "=", "~", "^"}) {
            int i = spec.indexOf(op);
            if (i >= 0 && (at < 0 || i < at)) at = i;
        }
        String id = at < 0 ? spec : spec.substring(0, at);
        var mod = FabricLoader.getInstance().getModContainer(id);
        if (mod.isEmpty()) return false;
        if (at < 0 || at == spec.length()) return true;
        try {
            return VersionPredicate.parse(spec.substring(at)).test(mod.get().getMetadata().getVersion());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    // OptiGUI's own answer for the open screen

    private static @Nullable MethodHandle replaceTexture;
    private static boolean looked = false;

    /**
     * What OptiGUI draws {@code texture} as on the open screen, for the screen's own
     * container (its {@code TextureReplacer.replaceTexture}, the one call of OptiGUI's
     * MenuKit makes): the texture itself when OptiGUI isn't installed, isn't in an
     * interaction, or has no such method.
     */
    public static Identifier onScreen(Identifier texture) {
        if (!LOADED) return texture;
        if (!looked) {
            looked = true;
            try {
                replaceTexture = MethodHandles.publicLookup().findStatic(Class.forName("opekope2.optigui.internal.TextureReplacer"),
                        "replaceTexture", MethodType.methodType(Identifier.class, Identifier.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                LOG.warn("OptiGUI has no TextureReplacer.replaceTexture; the open screen's own container keeps its texture: {}", e.toString());
            }
        }
        if (replaceTexture == null) return texture;
        try {
            Identifier out = (Identifier) replaceTexture.invokeExact(texture);
            return out == null ? texture : out;
        } catch (Throwable t) {
            return texture;
        }
    }
}
