package elite.intel.ai.brain.actions;

import elite.intel.ai.brain.actions.handlers.commands.CommandRegistry;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.queries.IntelQuery;
import elite.intel.ai.brain.actions.handlers.queries.QueryRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates that every parameter declared by registered commands and queries in
 * {@link CommandRegistry} and {@link QueryRegistry} satisfies {@link ActionParameterSpec#validate()}.
 */
class AllActionParameterSpecsValidTest {

    @BeforeAll
    static void setUp() {
        CommandRegistry.getInstance().load();
        QueryRegistry.getInstance().load();
    }

    @Test
    void testAllCommandParameterSpecsAreValid() {
        List<String> failures = new ArrayList<>();
        for (IntelCommand command : CommandRegistry.getInstance().byId().values()) {
            List<ActionParameterSpec> specs = command.parameters();
            if (specs == null) {
                continue;
            }
            for (ActionParameterSpec spec : specs) {
                try {
                    spec.validate();
                } catch (Exception e) {
                    failures.add(String.format("Command '%s' parameter '%s' failed validation: %s",
                            command.id(), spec.getName(), e.getMessage()));
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "Found invalid ActionParameterSpec(s) in CommandRegistry:\n" + String.join("\n", failures));
    }

    @Test
    void testAllQueryParameterSpecsAreValid() {
        List<String> failures = new ArrayList<>();
        for (IntelQuery query : QueryRegistry.getInstance().byId().values()) {
            List<ActionParameterSpec> specs = query.parameters();
            if (specs == null) {
                continue;
            }
            for (ActionParameterSpec spec : specs) {
                try {
                    spec.validate();
                } catch (Exception e) {
                    failures.add(String.format("Query '%s' parameter '%s' failed validation: %s",
                            query.id(), spec.getName(), e.getMessage()));
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "Found invalid ActionParameterSpec(s) in QueryRegistry:\n" + String.join("\n", failures));
    }
}
