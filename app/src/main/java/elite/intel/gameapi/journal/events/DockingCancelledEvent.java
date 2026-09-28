package elite.intel.gameapi.journal.events;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import elite.intel.gameapi.StationName;
import elite.intel.util.json.GsonFactory;

import java.time.Duration;

public class DockingCancelledEvent extends BaseEvent {

    @SerializedName("MarketID")
    private long marketID;

    @SerializedName("StationName")
    private String stationName;

    @SerializedName("StationType")
    private String stationType;

    public DockingCancelledEvent(JsonObject json) {
        super(json.get("timestamp").getAsString(), Duration.ofSeconds(30), "DockingCancelled");
        DockingCancelledEvent event = GsonFactory.getGson().fromJson(json, DockingCancelledEvent.class);
        this.marketID = event.marketID;
        this.stationName = StationName.display(event.stationName);
        this.stationType = event.stationType;
    }

    @Override
    public String getEventType() {
        return "DockingCancelled";
    }

    @Override
    public Importance importance() {
        return Importance.NORMAL;
    }

    @Override
    public String llmDescription() {
        return "A docking permission was cancelled; carries the station name and market id.";
    }

    @Override
    public String toJson() {
        return GsonFactory.getGson().toJson(this);
    }

    @Override
    public JsonObject toJsonObject() {
        return GsonFactory.toJsonObject(this);
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
