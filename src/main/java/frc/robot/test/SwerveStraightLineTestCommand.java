// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import frc.lib.test.DiagnosticTest;
import frc.lib.test.TestDashboard;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.SwerveModule;

/**
 * Diagnostic test that commands the robot to drive straight forward with known inputs
 * and measures how each module responds.
 *
 * <p>This test bypasses the joystick entirely, feeding exact ChassisSpeeds through the
 * same code path as normal driving ({@code driveFromChassisSpeeds()}). By removing human
 * input variability, it reveals whether the drift problem is caused by the drive code,
 * motor/encoder configuration, or mechanical issues.
 *
 * <p><b>Test sequence:</b>
 * <ol>
 *   <li>Command all modules to 0° (straight forward) and wait for them to settle</li>
 *   <li>Command a known ChassisSpeeds (e.g., 1 m/s forward, 0 strafe, 0 rotation)</li>
 *   <li>Record module states (angles and velocities) at regular intervals during the drive period</li>
 *   <li>Record gyro heading to detect rotation during straight-line driving</li>
 *   <li>Stop the drive and report per-module statistics</li>
 * </ol>
 *
 * <p><b>What this test reveals:</b>
 * <ul>
 *   <li>Module angle errors during driving (modules not all pointing the same direction)</li>
 *   <li>Module velocity mismatches (one module spinning faster/slower than others)</li>
 *   <li>Gyro heading drift during straight-line driving (robot rotating when it shouldn't be)</li>
 *   <li>Open-loop vs closed-loop differences (by toggling the control mode parameter)</li>
 *   <li>Whether kinematics module ordering matches physical module positions</li>
 * </ul>
 *
 * <p><b>Interpreting results:</b>
 * <ul>
 *   <li>All module angles ≈ 0° and velocities match: Drive code is correct — problem is elsewhere</li>
 *   <li>One module angle offset: Bad angleOffset calibration for that module</li>
 *   <li>Velocities differ significantly in open-loop but match in closed-loop: Normal motor variation (use closed-loop)</li>
 *   <li>Gyro drifts during test: Robot is physically rotating — one or more modules are pushing sideways</li>
 *   <li>Module angles are correct but gyro drifts: Mechanical issue (uneven wheel wear, friction, weight)</li>
 * </ul>
 */
public class SwerveStraightLineTestCommand extends Command implements DiagnosticTest {

    private final SwerveSubsystem swerveSubsystem;

    // Test parameters (read from SmartDashboard in initialize())
    private double driveSpeedMps;          // Forward speed in m/s
    private double settleTimeSeconds;      // Time to wait for modules to align before driving
    private double driveTimeSeconds;       // Duration to drive straight
    private double sampleIntervalSeconds;  // How often to record measurements
    private boolean useClosedLoop;         // Closed-loop (true) or open-loop (false) drive control

    // Test state
    private enum TestState {
        ALIGNING,     // Pre-aligning modules to 0° before driving
        DRIVING,      // Driving straight and recording measurements
        STOPPING,     // Stopped driving, recording final state
        COMPLETE      // Test finished
    }

    private TestState currentState;
    private double stateStartTime;
    private double lastSampleTime;
    private int sampleCount;
    private double initialYaw;

    // Results storage (per sample)
    private static final int MAX_SAMPLES = 500;  // 10 seconds at 20ms = 500 samples
    private double[] sampleTimes;
    private double[][] sampleAngles;       // [sampleIndex][moduleNumber]
    private double[][] sampleVelocities;   // [sampleIndex][moduleNumber]
    private double[] sampleYaw;            // Gyro heading at each sample

    // Module references
    private SwerveModule[] modules;
    private static final int NUM_MODULES = 4;

    /**
     * Creates a new SwerveStraightLineTestCommand.
     * Parameters are read from SmartDashboard in the initialize() method.
     *
     * @param swerveSubsystem The swerve subsystem to test
     */
    public SwerveStraightLineTestCommand(SwerveSubsystem swerveSubsystem) {
        this.swerveSubsystem = swerveSubsystem;
        addRequirements(swerveSubsystem);
    }

