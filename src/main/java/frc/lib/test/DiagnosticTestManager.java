package frc.lib.test;

import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import frc.robot.RobotContainer;

/**
 * Manages the lifecycle and SmartDashboard integration for diagnostic tests.
 *
 * <p>This manager provides a unified interface for running diagnostic tests:
 * <ul>
 *   <li>Test selection via dropdown (SendableChooser)</li>
 *   <li>Parameter initialization when tests are selected</li>
 *   <li>Test execution via CommandScheduler</li>
 *   <li>Status monitoring and display</li>
 * </ul>
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
 *   ├── TestSelector/ (SendableChooser - dropdown)
 *   ├── Start-Cancel Test/ (Boolean - button, behavior changes based on test state)
 *   ├── CurrentTest/ (String - name of running test or "None")
 *   ├── TestStatus/ (String - Idle, Running, Complete, Cancelled, Error)
 *   └── Message/ (String - status messages and error information)
 * </pre>
 *
 * <p><b>Button Behavior:</b>
 * <ul>
 *   <li><b>When no test is running:</b> Pressing the button starts the selected test (if one is selected).</li>
 *   <li><b>When a test is running:</b> Pressing the button immediately cancels the active test.</li>
 * </ul>
 *
 * <p>Defensive checks ensure the button only performs actions when appropriate (e.g., won't start
 * a test if none is selected, won't cancel if no test is running).
 */
public class DiagnosticTestManager {

    private static final String DASHBOARD_PREFIX = "DiagnosticTests/";
    private static final String KEY_TEST_SELECTOR = DASHBOARD_PREFIX + "TestSelector";
    private static final String KEY_START_CANCEL_TEST = DASHBOARD_PREFIX + "Start-Cancel Test";
    private static final String KEY_CURRENT_TEST = DASHBOARD_PREFIX + "CurrentTest";
    private static final String KEY_TEST_STATUS = DASHBOARD_PREFIX + "TestStatus";
    private static final String KEY_MESSAGE = DASHBOARD_PREFIX + "Message";

    private final RobotContainer robotContainer;
    private final SendableChooser<String> testChooser;

    private Command activeTest = null;
    private String lastSelectedTest = null;
    private String suppressedSelectionWarning = null; // Tracks which selection we've already warned about during a running test
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
     * @param robotContainer The robot container providing access to subsystems
     */
    public DiagnosticTestManager(RobotContainer robotContainer) {
        this.robotContainer = robotContainer;
        this.testChooser = new SendableChooser<>();

        initializeDashboard();
    }

    /**
     * Initializes the SmartDashboard UI for test selection and execution.
     * Called once during construction.
     */
    private void initializeDashboard() {
        // Populate dropdown with all available tests
        String[] testNames = DiagnosticTestRegistry.getAllDisplayNames();
        if (testNames.length > 0) {
            testChooser.setDefaultOption(testNames[0], testNames[0]);
            for (int i = 1; i < testNames.length; i++) {
                testChooser.addOption(testNames[i], testNames[i]);
            }
        } else {
            // No tests available - add a placeholder
            testChooser.setDefaultOption("No tests available", "No tests available");
        }

        SmartDashboard.putData(KEY_TEST_SELECTOR, testChooser);

        // Initialize button and status display
        SmartDashboard.putBoolean(KEY_START_CANCEL_TEST, false);
        SmartDashboard.putString(KEY_CURRENT_TEST, "None");
        SmartDashboard.putString(KEY_TEST_STATUS, TestStatus.IDLE.toString());
        SmartDashboard.putString(KEY_MESSAGE, "Select a test and press Start-Cancel Test to begin");
    }

