package elite.intel.gameapi.journal.events;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import elite.intel.util.TimestampFormatter;
import elite.intel.util.json.GsonFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class EngineerProgressEvent extends BaseEvent {
    @SerializedName("Engineers")
    private List<Engineer> engineers;

    public EngineerProgressEvent(JsonObject json) {
        super(json.get("timestamp").getAsString(), Duration.ofDays(30), "EngineerProgress");
        if (json.has("Engineers") && json.get("Engineers").isJsonArray()) {
            EngineerProgressEvent event = GsonFactory.getGson().fromJson(json, EngineerProgressEvent.class);
            this.engineers = event.engineers != null ? event.engineers : Collections.emptyList();
        } else if (json.has("Engineer")) {
            Engineer single = GsonFactory.getGson().fromJson(json, Engineer.class);
            this.engineers = single != null ? List.of(single) : Collections.emptyList();
        } else {
            this.engineers = Collections.emptyList();
        }
    }

    @Override
    public String getEventType() {
        return "EngineerProgress";
    }

    /** Engineer progress snapshot; memory context. */
    @Override
    public Importance importance() {
        return Importance.NORMAL;
    }

    @Override
    public String llmDescription() {
        return "Engineer unlock or progress update; carries the engineer and your rank or progress. Snapshot.";
    }

    @Override
    public String memorySummary() {
        return engineers == null || engineers.isEmpty() ? "" : "engineer progress: " + engineers.size() + " engineers on record";
    }

    @Override
    public String toJson() {
        return GsonFactory.getGson().toJson(this);
    }

    @Override
    public JsonObject toJsonObject() {
        return GsonFactory.toJsonObject(this);
    }

    public List<Engineer> getEngineers() {
        return engineers;
    }

    public String getFormattedTimestamp(boolean useLocalTime) {
        return TimestampFormatter.formatTimestamp(getTimestamp().toString(), useLocalTime);
    }

    public static class Engineer {
        @SerializedName("Engineer")
        private String name;

        @SerializedName("EngineerID")
        private Long engineerID;

        @SerializedName("Progress")
        private String progress;

        @SerializedName("RankProgress")
        private Integer rankProgress;

        @SerializedName("Rank")
        private Integer rank;

        public String getName() {
            return name;
        }

        public long getEngineerID() {
            return engineerID != null ? engineerID : 0L;
        }

        public Long getEngineerIdNullable() {
            return engineerID;
        }

        public String getProgress() {
            return progress;
        }

        public int getRankProgress() {
            return rankProgress != null ? rankProgress : 0;
        }

        public Integer getRankProgressNullable() {
            return rankProgress;
        }

        public int getRank() {
            return rank != null ? rank : 0;
        }

        public Integer getRankNullable() {
            return rank;
        }

        public boolean isFullyUnlocked() {
            return "Unlocked".equals(progress) && rank != null && rank == 5 && (rankProgress == null || rankProgress == 0);
        }
    }
}