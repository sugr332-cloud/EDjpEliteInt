package elite.intel.gameapi.search.spansh.station.refuel;

import elite.intel.gameapi.search.spansh.station.StationSearchClient;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchCriteria;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.util.ShipPadSizes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * The nearest SPACE station this ship can land on - an orbital, never a surface port, a settlement or a
 * fleet carrier.
 *
 * <p>WHY this is not {@link RefuelStationSearch}: that search asks for every station that stays put and only
 * PREFERS an orbital one, so a radius with nothing but surface ports in it yields a surface port. A commander
 * who asks for a space station must not be handed one. Here the Spansh type filter itself is the orbital
 * list, so a surface port can never be a candidate however thin the neighbourhood is.
 *
 * <p>WHY there is no service or market filter: the question is "where is a station", not "where can I do X".
 * The pad rules, the radius ladder and the arrival-distance cap are the refuel search's own, shared through
 * {@link RefuelStationSearch} so the two cannot drift apart.
 *
 * <p>WHY mega ships are left out of the types: they relocate on the weekly cycle, so the position Spansh
 * holds can be one the ship has already left.
 */
public final class OrbitalStationSearch {

    /**
     * {@link TradeStationSearchCriteria.StationType#ORBITAL_TRADE_TYPES} without {@code Mega ship}. Built
     * from the shared constant rather than retyped, so a type added there is picked up here.
     */
    static final List<String> ORBITAL_STATION_TYPES = TradeStationSearchCriteria.StationType.ORBITAL_TRADE_TYPES
            .stream().filter(type -> !"Mega ship".equals(type)).toList();

    private static final Logger log = LogManager.getLogger(OrbitalStationSearch.class);

    private OrbitalStationSearch() {
    }

    /**
     * A space station, with the market id the journal and Spansh share so the one the commander is docked
     * at can be told apart from the rest.
     *
     * @param marketId Spansh's market id as text, or null when the row carried none
     */
    public record OrbitalStation(String starSystem, String stationName, String stationType,
                                 double distanceLy, double arrivalLs, String marketId) {
    }

    /**
     * What a search came back with.
     *
     * @param stations the stations found, nearest first; empty when none
     * @param failed   true when a request got no answer at all. Only meaningful beside an empty list: it is
     *                 what separates "Spansh did not answer" from "there is no such station".
     */
    public record Result(List<OrbitalStation> stations, boolean failed) {
    }

    /**
     * The nearest space stations, nearest first.
     *
     * <p>The radius widens exactly as {@link RefuelStationSearch#radiiToTry} says, and stops at the first
     * radius that has a usable station - so a station that is excluded does not end the search, it sends it
     * on to the next ring.
     *
     * @param maxDistanceLy   the radius to try first
     * @param padSize         the pad the ship needs, as {@link ShipPadSizes#getPadSize} spells it
     * @param excludeMarketId the market id of the station the commander is docked at, or 0 for none
     */
    public static Result nearest(double x, double y, double z, int maxDistanceLy, String padSize,
                                 long excludeMarketId) {
        boolean failed = false;
        for (int radius : RefuelStationSearch.radiiToTry(maxDistanceLy)) {
            Fetched fetched = fetch(x, y, z, radius, padSize);
            failed |= fetched.failed();
            List<OrbitalStation> found = rank(fetched.stations(), padSize, excludeMarketId);
            if (!found.isEmpty()) return new Result(found, false);
            log.debug("No space station a {} ship can use within {} ly; widening", padSize, radius);
        }
        return new Result(List.of(), failed);
    }

    private record Fetched(List<TradeStationSearchResultDto.StationResult> stations, boolean failed) {
    }

