package elite.intel.gameapi.engineers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elite.intel.util.json.GsonFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads and provides access to static reference data for Elite Dangerous engineers from engineers.json.
 * Also owns the single canonical name normalization rule used for engineering data.
 */
public final class EngineerDirectory {

    private static final Logger log = LogManager.getLogger(EngineerDirectory.class);
    private static final String RESOURCE_PATH = "/engineers/engineers.json";
    private static final EngineerDirectory INSTANCE = new EngineerDirectory();

    public record Specialty(String module, int maxGrade) {}
    public record Coords(double x, double y, double z) {}

    public record EngineerInfo(
            String name,
            String type,
            String system,
            String base,
            String body,
            String invite,
            String unlock,
            String referral,
            List<Specialty> specialties,
            String notes,
            List<String> sources,
            Coords coords,
            boolean permitRequired
    ) {
        public boolean isShip() {
            return "ship".equalsIgnoreCase(type);
        }

        public boolean isOnFoot() {
            return "onfoot".equalsIgnoreCase(type);
        }
    }

    public record MetaInfo(
            String description,
            String checkedAt,
            List<String> sources,
            String matching,
            String notes
    ) {}

    private final MetaInfo meta;
    private final List<EngineerInfo> engineers;
    private final Map<String, EngineerInfo> byNormalizedName;

    public static EngineerDirectory getInstance() {
        return INSTANCE;
    }

    private EngineerDirectory() {
        MetaInfo loadedMeta = null;
        List<EngineerInfo> loadedEngineers = new ArrayList<>();
        Map<String, EngineerInfo> nameMap = new HashMap<>();

        try (InputStream is = getClass().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                log.error("Resource not found: {}", RESOURCE_PATH);
            } else {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();

                if (root.has("meta") && root.get("meta").isJsonObject()) {
                    JsonObject m = root.getAsJsonObject("meta");
                    List<String> sources = new ArrayList<>();
                    if (m.has("sources") && m.get("sources").isJsonArray()) {
                        for (JsonElement el : m.getAsJsonArray("sources")) {
                            sources.add(el.getAsString());
                        }
                    }
                    loadedMeta = new MetaInfo(
                            m.has("description") ? m.get("description").getAsString() : "",
                            m.has("checkedAt") ? m.get("checkedAt").getAsString() : "",
                            Collections.unmodifiableList(sources),
                            m.has("matching") ? m.get("matching").getAsString() : "",
                            m.has("notes") ? m.get("notes").getAsString() : ""
                    );
                }

                if (root.has("engineers") && root.get("engineers").isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray("engineers");
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();

                        String name = obj.has("name") ? obj.get("name").getAsString() : "";
                        String type = obj.has("type") ? obj.get("type").getAsString() : "";
                        String system = obj.has("system") ? obj.get("system").getAsString() : "";
                        String base = obj.has("base") ? obj.get("base").getAsString() : "";
                        String body = obj.has("body") ? obj.get("body").getAsString() : "";
                        String invite = obj.has("invite") && !obj.get("invite").isJsonNull() ? obj.get("invite").getAsString() : "";
                        String unlock = obj.has("unlock") && !obj.get("unlock").isJsonNull() ? obj.get("unlock").getAsString() : "";
                        String referral = obj.has("referral") && !obj.get("referral").isJsonNull() ? obj.get("referral").getAsString() : null;

                        List<Specialty> specs = new ArrayList<>();
                        if (obj.has("specialties") && obj.get("specialties").isJsonArray()) {
                            for (JsonElement spEl : obj.getAsJsonArray("specialties")) {
                                if (spEl.isJsonObject()) {
                                    JsonObject spObj = spEl.getAsJsonObject();
                                    String mod = spObj.has("module") ? spObj.get("module").getAsString() : "";
                                    int grade = spObj.has("maxGrade") ? spObj.get("maxGrade").getAsInt() : 0;
                                    specs.add(new Specialty(mod, grade));
                                } else if (spEl.isJsonPrimitive() && spEl.getAsJsonPrimitive().isString()) {
                                    specs.add(new Specialty(spEl.getAsString(), 0));
                                }
                            }
                        }

                        String notes = obj.has("notes") && !obj.get("notes").isJsonNull() ? obj.get("notes").getAsString() : "";

                        List<String> srcList = new ArrayList<>();
                        if (obj.has("sources") && obj.get("sources").isJsonArray()) {
                            for (JsonElement sEl : obj.getAsJsonArray("sources")) {
                                srcList.add(sEl.getAsString());
                            }
                        }

                        Coords coords = null;
                        if (obj.has("coords") && obj.get("coords").isJsonObject()) {
                            JsonObject cObj = obj.getAsJsonObject("coords");
                            coords = new Coords(
                                    cObj.has("x") ? cObj.get("x").getAsDouble() : 0.0,
                                    cObj.has("y") ? cObj.get("y").getAsDouble() : 0.0,
                                    cObj.has("z") ? cObj.get("z").getAsDouble() : 0.0
                            );
                        }

                        boolean permitRequired = obj.has("permitRequired") && obj.get("permitRequired").getAsBoolean();

                        EngineerInfo info = new EngineerInfo(
                                name, type, system, base, body, invite, unlock, referral,
                                Collections.unmodifiableList(specs),
                                notes, Collections.unmodifiableList(srcList),
                                coords, permitRequired
                        );

                        loadedEngineers.add(info);
                        nameMap.put(normalizeName(name), info);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load engineers reference data from {}", RESOURCE_PATH, e);
        }

        this.meta = loadedMeta;
        this.engineers = Collections.unmodifiableList(loadedEngineers);
        this.byNormalizedName = Collections.unmodifiableMap(nameMap);
        log.info("EngineerDirectory initialized with {} engineers from {}", this.engineers.size(), RESOURCE_PATH);
    }

    /**
     * Canonical name normalization rule for engineers:
     * - trims whitespace
     * - strips single quotes, double quotes, and curly quotation marks (' ’ ‘ " ” “ `)
     * - collapses multiple whitespaces
     * - converts to lower case using Locale.ROOT
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("['’‘\"”“`]", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public MetaInfo getMeta() {
        return meta;
    }

    public List<EngineerInfo> getAllEngineers() {
        return engineers;
    }

    public List<EngineerInfo> getShipEngineers() {
        return engineers.stream().filter(EngineerInfo::isShip).toList();
    }

    public List<EngineerInfo> getOnFootEngineers() {
        return engineers.stream().filter(EngineerInfo::isOnFoot).toList();
    }

    public Optional<EngineerInfo> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNormalizedName.get(normalizeName(name)));
    }
}
