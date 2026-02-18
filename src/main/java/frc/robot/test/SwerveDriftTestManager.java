// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import frc.robot.Subsystems.SwerveSubsystem;

/**
 * Manager class for the swerve angle drift test.
 * Handles SmartDashboard configuration and test triggering.
 * Keeps test code separate from production robot code.
 * 
 * @deprecated This class is being replaced by the DiagnosticTestManager framework.
 *             Use the framework's test selection and execution instead.
 *             This class will be removed in Phase 2 migration.
 */
@Deprecated
public class SwerveDriftTestManager {
    
    private static final String DASHBOARD_PREFIX = "DriftTest/";
    
    /**
     * Initializes SmartDashboard controls for the swerve angle drift test.
     * Should be called once during robot initialization.
     */
    public static void initializeDashboard() {
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "Test/Module", 0);
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "Test/Angle", 90.0);
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "Test/NumberOfCycles", 10);
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "Test/AngleTolerance", 2.0);
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "Test/MaxWaitTime", 1.0);  // 1 second is sufficient for swerve angle motors
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "Test/MinHoldTime", 0.5);  // Minimum time to hold at each position (for visibility in simulation)
        SmartDashboard.putBoolean(DASHBOARD_PREFIX + "Test/Start", false);
    }
    
    /**
     * Starts the drift test using parameters from SmartDashboard.
     * Validates parameters and schedules the test command.
     * 
     * @param swerveSubsystem The swerve subsystem to test
     * @return true if the test was started successfully, false if parameters were invalid
     */
    public static boolean startTestFromDashboard(SwerveSubsystem swerveSubsystem) {
        int moduleNumber = (int) SmartDashboard.getNumber(DASHBOARD_PREFIX + "Test/Module", 0);
        double testAngle = SmartDashboard.getNumber(DASHBOARD_PREFIX + "Test/Angle", 90.0);
        int numberOfCycles = (int) SmartDashboard.getNumber(DASHBOARD_PREFIX + "Test/NumberOfCycles", 10);
        double tolerance = SmartDashboard.getNumber(DASHBOARD_PREFIX + "Test/AngleTolerance", 2.0);
        double maxWait = SmartDashboard.getNumber(DASHBOARD_PREFIX + "Test/MaxWaitTime", 1.0);
        double minHold = SmartDashboard.getNumber(DASHBOARD_PREFIX + "Test/MinHoldTime", 0.5);
        
        return startTest(swerveSubsystem, moduleNumber, testAngle, numberOfCycles, tolerance, maxWait, minHold);
    }
    
    /**
     * Starts the drift test with specified parameters.
     * 
     * @param swerveSubsystem The swerve subsystem to test
     * @param moduleNumber The module number to test (0-3)
     * @param testAngleDegrees The target angle in degrees (0-360)
     * @param numberOfCycles The number of test cycles
     * @param angleToleranceDegrees The angle tolerance in degrees
     * @param maxWaitTimeSeconds The maximum wait time per position in seconds
     * @param minHoldTimeSeconds The minimum time to hold at each position in seconds (for visibility in simulation)
     * @return true if the test was started successfully, false if parameters were invalid
     */
    public static boolean startTest(
            SwerveSubsystem swerveSubsystem,
            int moduleNumber,
            double testAngleDegrees,
            int numberOfCycles,
            double angleToleranceDegrees,
            double maxWaitTimeSeconds,
            double minHoldTimeSeconds) {
        
        // Validate parameters
        if (moduleNumber < 0 || moduleNumber > 3) {
            System.err.println("ERROR: Invalid module number: " + moduleNumber + ". Must be 0-3.");
            return false;
        }
        if (numberOfCycles < 1) {
            System.err.println("ERROR: Number of cycles must be at least 1.");
            return false;
        }
        if (testAngleDegrees < 0 || testAngleDegrees >= 360) {
            System.err.println("WARNING: Test angle should be 0-360 degrees. Using: " + testAngleDegrees);
        }
        
        // Schedule the test command
        // Note: The test now reads parameters from SmartDashboard in initialize(),
        // so we need to set them up before creating the command
        String paramPrefix = "DiagnosticTests/Swerve Angle Drift Test/Parameters/";
        SmartDashboard.putNumber(paramPrefix + "ModuleNumber", moduleNumber);
        SmartDashboard.putNumber(paramPrefix + "Angle", testAngleDegrees);
        SmartDashboard.putNumber(paramPrefix + "NumberOfCycles", numberOfCycles);
        SmartDashboard.putNumber(paramPrefix + "AngleTolerance", angleToleranceDegrees);
        SmartDashboard.putNumber(paramPrefix + "MaxWaitTime", maxWaitTimeSeconds);
        SmartDashboard.putNumber(paramPrefix + "MinHoldTime", minHoldTimeSeconds);
        
        SwerveAngleDriftTestCommand testCommand = new SwerveAngleDriftTestCommand(swerveSubsystem);
        CommandScheduler.getInstance().schedule(testCommand);
        
        System.out.println("Starting drift test: Module " + moduleNumber + 
            ", Angle " + testAngleDegrees + "°, Cycles " + numberOfCycles);
        
        return true;
    }
    
    /**
     * Starts the drift test with default tolerance, timeout, and hold time values.
     * 
     * @param swerveSubsystem The swerve subsystem to test
     * @param moduleNumber The module number to test (0-3)
     * @param testAngleDegrees The target angle in degrees (0-360)
     * @param numberOfCycles The number of test cycles
     * @return true if the test was started successfully, false if parameters were invalid
     */
    public static boolean startTest(
            SwerveSubsystem swerveSubsystem,
            int moduleNumber,
            double testAngleDegrees,
            int numberOfCycles) {
        
        return startTest(swerveSubsystem, moduleNumber, testAngleDegrees, numberOfCycles, 2.0, 1.0, 0.5);
    }
    
    /**
     * Checks SmartDashboard for drift test trigger and starts the test if requested.
     * Should be called periodically (e.g., in a subsystem's periodic() method).
     * 
     * @param swerveSubsystem The swerve subsystem to test
     */
    public static void checkAndStartTest(SwerveSubsystem swerveSubsystem) {
        boolean startTest = SmartDashboard.getBoolean(DASHBOARD_PREFIX + "Test/Start", false);
        if (startTest) {
            // Reset the flag immediately to prevent multiple triggers
            SmartDashboard.putBoolean(DASHBOARD_PREFIX + "Test/Start", false);
            startTestFromDashboard(swerveSubsystem);
        }
    }
}
