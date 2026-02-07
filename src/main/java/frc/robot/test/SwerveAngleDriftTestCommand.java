// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.SwerveModule;
import frc.robot.test.SwerveModuleTestUtils;

/**
 * Test command to detect encoder drift in swerve module angle motors.
 * 
 * This command performs the following test sequence:
 * 1. Commands the angle motor to turn to a specific position
 * 2. Waits for the motor to reach the target position
 * 3. Commands the motor back to zero
 * 4. Waits for the motor to reach zero
 * 5. Repeats this cycle N times
 * 6. Compares the relative encoder to the absolute encoder at each cycle
 * 7. Reports drift statistics
 * 
 * This test helps identify if the relative (integrated) encoder is accumulating
 * error over multiple cycles, which would indicate drift issues.
 */
public class SwerveAngleDriftTestCommand extends Command {
    private final SwerveSubsystem swerveSubsystem;
    private final int moduleNumber;
    private final double testAngleDegrees;
    private final int numberOfCycles;
    private final double angleToleranceDegrees;
    private final double maxWaitTimeSeconds;

    // Test state machine - tracks where we are in the test cycle
    private enum TestState {
        MOVING_TO_TARGET,  // Module is rotating to the test angle
        AT_TARGET,         // Module has reached test angle (unused, kept for clarity)
        MOVING_TO_ZERO,    // Module is rotating back to zero
        AT_ZERO,           // Module has reached zero (unused, kept for clarity)
        COMPLETE           // All cycles finished
    }

    private TestState currentState = TestState.MOVING_TO_TARGET;
    private int currentCycle = 0;  // Current cycle number (0-indexed, displayed as cycle+1)
    private double stateStartTime = 0.0;  // Timestamp when current state started (for timeout detection)
    private SwerveModule testModule;  // The module being tested
    
    /**
     * Record to store the results of a single test cycle.
     * A complete cycle consists of:
     * 1. Moving to the target angle and measuring drift
     * 2. Moving back to zero and measuring drift
     */
    public static record TestCycleResult(
        int cycleNumber,
        // Measurement at target angle
        double relativeEncoderAtTarget,
        double absoluteEncoderAtTarget,
        double driftAtTarget,
        boolean wasTimeoutAtTarget,
        // Measurement at zero
        double relativeEncoderAtZero,
        double absoluteEncoderAtZero,
        double driftAtZero,
        boolean wasTimeoutAtZero
    ) {
        /**
         * Creates a TestCycleResult with measurements at both target and zero positions.
         */
        public static TestCycleResult create(
                int cycleNumber,
                double relativeAtTarget, double absoluteAtTarget, boolean timeoutAtTarget,
                double relativeAtZero, double absoluteAtZero, boolean timeoutAtZero) {
            double driftAtTarget = Math.IEEEremainder(relativeAtTarget - absoluteAtTarget, 360.0);
            double driftAtZero = Math.IEEEremainder(relativeAtZero - absoluteAtZero, 360.0);
            return new TestCycleResult(
                cycleNumber,
                relativeAtTarget, absoluteAtTarget, driftAtTarget, timeoutAtTarget,
                relativeAtZero, absoluteAtZero, driftAtZero, timeoutAtZero
            );
        }
    }
    
    // Test results storage - one entry per complete cycle
    // Each cycle contains measurements at both target and zero positions
    private TestCycleResult[] testResults;
    
    // Temporary storage for current cycle measurements at target position
    // These are stored when we reach the target, then combined with zero measurements
    // to create the complete TestCycleResult when we reach zero
    private double relativeAtTarget = 0.0;
    private double absoluteAtTarget = 0.0;
    private boolean timeoutAtTarget = false;

    /**
     * Creates a new SwerveAngleDriftTestCommand.
     * 
     * @param swerveSubsystem The swerve subsystem containing the modules
     * @param moduleNumber The module number to test (0-3)
     * @param testAngleDegrees The angle to rotate to during each cycle (0-360)
     * @param numberOfCycles The number of cycles to perform
     * @param angleToleranceDegrees The tolerance for considering the motor "at position" (default: 2.0)
     * @param maxWaitTimeSeconds Maximum time to wait for motor to reach position before timing out (default: 3.0)
     */
    public SwerveAngleDriftTestCommand(
            SwerveSubsystem swerveSubsystem,
            int moduleNumber,
            double testAngleDegrees,
            int numberOfCycles,
            double angleToleranceDegrees,
            double maxWaitTimeSeconds) {
        this.swerveSubsystem = swerveSubsystem;
        this.moduleNumber = moduleNumber;
        this.testAngleDegrees = testAngleDegrees;
        this.numberOfCycles = numberOfCycles;
        this.angleToleranceDegrees = angleToleranceDegrees;
        this.maxWaitTimeSeconds = maxWaitTimeSeconds;
        
        addRequirements(swerveSubsystem);
    }

