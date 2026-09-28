package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.IntelActionContext;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.commands.RegisterCommand;
import elite.intel.db.managers.PistonModeManager;
import elite.intel.session.Status;
import elite.intel.util.StringUtls;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Built-in command to initiate piston trading mode (PT-4).
 * Alternates navigation between endpoints A and B upon departure.
 */
@RegisterCommand
public final class StartPistonModeCommand implements IntelCommand {

    public static final String ID = "start_piston_mode";

    private final Supplier<PistonModeManager> managerSupplier;
    private final BooleanSupplier inMainShipChecker;

    public StartPistonModeCommand() {
        this(PistonModeManager::getInstance, () -> Status.getInstance().isInMainShip());
    }

    StartPistonModeCommand(Supplier<PistonModeManager> managerSupplier, BooleanSupplier inMainShipChecker) {
        this.managerSupplier = Objects.requireNonNull(managerSupplier, "managerSupplier");
        this.inMainShipChecker = Objects.requireNonNull(inMainShipChecker, "inMainShipChecker");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Start piston trading mode between two stations (from recent docking history or trade search candidate rank).";
    }

    @Override
    public boolean isVisibleForLLM(Status status) {
        return true;
    }

    @Override
    public boolean isAvailableIn(IntelActionContext context) {
        return true;
    }

    @Override
    public List<ActionParameterSpec> parameters() {
        return List.of(
                new ActionParameterSpec(
                        "rank",
                        "number",
                        false,
                        "Trade candidate rank (1 to 3, optional). If omitted, uses recent docking history.",
                        List.of("1", "2", "3"),
                        "Trade candidate rank 1, 2, or 3"
                )
        );
    }

    @Override
    public String execute(JsonObject params, String responseText) {
        // 1. Ship check
        if (!inMainShipChecker.getAsBoolean()) {
            return StringUtls.localizedResponse("handler.pistonMode.notInMainShip");
        }

        // 2. Parse rank parameter if provided
        Integer rank = null;
        if (params != null && params.has("rank") && !params.get("rank").isJsonNull()) {
            JsonElement rankElem = params.get("rank");
            try {
                double val = rankElem.getAsDouble();
                if (val != Math.floor(val) || val < 1) {
                    return StringUtls.localizedResponse("handler.pistonMode.invalidRank", rankElem.getAsString());
                }
                rank = (int) val;
            } catch (Exception e) {
                return StringUtls.localizedResponse("handler.pistonMode.invalidRank", rankElem.getAsString());
            }
        }

        PistonModeManager manager = managerSupplier.get();
        PistonModeManager.StartResult result;
        if (rank != null) {
            result = manager.startFromTradeCandidates(rank);
        } else {
            result = manager.startFromHistory();
        }

        return result.message();
    }
}
