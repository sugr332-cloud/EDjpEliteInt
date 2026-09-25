package elite.intel.gameapi.search.spansh.tradecandidates;

import com.google.gson.JsonObject;
import elite.intel.gameapi.search.spansh.client.SpanshClient;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.util.json.GsonFactory;

/**
 * Spansh client for searching stations with commodity markets for trade candidates.
 */
public class TradeCandidatesSearchClient extends SpanshClient {

    private static TradeCandidatesSearchClient instance;

    protected TradeCandidatesSearchClient() {
        super("https://spansh.co.uk/api/stations/search/save", "https://spansh.co.uk/api/stations/search/recall/");
    }

    public static synchronized TradeCandidatesSearchClient getInstance() {
        if (instance == null) {
            instance = new TradeCandidatesSearchClient();
        }
        return instance;
    }

    public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
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