    /**
     * Creates a new SwerveAngleDriftTestCommand with default tolerance and timeout.
     * 
     * @param swerveSubsystem The swerve subsystem containing the modules
     * @param moduleNumber The module number to test (0-3)
     * @param testAngleDegrees The angle to rotate to during each cycle (0-360)
     * @param numberOfCycles The number of cycles to perform
     */
    public SwerveAngleDriftTestCommand(
            SwerveSubsystem swerveSubsystem,
            int moduleNumber,
            double testAngleDegrees,
            int numberOfCycles) {
        this(swerveSubsystem, moduleNumber, testAngleDegrees, numberOfCycles, 2.0, 1.0);
    }

    /**
     * Called once by CommandScheduler when the command is first scheduled/started.
     * Sets up the test: validates parameters, initializes data structures, and begins the first cycle.
     * 
     * <p>This is part of WPILib's Command framework lifecycle:
     * <ul>
     *   <li>Called automatically when command is scheduled (e.g., via button press or SmartDashboard trigger)</li>
     *   <li>Runs once at the start of the command</li>
     *   <li>After this, execute() will be called repeatedly</li>
     * </ul>
     */
    @Override
    public void initialize() {
        // Validate module number
        if (moduleNumber < 0 || moduleNumber > 3) {
            System.err.println("ERROR: Invalid module number: " + moduleNumber + ". Must be 0-3.");
            currentState = TestState.COMPLETE;
            return;
        }

        // Get the module to test
        testModule = SwerveModuleTestUtils.getModule(swerveSubsystem, moduleNumber);
        if (testModule == null) {
            System.err.println("ERROR: Module " + moduleNumber + " not found in swerve subsystem.");
            currentState = TestState.COMPLETE;
            return;
        }
        
        // Initialize test results storage (one entry per complete cycle)
        testResults = new TestCycleResult[numberOfCycles];
        
        // Reset temporary storage
        relativeAtTarget = 0.0;
        absoluteAtTarget = 0.0;
        timeoutAtTarget = false;
        
        // Initialize test state machine
        currentCycle = 0;  // Start with cycle 0 (will display as cycle 1, also used as array index)
        currentState = TestState.MOVING_TO_TARGET;  // First action: move to test angle
        stateStartTime = Timer.getFPGATimestamp();  // Record start time for timeout detection
        
        // Begin first cycle: command module to rotate to the test angle
        testModule.pointInDirection(testAngleDegrees);
        
        // Log test start
        System.out.println("=== Swerve Angle Drift Test Started ===");
        System.out.println("Module: " + moduleNumber);
        System.out.println("Test Angle: " + testAngleDegrees + " degrees");
        System.out.println("Cycles: " + numberOfCycles);
        System.out.println("Tolerance: " + angleToleranceDegrees + " degrees");
        System.out.println("----------------------------------------");
        
        // Update SmartDashboard
        SmartDashboard.putString("DriftTest/Status", "Running");
        SmartDashboard.putNumber("DriftTest/Module", moduleNumber);
        SmartDashboard.putNumber("DriftTest/Cycle", currentCycle);
        SmartDashboard.putNumber("DriftTest/TotalCycles", numberOfCycles);
    }

    /**
     * Called repeatedly by CommandScheduler every 20ms while the command is active.
     * Manages the test state machine: checks if module has reached target positions,
     * records measurements, and transitions between states.
     * 
     * <p>This is part of WPILib's Command framework lifecycle:
     * <ul>
     *   <li>Called automatically by CommandScheduler.run() (which runs in Robot.robotPeriodic())</li>
     *   <li>Runs every 20ms (50 times per second) while command is scheduled</li>
     *   <li>Continues until isFinished() returns true or command is interrupted</li>
     * </ul>
     */
    @Override
    public void execute() {
        // This method runs every 20ms while the command is active
        // It checks if the module has reached its target position and manages the test cycle
        
        if (currentState == TestState.COMPLETE) {
            return;
        }

        double currentTime = Timer.getFPGATimestamp();
        double elapsedTime = currentTime - stateStartTime;  // Time spent in current state

        switch (currentState) {
            case MOVING_TO_TARGET:
                // Phase 1: Wait for module to reach the test angle (e.g., 90°)
                // Once reached, store the encoder measurements and move to zero
                if (SwerveModuleTestUtils.isAtAngle(testModule, testAngleDegrees, angleToleranceDegrees)) {
                    recordTargetMeasurement(false);
                    transitionToZero(currentTime);
                } else if (elapsedTime > maxWaitTimeSeconds) {
                    recordTargetMeasurement(true);
                    transitionToZero(currentTime);
                }
                break;

            case MOVING_TO_ZERO:
                // Phase 2: Wait for module to return to zero
                // Once reached, we have both measurements (target + zero) and can create the complete cycle result
                if (SwerveModuleTestUtils.isAtAngle(testModule, 0.0, angleToleranceDegrees)) {
                    recordZeroMeasurement(false);
                    completeCycle(currentTime);
                } else if (elapsedTime > maxWaitTimeSeconds) {
                    recordZeroMeasurement(true);
                    completeCycle(currentTime);
                }
                break;

            default:
                break;
        }
    }