    /**
     * Periodic update method. Should be called every 20ms in {@code Robot.testPeriodic()}.
     *
     * <p>This method:
     * <ul>
     *   <li>Monitors test selection dropdown for changes</li>
     *   <li>Initializes parameters when a test is selected</li>
     *   <li>Monitors Start/Cancel button and performs appropriate action</li>
     *   <li>Monitors active test status and updates display</li>
     *   <li>Detects unexpected test failures and handles errors</li>
     * </ul>
     */
    public void periodic() {
        // Check chooser and ensure Current Test field is synchronized with selection
        updateTestSelection();

        // Check for Start/Cancel button press
        boolean buttonPressed = SmartDashboard.getBoolean(KEY_START_CANCEL_TEST, false);
        if (buttonPressed) {
            // Reset button immediately to prevent multiple triggers
            SmartDashboard.putBoolean(KEY_START_CANCEL_TEST, false);
            handleStartCancelButton();
        }

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
        String selectedTest = testChooser.getSelected();
        if (selectedTest != null && !selectedTest.equals("No tests available")) {
            SmartDashboard.putString(KEY_CURRENT_TEST, selectedTest);
        } else {
            SmartDashboard.putString(KEY_CURRENT_TEST, "None");
            selectedTest = null; // Normalize to null for easier comparison
        }

        // Check if selection has changed
        if (selectedTest != null && !selectedTest.equals(lastSelectedTest)) {
            // Selection changed - check if we can allow the change

            // Don't allow selection change if a test is currently running
            // Ignore the change and keep showing the running test
            if (isTestRunning()) {
                // Print warning once per selection change (suppress repeats)
                if (!selectedTest.equals(suppressedSelectionWarning)) {
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

            DiagnosticTestRegistry registryEntry = DiagnosticTestRegistry.findByDisplayName(selectedTest);
            if (registryEntry == null) {
                System.err.println("Error: Test not found in registry: " + selectedTest);
                SmartDashboard.putString(KEY_MESSAGE, "Error: Test not found in registry");
                lastSelectedTest = selectedTest;
                return;
            }

            // Create a temporary throwaway instance solely to initialize SmartDashboard parameters.
            // This instance is discarded immediately after calling initializeParameters().
            // When the user presses Start, a fresh instance will be created that reads current
            // parameter values from SmartDashboard, ensuring any parameter changes made after
            // selection are respected.
            try {
                Command testCommand = registryEntry.createTest(robotContainer);
                if (testCommand instanceof DiagnosticTest) {
                    DiagnosticTest diagnosticTest = (DiagnosticTest) testCommand;
                    diagnosticTest.initializeParameters();
                    // Instance is discarded here - not stored or reused
                    System.out.println("Initialized parameters for: " + selectedTest);
                    SmartDashboard.putString(KEY_MESSAGE, "Test selected: " + selectedTest + ". Press Start-Cancel Test to begin.");
                } else {
                    // Test doesn't implement DiagnosticTest yet (e.g., during Phase 2 migration)
                    System.out.println("Note: " + selectedTest + " does not implement DiagnosticTest interface yet");
                    SmartDashboard.putString(KEY_MESSAGE, "Test selected: " + selectedTest + ". Press Start-Cancel Test to begin.");
                }
            } catch (Exception e) {
                System.err.println("Error creating test instance for parameter initialization: " + e.getMessage());
                e.printStackTrace();
                SmartDashboard.putString(KEY_MESSAGE, "Error initializing test: " + e.getMessage());
            }

            lastSelectedTest = selectedTest;
        } else if (selectedTest == null && lastSelectedTest != null) {
            // Selection was cleared (shouldn't normally happen, but handle it)
            lastSelectedTest = null;
            // Update message when selection is cleared (only if no test is running)
            if (!isTestRunning()) {
                SmartDashboard.putString(KEY_MESSAGE, "Select a test and press Start-Cancel Test to begin");
            }
        }
    }

    /**
     * Handles the Start/Cancel button press.
     * Determines whether to start or cancel based on current test state.
     */
    private void handleStartCancelButton() {
        if (isTestRunning()) {
            // Test is running - cancel it
            cancelActiveTest();
        } else {
            // No test running - start the selected test
            startSelectedTest();
        }
    }

    /**
     * Cancels the currently running test.
     * Immediately stops the test and updates status to Cancelled.
     */
    private void cancelActiveTest() {
        if (!isTestRunning()) {
            // No test running - ignore button press
            String message = "No test is currently running";
            SmartDashboard.putString(KEY_MESSAGE, message);
            System.out.println("Warning: " + message);
            return;
        }

        try {
            // Cancel the test
            activeTest.cancel();

            // Update status
            currentStatus = TestStatus.CANCELLED;
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            String testName = getActiveTestName();
            String message = "Test cancelled: " + testName;
            SmartDashboard.putString(KEY_MESSAGE, message);

            System.out.println("Cancelled test: " + testName);
        } catch (Exception e) {
            // Error during cancellation - still mark as cancelled but note the error
            System.err.println("Error cancelling test: " + e.getMessage());
            e.printStackTrace();
            currentStatus = TestStatus.ERROR;
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            SmartDashboard.putString(KEY_MESSAGE, "Error cancelling test: " + e.getMessage());
        }
    }

    /**
     * Starts the currently selected test.
     * Creates a new test instance, schedules it via CommandScheduler, and tracks it.
     */
    private void startSelectedTest() {
        // Don't start if a test is already running
        if (isTestRunning()) {
            String message = "A test is already running. Press Start-Cancel Test to cancel it first.";
            SmartDashboard.putString(KEY_MESSAGE, message);
            System.out.println("Warning: " + message);
            return;
        }

        // Get selected test from chooser (Current Test field is already kept in sync by updateTestSelection())
        String selectedTest = testChooser.getSelected();
        if (selectedTest == null) {
            String message = "No test selected. Please select a test from the dropdown.";
            SmartDashboard.putString(KEY_MESSAGE, message);
            System.err.println("Error: " + message);
            return;
        }

        DiagnosticTestRegistry registryEntry = DiagnosticTestRegistry.findByDisplayName(selectedTest);
        if (registryEntry == null) {
            String message = "Test not found in registry: " + selectedTest;
            SmartDashboard.putString(KEY_MESSAGE, message);
            System.err.println("Error: " + message);
            return;
        }

        // Cancel any existing test (shouldn't be necessary, but be safe)
        if (activeTest != null) {
            activeTest.cancel();
        }

        // Create and schedule the test
        try {
            activeTest = registryEntry.createTest(robotContainer);
            CommandScheduler.getInstance().schedule(activeTest);

            // Update status display
            currentStatus = TestStatus.RUNNING;
            SmartDashboard.putString(KEY_CURRENT_TEST, selectedTest);
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            SmartDashboard.putString(KEY_MESSAGE, "Test running: " + selectedTest);

            System.out.println("Started test: " + selectedTest);
        } catch (Exception e) {
            // Handle exceptions during test creation or scheduling
            System.err.println("Error starting test: " + e.getMessage());
            e.printStackTrace();

            // Cancel the test if it was created/scheduled (defensive cleanup)
            if (activeTest != null) {
                try {
                    activeTest.cancel();
                } catch (Exception cancelException) {
                    System.err.println("Error cancelling test after creation failure: " + cancelException.getMessage());
                }
            }

            currentStatus = TestStatus.ERROR;
            activeTest = null;
            suppressedSelectionWarning = null;
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            SmartDashboard.putString(KEY_CURRENT_TEST, "None");
            SmartDashboard.putString(KEY_MESSAGE, "Error starting test: " + e.getMessage());
        }
    }

    /**
     * Monitors the active test and updates status display.
     * Checks if the test has completed, was cancelled, or encountered an error.
     * Also detects unexpected test failures (exceptions during execution).
     */
    private void updateTestStatus() {
        // Note: Current Test field is kept in sync by updateTestSelection() called from periodic()

        if (activeTest == null) {
            // No active test
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

            // Cancel the test if it exists (defensive cleanup)
            if (activeTest != null) {
                try {
                    activeTest.cancel();
                } catch (Exception cancelException) {
                    System.err.println("Error cancelling test after monitoring failure: " + cancelException.getMessage());
                }
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
                // Test is not scheduled and not finished — it was cancelled unexpectedly
                // (e.g., by CommandScheduler due to subsystem conflict)
                // Note: If cancelled via our button, currentStatus would already be CANCELLED
                // and the outer if (currentStatus == RUNNING) would have been false, so we
                // wouldn't reach this point.
                currentStatus = TestStatus.CANCELLED;
                SmartDashboard.putString(KEY_MESSAGE, "Test cancelled unexpectedly: " + testName);
            }
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            // Note: Current Test field is kept in sync by updateTestSelection() called from periodic()

            // Keep activeTest reference so completion status is displayed until a new test starts
        } else if (isScheduled && currentStatus != TestStatus.RUNNING) {
            // Test is running
            currentStatus = TestStatus.RUNNING;
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            SmartDashboard.putString(KEY_MESSAGE, "Test running: " + getActiveTestName());
        }
    }

    /**
     * Cleans up resources and cancels any active test.
     * Should be called in {@code Robot.testEnd()}.
     */
    public void cleanup() {
        // Cancel any active test
        if (activeTest != null) {
            try {
                activeTest.cancel();
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
        SmartDashboard.putBoolean(KEY_START_CANCEL_TEST, false);
        SmartDashboard.putString(KEY_CURRENT_TEST, "");
        SmartDashboard.putString(KEY_TEST_STATUS, "");
        SmartDashboard.putString(KEY_MESSAGE, "");
        // Note: SendableChooser (TestSelector) cannot be easily removed, but it will be overwritten
        // on next testInit() when we call putData() again

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
