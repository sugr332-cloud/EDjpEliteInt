package elite.intel.ui.screen;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.commands.builtin.NavigateToEngineerCommand;
import elite.intel.ai.brain.actions.handlers.commands.builtin.NavigateToSearchResultCommand;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.db.FuzzySearch;
import elite.intel.db.dao.EngineerProgressDao.EngineerProgressRecord;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.EngineerChecklistManager;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.gameapi.engineers.EngineerDirectory;
import elite.intel.gameapi.engineers.EngineerDirectory.EngineerInfo;
import elite.intel.gameapi.engineers.EngineerDirectory.Specialty;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
import elite.intel.ui.overlay.QueryResultObjectiveSource;
import elite.intel.ui.support.GuiCommandRunner;
import elite.intel.ui.widget.HudButton;
import elite.intel.util.NavigationUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static elite.intel.ui.i18n.MultiLingualTextProvider.getText;
import static elite.intel.ui.theme.AppTheme.hudMajorPanelBorder;
import static elite.intel.ui.theme.HudPalette.*;

/**
 * A bordered card component displaying one trade candidate, outfitting result, or engineer item.
 * Lays out key:value rows vertically in fixed order, or custom body for engineers.
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

    public QueryResultCard(String title, JComponent customBody, List<JButton> actionButtons) {
        this.title = title;
        this.rows = Collections.emptyList();
        this.actionButtons = actionButtons != null ? List.copyOf(actionButtons) : Collections.emptyList();
        buildCustomUi(customBody);
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

    public static QueryResultCard forEngineer(String engineerName, String highlightModule, LocationDao.Coordinates here) {
        return forEngineer(engineerName, highlightModule, here, false);
    }

    public static QueryResultCard forEngineer(String engineerName, String highlightModule, LocationDao.Coordinates here, boolean showFilterButton) {
        Optional<EngineerInfo> opt = EngineerDirectory.getInstance().findByName(engineerName);
        if (opt.isEmpty()) {
            JPanel emptyBody = new JPanel();
            emptyBody.setOpaque(false);
            JLabel lbl = new JLabel(getText("ai.queryResult.engineer.noRecord"));
            lbl.setForeground(HUD_COLOR_5A6368);
            emptyBody.add(lbl);
            return new QueryResultCard(engineerName, emptyBody, Collections.emptyList());
        }

        EngineerInfo info = opt.get();
        Language lang = SystemSession.getInstance().getLanguage();
        EngineerDirectory dir = EngineerDirectory.getInstance();
        String dispName = dir.displayName(info, lang);
        String typeStr = "onfoot".equalsIgnoreCase(info.type())
                ? getText("ai.queryResult.engineer.onfoot")
                : getText("ai.queryResult.engineer.ship");
        String title = (lang == Language.JA)
                ? dispName + "（" + typeStr + "）"
                : dispName + " (" + typeStr + ")";

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        // 1. 基本情報段落: 星系 / 基地 / 天体 + 距離
        JPanel locPanel = new JPanel(new BorderLayout(8, 0));
        locPanel.setOpaque(false);
        locPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        locPanel.setBorder(new EmptyBorder(1, 4, 1, 4));

        JLabel locLabel = new JLabel(getText("ai.queryResult.row.station") + getText("ai.queryResult.rowSeparator"));
        locLabel.setFont(locLabel.getFont().deriveFont(11.0f));
        locLabel.setForeground(HUD_COLOR_ROLE_READOUT_LABEL);

        String stationText = info.system() + " / " + info.base() + (info.body() != null && !info.body().isBlank() ? " (" + info.body() + ")" : "");
        if (info.permitRequired()) {
            stationText += "  " + getText("ai.queryResult.engineer.permitRequired");
        }
        JLabel locValue = new JLabel(stationText);
        locValue.setFont(locValue.getFont().deriveFont(11.0f));
        locValue.setForeground(info.permitRequired() ? HUD_COLOR_D94F4F : HUD_COLOR_ROLE_PRIMARY_TEXT);

        locPanel.add(locLabel, BorderLayout.WEST);
        locPanel.add(locValue, BorderLayout.CENTER);
        body.add(locPanel);

        // 距離行
        JPanel distPanel = new JPanel(new BorderLayout(8, 0));
        distPanel.setOpaque(false);
        distPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        distPanel.setBorder(new EmptyBorder(1, 4, 1, 4));

        JLabel distLabel = new JLabel(getText("ai.queryResult.row.distance") + getText("ai.queryResult.rowSeparator"));
        distLabel.setFont(distLabel.getFont().deriveFont(11.0f));
        distLabel.setForeground(HUD_COLOR_ROLE_READOUT_LABEL);

        String distStr = "-";
        if (here != null && info.coords() != null) {
            double d = NavigationUtils.calculateGalacticDistance(
                    here.x(), here.y(), here.z(),
                    info.coords().x(), info.coords().y(), info.coords().z()
            );
            distStr = (Math.round(d * 10.0) / 10.0) + " ly";
        }
        JLabel distValue = new JLabel(distStr);
        distValue.setFont(distValue.getFont().deriveFont(11.0f));
        distValue.setForeground(HUD_COLOR_ROLE_PRIMARY_TEXT);

        distPanel.add(distLabel, BorderLayout.WEST);
        distPanel.add(distValue, BorderLayout.CENTER);
        body.add(distPanel);

        body.add(Box.createVerticalStrut(4));

        // 2. 得意分野段落
        if (info.specialties() != null && !info.specialties().isEmpty()) {
            JPanel specPanel = new JPanel(new BorderLayout(8, 0));
            specPanel.setOpaque(false);
            specPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            specPanel.setBorder(new EmptyBorder(1, 4, 1, 4));

            JLabel specLabel = new JLabel(getText("ai.queryResult.engineer.specialties") + getText("ai.queryResult.rowSeparator"));
            specLabel.setFont(specLabel.getFont().deriveFont(11.0f));
            specLabel.setForeground(HUD_COLOR_ROLE_READOUT_LABEL);

            JPanel specList = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            specList.setOpaque(false);

            for (Specialty sp : info.specialties()) {
                String modDisp = dir.localizedSpecialtyName(sp.module(), lang);
                String spText = modDisp + (sp.maxGrade() > 0 ? " G" + sp.maxGrade() : "");
                boolean isMatch = highlightModule != null && sp.module().equalsIgnoreCase(highlightModule);

                JLabel spComp = new JLabel(spText);
                spComp.setFont(spComp.getFont().deriveFont(isMatch ? Font.BOLD : Font.PLAIN, 11.0f));
                spComp.setForeground(isMatch ? HUD_COLOR_4FC56B : HUD_COLOR_ROLE_PRIMARY_TEXT);
                specList.add(spComp);
            }

            specPanel.add(specLabel, BorderLayout.WEST);
            specPanel.add(specList, BorderLayout.CENTER);
            body.add(specPanel);
        }

        body.add(Box.createVerticalStrut(4));

        // 3. 現在状況・自動チェックリスト段落
        Optional<EngineerProgressRecord> progOpt = EngineerProgressManager.getInstance().findByName(info.name());
        boolean isBarred = progOpt.isPresent() && "Barred".equalsIgnoreCase(progOpt.get().progress());

        if (isBarred) {
            JLabel barredLbl = new JLabel("⚠ " + getText("ai.queryResult.engineer.barred"));
            barredLbl.setFont(barredLbl.getFont().deriveFont(Font.BOLD, 11.0f));
            barredLbl.setForeground(HUD_COLOR_D94F4F);
            barredLbl.setBorder(new EmptyBorder(2, 4, 2, 4));
            barredLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(barredLbl);
        }

        String progress = progOpt.map(EngineerProgressRecord::progress).orElse(null);
        Integer rank = progOpt.map(EngineerProgressRecord::rank).orElse(null);
        Integer rankProg = progOpt.map(EngineerProgressRecord::rankProgress).orElse(null);

        int stage = 0;
        if (progress != null) {
            switch (progress.toLowerCase(Locale.ROOT)) {
                case "known" -> stage = 1;
                case "invited" -> stage = 2;
                case "acquainted" -> stage = 3;
                case "unlocked" -> stage = 4;
            }
        }
        if (rank != null && rank >= 1) {
            stage = Math.max(stage, 4);
        }

        JPanel autoProgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        autoProgPanel.setOpaque(false);
        autoProgPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (progOpt.isEmpty() || (stage == 0 && !isBarred)) {
            JLabel noRecLbl = new JLabel(getText("ai.queryResult.engineer.noRecord"));
            noRecLbl.setFont(noRecLbl.getFont().deriveFont(11.0f));
            noRecLbl.setForeground(HUD_COLOR_5A6368);
            autoProgPanel.add(noRecLbl);
        }

        record StageItem(boolean checked, String text) {}
        List<StageItem> allStageItems = new ArrayList<>();
        allStageItems.add(new StageItem(stage >= 4, getText("ai.queryResult.engineer.unlocked")));
        allStageItems.add(new StageItem(stage >= 3, getText("ai.queryResult.engineer.acquainted")));
        allStageItems.add(new StageItem(stage >= 2, getText("ai.queryResult.engineer.invited")));
        allStageItems.add(new StageItem(stage >= 1, getText("ai.queryResult.engineer.known")));

        for (int r = 1; r <= 5; r++) {
            boolean rChecked = (rank != null && rank >= r);
            String rText = getText("ai.queryResult.engineer.rank", r);
            if (rank != null && rank == r && rankProg != null && rankProg > 0) {
                rText += (lang == Language.JA) ? "（" + rankProg + "%）" : " (" + rankProg + "%)";
            }
            allStageItems.add(new StageItem(rChecked, rText));
        }

        // チェック済みが上（先頭）、未チェックが下（後続）
        for (StageItem it : allStageItems) {
            if (it.checked()) {
                addAutoStageLabel(autoProgPanel, it.text(), true);
            }
        }
        for (StageItem it : allStageItems) {
            if (!it.checked()) {
                addAutoStageLabel(autoProgPanel, it.text(), false);
            }
        }

        body.add(autoProgPanel);

        body.add(Box.createVerticalStrut(4));

        // 4. 手動チェックリスト段落
        boolean hasInvite = info.invite() != null && !info.invite().isBlank();
        boolean hasUnlock = info.unlock() != null && !info.unlock().isBlank();
        boolean hasReferral = "onfoot".equalsIgnoreCase(info.type()) && info.referral() != null && !info.referral().isBlank();

        if (hasInvite || hasUnlock || hasReferral) {
            JLabel chkTitle = new JLabel(getText("ai.queryResult.checklist.title") + getText("ai.queryResult.rowSeparator"));
            chkTitle.setFont(chkTitle.getFont().deriveFont(Font.BOLD, 11.0f));
            chkTitle.setForeground(HUD_COLOR_ROLE_READOUT_LABEL);
            chkTitle.setBorder(new EmptyBorder(2, 4, 2, 4));
            chkTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(chkTitle);

            EngineerChecklistManager chkMgr = EngineerChecklistManager.getInstance();

            if (hasInvite) {
                boolean initChecked = chkMgr.isChecked(info.name(), EngineerChecklistManager.ITEM_INVITE);
                String inviteText = dir.localizedInvite(info, lang);
                String baseText = getText("ai.queryResult.checklist.invite") + ": " + inviteText;
                JCheckBox cb = createChecklistCheckBox(info.name(), EngineerChecklistManager.ITEM_INVITE, baseText, initChecked);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(cb);
            }

            if (hasUnlock) {
                boolean initChecked = chkMgr.isChecked(info.name(), EngineerChecklistManager.ITEM_UNLOCK);
                String unlockText = dir.localizedUnlock(info, lang);
                String baseText = getText("ai.queryResult.checklist.unlock") + ": " + unlockText;
                JCheckBox cb = createChecklistCheckBox(info.name(), EngineerChecklistManager.ITEM_UNLOCK, baseText, initChecked);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(cb);
            }

            if (hasReferral) {
                boolean initChecked = chkMgr.isChecked(info.name(), EngineerChecklistManager.ITEM_REFERRAL_TASK);
                String refText = dir.formatReferral(info.referral(), lang);
                String baseText = getText("ai.queryResult.checklist.referral") + ": " + refText;
                JCheckBox cb = createChecklistCheckBox(info.name(), EngineerChecklistManager.ITEM_REFERRAL_TASK, baseText, initChecked);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(cb);
            }
        }

        List<JButton> buttons = new ArrayList<>();
        if (showFilterButton) {
            JButton isolateBtn = new HudButton(getText("ai.queryResult.btn.isolateEngineer"), false);
            buttons.add(isolateBtn);
            isolateBtn.addActionListener(e -> {
                QueryResultDisplayManager.getInstance().saveEngineers(
                        new EngineersDisplayDto("engineer", null, List.of(info.name()))
                );
            });
        }
        JButton goBtn = new HudButton(getText("ai.queryResult.btn.engineerStation"), false);
        buttons.add(goBtn);
        setupEngineerButtonAction(goBtn, buttons, info.name());

        return new QueryResultCard(title, body, buttons);
    }

    private static void addAutoStageLabel(JPanel container, String text, boolean checked) {
        JLabel lbl = new JLabel((checked ? "☑ " : "☐ ") + text);
        lbl.setFont(lbl.getFont().deriveFont(11.0f));
        lbl.setForeground(checked ? HUD_COLOR_4FC56B : HUD_COLOR_5A6368);
        container.add(lbl);
    }

    public static JCheckBox createChecklistCheckBox(String engineerName, String itemKey, String baseText, boolean initialChecked) {
        JCheckBox cb = new JCheckBox();
        cb.setOpaque(false);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setFont(cb.getFont().deriveFont(11.0f));
        cb.setSelected(initialChecked);
        updateChecklistStyle(cb, baseText, initialChecked);

        // 修正 1: ActionListener のみ使用（setSelected では呼ばれず、ユーザークリック時のみ発火）
        cb.addActionListener(e -> {
            boolean checked = cb.isSelected();
            EngineerChecklistManager.getInstance().setChecked(engineerName, itemKey, checked);
            updateChecklistStyle(cb, baseText, checked);
        });
        return cb;
    }

    private static void updateChecklistStyle(JCheckBox cb, String baseText, boolean checked) {
        if (checked) {
            cb.setText("<html><strike style='color:#5A6368;'>" + escapeHtml(baseText) + "</strike></html>");
            cb.setForeground(HUD_COLOR_5A6368);
        } else {
            cb.setText(baseText);
            cb.setForeground(HUD_COLOR_ROLE_PRIMARY_TEXT);
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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

    private static void setupEngineerButtonAction(JButton button, List<JButton> cardButtons, String engineerName) {
        button.addActionListener(e -> {
            disableButtonsTemporarily(cardButtons);
            JsonObject params = new JsonObject();
            params.addProperty("name", engineerName);
            params.addProperty("source", "gui");
            GuiCommandRunner.runAfterClosingWindow(null, NavigateToEngineerCommand.ID, params, true);
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

            JLabel labelComp = new JLabel(r.label() + getText("ai.queryResult.rowSeparator"));
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
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            btnPanel.setOpaque(false);
            btnPanel.setBorder(new EmptyBorder(2, 4, 4, 4));
            for (JButton btn : actionButtons) {
                btnPanel.add(btn);
            }
            add(btnPanel, BorderLayout.SOUTH);
        }
    }

    private void buildCustomUi(JComponent customBody) {
        setLayout(new BorderLayout(0, 4));
        setBackground(HUD_COLOR_ROLE_PANEL_BACKGROUND);
        setBorder(hudMajorPanelBorder());

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13.0f));
        titleLabel.setForeground(HUD_COLOR_FF822E);
        titleLabel.setBorder(new EmptyBorder(2, 4, 4, 4));
        add(titleLabel, BorderLayout.NORTH);

        if (customBody != null) {
            add(customBody, BorderLayout.CENTER);
        }

        if (!actionButtons.isEmpty()) {
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            btnPanel.setOpaque(false);
            btnPanel.setBorder(new EmptyBorder(2, 4, 4, 4));
            for (JButton btn : actionButtons) {
                btnPanel.add(btn);
            }
            add(btnPanel, BorderLayout.SOUTH);
        }
    }
}