    /**
     * Called once by CommandScheduler when the command ends (either normally or interrupted).
     * Performs cleanup and final status updates.
     * 
     * <p>This is part of WPILib's Command framework lifecycle:
     * <ul>
     *   <li>Called automatically when isFinished() returns true OR when command is interrupted/cancelled</li>
     *   <li>Runs once at the end of the command</li>
     *   <li>The interrupted parameter indicates if command was cancelled (true) or completed normally (false)</li>
     * </ul>
     * 
     * @param interrupted true if command was cancelled/interrupted, false if it completed normally
     */
    @Override
    public void end(boolean interrupted) {
        if (interrupted) {
            System.out.println("=== Swerve Angle Drift Test INTERRUPTED ===");
            SmartDashboard.putString("DriftTest/Status", "Interrupted");
        } else {
            System.out.println("=== Swerve Angle Drift Test COMPLETED ===");
            SmartDashboard.putString("DriftTest/Status", "Complete");
        }
    }

    /**
     * Called by CommandScheduler every 20ms to check if the command should end.
     * When this returns true, the command will end and end() will be called.
     * 
     * <p>This is part of WPILib's Command framework lifecycle:
     * <ul>
     *   <li>Called automatically by CommandScheduler after each execute() call</li>
     *   <li>If returns true, command ends and end() is called</li>
     *   <li>If returns false, command continues and execute() is called again next cycle</li>
     * </ul>
     * 
     * @return true if command should end, false to continue running
     */
    @Override
    public boolean isFinished() {
        return currentState == TestState.COMPLETE;
    }

    // ============================================================================
    // State Transition Methods
    // ============================================================================
    
    /**
     * Transitions the state machine to moving to zero position.
     * 
     * @param currentTime Current timestamp for state transition
     */
    private void transitionToZero(double currentTime) {
        currentState = TestState.MOVING_TO_ZERO;
        stateStartTime = currentTime;
        testModule.pointInDirection(0.0);
    }
    
    /**
     * Transitions to the next test cycle or completes the test if all cycles are done.
     * 
     * @param currentTime Current timestamp for state transition
     */
    private void transitionToNextCycle(double currentTime) {
        currentState = TestState.MOVING_TO_TARGET;
        stateStartTime = currentTime;
        testModule.pointInDirection(testAngleDegrees);
        SmartDashboard.putNumber("DriftTest/Cycle", currentCycle);
    }
    
    /**
     * Handles cycle completion: checks if more cycles are needed or finishes the test.
     * 
     * @param currentTime Current timestamp for state transition
     */
    private void completeCycle(double currentTime) {
        // Cycle complete! Increment cycle counter and check if we need to run more cycles or finish
        currentCycle++;
        if (currentCycle >= numberOfCycles) {
            // All cycles completed - print final statistics and end test
            currentState = TestState.COMPLETE;
            printResults();
        } else {
            // Start the next cycle
            transitionToNextCycle(currentTime);
        }
    }
    
    // ============================================================================
    // Recording Methods
    // ============================================================================
    
    /**
     * Records the target position measurement.
     * 
     * @param wasTimeout true if this measurement was taken after a timeout
     */
    private void recordTargetMeasurement(boolean wasTimeout) {
        // Store encoder measurements temporarily - we'll create the complete cycle result
        // when we also have the zero position measurement
        relativeAtTarget = SwerveModuleTestUtils.getRelativeEncoderDegrees(testModule);
        absoluteAtTarget = SwerveModuleTestUtils.getAbsoluteEncoderDegrees(testModule);
        timeoutAtTarget = wasTimeout;
        
        // Print measurement results
        printTargetMeasurement(wasTimeout);
    }
    
