package frc.lib.test;

import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.RobotContainer;
import frc.robot.test.SwerveAngleDriftTestCommand;

/**
 * Central registry of available diagnostic tests.
 * 
 * <p>This enum-based registry provides compile-time safety and makes all available tests
 * visible in one place. Each enum value represents a test and provides:
 * <ul>
 *   <li>Display name and description for the SmartDashboard UI</li>
 *   <li>Factory method to create test instances</li>
 * </ul>
 * 
 * <p>To add a new test:
 * <ol>
 *   <li>Create a test class that extends {@link edu.wpi.first.wpilibj2.command.Command}
 *       and implements {@link DiagnosticTest}</li>
 *   <li>Add a new enum value to this registry</li>
 *   <li>Implement the factory method to create your test instance</li>
 * </ol>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * MY_NEW_TEST("My New Test", "Description of what this test does") {
 *     @Override
 *     public Command createTest(RobotContainer robotContainer) {
 *         return new MyNewTestCommand(robotContainer.getSwerveSubsystem());
 *     }
 * }
 * }</pre>
 */
public enum DiagnosticTestRegistry {
    
    /**
     * Swerve angle drift test.
     * Tests encoder drift by rotating a swerve module through multiple cycles
     * and comparing relative encoder to absolute encoder measurements.
     * 
     * <p><b>Note:</b> This test will be refactored in Phase 2 to properly implement
     * {@link DiagnosticTest} and use the framework's parameter management.
     */
    SWERVE_ANGLE_DRIFT(
        "Swerve Angle Drift Test",
        "Tests encoder drift by rotating a swerve module through multiple cycles and comparing relative encoder to absolute encoder measurements."
    ) {
        @Override
        public Command createTest(RobotContainer robotContainer) {
            // TODO: Phase 2 - This will be refactored to take only SwerveSubsystem
            // and read parameters from SmartDashboard in initialize()
            // For now, using default parameters - test will need to be started via
            // old SwerveDriftTestManager until Phase 2 migration is complete
            return new SwerveAngleDriftTestCommand(
                robotContainer.getSwerveSubsystem(),
                0,      // moduleNumber - will come from SmartDashboard in Phase 2
                90.0,   // testAngleDegrees - will come from SmartDashboard in Phase 2
                10      // numberOfCycles - will come from SmartDashboard in Phase 2
            );
        }
    };
    
    private final String displayName;
    private final String description;
    
    /**
     * Creates a new registry entry.
     * 
     * @param displayName The name to display in the SmartDashboard dropdown
     * @param description A description of what this test does
     */
    DiagnosticTestRegistry(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Gets the display name for this test.
     * This name will be shown in the SmartDashboard test selector dropdown.
     * 
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the description of what this test does.
     * 
     * @return The test description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Factory method to create an instance of this test.
     * 
     * <p>Each enum value must implement this method to create its specific test instance.
     * The test should be configured with dependencies from the RobotContainer, but
     * parameters should be read from SmartDashboard in the test's {@code initialize()}
     * method (after {@link DiagnosticTest#initializeParameters()} has been called).
     * 
     * <p><b>Note:</b> Return type is {@link Command} for now to allow tests that haven't
     * yet implemented {@link DiagnosticTest} (e.g., during Phase 2 migration). Once all
     * tests implement the interface, this should be changed to return {@link DiagnosticTest}.
     * 
     * @param robotContainer The robot container providing access to subsystems
     * @return A new instance of the diagnostic test command
     */
    public abstract Command createTest(RobotContainer robotContainer);
    
    /**
     * Gets an array of all test display names.
     * Useful for populating dropdowns or listing available tests.
     * 
     * @return Array of all test display names
     */
    public static String[] getAllDisplayNames() {
        DiagnosticTestRegistry[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].getDisplayName();
        }
        return names;
    }
    
    /**
     * Finds a test registry entry by its display name.
     * 
     * @param displayName The display name to search for
     * @return The matching registry entry, or null if not found
     */
    public static DiagnosticTestRegistry findByDisplayName(String displayName) {
        for (DiagnosticTestRegistry test : values()) {
            if (test.getDisplayName().equals(displayName)) {
                return test;
            }
        }
        return null;
    }
}
