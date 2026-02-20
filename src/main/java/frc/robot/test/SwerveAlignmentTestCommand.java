// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import frc.lib.test.DiagnosticTest;
import frc.lib.test.TestDashboard;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.SwerveModule;

/**
 * Diagnostic test that commands all four swerve modules to the same angle and measures
 * how accurately each module reaches the target.
 *
 * <p>This test directly diagnoses angle offset calibration errors, which are the most
 * common cause of swerve drive drift. If one module has a bad angleOffset or a failed
 * resetToAbsolute() at startup, this test will reveal it clearly.
 *
 * <p><b>Test sequence:</b>
 * <ol>
 *   <li>Command all 4 modules to a target angle (e.g., 0° = straight forward) with zero drive speed</li>
 *   <li>Wait for all modules to settle (within tolerance or timeout)</li>
 *   <li>Record the actual integrated encoder angle and absolute encoder (CANcoder) angle for each module</li>
 *   <li>Optionally repeat at additional angles (90°, 180°, 270°) to detect angle-dependent errors</li>
 *   <li>Report per-module angle errors and flag any module that deviates significantly</li>
 * </ol>
 *
 * <p><b>What this test reveals:</b>
 * <ul>
 *   <li>Incorrect angleOffset values (module points in wrong direction)</li>
 *   <li>Failed resetToAbsolute() at startup (integrated encoder not calibrated)</li>
 *   <li>Stale Preferences values overriding Constants.java</li>
 *   <li>Mechanical binding or friction preventing a module from reaching its target</li>
 *   <li>PID tuning issues (one module reaches target much slower than others)</li>
 * </ul>
 *
 * <p><b>Interpreting results:</b>
 * <ul>
 *   <li>All modules within ±2°: Angle calibration is good — drift cause is elsewhere</li>
 *   <li>One module off by a consistent amount: Bad angleOffset for that module</li>
 *   <li>One module off by varying amounts at different test angles: Encoder or mechanical issue</li>
 *   <li>All modules off by similar amount: Systematic error (wrong conversion factor or gear ratio)</li>
 * </ul>
 */
public class SwerveAlignmentTestCommand extends Command implements DiagnosticTest {

    private final SwerveSubsystem swerveSubsystem;

    // Test parameters (read from SmartDashboard in initialize())
    private double[] testAngles;       // Angles to test (e.g., {0, 90, 180, 270})
    private double settleTimeSeconds;  // Time to wait for modules to settle at each angle
    private double toleranceDegrees;   // Angle tolerance for "at position" check

    // Test state
    private enum TestState {
        COMMANDING,   // Just commanded modules to a new angle
        SETTLING,     // Waiting for modules to settle
        RECORDING,    // Recording measurements at current angle
        COMPLETE      // All angles tested
    }

    private TestState currentState;
    private int currentAngleIndex;     // Index into testAngles array
    private double stateStartTime;

    // Results storage: [angleIndex][moduleNumber]
    private double[][] integratedAngles;  // Integrated encoder readings
    private double[][] absoluteAngles;    // CANcoder readings
    private double[][] angleErrors;       // Difference from target
    private boolean[][] settledInTime;    // Whether module reached target before timeout

    // Module references
    private SwerveModule[] modules;
    private static final int NUM_MODULES = 4;

    /**
     * Creates a new SwerveAlignmentTestCommand.
     * Parameters are read from SmartDashboard in the initialize() method.
     *
     * @param swerveSubsystem The swerve subsystem containing the modules
     */
    public SwerveAlignmentTestCommand(SwerveSubsystem swerveSubsystem) {
        this.swerveSubsystem = swerveSubsystem;
        addRequirements(swerveSubsystem);
    }

    // ============================================================================
    // DiagnosticTest Interface Implementation
    // ============================================================================

    @Override
    public String getTestName() {
        return "Swerve Alignment Test";
    }

    @Override
    public String getTestDescription() {
        return "Commands all four swerve modules to the same angle and measures how accurately "
             + "each module reaches the target. Reveals angle offset calibration errors, "
             + "failed encoder calibration, and module-specific issues that cause drift.";
    }

