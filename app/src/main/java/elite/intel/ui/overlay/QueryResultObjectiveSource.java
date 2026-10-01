package elite.intel.ui.overlay;

import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.FuzzySearch;
import elite.intel.db.dao.EngineerProgressDao.EngineerProgressRecord;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.gameapi.engineers.EngineerDirectory;
import elite.intel.gameapi.engineers.EngineerDirectory.EngineerInfo;
import elite.intel.gameapi.engineers.EngineerDirectory.Specialty;
import elite.intel.gameapi.search.spansh.SpanshTimestamps;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import static elite.intel.ui.i18n.MultiLingualTextProvider.getText;

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
        } else if (latest.isEngineers()) {
            return buildEngineersObjective(latest.engineers(), now);
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

    static String formatEngineerStatus(String progress, Integer rank) {
        if (progress == null || progress.isBlank()) {
            return getText("ai.queryResult.engineer.noRecord");
        }
        switch (progress.toLowerCase(Locale.ROOT)) {
            case "unlocked" -> {
                String unlockedText = getText("ai.queryResult.engineer.unlocked");
                if (rank != null && rank >= 1) {
                    return unlockedText + " " + getText("ai.queryResult.engineer.rank", rank);
                }
                return unlockedText;
            }
            case "barred" -> {
                return getText("ai.queryResult.engineer.barred");
            }
            case "acquainted" -> {
                return getText("ai.queryResult.engineer.acquainted");
            }
            case "invited" -> {
                return getText("ai.queryResult.engineer.invited");
            }
            case "known" -> {
                return getText("ai.queryResult.engineer.known");
            }
            default -> {
                return progress;
            }
        }
    }

    private Optional<HudObjective> buildEngineersObjective(EngineersDisplayDto dto, Instant now) {
        if (dto == null || dto.engineerNames() == null || dto.engineerNames().isEmpty()) {
            return Optional.empty();
        }

        List<HudRow> rows = new ArrayList<>();
        String queryKind = dto.queryKind();

        Language lang = SystemSession.getInstance().getLanguage();
        if ("module".equalsIgnoreCase(queryKind)) {
            String rawMod = dto.moduleName() != null ? dto.moduleName() : "-";
            String mod = EngineerDirectory.getInstance().displaySpecialtyName(rawMod, lang);
            rows.add(HudRow.of(HudText.get("overlay.card.row.module"), mod));

            String firstEngName = dto.engineerNames().get(0);
            Optional<EngineerInfo> infoOpt = EngineerDirectory.getInstance().findByName(firstEngName);
            Optional<EngineerProgressRecord> progOpt = EngineerProgressManager.getInstance().findByName(firstEngName);
            String firstEngDispName = EngineerDirectory.getInstance().displayName(firstEngName, lang);

            int grade = 0;
            if (infoOpt.isPresent() && infoOpt.get().specialties() != null) {
                for (Specialty sp : infoOpt.get().specialties()) {
                    if (sp.module().equalsIgnoreCase(dto.moduleName())) {
                        grade = sp.maxGrade();
                        break;
                    }
                }
            }
            String gradeStr = grade > 0 ? " G" + grade : "";
            String statusStr = formatEngineerStatus(
                    progOpt.map(EngineerProgressRecord::progress).orElse(null),
                    progOpt.map(EngineerProgressRecord::rank).orElse(null)
            );
            String statusInParens = HudText.get("overlay.card.row.engineerStatusInParens", statusStr);

            int others = dto.engineerNames().size() - 1;
            String secondLine = firstEngDispName + gradeStr + " " + statusInParens;
            if (others > 0) {
                secondLine += " " + HudText.get("overlay.card.row.engineerOtherCandidates", others);
            }
            rows.add(HudRow.of(HudText.get("overlay.card.title.engineers"), secondLine));

        } else if ("engineer".equalsIgnoreCase(queryKind)) {
            String engName = dto.engineerNames().get(0);
            String dispName = EngineerDirectory.getInstance().displayName(engName, lang);
            rows.add(HudRow.of(HudText.get("overlay.card.title.engineers"), dispName));

            Optional<EngineerInfo> infoOpt = EngineerDirectory.getInstance().findByName(engName);
            Optional<EngineerProgressRecord> progOpt = EngineerProgressManager.getInstance().findByName(engName);

            String loc = infoOpt.map(i -> i.base() + " (" + i.system() + ")").orElse("-");
            String statusStr = formatEngineerStatus(
                    progOpt.map(EngineerProgressRecord::progress).orElse(null),
                    progOpt.map(EngineerProgressRecord::rank).orElse(null)
            );
            rows.add(HudRow.of(loc, statusStr));

        } else if ("directory".equalsIgnoreCase(queryKind)) {
            rows.add(HudRow.of(HudText.get("overlay.card.title.engineers"), HudText.get("overlay.card.row.engineerDirectoryList")));

            int shipCount = 0;
            int onFootCount = 0;
            for (String name : dto.engineerNames()) {
                Optional<EngineerInfo> infoOpt = EngineerDirectory.getInstance().findByName(name);
                if (infoOpt.isPresent()) {
                    if (infoOpt.get().isShip()) {
                        shipCount++;
                    } else if (infoOpt.get().isOnFoot()) {
                        onFootCount++;
                    }
                }
            }

            String summary;
            if (shipCount > 0 && onFootCount > 0) {
                summary = HudText.get("overlay.card.row.engineerDirectorySummaryBoth", shipCount, onFootCount);
            } else if (shipCount > 0) {
                summary = HudText.get("overlay.card.row.engineerDirectorySummaryShip", shipCount);
            } else {
                summary = HudText.get("overlay.card.row.engineerDirectorySummaryOnFoot", onFootCount);
            }
            rows.add(HudRow.of(HudText.get("overlay.card.row.engineerSummary"), summary));

        } else {
            rows.add(HudRow.of(HudText.get("overlay.card.title.engineers"), HudText.get("overlay.card.row.engineerProgressList")));

            int total = dto.engineerNames().size();
            long unlockedCount = 0;
            for (String name : dto.engineerNames()) {
                Optional<EngineerProgressRecord> progOpt = EngineerProgressManager.getInstance().findByName(name);
                if (progOpt.isPresent() && "Unlocked".equalsIgnoreCase(progOpt.get().progress())) {
                    unlockedCount++;
                }
            }
            String summary = HudText.get("overlay.card.row.engineerUnlockedSummary", unlockedCount, total);
            rows.add(HudRow.of(HudText.get("overlay.card.row.progress"), summary));
        }

        return Optional.of(new HudObjective(
                ID,
                HudText.get("overlay.card.title.engineers"),
                null,
                rows,
                HudObjective.PRIORITY_AMBIENT
        ));
    }
}
