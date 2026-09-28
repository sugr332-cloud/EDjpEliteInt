package elite.intel.gameapi.journal.events;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import elite.intel.gameapi.StationName;
import elite.intel.util.json.GsonFactory;

import java.time.Duration;

public class DockingDeniedEvent extends BaseEvent {

    @SerializedName("Reason")
    private String reason;

    @SerializedName("MarketID")
    private long marketID;

    @SerializedName("StationName")
    private String stationName;

    @SerializedName("StationType")
    private String stationType;

    public DockingDeniedEvent(JsonObject json) {
        super(json.get("timestamp").getAsString(), Duration.ofSeconds(30), "DockingDenied");
        DockingDeniedEvent event = GsonFactory.getGson().fromJson(json, DockingDeniedEvent.class);
        this.reason = event.reason;
        this.marketID = event.marketID;
        this.stationName = StationName.display(event.stationName);
        this.stationType = event.stationType;
    }

    @Override
    public String getEventType() {
        return "DockingDenied";
    }

    @Override
    public Importance importance() {
        return Importance.NORMAL;
    }

    @Override
    public String llmDescription() {
        return "A station denied your docking request; carries the station name and refusal reason.";
    }

    @Override
    public String toJson() {
        return GsonFactory.getGson().toJson(this);
    }

    @Override
    public JsonObject toJsonObject() {
        return GsonFactory.toJsonObject(this);
    }

    public String getReason() {
        return reason;
    }

    public long getMarketID() {
        return marketID;
    }

    public String getStationName() {
        return stationName;
    }

    public String getStationType() {
        return stationType;
    }
}