    @Override
    public void initializeParameters() {
        TestDashboard.putParamBoolean(this, "TestMultipleAngles", true);
        TestDashboard.putParamDouble(this, "SingleTestAngle", 0.0);
        TestDashboard.putParamDouble(this, "SettleTime", 2.0);
        TestDashboard.putParamDouble(this, "Tolerance", 2.0);
    }

    // ============================================================================
    // Command Lifecycle
    // ============================================================================

    @Override
    public void initialize() {
        // Read parameters from SmartDashboard
        boolean testMultiple = TestDashboard.getParamBoolean(this, "TestMultipleAngles", true);
        double singleAngle = TestDashboard.getParamDouble(this, "SingleTestAngle", 0.0);
        settleTimeSeconds = TestDashboard.getParamDouble(this, "SettleTime", 2.0);
        toleranceDegrees = TestDashboard.getParamDouble(this, "Tolerance", 2.0);

        // Set up test angles
        if (testMultiple) {
            testAngles = new double[]{0.0, 90.0, 180.0, 270.0};
        } else {
            testAngles = new double[]{singleAngle};
        }

        // Get module references
        modules = new SwerveModule[NUM_MODULES];
        for (int i = 0; i < NUM_MODULES; i++) {
            modules[i] = swerveSubsystem.getModule(i);
            if (modules[i] == null) {
                System.err.println("ERROR: Module " + i + " not found in swerve subsystem.");
                currentState = TestState.COMPLETE;
                return;
            }
        }

        // Initialize results storage
        integratedAngles = new double[testAngles.length][NUM_MODULES];
        absoluteAngles = new double[testAngles.length][NUM_MODULES];
        angleErrors = new double[testAngles.length][NUM_MODULES];
        settledInTime = new boolean[testAngles.length][NUM_MODULES];

        // Start first angle test
        currentAngleIndex = 0;
        commandCurrentAngle();

        // Log test start
        System.out.println("=== Swerve Alignment Test Started ===");
        System.out.println("Test Angles: " + formatAngles(testAngles));
        System.out.println("Settle Time: " + settleTimeSeconds + "s");
        System.out.println("Tolerance: " + toleranceDegrees + "°");
        System.out.println("-------------------------------------");

        // Update dashboard
        TestDashboard.putResultString(this, "Status", "Running");
        TestDashboard.putResultInt(this, "Config/NumAngles", testAngles.length);
    }

    @Override
    public void execute() {
        if (currentState == TestState.COMPLETE) {
            return;
        }

        double elapsed = Timer.getFPGATimestamp() - stateStartTime;

        switch (currentState) {
            case COMMANDING:
                // Transition to settling immediately (command was sent in commandCurrentAngle)
                currentState = TestState.SETTLING;
                stateStartTime = Timer.getFPGATimestamp();
                break;

            case SETTLING:
                // Wait for settle time to elapse
                if (elapsed >= settleTimeSeconds) {
                    currentState = TestState.RECORDING;
                    recordMeasurements();
                }
                // Update real-time display while settling
                updateRealTimeDisplay();
                break;

            case RECORDING:
                // Measurements recorded, move to next angle or finish
                currentAngleIndex++;
                if (currentAngleIndex < testAngles.length) {
                    commandCurrentAngle();
                } else {
                    currentState = TestState.COMPLETE;
                    printResults();
                }
                break;

            default:
                break;
        }
    }

    @Override
    public boolean isFinished() {
        return currentState == TestState.COMPLETE;
    }

    @Override
    public void end(boolean interrupted) {
        if (interrupted) {
            System.out.println("=== Swerve Alignment Test INTERRUPTED ===");
            TestDashboard.putResultString(this, "Status", "Interrupted");
        } else {
            System.out.println("=== Swerve Alignment Test COMPLETED ===");
            TestDashboard.putResultString(this, "Status", "Complete");
        }
    }

    // ============================================================================
    // Test Logic
    // ============================================================================

