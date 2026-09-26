package elite.intel.ui.screen;

import com.google.common.eventbus.Subscribe;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.eventbus.UiBus;
import elite.intel.ui.event.QueryResultDisplayUpdatedEvent;
import elite.intel.ui.theme.HudPalette;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static elite.intel.ui.i18n.MultiLingualTextProvider.getText;
import static elite.intel.ui.theme.AppTheme.hudApplicationScrollPane;
import static elite.intel.ui.theme.HudPalette.*;

/**
 * Main panel displaying query results (trade candidates or nearest outfitting) in the AI tab.
 * Contains a 1-line search criteria header, empty state fallback, and vertically scrolling cards.
 */
public class QueryResultDisplayPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JLabel headerLabel;
    private final JPanel cardsContainer;
    private final JLabel emptyLabel;

    public QueryResultDisplayPanel() {
        setLayout(new BorderLayout(0, 4));
        setOpaque(false);

        // Header: Search conditions (1 line)
        headerLabel = new JLabel();
        headerLabel.setFont(headerLabel.getFont().deriveFont(11.0f));
        headerLabel.setForeground(HUD_COLOR_ROLE_READOUT_LABEL);
        headerLabel.setBorder(new EmptyBorder(2, 4, 4, 4));
        add(headerLabel, BorderLayout.NORTH);

        // Center: Scroll pane containing cards
        cardsContainer = new JPanel();
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setOpaque(false);

        emptyLabel = new JLabel(getText("ai.queryResult.noResult"), SwingConstants.CENTER);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(12.0f));
        emptyLabel.setForeground(HUD_COLOR_5A6368);
        emptyLabel.setBorder(new EmptyBorder(20, 10, 20, 10));

        JScrollPane scrollPane = hudApplicationScrollPane(cardsContainer);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        UiBus.register(this);
        refreshFromDb();
    }

    public void dispose() {
        UiBus.unregister(this);
    }

    @Subscribe
    public void onQueryResultUpdated(QueryResultDisplayUpdatedEvent event) {
        SwingUtilities.invokeLater(this::refreshFromDb);
    }

    public void refreshFromDb() {
        Optional<LatestDisplay> opt = QueryResultDisplayManager.getInstance().getLatest();
        if (opt.isEmpty()) {
            showEmpty();
            return;
        }

        LatestDisplay latest = opt.get();
        Instant now = Instant.now();
        String timeStr = latest.savedAt() != null ? TIME_FORMATTER.format(latest.savedAt()) : "-";

        if (latest.isTradeCandidates()) {
            displayTradeCandidates(latest.tradeCandidates(), timeStr, now);
        } else if (latest.isOutfitting()) {
            displayOutfitting(latest.outfitting(), timeStr, now);
        } else {
            showEmpty();
        }
    }

    private void showEmpty() {
        headerLabel.setText("");
        cardsContainer.removeAll();
        cardsContainer.add(emptyLabel);
        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    private void displayTradeCandidates(TradeCandidatesDataDto dto, String timeStr, Instant now) {
        if (dto == null || dto.candidates() == null || dto.candidates().isEmpty()) {
            showEmpty();
            return;
        }

        String refSys = dto.searchedFromSystem() != null ? dto.searchedFromSystem() : (dto.currentSystem() != null ? dto.currentSystem() : "-");
        String radius = (dto.searchRadiusLyDisplay() != null ? dto.searchRadiusLyDisplay() : String.valueOf(dto.searchRadiusLy())) + " ly";
        String prioText = "nearest".equalsIgnoreCase(dto.priority()) ? getText("ai.queryResult.priority.nearest") : getText("ai.queryResult.priority.profit");

        headerLabel.setText(getText("ai.queryResult.header.tradeCandidates", refSys, radius, prioText, timeStr));

        cardsContainer.removeAll();
        for (TradeCandidateDto c : dto.candidates()) {
            QueryResultCard card = QueryResultCard.forTradeCandidate(c, now);
            cardsContainer.add(card);
            cardsContainer.add(Box.createVerticalStrut(6));
        }
        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    private void displayOutfitting(OutfittingDataDto dto, String timeStr, Instant now) {
        if (dto == null || !"found".equalsIgnoreCase(dto.status())) {
            showEmpty();
            return;
        }

        String modName = dto.module() != null && dto.module().name() != null ? dto.module().name() : dto.rawModuleInput();
        headerLabel.setText(getText("ai.queryResult.header.outfitting", modName != null ? modName : "-", timeStr));

        cardsContainer.removeAll();
        QueryResultCard card = QueryResultCard.forOutfitting(dto, now);
        cardsContainer.add(card);
        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    public JLabel getHeaderLabel() {
        return headerLabel;
    }

    public JPanel getCardsContainer() {
        return cardsContainer;
    }
}
