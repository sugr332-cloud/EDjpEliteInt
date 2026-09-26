package elite.intel.ui.screen;

import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.commands.builtin.NavigateToSearchResultCommand;
import elite.intel.db.FuzzySearch;
import elite.intel.ui.overlay.QueryResultObjectiveSource;
import elite.intel.ui.support.GuiCommandRunner;
import elite.intel.ui.theme.HudPalette;
import elite.intel.ui.widget.HudButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static elite.intel.ui.i18n.MultiLingualTextProvider.getText;
import static elite.intel.ui.theme.AppTheme.hudMajorPanelBorder;
import static elite.intel.ui.theme.HudPalette.*;

/**
 * A bordered card component displaying one trade candidate or outfitting result item.
 * Lays out key:value rows vertically in fixed order.
 */
public class QueryResultCard extends JPanel {

    public record CardRow(String label, String value, boolean isWarn, boolean isHighlight) {
    }

    private final String title;
    private final List<CardRow> rows;
    private final List<JButton> actionButtons;

    public QueryResultCard(String title, List<CardRow> rows) {
        this(title, rows, Collections.emptyList());
    }

    public QueryResultCard(String title, List<CardRow> rows, List<JButton> actionButtons) {
        this.title = title;
        this.rows = rows != null ? List.copyOf(rows) : Collections.emptyList();
        this.actionButtons = actionButtons != null ? List.copyOf(actionButtons) : Collections.emptyList();
        buildUi();
    }

    public List<JButton> getActionButtons() {
        return actionButtons;
    }

    public String getCardTitle() {
        return title;
    }

    public List<CardRow> getRows() {
        return rows;
    }

    public static QueryResultCard forTradeCandidate(TradeCandidateDto c, Instant now) {
        String title = getText("ai.queryResult.rank", c.rank());
        List<CardRow> list = new ArrayList<>();

        // 1. 品目
        list.add(new CardRow(getText("ai.queryResult.row.commodity"), FuzzySearch.localizedCommodityName(c.commodity()), false, false));
        // 2. 購入
        list.add(new CardRow(getText("ai.queryResult.row.buy"), c.buyStation() + "（" + c.buySystem() + "）", false, false));
        // 3. 購入 Ls
        String buyLs = (c.buyStationDistanceLsDisplay() != null ? c.buyStationDistanceLsDisplay() : "-") + " Ls";
        list.add(new CardRow(getText("ai.queryResult.row.buyLs"), buyLs, false, false));
        // 4. 売却
        list.add(new CardRow(getText("ai.queryResult.row.sell"), c.sellStation() + "（" + c.sellSystem() + "）", false, false));
        // 5. 売却 Ls
        String sellLs = (c.sellStationDistanceLsDisplay() != null ? c.sellStationDistanceLsDisplay() : "-") + " Ls";
        list.add(new CardRow(getText("ai.queryResult.row.sellLs"), sellLs, false, false));
        // 6. 1 回の総利益
        String tripProfit = (c.tripProfitDisplay() != null ? c.tripProfitDisplay() : "-") + " cr";
        list.add(new CardRow(getText("ai.queryResult.row.tripProfit"), tripProfit, false, true));
        // 7. 単位利益
        String unitProfit = (c.unitProfitDisplay() != null ? c.unitProfitDisplay() : "-") + " cr/t";
        list.add(new CardRow(getText("ai.queryResult.row.unitProfit"), unitProfit, false, false));
        // 8. 数量
        String units = (c.unitsDisplay() != null ? c.unitsDisplay() : "-") + " t";
        list.add(new CardRow(getText("ai.queryResult.row.units"), units, false, false));
        // 9. 区間距離
        String routeDist = (c.routeDistanceLyDisplay() != null ? c.routeDistanceLyDisplay() : "-") + " ly";
        list.add(new CardRow(getText("ai.queryResult.row.routeDistance"), routeDist, false, false));
        // 10. 基準から
        String fromRefDist = (c.distanceFromCurrentLyDisplay() != null ? c.distanceFromCurrentLyDisplay() : "-") + " ly";
        list.add(new CardRow(getText("ai.queryResult.row.distanceFromReference"), fromRefDist, false, false));
        // 11. 鮮度
        String freshness = QueryResultObjectiveSource.calculateFreshness(c.buyMarketUpdatedAt(), c.sellMarketUpdatedAt(), now);
        list.add(new CardRow(getText("ai.queryResult.row.freshness"), freshness != null ? freshness : "-", false, false));

        return new QueryResultCard(title, list, createTradeCandidateButtons(c.rank()));
    }

    private static List<JButton> createTradeCandidateButtons(int rank) {
        List<JButton> buttons = new ArrayList<>();
        JButton buyBtn = new HudButton(getText("ai.queryResult.btn.buy"), false);
        JButton sellBtn = new HudButton(getText("ai.queryResult.btn.sell"), false);
        buttons.add(buyBtn);
        buttons.add(sellBtn);
        setupButtonAction(buyBtn, buttons, rank, "buy");
        setupButtonAction(sellBtn, buttons, rank, "sell");
        return buttons;
    }

