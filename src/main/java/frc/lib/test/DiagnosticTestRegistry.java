package frc.lib.test;

import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.RobotContainer;
import frc.robot.test.LedStateTestCommand;
import frc.robot.test.OdometryResetTestCommand;
import frc.robot.test.SwerveAlignmentTestCommand;
import frc.robot.test.SwerveAngleDriftTestCommand;
import frc.robot.test.SwerveStraightLineTestCommand;

/**
 * Central registry of available diagnostic tests.
 *
 * <p>This enum-based registry provides compile-time safety and makes all available tests
 * visible in one place. Each enum value represents a test and provides:
 * <ul>
 *   <li>Display name for the SmartDashboard UI</li>
 *   <li>Factory method to create test instances</li>
 * </ul>
 *
 * <p><b>Note:</b> Test descriptions are provided by each test's
 * {@link DiagnosticTest#getTestDescription()} method, not by the registry.
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
 * MY_NEW_TEST("My New Test") {
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
     */
    SWERVE_ANGLE_DRIFT("Swerve Angle Drift Test") {
        @Override
        public Command createTest(RobotContainer robotContainer) {
            return new SwerveAngleDriftTestCommand(robotContainer.getSwerveSubsystem());
        }
    },

    /**
     * Swerve Alignment Test - Commands all modules to the same angle and measures accuracy.
     * Reveals angle offset calibration errors that cause drift during driving.
     */
    SWERVE_ALIGNMENT("Swerve Alignment Test") {
        @Override
        public Command createTest(RobotContainer robotContainer) {
            return new SwerveAlignmentTestCommand(robotContainer.getSwerveSubsystem());
        }
    },

    /**
     * Swerve Straight Line Test - Drives the robot straight with known inputs.
     * Bypasses the joystick to test whether the drive code, motors, and mechanics
     * allow the robot to drive straight.
     */
    SWERVE_STRAIGHT_LINE("Swerve Straight Line Test") {
        @Override
        public Command createTest(RobotContainer robotContainer) {
            return new SwerveStraightLineTestCommand(robotContainer.getSwerveSubsystem());
        }
    },

    /**
     * LED State Test - Diagnostic test for CandleSubsystem.
     * Allows independent testing of LED states without other robot systems.
     * Useful for verifying LED hardware functionality and visual feedback.
     */
    LED_STATE_TEST("LED State Test") {
        @Override
        public Command createTest(RobotContainer robotContainer) {
            return new LedStateTestCommand(robotContainer.getCandleSubsystem());
        }
    },

    /**
     * Odometry Reset Test - Demonstrates the pose reset bug (issue 8.1/8.2).
     * Resets the robot's pose and observes whether the heading stays correct
     * across subsequent cycles. Reveals bugs where the odometry baseline or
     * SimulationManager stale data corrupt the heading.
     */
    ODOMETRY_RESET("Odometry Reset Test") {
        @Override
        public Command createTest(RobotContainer robotContainer) {
            return new OdometryResetTestCommand(robotContainer.getSwerveSubsystem());
        }
    };

    private final String displayName;

    /**
     * Creates a new registry entry.
     *
     * @param displayName The name to display in the SmartDashboard dropdown
     */
    DiagnosticTestRegistry(String displayName) {
        this.displayName = displayName;
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

}