    /**
     * One radius, over every pad filter this ship needs, de-duplicated; same merge as the refuel search.
     */
    private static Fetched fetch(double x, double y, double z, int radius, String padSize) {
        Map<String, TradeStationSearchResultDto.StationResult> byId = new LinkedHashMap<>();
        boolean failed = false;
        for (RefuelStationSearch.PadFilter padFilter : RefuelStationSearch.padFilters(padSize)) {
            TradeStationSearchCriteria criteria = searchCriteria(x, y, z, radius, padFilter);
            log.debug("Orbital station criteria: {}", criteria.toJson());
            TradeStationSearchResultDto response = StationSearchClient.getInstance().searchStations(criteria);
            // A failed POST, a search that times out and an empty body all arrive here as a null.
            if (response == null || response.getResults() == null) {
                failed = true;
                continue;
            }
            for (TradeStationSearchResultDto.StationResult station : response.getResults()) {
                byId.putIfAbsent(RefuelStationSearch.identity(station), station);
            }
        }
        return new Fetched(List.copyOf(byId.values()), failed);
    }

    /**
     * The request body: orbital types only, no service, no market, nearest first.
     *
     * <p>Package-private and separate from the call so the wire shape can be asserted without a live search;
     * Spansh matches nothing at all against a type it does not know, which narrows the search silently.
     */
    static TradeStationSearchCriteria searchCriteria(
            double x, double y, double z, int radius, RefuelStationSearch.PadFilter padFilter) {

        TradeStationSearchCriteria.StationType stationType = new TradeStationSearchCriteria.StationType();
        stationType.setTypes(ORBITAL_STATION_TYPES);

        /// NOTE: the light year radius takes a min/max pair of STRINGS; see RefuelStationSearch.
        TradeStationSearchCriteria.Distance distance = new TradeStationSearchCriteria.Distance();
        distance.setMin(0);
        distance.setMax(radius);

        TradeStationSearchCriteria.Filters filters = new TradeStationSearchCriteria.Filters();
        filters.setStationType(stationType);
        filters.setDistanceToStarSystem(distance);
        filters.setDistanceToArrival(new TradeStationSearchCriteria.RangeFilter(0, RefuelStationSearch.MAX_ARRIVAL_LS));
        if (padFilter == RefuelStationSearch.PadFilter.MEDIUM) filters.setMediumPads(RefuelStationSearch.atLeastOnePad());
        if (padFilter == RefuelStationSearch.PadFilter.LARGE) filters.setLargePads(RefuelStationSearch.atLeastOnePad());

        TradeStationSearchCriteria criteria = new TradeStationSearchCriteria();
        criteria.setFilters(filters);
        criteria.setReferenceCoords(new TradeStationSearchCriteria.ReferenceCoords(x, y, z));
        criteria.setSort(List.of(new TradeStationSearchCriteria.DistanceSort()));
        criteria.setSize(RefuelStationSearch.CANDIDATES);
        criteria.setPage(0);
        return criteria;
    }

    /**
     * The stations this ship can land on, nearest first, then shortest supercruise once there.
     *
     * <p>The type is checked again here over what Spansh sent, as the pad counts are: the request is a
     * search, and a row of another type must not become an answer because a filter was mis-sent.
     */
    static List<OrbitalStation> rank(List<TradeStationSearchResultDto.StationResult> stations,
                                     String padSize, long excludeMarketId) {
        String excluded = excludeMarketId > 0 ? String.valueOf(excludeMarketId) : null;
        List<OrbitalStation> usable = new ArrayList<>();
        for (TradeStationSearchResultDto.StationResult station : stations) {
            if (station.getName() == null || station.getSystemName() == null) continue;
            if (!ORBITAL_STATION_TYPES.contains(station.getType())) continue;
            if (excluded != null && excluded.equals(station.getMarketId())) continue;
            if (!ShipPadSizes.canDock(padSize, RefuelStationSearch.count(station.getSmallPads()),
                    RefuelStationSearch.count(station.getMediumPads()),
                    RefuelStationSearch.count(station.getLargePads()))) {
                continue;
            }
            usable.add(new OrbitalStation(
                    station.getSystemName(), station.getName(), station.getType(),
                    station.getDistance() == null ? 0 : station.getDistance(),
                    station.getDistanceToArrival() == null ? 0 : station.getDistanceToArrival(),
                    station.getMarketId()));
        }
        usable.sort(Comparator.comparingDouble(OrbitalStation::distanceLy)
                .thenComparingDouble(OrbitalStation::arrivalLs));
        return List.copyOf(usable);
    }
}