    public static QueryResultCard forOutfitting(OutfittingDataDto dto, Instant now) {
        String title = getText("ai.queryResult.title.outfitting");
        List<CardRow> list = new ArrayList<>();

        String modName = dto.module() != null && dto.module().name() != null ? dto.module().name() : dto.rawModuleInput();
        // 1. モジュール
        list.add(new CardRow(getText("ai.queryResult.row.module"), modName != null ? modName : "-", false, false));
        // 2. ステーション
        list.add(new CardRow(getText("ai.queryResult.row.station"), dto.stationName() != null ? dto.stationName() : "-", false, false));
        // 3. 星系
        list.add(new CardRow(getText("ai.queryResult.row.system"), dto.starSystem() != null ? dto.starSystem() : "-", false, false));
        // 4. 種別
        list.add(new CardRow(getText("ai.queryResult.row.stationType"), dto.stationType() != null ? dto.stationType() : "-", false, false));
        // 5. 距離
        String dist = (dto.distanceLyDisplay() != null ? dto.distanceLyDisplay() : "-") + " ly";
        list.add(new CardRow(getText("ai.queryResult.row.distance"), dist, false, false));
        // 6. 到着 Ls
        String arrivalLs = (dto.distanceToArrivalLsDisplay() != null ? dto.distanceToArrivalLsDisplay() + " Ls" : "-");
        list.add(new CardRow(getText("ai.queryResult.row.distanceToArrival"), arrivalLs, false, false));
        // 7. 価格
        String price = (dto.priceDisplay() != null ? dto.priceDisplay() : "-") + " cr";
        list.add(new CardRow(getText("ai.queryResult.row.price"), price, false, false));
        // 8. 鮮度 (7日超は警告色)
        String freshness = QueryResultObjectiveSource.calculateFreshness(dto.outfittingUpdatedAt(), null, now);
        list.add(new CardRow(getText("ai.queryResult.row.freshness"), freshness != null ? freshness : "-", dto.stale(), false));

        List<JButton> buttons = new ArrayList<>();
        JButton goBtn = new HudButton(getText("ai.queryResult.btn.outfitting"), false);
        buttons.add(goBtn);
        setupButtonAction(goBtn, buttons, 1, "buy");

        return new QueryResultCard(title, list, buttons);
    }

    private static void setupButtonAction(JButton button, List<JButton> cardButtons, int rank, String leg) {
        button.addActionListener(e -> {
            disableButtonsTemporarily(cardButtons);
            JsonObject params = new JsonObject();
            params.addProperty("rank", rank);
            params.addProperty("leg", leg);
            params.addProperty("source", "gui");
            GuiCommandRunner.runAfterClosingWindow(null, NavigateToSearchResultCommand.ID, params, true);
        });
    }

    private static void disableButtonsTemporarily(List<JButton> buttons) {
        for (JButton btn : buttons) {
            btn.setEnabled(false);
        }
        Timer timer = new Timer(5000, e -> {
            for (JButton btn : buttons) {
                btn.setEnabled(true);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void buildUi() {
        setLayout(new BorderLayout(0, 4));
        setBackground(HUD_COLOR_ROLE_PANEL_BACKGROUND);
        setBorder(hudMajorPanelBorder());

        // Header: Title
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.0f));
        titleLabel.setForeground(HUD_COLOR_FF822E);
        titleLabel.setBorder(new EmptyBorder(2, 4, 4, 4));
        add(titleLabel, BorderLayout.NORTH);

        // Body: Rows
        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setOpaque(false);

        for (CardRow r : rows) {
            JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
            rowPanel.setOpaque(false);
            rowPanel.setBorder(new EmptyBorder(1, 4, 1, 4));

            JLabel labelComp = new JLabel(r.label() + "：");
            labelComp.setFont(labelComp.getFont().deriveFont(11.0f));
            labelComp.setForeground(HUD_COLOR_ROLE_READOUT_LABEL);

            JLabel valueComp = new JLabel(r.value());
            valueComp.setFont(valueComp.getFont().deriveFont(11.0f));
            if (r.isWarn()) {
                valueComp.setForeground(HUD_COLOR_D94F4F);
            } else if (r.isHighlight()) {
                valueComp.setForeground(HUD_COLOR_4FC56B);
            } else {
                valueComp.setForeground(HUD_COLOR_ROLE_PRIMARY_TEXT);
            }

            rowPanel.add(labelComp, BorderLayout.WEST);
            rowPanel.add(valueComp, BorderLayout.CENTER);
            rowsPanel.add(rowPanel);
        }

        add(rowsPanel, BorderLayout.CENTER);

        // Footer: Action Buttons
        if (!actionButtons.isEmpty()) {
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
            btnPanel.setOpaque(false);
            btnPanel.setBorder(new EmptyBorder(2, 4, 4, 4));
            for (JButton btn : actionButtons) {
                btnPanel.add(btn);
            }
            add(btnPanel, BorderLayout.SOUTH);
        }
    }
}
