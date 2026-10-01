package elite.intel.gameapi.engineers;

import elite.intel.gameapi.search.spansh.outfitting.MatchedModule;
import elite.intel.gameapi.search.spansh.outfitting.ModuleDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * Resolves spoken words or module parameter text into a canonical engineers.json ship module name.
 */
public final class EngineerModuleMatcher {

    private static final Logger log = LogManager.getLogger(EngineerModuleMatcher.class);
    private static final String ALIASES_RESOURCE = "/engineers/engineer_module_aliases_ja.properties";
    private static final EngineerModuleMatcher INSTANCE = new EngineerModuleMatcher();

    private final Map<String, String> engineerAliases = new HashMap<>();
    private final Set<String> shipModules = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> normalizedToCanonicalModule = new HashMap<>();

    public static EngineerModuleMatcher getInstance() {
        return INSTANCE;
    }

    private EngineerModuleMatcher() {
        loadShipModules();
        loadEngineerAliases();
    }

    private void loadShipModules() {
        EngineerDirectory dir = EngineerDirectory.getInstance();
        for (EngineerDirectory.EngineerInfo eng : dir.getShipEngineers()) {
            if (eng.specialties() != null) {
                for (EngineerDirectory.Specialty sp : eng.specialties()) {
                    if (sp.module() != null && !sp.module().isBlank()) {
                        String mod = sp.module().trim();
                        shipModules.add(mod);
                        normalizedToCanonicalModule.put(normalizeForComparison(mod), mod);
                    }
                }
            }
        }
        log.info("EngineerModuleMatcher initialized with {} unique ship modules", shipModules.size());
    }

    private void loadEngineerAliases() {
        try (InputStream is = getClass().getResourceAsStream(ALIASES_RESOURCE)) {
            if (is == null) {
                log.warn("Resource not found: {}", ALIASES_RESOURCE);
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq < 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                    String val = trimmed.substring(eq + 1).trim();
                    if (!key.isEmpty() && !val.isEmpty()) {
                        engineerAliases.put(key, val);
                    }
                }
            }
            log.info("Loaded {} engineer module aliases from {}", engineerAliases.size(), ALIASES_RESOURCE);
        } catch (Exception e) {
            log.error("Failed to load engineer module aliases from {}", ALIASES_RESOURCE, e);
        }
    }

    public static String normalizeForComparison(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[-\\s]", "")
                .toLowerCase(Locale.ROOT);
    }

    public static String normalizeUtterance(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a raw module name or phrase directly into an engineers.json module name.
     * Order:
     * 1. engineer module aliases
     * 2. ModuleDictionary canonical resolution -> compared with engineers.json modules (strip hyphen and space, lowercase)
     * 3. Direct comparison with engineers.json modules (strip hyphen and space, lowercase)
     */
    public Optional<String> matchModule(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rawInput.trim();
        String lowerKey = trimmed.toLowerCase(Locale.ROOT);

        // (1) Engineer-specific aliases
        if (engineerAliases.containsKey(lowerKey)) {
            return Optional.of(engineerAliases.get(lowerKey));
        }

        // Also check NFKC normalized key in engineerAliases
        String nfkcKey = normalizeUtterance(trimmed);
        if (engineerAliases.containsKey(nfkcKey)) {
            return Optional.of(engineerAliases.get(nfkcKey));
        }

        // (2) ModuleDictionary match -> compare with engineers.json modules
        Optional<MatchedModule> dictMatch = ModuleDictionary.getInstance().match(trimmed);
        if (dictMatch.isPresent()) {
            String canonical = dictMatch.get().canonicalName();
            String norm = normalizeForComparison(canonical);
            if (normalizedToCanonicalModule.containsKey(norm)) {
                return Optional.of(normalizedToCanonicalModule.get(norm));
            }
        }

        // (3) Direct comparison with engineers.json modules
        String directNorm = normalizeForComparison(trimmed);
        if (normalizedToCanonicalModule.containsKey(directNorm)) {
            return Optional.of(normalizedToCanonicalModule.get(directNorm));
        }

        return Optional.empty();
    }

    /**
     * Finds the longest mentioned module keyword in utterance, then matches it.
     */
    public Optional<String> findMentionedModule(String utterance) {
        if (utterance == null || utterance.isBlank()) {
            return Optional.empty();
        }

        String normUtterance = normalizeUtterance(utterance);
        if (normUtterance.isEmpty()) {
            return Optional.empty();
        }

        // Collect all candidates with their priority group
        // Group 1: engineer aliases
        // Group 2: ModuleDictionary aliases
        // Group 3: ModuleDictionary canonical names + engineers.json module names
        record Candidate(String key, int length, int priority) {}

        List<Candidate> matchingCandidates = new ArrayList<>();

        for (String alias : engineerAliases.keySet()) {
            String normAlias = normalizeUtterance(alias);
            if (!normAlias.isEmpty() && normUtterance.contains(normAlias)) {
                matchingCandidates.add(new Candidate(alias, normAlias.length(), 1));
            }
        }

        for (String alias : ModuleDictionary.getInstance().getAliasKeys()) {
            String normAlias = normalizeUtterance(alias);
            if (!normAlias.isEmpty() && normUtterance.contains(normAlias)) {
                matchingCandidates.add(new Candidate(alias, normAlias.length(), 2));
            }
        }

        for (String can : ModuleDictionary.getInstance().getCanonicalNames()) {
            String normCan = normalizeUtterance(can);
            if (!normCan.isEmpty() && normUtterance.contains(normCan)) {
                matchingCandidates.add(new Candidate(can, normCan.length(), 3));
            }
        }

        for (String mod : shipModules) {
            String normMod = normalizeUtterance(mod);
            if (!normMod.isEmpty() && normUtterance.contains(normMod)) {
                matchingCandidates.add(new Candidate(mod, normMod.length(), 3));
            }
        }

        if (matchingCandidates.isEmpty()) {
            return Optional.empty();
        }

        // Sort by length desc, then priority asc (1 < 2 < 3)
        matchingCandidates.sort((a, b) -> {
            if (b.length != a.length) {
                return Integer.compare(b.length, a.length);
            }
            return Integer.compare(a.priority, b.priority);
        });

        // Try matching candidates in sorted order
        for (Candidate c : matchingCandidates) {
            Optional<String> matched = matchModule(c.key);
            if (matched.isPresent()) {
                return matched;
            }
        }

        return Optional.empty();
    }

    public Set<String> getAllShipModules() {
        return Collections.unmodifiableSet(shipModules);
    }
}
