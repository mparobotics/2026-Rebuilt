package frc.lib.test;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import java.util.function.Supplier;

/**
 * Proxy command that bridges the Elastic dashboard button to test execution.
 *
 * <p>Published once via SmartDashboard.putData(), this command appears as a
 * clickable button in Elastic (and SimGUI). When clicked, it schedules
 * the currently selected test. When clicked again (or the test completes),
 * the button resets.
 *
 * <p>This command has no subsystem requirements, so it runs concurrently
 * with the actual test command without scheduling conflicts.
 *
 * <p><b>Lifecycle:</b>
 * <ul>
 *   <li><b>Button click ("Start Test"):</b> Elastic sets {@code running=true} →
 *       CommandScheduler schedules this proxy → {@code initialize()} fires →
 *       supplier returns selected test → proxy schedules it →
 *       button label changes to "Cancel Test"</li>
 *   <li><b>Test completes naturally:</b> Inner test's {@code isFinished()} returns true →
 *       scheduler ends it → proxy detects it's gone → proxy ends →
 *       button label reverts to "Start Test"</li>
 *   <li><b>Button click ("Cancel Test"):</b> Elastic sets {@code running=false} →
 *       scheduler cancels the proxy → {@code end(true)} fires →
 *       proxy cancels inner test → button label reverts to "Start Test"</li>
 *   <li><b>No test selected:</b> Supplier returns null → {@code isFinished()} returns
 *       true immediately → proxy ends → button resets</li>
 * </ul>
 *
 * <p><b>Dynamic button label:</b> Calls {@code setName()} in {@code initialize()}
 * and {@code end()} to toggle the button label. This works because WPILib's
 * {@code Command.initSendable()} registers the {@code .name} property with a getter
 * that the {@code SendableBuilder} polls periodically — name changes propagate
 * to NetworkTables automatically.
 */
public class TestRunnerCommand extends Command {

    private static final String LABEL_START = "Start Test";
    private static final String LABEL_CANCEL = "Cancel Test";

    private final Supplier<Command> selectedTestSupplier;
    private Command runningTest;

    /**
     * Creates a new TestRunnerCommand.
     *
     * @param selectedTestSupplier Supplier that returns the persistent test
     *     instance to run. Returns null if no test is selected or available.
     *     Called once per button click (in initialize()).
     */
    public TestRunnerCommand(Supplier<Command> selectedTestSupplier) {
        this.selectedTestSupplier = selectedTestSupplier;
        setName(LABEL_START); // Initial button label shown in Elastic
    }

    @Override
    public void initialize() {
        runningTest = selectedTestSupplier.get();
        if (runningTest != null) {
            CommandScheduler.getInstance().schedule(runningTest);
            setName(LABEL_CANCEL); // Button label changes to "Cancel Test"
        }
    }

    @Override
    public void execute() {
        // Nothing — just waiting for the inner test to finish
    }

    @Override
    public boolean isFinished() {
        // Finished when: no test was selected, or inner test is done
        return runningTest == null
            || !CommandScheduler.getInstance().isScheduled(runningTest);
    }

    @Override
    public void end(boolean interrupted) {
        if (interrupted && runningTest != null
                && CommandScheduler.getInstance().isScheduled(runningTest)) {
            runningTest.cancel();
        }
        runningTest = null;
        setName(LABEL_START); // Button label reverts to "Start Test"
    }
}
