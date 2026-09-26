package elite.intel.ui.overlay;

import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.FuzzySearch;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.gameapi.search.spansh.SpanshTimestamps;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Projects the most recent trade candidate or outfitting query result into a HUD objective card.
 * Registered as PRIORITY_AMBIENT at the end of the source list.
 */
public class QueryResultObjectiveSource implements HudObjectiveSource {

    public static final String ID = "query-result";
    private static final int MAX_AGE_HOURS = 10;

    private final Supplier<Optional<LatestDisplay>> latestSupplier;
    private final Supplier<Instant> nowSupplier;

    public QueryResultObjectiveSource() {
        this(() -> QueryResultDisplayManager.getInstance().getLatest(), Instant::now);
    }

    // Visible for testing
    QueryResultObjectiveSource(Supplier<Optional<LatestDisplay>> latestSupplier, Supplier<Instant> nowSupplier) {
        this.latestSupplier = latestSupplier;
        this.nowSupplier = nowSupplier;
    }

    @Override
    public Optional<HudObjective> currentObjective() {
        Optional<LatestDisplay> opt = latestSupplier.get();
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        LatestDisplay latest = opt.get();
        Instant now = nowSupplier.get();

        // 10 hours freshness cutoff based on when result was saved
        if (latest.savedAt() == null || latest.savedAt().isBefore(now.minus(MAX_AGE_HOURS, ChronoUnit.HOURS))) {
            return Optional.empty();
        }

        if (latest.isTradeCandidates()) {
            return buildTradeCandidatesObjective(latest.tradeCandidates(), now);
        } else if (latest.isOutfitting()) {
            return buildOutfittingObjective(latest.outfitting(), now);
        }

        return Optional.empty();
    }

    private Optional<HudObjective> buildTradeCandidatesObjective(TradeCandidatesDataDto dto, Instant now) {
        if (dto == null || dto.candidates() == null || dto.candidates().isEmpty()) {
            return Optional.empty();
        }

        TradeCandidateDto first = dto.candidates().get(0);
        List<HudRow> rows = new ArrayList<>();

        // 1. Commodity
        String localizedCommodity = FuzzySearch.localizedCommodityName(first.commodity());
        rows.add(HudRow.of(HudText.get("overlay.card.row.commodity"), localizedCommodity));

        // 2. Buy station (system)
        String buyText = first.buyStation() + " (" + first.buySystem() + ")";
        rows.add(HudRow.of(HudText.get("overlay.card.row.buy"), buyText));

        // 3. Sell station (system)
        String sellText = first.sellStation() + " (" + first.sellSystem() + ")";
        rows.add(HudRow.of(HudText.get("overlay.card.row.sell"), sellText));

        // 4. Trip profit
        String profitText = (first.tripProfitDisplay() != null ? first.tripProfitDisplay() : "-") + " cr";
        rows.add(HudRow.of(HudText.get("overlay.card.row.tripProfit"), profitText, HudRow.State.GOOD));

        // 5. Freshness
        String freshness = calculateFreshness(first.buyMarketUpdatedAt(), first.sellMarketUpdatedAt(), now);
        if (freshness != null) {
            rows.add(HudRow.of(HudText.get("overlay.card.row.freshness"), freshness));
        }

        // Additional row if there are other candidates
        int others = dto.candidates().size() - 1;
        if (others > 0) {
            rows.add(HudRow.of(HudText.get("overlay.card.row.otherCandidates"), String.valueOf(others)));
        }

        return Optional.of(new HudObjective(
                ID,
                HudText.get("overlay.card.title.tradeCandidates"),
                null,
                rows,
                HudObjective.PRIORITY_AMBIENT
        ));
    }

    private Optional<HudObjective> buildOutfittingObjective(OutfittingDataDto dto, Instant now) {
        if (dto == null || !"found".equalsIgnoreCase(dto.status())) {
            return Optional.empty();
        }

        List<HudRow> rows = new ArrayList<>();

        // 1. Module
        String moduleName = dto.module() != null && dto.module().name() != null ? dto.module().name() : dto.rawModuleInput();
        rows.add(HudRow.of(HudText.get("overlay.card.row.module"), moduleName));

        // 2. Station
        rows.add(HudRow.of(HudText.get("overlay.card.row.station"), dto.stationName()));

        // 3. System
        rows.add(HudRow.of(HudText.get("overlay.card.row.system"), dto.starSystem()));

        // 4. Distance
        String distText = (dto.distanceLyDisplay() != null ? dto.distanceLyDisplay() : "-") + " ly";
        rows.add(HudRow.of(HudText.get("overlay.card.row.distance"), distText));

        // 5. Price
        String priceText = (dto.priceDisplay() != null ? dto.priceDisplay() : "-") + " cr";
        HudRow.State priceState = dto.stale() ? HudRow.State.WARN : HudRow.State.NORMAL;
        rows.add(HudRow.of(HudText.get("overlay.card.row.price"), priceText, priceState));

        return Optional.of(new HudObjective(
                ID,
                HudText.get("overlay.card.title.outfitting"),
                null,
                rows,
                HudObjective.PRIORITY_AMBIENT
        ));
    }

    public static String calculateFreshness(String time1, String time2, Instant now) {
        Instant i1 = parseTime(time1);
        Instant i2 = parseTime(time2);
        Instant oldest = null;
        if (i1 != null && i2 != null) {
            oldest = i1.isBefore(i2) ? i1 : i2;
        } else if (i1 != null) {
            oldest = i1;
        } else if (i2 != null) {
            oldest = i2;
        }
        if (oldest == null) {
            return null;
        }
        return formatTimeAgo(oldest, now);
    }

    public static String formatTimeAgo(Instant updated, Instant now) {
        if (updated == null) return null;
        Duration duration = Duration.between(updated, now);
        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }
        long hours = duration.toHours();
        if (hours >= 24) {
            long days = hours / 24;
            return HudText.get("overlay.card.time.daysAgo", days);
        } else if (hours >= 1) {
            return HudText.get("overlay.card.time.hoursAgo", hours);
        } else {
            long minutes = Math.max(1, duration.toMinutes());
            return HudText.get("overlay.card.time.minutesAgo", minutes);
        }
    }

    private static Instant parseTime(String ts) {
        if (ts == null || ts.isBlank()) return null;
        try {
            return SpanshTimestamps.parse(ts);
        } catch (Exception e) {
            return null;
        }
    }
}
