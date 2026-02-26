package frc.lib.test;

import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import frc.lib.SendableChooserUtil;
import frc.robot.RobotContainer;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the lifecycle and SmartDashboard integration for diagnostic tests.
 *
 * <p>This manager provides a unified interface for running diagnostic tests:
 * <ul>
 *   <li>Test selection via dropdown (SendableChooser)</li>
 *   <li>Parameter initialization when tests are selected</li>
 *   <li>Test execution via a proxy {@link TestRunnerCommand} published as a dashboard button</li>
 *   <li>Status monitoring and display</li>
 * </ul>
 *
 * <p>Test instances are created once in the constructor and reused across runs.
 * All test commands support reuse because they fully reset state in
 * {@code initialize()} and read fresh parameters from SmartDashboard each run.
 *
 * <p>Usage:
 * <ol>
 *   <li>Create instance in {@code Robot.testInit()}</li>
 *   <li>Call {@code periodic()} in {@code Robot.testPeriodic()}</li>
 *   <li>Call {@code cleanup()} in {@code Robot.testEnd()}</li>
 * </ol>
 *
 * <p><b>SmartDashboard Layout:</b>
 * <pre>
 * DiagnosticTests/
 *   ├── TestSelector/             (SendableChooser - dropdown)
 *   ├── StartTest/                (Command button - clickable in Elastic and SimGUI)
 *   ├── CurrentTest/              (String - name of running test or "None")
 *   ├── CurrentTest Description/  (String - description of the selected test)
 *   ├── CurrentTest Status/       (String - Idle, Running, Complete, Cancelled, Error)
 *   └── Message/                  (String - status messages and error information)
 * </pre>
 *
 * <p><b>Button Behavior:</b> The "StartTest" entry is a {@link TestRunnerCommand} published
 * via {@code SmartDashboard.putData()}. Elastic and SimGUI render it as a native Command
 * toggle button. When clicked, it schedules the currently selected test. When clicked again
 * (or the test completes), the button resets.
 */
public class DiagnosticTestManager {

    private static final String DASHBOARD_PREFIX = "DiagnosticTests/";
    private static final String KEY_TEST_SELECTOR = DASHBOARD_PREFIX + "TestSelector";
    private static final String KEY_START_TEST = DASHBOARD_PREFIX + "StartTest";
    private static final String KEY_CURRENT_TEST = DASHBOARD_PREFIX + "CurrentTest";
    private static final String KEY_TEST_STATUS = DASHBOARD_PREFIX + "CurrentTest Status";
    private static final String KEY_MESSAGE = DASHBOARD_PREFIX + "Message";
    private static final String KEY_DESCRIPTION = DASHBOARD_PREFIX + "CurrentTest Description";

    private final RobotContainer robotContainer;
    private final SendableChooser<DiagnosticTestRegistry> testChooser;
    private final Map<DiagnosticTestRegistry, Command> testInstances = new HashMap<>();
    private final TestRunnerCommand runTestCommand;

    private Command activeTest = null;
    private DiagnosticTestRegistry lastSelectedTest = null;
    private DiagnosticTestRegistry suppressedSelectionWarning = null; // Tracks which selection we've already warned about during a running test
    private TestStatus currentStatus = TestStatus.IDLE;

    /**
     * Internal enum for tracking test status.
     */
    private enum TestStatus {
        IDLE("Idle"),
        RUNNING("Running"),
        COMPLETE("Complete"),
        ERROR("Error"),
        CANCELLED("Cancelled");

        private final String displayName;

