package elite.intel.gameapi.journal.subscribers;

import com.google.common.eventbus.Subscribe;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.gameapi.journal.events.EngineerProgressEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EngineerProgressSubscriber {

    private static final Logger log = LogManager.getLogger(EngineerProgressSubscriber.class);

    @Subscribe
    public void onEngineerProgressEvent(EngineerProgressEvent event) {
        if (event == null || event.getEngineers() == null) {
            return;
        }

        String eventTimestamp = event.getTimestamp();
        EngineerProgressManager manager = EngineerProgressManager.getInstance();

        for (EngineerProgressEvent.Engineer eng : event.getEngineers()) {
            if (eng == null || eng.getName() == null || eng.getName().isBlank()) {
                continue;
            }
            manager.recordProgress(
                    eng.getName(),
                    eng.getEngineerIdNullable(),
                    eng.getProgress(),
                    eng.getRankNullable(),
                    eng.getRankProgressNullable(),
                    eventTimestamp
            );
        }
    }
}
