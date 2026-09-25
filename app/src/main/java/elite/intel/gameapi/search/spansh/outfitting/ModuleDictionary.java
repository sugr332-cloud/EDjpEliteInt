package elite.intel.gameapi.search.spansh.outfitting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic module name dictionary for outfitting searches.
 * Resolves Japanese / colloquial module names and class/rating combinations to canonical Spansh module names.
 */
public final class ModuleDictionary {

    private static final Logger log = LogManager.getLogger(ModuleDictionary.class);

    private static final String CANONICAL_MODULES_RESOURCE = "/outfitting/spansh_canonical_modules.json";
    private static final String ALIASES_RESOURCE = "/outfitting/module_aliases_ja.properties";

    private static final ModuleDictionary INSTANCE = new ModuleDictionary();

    // Valid class: 0-8, Valid rating: A-I
    private static final Pattern PREFIX_CLASS_RATING = Pattern.compile("^([0-8])\\s*([A-Ia-i])\\s+(.+)$");
    private static final Pattern PREFIX_CLASS_RATING_NO_SPACE = Pattern.compile("^([0-8])([A-Ia-i])\\s*(.+)$");
    private static final Pattern SUFFIX_CLASS_RATING = Pattern.compile("^(.+?)\\s+([0-8])\\s*([A-Ia-i])$");
    private static final Pattern SUFFIX_CLASS_RATING_NO_SPACE = Pattern.compile("^(.+?)\\s*([0-8])([A-Ia-i])$");

    // Invalid class (9+) or invalid rating (J-Z) check (e.g. "9A FSD", "5Z FSD")
    private static final Pattern INVALID_PREFIX = Pattern.compile("^(\\d+)\\s*([A-Za-z])\\s*(.+)$");
    private static final Pattern INVALID_SUFFIX = Pattern.compile("^(.+?)\\s*(\\d+)\\s*([A-Za-z])$");

    private final Map<String, String> canonicalByLower = new HashMap<>();
    private final Map<String, String> aliasToCanonical = new HashMap<>();

    public static ModuleDictionary getInstance() {
        return INSTANCE;
    }

    private ModuleDictionary() {
        loadCanonicalModules();
        loadAliases();
    }

    private void loadCanonicalModules() {
        try (InputStream is = getClass().getResourceAsStream(CANONICAL_MODULES_RESOURCE)) {
            if (is == null) {
                log.error("Resource not found: {}", CANONICAL_MODULES_RESOURCE);
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("modules");
            for (JsonElement el : arr) {
                String name = el.getAsString().trim();
                canonicalByLower.put(name.toLowerCase(Locale.ROOT), name);
            }
            log.info("Loaded {} canonical outfitting modules from {}", canonicalByLower.size(), CANONICAL_MODULES_RESOURCE);
        } catch (Exception e) {
            log.error("Failed to load canonical modules from {}", CANONICAL_MODULES_RESOURCE, e);
        }
    }

    private void loadAliases() {
        try (InputStream is = getClass().getResourceAsStream(ALIASES_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + ALIASES_RESOURCE);
            }
            Map<String, String> parsed = parseAliases(new InputStreamReader(is, StandardCharsets.UTF_8), canonicalByLower);
            aliasToCanonical.putAll(parsed);
            log.info("Loaded {} Japanese module aliases from {}", aliasToCanonical.size(), ALIASES_RESOURCE);
        } catch (Exception e) {
            log.error("Failed to load aliases from {}", ALIASES_RESOURCE, e);
            throw new IllegalStateException("Failed to load aliases from " + ALIASES_RESOURCE, e);
        }
    }

    static Map<String, String> parseAliases(java.io.Reader in, Map<String, String> canonicalByLower) throws java.io.IOException {
        Map<String, String> result = new HashMap<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(in)) {
            String rawLine;
            int lineNo = 0;
            while ((rawLine = reader.readLine()) != null) {
                lineNo++;
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx < 0) {
                    throw new IllegalStateException("Missing '=' delimiter at line " + lineNo + ": " + rawLine);
                }
                String key = line.substring(0, eqIdx).trim().toLowerCase(Locale.ROOT);
                if (key.isEmpty()) {
                    throw new IllegalStateException("Empty key at line " + lineNo + ": " + rawLine);
                }
                String canonicalVal = line.substring(eqIdx + 1).trim();
                if (result.containsKey(key)) {
                    throw new IllegalStateException("Duplicate alias key '" + key + "' at line " + lineNo);
                }
                String realCanonical = canonicalByLower.get(canonicalVal.toLowerCase(Locale.ROOT));
                if (realCanonical != null) {
                    result.put(key, realCanonical);
                } else {
                    log.warn("Alias '{}' maps to unknown canonical name '{}'", key, canonicalVal);
                }
            }
        }
        return result;
    }

    /**
     * Matches user module text into a canonical MatchedModule.
     * Returns Optional.empty() (UNKNOWN) if cannot be resolved or if class/rating is out of range.
     */
    public Optional<MatchedModule> match(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return Optional.empty();
        }

        String normalized = Normalizer.normalize(rawInput.trim(), Normalizer.Form.NFKC).trim();

        // 1. Try prefix class + rating (0-8, A-I)
        Matcher m = PREFIX_CLASS_RATING.matcher(normalized);
        if (m.matches()) {
            return resolve(m.group(3), Integer.parseInt(m.group(1)), m.group(2));
        }
        m = PREFIX_CLASS_RATING_NO_SPACE.matcher(normalized);
        if (m.matches()) {
            return resolve(m.group(3), Integer.parseInt(m.group(1)), m.group(2));
        }

        // 2. Try suffix class + rating (0-8, A-I)
        m = SUFFIX_CLASS_RATING.matcher(normalized);
        if (m.matches()) {
            return resolve(m.group(1), Integer.parseInt(m.group(2)), m.group(3));
        }
        m = SUFFIX_CLASS_RATING_NO_SPACE.matcher(normalized);
        if (m.matches()) {
            return resolve(m.group(1), Integer.parseInt(m.group(2)), m.group(3));
        }

        // 3. Check for out-of-range prefix or suffix (e.g. 9A, 5Z) -> reject as UNKNOWN
        Matcher invPre = INVALID_PREFIX.matcher(normalized);
        if (invPre.matches()) {
            int cls = parseOrNegative(invPre.group(1));
            String rat = invPre.group(2).toUpperCase(Locale.ROOT);
            if (cls < 0 || cls > 8 || rat.charAt(0) < 'A' || rat.charAt(0) > 'I') {
                return Optional.empty();
            }
        }
        Matcher invSuf = INVALID_SUFFIX.matcher(normalized);
        if (invSuf.matches()) {
            int cls = parseOrNegative(invSuf.group(2));
            String rat = invSuf.group(3).toUpperCase(Locale.ROOT);
            if (cls < 0 || cls > 8 || rat.charAt(0) < 'A' || rat.charAt(0) > 'I') {
                return Optional.empty();
            }
        }

        // 4. No class / rating specified -> match name only
        return resolve(normalized, null, null);
    }

    private Optional<MatchedModule> resolve(String rawName, Integer clazz, String rating) {
        String cleanName = rawName.trim().toLowerCase(Locale.ROOT);
        if (cleanName.isEmpty()) {
            return Optional.empty();
        }

        // Check alias mapping first
        String canonical = aliasToCanonical.get(cleanName);
        if (canonical == null) {
            // Check direct canonical name
            canonical = canonicalByLower.get(cleanName);
        }

        if (canonical == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new MatchedModule(canonical, clazz, rating));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static int parseOrNegative(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
