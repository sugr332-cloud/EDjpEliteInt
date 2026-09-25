package elite.intel.gameapi.search.spansh.outfitting;

import com.google.gson.JsonObject;
import elite.intel.gameapi.search.spansh.client.SpanshClient;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.util.json.GsonFactory;

/**
 * Spansh client for searching stations with specific outfitting modules.
 */
public class OutfittingStationSearchClient extends SpanshClient {

    private static OutfittingStationSearchClient instance;

    protected OutfittingStationSearchClient() {
        super("https://spansh.co.uk/api/stations/search/save", "https://spansh.co.uk/api/stations/search/recall/");
    }

    public static synchronized OutfittingStationSearchClient getInstance() {
        if (instance == null) {
            instance = new OutfittingStationSearchClient();
        }
        return instance;
    }

    public TradeStationSearchResultDto searchOutfittingStations(OutfittingStationSearchCriteria criteria) {
        if (criteria == null) {
            return null;
        }
        JsonObject json = performSearch(criteria);
        if (json == null || json.isEmpty()) {
            return null;
        }
        return GsonFactory.getGson().fromJson(json, TradeStationSearchResultDto.class);
    }
}
