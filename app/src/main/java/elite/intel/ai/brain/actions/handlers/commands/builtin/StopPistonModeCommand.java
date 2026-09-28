package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.IntelActionContext;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.commands.RegisterCommand;
import elite.intel.db.managers.PistonModeManager;
import elite.intel.session.Status;
import elite.intel.util.StringUtls;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Built-in command to terminate active piston trading mode (PT-4).
 */
@RegisterCommand
public final class StopPistonModeCommand implements IntelCommand {

    public static final String ID = "stop_piston_mode";

    private final Supplier<PistonModeManager> managerSupplier;

    public StopPistonModeCommand() {
        this(PistonModeManager::getInstance);
    }

    StopPistonModeCommand(Supplier<PistonModeManager> managerSupplier) {
        this.managerSupplier = Objects.requireNonNull(managerSupplier, "managerSupplier");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Stop active piston trading mode.";
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
    public boolean sendsGameInput() {
        return false;
    }

    @Override
    public String execute(JsonObject params, String responseText) {
        boolean stopped = managerSupplier.get().stop();
        if (stopped) {
            return StringUtls.localizedResponse("handler.pistonMode.stopped");
        } else {
            return StringUtls.localizedResponse("handler.pistonMode.stoppedNotActive");
        }
    }
}