        TestStatus(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Creates a new DiagnosticTestManager.
     *
     * <p>Creates all test instances once and stores them for reuse. Also creates
     * the proxy {@link TestRunnerCommand} that will be published as a dashboard button.
     *
     * @param robotContainer The robot container providing access to subsystems
     */
    public DiagnosticTestManager(RobotContainer robotContainer) {
        this.robotContainer = robotContainer;
        this.testChooser = SendableChooserUtil.fromEnum(
            DiagnosticTestRegistry.class,
            DiagnosticTestRegistry.values()[0],
            DiagnosticTestRegistry::getDisplayName);

        // Create all test instances once (persistent — reused across runs)
        // * iterate over all test entries in the DiagnosticTestRegistry enum
        for (DiagnosticTestRegistry entry : DiagnosticTestRegistry.values()) {
            try {
                Command test = entry.createTest(robotContainer);
                testInstances.put(entry, test);
            } catch (Exception e) {
                System.err.println("Failed to create test: " + entry.getDisplayName()
                    + ": " + e.getMessage());
                // Test won't be available, but other tests still work
            }
        }

        // Create proxy command (published as a button in initializeDashboard())
        this.runTestCommand = new TestRunnerCommand(this::getSelectedTestInstance);

        initializeDashboard();
    }

    /**
     * Initializes the SmartDashboard UI for test selection and execution.
     * Called once during construction.
     */
    private void initializeDashboard() {
        // testChooser is already populated by SendableChooserUtil.fromEnum() in constructor
        SmartDashboard.putData(KEY_TEST_SELECTOR, testChooser);

        // Publish proxy command as a clickable button (works in Elastic and SimGUI)
        SmartDashboard.putData(KEY_START_TEST, runTestCommand);

        // Initialize status display
        SmartDashboard.putString(KEY_CURRENT_TEST, "None");
        SmartDashboard.putString(KEY_TEST_STATUS, TestStatus.IDLE.toString());
        SmartDashboard.putString(KEY_MESSAGE, "Select a test and click Start Test to begin");
        SmartDashboard.putString(KEY_DESCRIPTION, "");
    }

    /**
     * Periodic update method. Should be called every 20ms in {@code Robot.testPeriodic()}.
     *
     * <p>This method:
     * <ul>
     *   <li>Monitors test selection dropdown for changes</li>
     *   <li>Initializes parameters when a test is selected</li>
     *   <li>Monitors active test status and updates display</li>
     *   <li>Detects unexpected test failures and handles errors</li>
     * </ul>
     *
     * <p>Note: Start/cancel actions are handled by the {@link TestRunnerCommand} proxy
     * via the CommandScheduler, not by polling a boolean.
     */
    public void periodic() {
        // Check chooser and ensure Current Test field is synchronized with selection
        updateTestSelection();

        // Monitor active test status and detect failures
        updateTestStatus();
    }

    /**
     * Checks the chooser selection and ensures the Current Test field and test selection are synchronized.
     *
     * <p>This method centralizes all logic related to monitoring the test selector chooser:
     * <ul>
     *   <li>Updates the Current Test field to match the chooser selection</li>
     *   <li>Detects when the selection has changed</li>
     *   <li>Initializes parameters for newly selected tests</li>
     * </ul>
     *
     * <p>This should be called periodically (e.g., from {@code periodic()}) to keep the UI
     * synchronized with the chooser state.
     */
    private void updateTestSelection() {
        // Get current selection and update Current Test field
        DiagnosticTestRegistry selectedTest = testChooser.getSelected();
        if (selectedTest != null) {
            SmartDashboard.putString(KEY_CURRENT_TEST, selectedTest.getDisplayName());
        } else {
            SmartDashboard.putString(KEY_CURRENT_TEST, "None");
        }

        // Check if selection has changed (enums use == for identity comparison)
        if (selectedTest != null && selectedTest != lastSelectedTest) {
            // Selection changed - check if we can allow the change

            // Don't allow selection change if a test is currently running
            // Ignore the change and keep showing the running test
            if (isTestRunning()) {
                // Print warning once per selection change (suppress repeats)
                if (selectedTest != suppressedSelectionWarning) {
                    System.out.println("Warning: Cannot change test selection while a test is running. " +
                        "Please wait for the current test to complete or cancel it first.");
                    suppressedSelectionWarning = selectedTest;
                }

                // Keep "Current Test" showing the running test (not the new selection)
                String runningTestName = getActiveTestName();
                if (!runningTestName.equals("No Active Test")) {
                    SmartDashboard.putString(KEY_CURRENT_TEST, runningTestName);
                }

                // Do NOT update lastSelectedTest here — when the test completes,
                // the selection change will be detected and parameters will be initialized
                return;
            }

            // Get test display name
            String displayName = selectedTest.getDisplayName();
            // Get the selected test command
            Command testCommand = testInstances.get(selectedTest);

            if (testCommand == null) {
                // Test instance failed to create during construction
                SmartDashboard.putString(KEY_DESCRIPTION, "");
                SmartDashboard.putString(KEY_MESSAGE, "Test creation failed: " + displayName);
                System.err.println("Test creation failed: " + displayName);
            } else if (testCommand instanceof DiagnosticTest) {
                DiagnosticTest diagnosticTest = (DiagnosticTest) testCommand;
                diagnosticTest.initializeParameters();
                SmartDashboard.putString(KEY_DESCRIPTION, diagnosticTest.getTestDescription());
                System.out.println("Initialized parameters for: " + displayName);
                SmartDashboard.putString(KEY_MESSAGE, "Test selected: " + displayName + ". Click Start Test to begin.");
            } else {
                // Test doesn't implement DiagnosticTest yet (e.g., during Phase 2 migration)
                SmartDashboard.putString(KEY_DESCRIPTION, "");
                System.out.println("Note: " + displayName + " does not implement DiagnosticTest interface yet");
                SmartDashboard.putString(KEY_MESSAGE, "Test selected: " + displayName + ". Click Start Test to begin.");
            }

            lastSelectedTest = selectedTest;
        } else if (selectedTest == null && lastSelectedTest != null) {
            // Selection was cleared (shouldn't normally happen, but handle it)
            lastSelectedTest = null;
            SmartDashboard.putString(KEY_DESCRIPTION, "");
            // Update message when selection is cleared (only if no test is running)
            if (!isTestRunning()) {
                SmartDashboard.putString(KEY_MESSAGE, "Select a test and click Start Test to begin");
            }
        }
    }

    /**
     * Supplier method for {@link TestRunnerCommand}. Returns the currently selected
     * persistent test instance, or null if no valid test is available.
     *
     * <p>Called by the proxy's {@code initialize()} when the user clicks the button.
     * This method is a pure lookup — it does not modify manager state. The manager
     * detects the newly scheduled test in {@link #updateTestStatus()} on the next
     * {@link #periodic()} call (same cycle — no gap).
     *
     * @return The persistent test command to schedule, or null if unavailable
     */
    private Command getSelectedTestInstance() {
        DiagnosticTestRegistry selected = testChooser.getSelected();
        if (selected == null) {
            SmartDashboard.putString(KEY_MESSAGE,
                "No test selected. Select a test from the dropdown.");
            return null;
        }

        Command test = testInstances.get(selected);
        if (test == null) {
            SmartDashboard.putString(KEY_MESSAGE,
                "Test creation failed: " + selected.getDisplayName());
            return null;
        }

        return test;
    }

    /**
     * Monitors the active test and updates status display.
     *
     * <p>Detects three kinds of transitions:
     * <ul>
     *   <li><b>New test started:</b> No active test, but the selected test instance is now
     *       scheduled (started by the proxy). Sets {@code activeTest} and transitions to RUNNING.</li>
     *   <li><b>Test completed/cancelled:</b> Active test is no longer scheduled. Transitions
     *       to COMPLETE or CANCELLED based on {@code isFinished()}.</li>
     *   <li><b>Error:</b> Exception while monitoring the active test. Transitions to ERROR.</li>
     * </ul>
     */
    private void updateTestStatus() {
        // Note: Current Test field is kept in sync by updateTestSelection() called from periodic()

        // If no test is currently being tracked as running, check if the proxy started one.
        // This handles: first start (activeTest==null), re-run after completion (activeTest
        // kept for status display but not scheduled), and switching to a different test.
        if (!isTestRunning()) {
            DiagnosticTestRegistry selected = testChooser.getSelected();
            if (selected != null) {
                Command test = testInstances.get(selected);
                if (test != null && CommandScheduler.getInstance().isScheduled(test)) {
                    // Proxy scheduled this test — begin tracking it
                    activeTest = test;
                    suppressedSelectionWarning = null;
                }
            }
        }

        // No active test to monitor — ensure IDLE status and return early
        if (activeTest == null) {
            if (currentStatus != TestStatus.IDLE) {
                currentStatus = TestStatus.IDLE;
                SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
                // Message is handled by updateTestSelection() based on current selection state
            }
            return;
        }

        // Check if test is still scheduled and valid
        boolean isScheduled = false;
        boolean isFinished = false;

        try {
            isScheduled = CommandScheduler.getInstance().isScheduled(activeTest);
            isFinished = activeTest.isFinished();
        } catch (Exception e) {
            // Test encountered an exception - mark as error
            System.err.println("Unexpected error monitoring test: " + e.getMessage());
            e.printStackTrace();

            // Cancel the test (defensive cleanup)
            // - activeTest cannot be null here
            try {
                activeTest.cancel();
            } catch (Exception cancelException) {
                System.err.println("Error cancelling test after monitoring failure: " + cancelException.getMessage());
            }

            currentStatus = TestStatus.ERROR;
            String testName = getActiveTestName();
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            SmartDashboard.putString(KEY_MESSAGE, "Test error: " + e.getMessage() +
                " (Test: " + testName + ")");
            // Clear active test reference since it's in an error state
            activeTest = null;
            suppressedSelectionWarning = null;
            return;
        }

        if (!isScheduled && currentStatus == TestStatus.RUNNING) {
            // Test is no longer scheduled - determine why
            String testName = getActiveTestName();
            if (isFinished) {
                // Test completed normally
                currentStatus = TestStatus.COMPLETE;
                SmartDashboard.putString(KEY_MESSAGE, "Test completed: " + testName);
            } else {
                // Test is not scheduled and not finished — it was cancelled
                // (e.g., by the proxy's end(interrupted) or by CommandScheduler due to subsystem conflict)
                currentStatus = TestStatus.CANCELLED;
                SmartDashboard.putString(KEY_MESSAGE, "Test cancelled: " + testName);
            }
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            // Note: Current Test field is kept in sync by updateTestSelection() called from periodic()

            // Keep activeTest reference so completion status is displayed until a new test starts
        } else if (isScheduled && currentStatus != TestStatus.RUNNING) {
            // Test is running (either just started via proxy, or resumed unexpectedly)
            currentStatus = TestStatus.RUNNING;
            String testName = getActiveTestName();
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            SmartDashboard.putString(KEY_MESSAGE, "Test running: " + testName);
            System.out.println("Started test: " + testName);
        }
    }

    /**
     * Cleans up resources and cancels any active test.
     * Should be called in {@code Robot.testEnd()}.
     */
    public void cleanup() {
        // Cancel the proxy command (which will also cancel the inner test if running)
        if (CommandScheduler.getInstance().isScheduled(runTestCommand)) {
            runTestCommand.cancel();
        }

        // Cancel any active test (defensive — proxy's end() should have done this)
        if (activeTest != null) {
            try {
                if (CommandScheduler.getInstance().isScheduled(activeTest)) {
                    activeTest.cancel();
                }
            } catch (Exception e) {
                System.err.println("Error cancelling test during cleanup: " + e.getMessage());
                e.printStackTrace();
            }
            activeTest = null;
            suppressedSelectionWarning = null;
        }

        // Clear SmartDashboard entries when exiting test mode by setting to default/empty values
        // NetworkTables entries persist until overwritten, so we set them to empty values
        // They'll be recreated with proper values on next testInit()
        SmartDashboard.putString(KEY_CURRENT_TEST, "");
        SmartDashboard.putString(KEY_DESCRIPTION, "");
        SmartDashboard.putString(KEY_TEST_STATUS, "");
        SmartDashboard.putString(KEY_MESSAGE, "");
        // Note: SendableChooser (TestSelector) and Command (StartTest) cannot be easily removed,
        // but they will be overwritten on next testInit() when we call putData() again

        currentStatus = TestStatus.IDLE;
        lastSelectedTest = null;
    }

    /**
     * Checks whether a test is currently running (scheduled in the CommandScheduler).
     *
     * @return true if a test is actively running, false otherwise
     */
    private boolean isTestRunning() {
        return activeTest != null && CommandScheduler.getInstance().isScheduled(activeTest);
    }

    /**
     * Gets the display name of the active test.
     *
     * <p>If the test implements {@link DiagnosticTest}, returns {@link DiagnosticTest#getTestName()}.
     * Otherwise, returns the simple class name as a fallback.
     *
     * @return The test name, or "No Active Test" if activeTest is null
     */
    private String getActiveTestName() {
        if (activeTest == null) {
            return "No Active Test";
        }

        if (activeTest instanceof DiagnosticTest) {
            return ((DiagnosticTest) activeTest).getTestName();
        } else {
            // Fallback to class name for tests that don't implement DiagnosticTest yet
            return activeTest.getClass().getSimpleName();
        }
    }
}