    /**
     * Commands all four modules to the current test angle with zero drive speed.
     */
    private void commandCurrentAngle() {
        double angle = testAngles[currentAngleIndex];
        SwerveModuleState targetState = new SwerveModuleState(0.0, Rotation2d.fromDegrees(angle));

        for (int i = 0; i < NUM_MODULES; i++) {
            modules[i].setDesiredState(targetState, false);
        }

        currentState = TestState.COMMANDING;
        stateStartTime = Timer.getFPGATimestamp();

        System.out.println(String.format("\nCommanding all modules to %.1f°...", angle));
        TestDashboard.putResultDouble(this, "Progress/TargetAngle", angle);
        TestDashboard.putResultInt(this, "Progress/AngleStep",
            currentAngleIndex + 1);
    }

    /**
     * Records encoder measurements for all modules at the current test angle.
     */
    private void recordMeasurements() {
        double targetAngle = testAngles[currentAngleIndex];

        System.out.println(String.format("Recording measurements at %.1f°:", targetAngle));
        System.out.println(String.format("  %-10s %-15s %-15s %-12s %-10s",
            "Module", "Integrated(°)", "Absolute(°)", "Error(°)", "Settled?"));
        System.out.println(String.format("  %-10s %-15s %-15s %-12s %-10s",
            "------", "-------------", "-----------", "--------", "--------"));

        for (int i = 0; i < NUM_MODULES; i++) {
            double integrated = modules[i].getRawTurnEncoder();
            double absolute = modules[i].getCanCoder().getDegrees();
            double error = Math.IEEEremainder(integrated - targetAngle, 360.0);
            boolean settled = Math.abs(error) <= toleranceDegrees;

            integratedAngles[currentAngleIndex][i] = integrated;
            absoluteAngles[currentAngleIndex][i] = absolute;
            angleErrors[currentAngleIndex][i] = error;
            settledInTime[currentAngleIndex][i] = settled;

            System.out.println(String.format("  Mod %-5d %-15.2f %-15.2f %-12.3f %-10s",
                i, integrated, absolute, error, settled ? "YES" : "NO ⚠️"));

            // Publish per-module results for this angle
            String prefix = String.format("Angle%.0f/Mod%d/", targetAngle, i);
            TestDashboard.putResultDouble(this, prefix + "Integrated", integrated);
            TestDashboard.putResultDouble(this, prefix + "Absolute", absolute);
            TestDashboard.putResultDouble(this, prefix + "Error", error);
            TestDashboard.putResultBoolean(this, prefix + "Settled", settled);
        }
    }

    /**
     * Updates the real-time SmartDashboard display during settling.
     */
    private void updateRealTimeDisplay() {
        double targetAngle = testAngles[currentAngleIndex];
        for (int i = 0; i < NUM_MODULES; i++) {
            double current = modules[i].getRawTurnEncoder();
            double error = Math.IEEEremainder(current - targetAngle, 360.0);
            TestDashboard.putResultDouble(this, "RealTime/Mod" + i + "Error", error);
            TestDashboard.putResultDouble(this, "RealTime/Mod" + i + "Angle", current);
        }
        double remaining = settleTimeSeconds - (Timer.getFPGATimestamp() - stateStartTime);
        TestDashboard.putResultDouble(this, "RealTime/SettleRemaining", Math.max(0.0, remaining));
    }

    // ============================================================================
    // Results Reporting
    // ============================================================================

