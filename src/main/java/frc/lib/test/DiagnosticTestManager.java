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
 *   ├── StartTest/ (Boolean - button)
 *   ├── CurrentTest/ (String - name of running test)
 *   └── TestStatus/ (String - Idle, Running, Complete)
 * </pre>
 */
public class DiagnosticTestManager {
    
    private static final String DASHBOARD_PREFIX = "DiagnosticTests/";
    private static final String KEY_TEST_SELECTOR = DASHBOARD_PREFIX + "TestSelector";
    private static final String KEY_START_TEST = DASHBOARD_PREFIX + "StartTest";
    private static final String KEY_CURRENT_TEST = DASHBOARD_PREFIX + "CurrentTest";
    private static final String KEY_TEST_STATUS = DASHBOARD_PREFIX + "TestStatus";
    
    private final RobotContainer robotContainer;
    private final SendableChooser<String> testChooser;
    
    private Command activeTest = null;
    private String lastSelectedTest = null;
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
        
        // Initialize status display
        SmartDashboard.putBoolean(KEY_START_TEST, false);
        SmartDashboard.putString(KEY_CURRENT_TEST, "None");
        SmartDashboard.putString(KEY_TEST_STATUS, TestStatus.IDLE.toString());
    }
    
    /**
     * Periodic update method. Should be called every 20ms in {@code Robot.testPeriodic()}.
     * 
     * <p>This method:
     * <ul>
     *   <li>Monitors test selection dropdown for changes</li>
     *   <li>Initializes parameters when a test is selected</li>
     *   <li>Monitors start button and launches tests</li>
     *   <li>Monitors active test status and updates display</li>
     * </ul>
     */
    public void periodic() {
        // Check for test selection changes
        String selectedTest = testChooser.getSelected();
        if (selectedTest != null && !selectedTest.equals(lastSelectedTest)) {
            changeSelectedTest(selectedTest);
            lastSelectedTest = selectedTest;
        }
        
        // Check for start button press
        boolean startRequested = SmartDashboard.getBoolean(KEY_START_TEST, false);
        if (startRequested) {
            // Reset button immediately to prevent multiple triggers
            SmartDashboard.putBoolean(KEY_START_TEST, false);
            startSelectedTest();
        }
        
        // Monitor active test status
        updateTestStatus();
    }
    
    /**
     * Handles when a test is selected in the dropdown.
     * Creates a temporary test instance to initialize SmartDashboard parameters, then discards it.
     * 
     * <p>This method creates a throwaway test instance solely to call {@link DiagnosticTest#initializeParameters()}
     * to set up the SmartDashboard parameter UI. This instance is immediately discarded - a fresh
     * instance will be created when the user presses Start, ensuring it reads the current parameter
     * values from SmartDashboard at execution time.
     * 
     * @param testName The display name of the selected test
     */
    private void changeSelectedTest(String testName) {
        // Don't initialize parameters if a test is currently running
        if (activeTest != null && CommandScheduler.getInstance().isScheduled(activeTest)) {
            System.out.println("Warning: Cannot change test selection while a test is running");
            return;
        }
        
        DiagnosticTestRegistry registryEntry = DiagnosticTestRegistry.findByDisplayName(testName);
        if (registryEntry == null) {
            System.err.println("Error: Test not found in registry: " + testName);
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
                System.out.println("Initialized parameters for: " + testName);
            } else {
                // Test doesn't implement DiagnosticTest yet (e.g., during Phase 2 migration)
                System.out.println("Note: " + testName + " does not implement DiagnosticTest interface yet");
            }
        } catch (Exception e) {
            System.err.println("Error creating test instance for parameter initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Starts the currently selected test.
     * Creates a new test instance, schedules it via CommandScheduler, and tracks it.
     */
    private void startSelectedTest() {
        // Don't start if a test is already running
        if (activeTest != null && CommandScheduler.getInstance().isScheduled(activeTest)) {
            System.out.println("Warning: A test is already running. Cancel it first before starting a new one.");
            return;
        }
        
        String selectedTest = testChooser.getSelected();
        if (selectedTest == null || selectedTest.equals("No tests available")) {
            System.err.println("Error: No test selected");
            return;
        }
        
        DiagnosticTestRegistry registryEntry = DiagnosticTestRegistry.findByDisplayName(selectedTest);
        if (registryEntry == null) {
            System.err.println("Error: Test not found in registry: " + selectedTest);
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
            
            System.out.println("Started test: " + selectedTest);
        } catch (Exception e) {
            System.err.println("Error starting test: " + e.getMessage());
            e.printStackTrace();
            currentStatus = TestStatus.ERROR;
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
        }
    }
    
    /**
     * Monitors the active test and updates status display.
     * Checks if the test has completed and updates the status accordingly.
     */
    private void updateTestStatus() {
        if (activeTest == null) {
            // No active test
            if (currentStatus != TestStatus.IDLE) {
                currentStatus = TestStatus.IDLE;
                SmartDashboard.putString(KEY_CURRENT_TEST, "None");
                SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            }
            return;
        }
        
        // Check if test is still scheduled
        boolean isScheduled = CommandScheduler.getInstance().isScheduled(activeTest);
        boolean isFinished = activeTest.isFinished();
        
        if (!isScheduled && currentStatus == TestStatus.RUNNING) {
            // Test completed (either finished normally or was cancelled)
            if (isFinished) {
                currentStatus = TestStatus.COMPLETE;
            } else {
                // Test was cancelled/interrupted
                currentStatus = TestStatus.CANCELLED;
            }
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
            
            // Keep activeTest reference so completion status is displayed until a new test starts
        } else if (isScheduled && currentStatus != TestStatus.RUNNING) {
            // Test is running
            currentStatus = TestStatus.RUNNING;
            SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
        }
    }
    
    /**
     * Cleans up resources and cancels any active test.
     * Should be called in {@code Robot.testEnd()}.
     */
    public void cleanup() {
        // Cancel any active test
        if (activeTest != null) {
            activeTest.cancel();
            activeTest = null;
        }
        
        // Clear SmartDashboard entries when exiting test mode by setting to default/empty values
        // NetworkTables entries persist until overwritten, so we set them to empty values
        // They'll be recreated with proper values on next testInit()
        SmartDashboard.putBoolean(KEY_START_TEST, false);
        SmartDashboard.putString(KEY_CURRENT_TEST, "");
        SmartDashboard.putString(KEY_TEST_STATUS, "");
        // Note: SendableChooser (TestSelector) cannot be easily removed, but it will be overwritten
        // on next testInit() when we call putData() again
        
        currentStatus = TestStatus.IDLE;
        lastSelectedTest = null;
    }
}