    /**
     * Records the zero position measurement and creates the complete cycle result.
     * 
     * @param wasTimeout true if this measurement was taken after a timeout
     */
    private void recordZeroMeasurement(boolean wasTimeout) {
        // Get zero position measurements
        double relativeAtZero = SwerveModuleTestUtils.getRelativeEncoderDegrees(testModule);
        double absoluteAtZero = SwerveModuleTestUtils.getAbsoluteEncoderDegrees(testModule);
        
        // Record the complete cycle result (contains both target and zero measurements)
        recordCycleResult(relativeAtZero, absoluteAtZero, wasTimeout);
        
        // Print measurement results
        printZeroMeasurement(wasTimeout, relativeAtZero, absoluteAtZero);
    }
    
    /**
     * Records the complete cycle result with both target and zero measurements.
     * 
     * @param relativeAtZero Relative encoder value at zero position
     * @param absoluteAtZero Absolute encoder value at zero position
     * @param wasTimeoutAtZero true if zero measurement was taken after a timeout
     */
    private void recordCycleResult(double relativeAtZero, double absoluteAtZero, boolean wasTimeoutAtZero) {
        // Create and store the complete cycle result (contains both target and zero measurements)
        // currentCycle is used as the array index (0-indexed) and cycle number (1-indexed for display)
        testResults[currentCycle] = TestCycleResult.create(
            currentCycle + 1,  // Cycle number (1-indexed for display)
            relativeAtTarget, absoluteAtTarget, timeoutAtTarget,  // Target position data
            relativeAtZero, absoluteAtZero, wasTimeoutAtZero  // Zero position data
        );
    }
    
    // ============================================================================
    // Printing Methods
    // ============================================================================
    
    /**
     * Prints the target position measurement results.
     * 
     * @param wasTimeout true if this measurement was taken after a timeout
     */
    private void printTargetMeasurement(boolean wasTimeout) {
        double driftAtTarget = Math.IEEEremainder(relativeAtTarget - absoluteAtTarget, 360.0);
        
        if (wasTimeout) {
            System.err.println(String.format(
                "WARNING: Cycle %d timed out waiting to reach target angle %.2f° (within %.2f° tolerance)",
                currentCycle + 1, testAngleDegrees, angleToleranceDegrees));
            SmartDashboard.putString("DriftTest/Status", "Timeout at Target");
        }
        
        System.out.println(String.format(
            "%sCycle %d: Reached target (%.2f°) - Drift: %.3f° (Rel: %.2f°, Abs: %.2f°)",
            wasTimeout ? "  " : "", currentCycle + 1, testAngleDegrees, driftAtTarget, 
            relativeAtTarget, absoluteAtTarget));
        
        SmartDashboard.putNumber("DriftTest/DriftAtTarget", driftAtTarget);
    }
    
    /**
     * Prints the zero position measurement results and cycle completion summary.
     * 
     * @param wasTimeout true if this measurement was taken after a timeout
     * @param relativeAtZero Relative encoder value at zero position
     * @param absoluteAtZero Absolute encoder value at zero position
     */
    private void printZeroMeasurement(boolean wasTimeout, double relativeAtZero, double absoluteAtZero) {
        double driftAtZero = Math.IEEEremainder(relativeAtZero - absoluteAtZero, 360.0);
        
        if (wasTimeout) {
            System.err.println(String.format(
                "WARNING: Cycle %d timed out waiting to reach zero (within %.2f° tolerance)",
                currentCycle + 1, angleToleranceDegrees));
            SmartDashboard.putString("DriftTest/Status", "Timeout at Zero");
        }
        
        System.out.println(String.format(
            "%sCycle %d: Reached zero - Drift: %.3f° (Rel: %.2f°, Abs: %.2f°)",
            wasTimeout ? "  " : "", currentCycle + 1, driftAtZero, relativeAtZero, absoluteAtZero));
            System.out.println(String.format(
            "  Cycle %d complete%s - Target drift: %.3f°, Zero drift: %.3f°",
            currentCycle + 1, wasTimeout ? " (with timeout)" : "",
            testResults[currentCycle].driftAtTarget(), driftAtZero));
        
        SmartDashboard.putNumber("DriftTest/DriftAtZero", driftAtZero);
    }
    