    /**
     * Prints the final results summary with per-module analysis.
     */
    private void printResults() {
        System.out.println("\n=== ALIGNMENT TEST RESULTS ===");

        // Per-module summary across all angles
        System.out.println("\n--- Per-Module Summary ---");
        for (int mod = 0; mod < NUM_MODULES; mod++) {
            double maxAbsError = 0.0;
            double sumAbsError = 0.0;
            int failCount = 0;

            for (int a = 0; a < testAngles.length; a++) {
                double absError = Math.abs(angleErrors[a][mod]);
                if (absError > maxAbsError) maxAbsError = absError;
                sumAbsError += absError;
                if (!settledInTime[a][mod]) failCount++;
            }

            double avgAbsError = sumAbsError / testAngles.length;
            String status;
            if (maxAbsError <= toleranceDegrees) {
                status = "✓ PASS";
            } else if (maxAbsError <= toleranceDegrees * 2) {
                status = "⚠️ MARGINAL";
            } else {
                status = "✗ FAIL";
            }

            System.out.println(String.format("  Module %d: %s  (avg error: %.2f°, max error: %.2f°, failed: %d/%d angles)",
                mod, status, avgAbsError, maxAbsError, failCount, testAngles.length));

            // Publish summary results
            TestDashboard.putResultDouble(this, "Summary/Mod" + mod + "/AvgError", avgAbsError);
            TestDashboard.putResultDouble(this, "Summary/Mod" + mod + "/MaxError", maxAbsError);
            TestDashboard.putResultString(this, "Summary/Mod" + mod + "/Status", status);
        }

        // Cross-module comparison: are all modules pointing the same direction?
        System.out.println("\n--- Cross-Module Comparison (do all modules agree?) ---");
        for (int a = 0; a < testAngles.length; a++) {
            double minAngle = integratedAngles[a][0];
            double maxAngle = integratedAngles[a][0];
            for (int mod = 1; mod < NUM_MODULES; mod++) {
                double angle = integratedAngles[a][mod];
                // Use IEEEremainder to handle wrapping when comparing
                double diffFromFirst = Math.IEEEremainder(angle - integratedAngles[a][0], 360.0);
                double adjusted = integratedAngles[a][0] + diffFromFirst;
                if (adjusted < minAngle) minAngle = adjusted;
                if (adjusted > maxAngle) maxAngle = adjusted;
            }
            double spread = maxAngle - minAngle;

            String spreadStatus;
            if (spread <= 2.0) {
                spreadStatus = "✓ Tight";
            } else if (spread <= 5.0) {
                spreadStatus = "⚠️ Moderate";
            } else {
                spreadStatus = "✗ Wide — modules disagree significantly!";
            }

            System.out.println(String.format("  At %.0f°: spread = %.2f° %s",
                testAngles[a], spread, spreadStatus));

            TestDashboard.putResultDouble(this,
                String.format("Summary/Angle%.0f/Spread", testAngles[a]), spread);
        }

        // Integrated vs Absolute comparison: is resetToAbsolute() working?
        // At calibration: integrated = absolute - angleOffset
        // So at any time: (absolute - angleOffset) should ≈ integrated
        // The "calibration error" is how much the integrated encoder has drifted from
        // what the absolute encoder says it should be.
        System.out.println("\n--- Integrated vs Absolute Encoder Comparison ---");
        System.out.println("  Checks whether resetToAbsolute() calibrated correctly.");
        System.out.println("  CalibError = integrated - (absolute - angleOffset)  [should be ≈ 0°]");
        for (int mod = 0; mod < NUM_MODULES; mod++) {
            // Use the first test angle for this comparison
            double integ = integratedAngles[0][mod];
            double absol = absoluteAngles[0][mod];
            double offset = modules[mod].getAngleOffset().getDegrees();
            // What the integrated encoder SHOULD read based on the absolute encoder and offset
            double expectedIntegrated = absol - offset;
            // How far off the integrated encoder is from what the absolute encoder says
            double calibError = Math.IEEEremainder(integ - expectedIntegrated, 360.0);

            String calibStatus;
            if (Math.abs(calibError) <= 3.0) {
                calibStatus = "✓";
            } else if (Math.abs(calibError) <= 10.0) {
                calibStatus = "⚠️";
            } else {
                calibStatus = "✗ resetToAbsolute() may have failed!";
            }

            System.out.println(String.format(
                "  Module %d: Integrated=%.2f°, Absolute=%.2f°, Offset=%.2f°, CalibError=%.2f° %s",
                mod, integ, absol, offset, calibError, calibStatus));

            TestDashboard.putResultDouble(this, "Summary/Mod" + mod + "/CalibError", calibError);
            TestDashboard.putResultDouble(this, "Summary/Mod" + mod + "/AngleOffset", offset);
        }

        System.out.println("\n==============================\n");
    }

    // ============================================================================
    // Utilities
    // ============================================================================

    private String formatAngles(double[] angles) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < angles.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.0f°", angles[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