    // ============================================================================
    // DiagnosticTest Interface Implementation
    // ============================================================================

    @Override
    public String getTestName() {
        return "Swerve Straight Line Test";
    }

    @Override
    public String getTestDescription() {
        return "Commands the robot to drive straight forward with known inputs (bypassing the joystick) "
             + "and measures each module's angle and velocity response. Reveals angle calibration errors, "
             + "velocity mismatches, and heading drift that cause the robot to not drive straight.";
    }

    @Override
    public void initializeParameters() {
        TestDashboard.putParamDouble(this, "DriveSpeed", 1.0);
        TestDashboard.putParamDouble(this, "SettleTime", 1.5);
        TestDashboard.putParamDouble(this, "DriveTime", 3.0);
        TestDashboard.putParamDouble(this, "SampleInterval", 0.1);
        TestDashboard.putParamBoolean(this, "UseClosedLoop", false);
    }

    // ============================================================================
    // Command Lifecycle
    // ============================================================================

    @Override
    public void initialize() {
        // Read parameters from SmartDashboard
        driveSpeedMps = TestDashboard.getParamDouble(this, "DriveSpeed", 1.0);
        settleTimeSeconds = TestDashboard.getParamDouble(this, "SettleTime", 1.5);
        driveTimeSeconds = TestDashboard.getParamDouble(this, "DriveTime", 3.0);
        sampleIntervalSeconds = TestDashboard.getParamDouble(this, "SampleInterval", 0.1);
        useClosedLoop = TestDashboard.getParamBoolean(this, "UseClosedLoop", false);

        // Validate
        if (driveSpeedMps <= 0) {
            System.err.println("ERROR: DriveSpeed must be positive. Got: " + driveSpeedMps);
            currentState = TestState.COMPLETE;
            return;
        }
        if (driveTimeSeconds <= 0) {
            System.err.println("ERROR: DriveTime must be positive. Got: " + driveTimeSeconds);
            currentState = TestState.COMPLETE;
            return;
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
        int expectedSamples = Math.min(MAX_SAMPLES,
            (int) (driveTimeSeconds / sampleIntervalSeconds) + 10);
        sampleTimes = new double[expectedSamples];
        sampleAngles = new double[expectedSamples][NUM_MODULES];
        sampleVelocities = new double[expectedSamples][NUM_MODULES];
        sampleYaw = new double[expectedSamples];
        sampleCount = 0;

        // Record initial yaw
        initialYaw = swerveSubsystem.getYaw().getDegrees();

        // Start by aligning all modules to 0° (straight forward)
        currentState = TestState.ALIGNING;
        stateStartTime = Timer.getFPGATimestamp();
        lastSampleTime = 0.0;

        // Command all modules to 0° with zero speed
        SwerveModuleState alignState = new SwerveModuleState(0.0, Rotation2d.fromDegrees(0.0));
        for (SwerveModule mod : modules) {
            mod.setDesiredState(alignState, false);
        }

        // Log test start
        System.out.println("=== Swerve Straight Line Test Started ===");
        System.out.println("Drive Speed: " + driveSpeedMps + " m/s");
        System.out.println("Settle Time: " + settleTimeSeconds + "s");
        System.out.println("Drive Time: " + driveTimeSeconds + "s");
        System.out.println("Control Mode: " + (useClosedLoop ? "Closed-Loop" : "Open-Loop"));
        System.out.println("Initial Yaw: " + String.format("%.2f°", initialYaw));
        System.out.println("-----------------------------------------");

        // Update dashboard
        TestDashboard.putResultString(this, "Status", "Aligning modules...");
        TestDashboard.putResultString(this, "Config/ControlMode",
            useClosedLoop ? "Closed-Loop" : "Open-Loop");
        TestDashboard.putResultDouble(this, "Config/DriveSpeed", driveSpeedMps);
    }

    @Override
    public void execute() {
        if (currentState == TestState.COMPLETE) {
            return;
        }

        double currentTime = Timer.getFPGATimestamp();
        double elapsed = currentTime - stateStartTime;

        switch (currentState) {
            case ALIGNING:
                // Wait for modules to settle at 0° before starting to drive
                if (elapsed >= settleTimeSeconds) {
                    // Start driving
                    currentState = TestState.DRIVING;
                    stateStartTime = currentTime;
                    lastSampleTime = currentTime;

                    // Command straight-forward ChassisSpeeds through the full drive pipeline
                    // This uses the SAME code path as teleop driving
                    ChassisSpeeds straightForward = new ChassisSpeeds(driveSpeedMps, 0.0, 0.0);
                    swerveSubsystem.driveFromChassisSpeeds(straightForward, !useClosedLoop);

                    System.out.println("Modules aligned. Driving straight at "
                        + driveSpeedMps + " m/s...");
                    TestDashboard.putResultString(this, "Status", "Driving...");

                    // Record first sample immediately
                    recordSample(currentTime);
                }
                break;

            case DRIVING:
                // Continue commanding the drive (ChassisSpeeds needs to be sent every cycle)
                ChassisSpeeds straightForward = new ChassisSpeeds(driveSpeedMps, 0.0, 0.0);
                swerveSubsystem.driveFromChassisSpeeds(straightForward, !useClosedLoop);

                // Record samples at the specified interval
                if (currentTime - lastSampleTime >= sampleIntervalSeconds) {
                    recordSample(currentTime);
                    lastSampleTime = currentTime;
                }

                // Update real-time display
                updateRealTimeDisplay();

                // Check if drive time has elapsed
                if (elapsed >= driveTimeSeconds) {
                    // Stop driving
                    ChassisSpeeds stop = new ChassisSpeeds(0.0, 0.0, 0.0);
                    swerveSubsystem.driveFromChassisSpeeds(stop, true);

                    currentState = TestState.STOPPING;
                    System.out.println("Drive period complete. Recording final measurements...");
                    TestDashboard.putResultString(this, "Status", "Analyzing...");
                }
                break;

            case STOPPING:
                // Done — print results
                currentState = TestState.COMPLETE;
                printResults();
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
        // Ensure motors are stopped
        ChassisSpeeds stop = new ChassisSpeeds(0.0, 0.0, 0.0);
        swerveSubsystem.driveFromChassisSpeeds(stop, true);

        if (interrupted) {
            System.out.println("=== Swerve Straight Line Test INTERRUPTED ===");
            TestDashboard.putResultString(this, "Status", "Interrupted");
        } else {
            System.out.println("=== Swerve Straight Line Test COMPLETED ===");
            TestDashboard.putResultString(this, "Status", "Complete");
        }
    }

    // ============================================================================
    // Data Recording
    // ============================================================================

    /**
     * Records a single sample of all module states and gyro heading.
     */
    private void recordSample(double timestamp) {
        if (sampleCount >= sampleTimes.length) {
            return; // Buffer full
        }

        sampleTimes[sampleCount] = timestamp - stateStartTime; // Time relative to drive start

        for (int i = 0; i < NUM_MODULES; i++) {
            SwerveModuleState state = modules[i].getState();
            sampleAngles[sampleCount][i] = state.angle.getDegrees();
            sampleVelocities[sampleCount][i] = state.speedMetersPerSecond;
        }

        sampleYaw[sampleCount] = swerveSubsystem.getYaw().getDegrees();
        sampleCount++;
    }

    /**
     * Updates real-time SmartDashboard display during driving.
     */
    private void updateRealTimeDisplay() {
        double currentYaw = swerveSubsystem.getYaw().getDegrees();
        double yawDrift = Math.IEEEremainder(currentYaw - initialYaw, 360.0);
        TestDashboard.putResultDouble(this, "RealTime/YawDrift", yawDrift);

        for (int i = 0; i < NUM_MODULES; i++) {
            SwerveModuleState state = modules[i].getState();
            TestDashboard.putResultDouble(this, "RealTime/Mod" + i + "Angle",
                state.angle.getDegrees());
            TestDashboard.putResultDouble(this, "RealTime/Mod" + i + "Velocity",
                state.speedMetersPerSecond);
        }
    }

    // ============================================================================
    // Results Reporting
    // ============================================================================

    /**
     * Prints comprehensive results analysis.
     */
    private void printResults() {
        System.out.println("\n=== STRAIGHT LINE TEST RESULTS ===");
        System.out.println("Samples collected: " + sampleCount);
        System.out.println("Control mode: " + (useClosedLoop ? "Closed-Loop" : "Open-Loop"));
        System.out.println("Commanded speed: " + driveSpeedMps + " m/s");

        if (sampleCount == 0) {
            System.out.println("No samples collected.");
            System.out.println("==================================\n");
            return;
        }

        // === Module Angle Analysis ===
        System.out.println("\n--- Module Angle Analysis ---");
        System.out.println("(All modules should read ≈ 0° for straight-forward driving)");
        System.out.println(String.format("  %-10s %-12s %-12s %-12s %-12s",
            "Module", "Avg Angle", "Std Dev", "Min", "Max"));

        for (int mod = 0; mod < NUM_MODULES; mod++) {
            double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (int s = 0; s < sampleCount; s++) {
                double angle = sampleAngles[s][mod];
                sum += angle;
                if (angle < min) min = angle;
                if (angle > max) max = angle;
            }
            double avg = sum / sampleCount;

            double variance = 0;
            for (int s = 0; s < sampleCount; s++) {
                double diff = sampleAngles[s][mod] - avg;
                variance += diff * diff;
            }
            double stdDev = Math.sqrt(variance / sampleCount);

            String status;
            if (Math.abs(avg) <= 2.0 && stdDev <= 1.0) {
                status = "✓";
            } else if (Math.abs(avg) <= 5.0) {
                status = "⚠️";
            } else {
                status = "✗";
            }

            System.out.println(String.format("  Mod %-5d %-12.2f %-12.2f %-12.2f %-12.2f %s",
                mod, avg, stdDev, min, max, status));

            TestDashboard.putResultDouble(this, "Summary/Mod" + mod + "/AvgAngle", avg);
            TestDashboard.putResultDouble(this, "Summary/Mod" + mod + "/AngleStdDev", stdDev);
        }

        // === Module Velocity Analysis ===
        System.out.println("\n--- Module Velocity Analysis ---");
        System.out.println(String.format("(All modules should read ≈ %.2f m/s)", driveSpeedMps));
        System.out.println(String.format("  %-10s %-12s %-12s %-12s %-12s",
            "Module", "Avg Vel", "Std Dev", "Min", "Max"));

        double[] avgVelocities = new double[NUM_MODULES];
        for (int mod = 0; mod < NUM_MODULES; mod++) {
            double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (int s = 0; s < sampleCount; s++) {
                double vel = sampleVelocities[s][mod];
                sum += vel;
                if (vel < min) min = vel;
                if (vel > max) max = vel;
            }
            double avg = sum / sampleCount;
            avgVelocities[mod] = avg;

            double variance = 0;
            for (int s = 0; s < sampleCount; s++) {
                double diff = sampleVelocities[s][mod] - avg;
                variance += diff * diff;
            }
            double stdDev = Math.sqrt(variance / sampleCount);

            double pctError = Math.abs(avg - driveSpeedMps) / driveSpeedMps * 100.0;
            String status;
            if (pctError <= 10.0) {
                status = "✓";
            } else if (pctError <= 25.0) {
                status = "⚠️";
            } else {
                status = "✗";
            }

            System.out.println(String.format("  Mod %-5d %-12.3f %-12.3f %-12.3f %-12.3f %s (%.1f%% error)",
                mod, avg, stdDev, min, max, status, pctError));

            TestDashboard.putResultDouble(this, "Summary/Mod" + mod + "/AvgVelocity", avg);
            TestDashboard.putResultDouble(this, "Summary/Mod" + mod + "/VelStdDev", stdDev);
        }

        // Velocity spread (difference between fastest and slowest module)
        double minAvgVel = avgVelocities[0], maxAvgVel = avgVelocities[0];
        for (int mod = 1; mod < NUM_MODULES; mod++) {
            if (avgVelocities[mod] < minAvgVel) minAvgVel = avgVelocities[mod];
            if (avgVelocities[mod] > maxAvgVel) maxAvgVel = avgVelocities[mod];
        }
        double velSpread = maxAvgVel - minAvgVel;
        System.out.println(String.format("\n  Velocity spread (fastest - slowest): %.3f m/s", velSpread));
        if (velSpread > 0.3) {
            System.out.println("  ✗ Significant velocity mismatch — modules are not driving at the same speed.");
            System.out.println("    → If open-loop: Try closed-loop control (motor variation is normal in open-loop).");
            System.out.println("    → If closed-loop: Check PID/feedforward tuning, or mechanical drag on one module.");
        } else if (velSpread > 0.1) {
            System.out.println("  ⚠️ Moderate velocity mismatch — may contribute to drift.");
        } else {
            System.out.println("  ✓ Velocities are well-matched across modules.");
        }
        TestDashboard.putResultDouble(this, "Summary/VelocitySpread", velSpread);

        // === Gyro Heading Analysis ===
        System.out.println("\n--- Gyro Heading Analysis ---");
        double finalYaw = sampleYaw[sampleCount - 1];
        double totalYawDrift = Math.IEEEremainder(finalYaw - initialYaw, 360.0);

        System.out.println(String.format("  Initial Yaw:  %.2f°", initialYaw));
        System.out.println(String.format("  Final Yaw:    %.2f°", finalYaw));
        System.out.println(String.format("  Total Drift:  %.2f°", totalYawDrift));
        System.out.println(String.format("  Drift Rate:   %.2f °/s", totalYawDrift / driveTimeSeconds));

        if (Math.abs(totalYawDrift) <= 2.0) {
            System.out.println("  ✓ Heading is stable — robot drove straight.");
        } else if (Math.abs(totalYawDrift) <= 10.0) {
            System.out.println("  ⚠️ Moderate heading drift — robot is turning slightly.");
        } else {
            System.out.println("  ✗ Significant heading drift — robot is veering off course.");
        }

        TestDashboard.putResultDouble(this, "Summary/TotalYawDrift", totalYawDrift);
        TestDashboard.putResultDouble(this, "Summary/YawDriftRate", totalYawDrift / driveTimeSeconds);

        // === Diagnostic Summary ===
        System.out.println("\n--- Diagnostic Summary ---");

        // Check if angle errors could explain drift
        boolean angleIssue = false;
        for (int mod = 0; mod < NUM_MODULES; mod++) {
            double sum = 0;
            for (int s = 0; s < sampleCount; s++) {
                sum += sampleAngles[s][mod];
            }
            double avg = sum / sampleCount;
            if (Math.abs(avg) > 3.0) {
                System.out.println(String.format(
                    "  ⚠️ Module %d average angle is %.2f° (should be ≈ 0°). "
                    + "Check angleOffset calibration.", mod, avg));
                angleIssue = true;
            }
        }
        if (!angleIssue && Math.abs(totalYawDrift) > 5.0) {
            System.out.println("  Module angles look correct but robot still drifts.");
            System.out.println("  Likely causes: velocity mismatch, mechanical (uneven wheels/friction), or weight distribution.");
        }
        if (!angleIssue && Math.abs(totalYawDrift) <= 2.0 && velSpread <= 0.1) {
            System.out.println("  ✓ All measurements look good. Drive code appears correct.");
            System.out.println("    If the robot still drifts with joystick input, the issue may be in");
            System.out.println("    TeleopSwerve (deadband, input scaling) or field-oriented heading.");
        }

        System.out.println("\n==================================\n");
    }
}