    /**
     * Calculates and prints drift statistics from the collected test results.
     * 
     * Analyzes the complete cycle results to show:
     * - Statistics for drift at target position (across all cycles)
     * - Statistics for drift at zero position (across all cycles)
     * - Total accumulated drift over the entire test
     * - Warnings if drift exceeds acceptable thresholds
     */
    private void printResults() {
        System.out.println("\n=== DRIFT TEST RESULTS ===");
        System.out.println("Module: " + moduleNumber);
        System.out.println("Test Angle: " + testAngleDegrees + " degrees");
        System.out.println("Cycles Completed: " + currentCycle);
        
        if (currentCycle == 0) {
            System.out.println("No cycles completed.");
            System.out.println("==========================\n");
            return;
        }
        
        // Extract drift values from each cycle for separate analysis
        // We analyze target and zero positions separately to see if drift patterns differ
        double[] driftAtTarget = new double[currentCycle];
        double[] driftAtZero = new double[currentCycle];
        int timeoutCountAtTarget = 0;
        int timeoutCountAtZero = 0;
        
        for (int i = 0; i < currentCycle; i++) {
            if (testResults[i] != null) {
                // Extract drift values from each complete cycle
                driftAtTarget[i] = testResults[i].driftAtTarget();
                driftAtZero[i] = testResults[i].driftAtZero();
                // Count timeouts for reporting
                if (testResults[i].wasTimeoutAtTarget()) timeoutCountAtTarget++;
                if (testResults[i].wasTimeoutAtZero()) timeoutCountAtZero++;
            }
        }
        
        System.out.println("\n--- Drift at Target Position ---");
        printDriftStats(driftAtTarget, currentCycle);
        if (timeoutCountAtTarget > 0) {
            System.out.println(String.format("  (%d cycle(s) recorded after timeout at target)", timeoutCountAtTarget));
        }
        
        System.out.println("\n--- Drift at Zero Position ---");
        printDriftStats(driftAtZero, currentCycle);
        if (timeoutCountAtZero > 0) {
            System.out.println(String.format("  (%d cycle(s) recorded after timeout at zero)", timeoutCountAtZero));
        }
        
        // Calculate total accumulated drift over all cycles
        // Uses zero position measurements because we return to zero each cycle,
        // making it easier to see if drift accumulates over time
        if (currentCycle > 0) {
            double initialDrift = driftAtZero[0];  // Drift at zero in first cycle
            double finalDrift = driftAtZero[currentCycle - 1];  // Drift at zero in last cycle
            double totalDrift = finalDrift - initialDrift;  // How much drift accumulated
            
            System.out.println("\n--- Total Drift Over Test ---");
            System.out.println(String.format("Initial Drift at Zero: %.3f°", initialDrift));
            System.out.println(String.format("Final Drift at Zero: %.3f°", finalDrift));
            System.out.println(String.format("Total Accumulated Drift: %.3f°", totalDrift));
            if (currentCycle > 1) {
                System.out.println(String.format("Average Drift per Cycle: %.3f°", totalDrift / (currentCycle - 1)));
            }
            
            // Update SmartDashboard
            SmartDashboard.putNumber("DriftTest/TotalDrift", totalDrift);
            if (currentCycle > 1) {
                SmartDashboard.putNumber("DriftTest/AvgDriftPerCycle", totalDrift / (currentCycle - 1));
            }
            
            // Warning if drift is significant
            if (Math.abs(totalDrift) > 5.0) {
                System.out.println("\n⚠️  WARNING: Significant drift detected! (>5°)");
                System.out.println("   Consider checking:");
                System.out.println("   - PID tuning (especially I term)");
                System.out.println("   - Motor controller configuration");
                System.out.println("   - Mechanical backlash or binding");
                System.out.println("   - Encoder calibration");
            } else if (Math.abs(totalDrift) > 2.0) {
                System.out.println("\n⚠️  CAUTION: Moderate drift detected (>2°)");
            } else {
                System.out.println("\n✓ Drift is within acceptable range (<2°)");
            }
        }
        
        System.out.println("==========================\n");
    }
    
    /**
     * Prints statistics for drift values.
     * 
     * @param driftArray Array of drift values
     * @param count Number of valid values in the array
     */
    private void printDriftStats(double[] driftArray, int count) {
        if (count == 0) {
            System.out.println("No data collected");
            return;
        }

        double min = driftArray[0];
        double max = driftArray[0];
        double sum = 0.0;

        for (int i = 0; i < count; i++) {
            double drift = driftArray[i];
            if (drift < min) min = drift;
            if (drift > max) max = drift;
            sum += drift;
        }

        double avg = sum / count;
        
        // Calculate standard deviation
        double variance = 0.0;
        for (int i = 0; i < count; i++) {
            double diff = driftArray[i] - avg;
            variance += diff * diff;
        }
        double stdDev = Math.sqrt(variance / count);

        System.out.println(String.format("Cycles: %d", count));
        System.out.println(String.format("Min: %.3f°", min));
        System.out.println(String.format("Max: %.3f°", max));
        System.out.println(String.format("Average: %.3f°", avg));
        System.out.println(String.format("Std Dev: %.3f°", stdDev));
    }

}
