// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.Subsystems.SwerveSubsystem;

/**
 * Manager class for the swerve angle drift test.
 * Handles SmartDashboard configuration and test triggering.
 * Keeps test code separate from production robot code.
 */
public class SwerveDriftTestManager {
    
    private static final String DASHBOARD_PREFIX = "DriftTest/";
    
    /**
     * Initializes SmartDashboard controls for the swerve angle drift test.
     * Should be called once during robot initialization.
     */
    public static void initializeDashboard() {
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "ModuleNumber", 0);
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "TestAngle", 90.0);
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "NumberOfCycles", 10);
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "AngleTolerance", 2.0);
        SmartDashboard.putNumber(DASHBOARD_PREFIX + "MaxWaitTime", 1.0);  // 1 second is sufficient for swerve angle motors
        SmartDashboard.putBoolean(DASHBOARD_PREFIX + "StartTest", false);
    }
    
    /**
     * Starts the drift test using parameters from SmartDashboard.
     * Validates parameters and schedules the test command.
     * 
     * @param swerveSubsystem The swerve subsystem to test
     * @return true if the test was started successfully, false if parameters were invalid
     */
    public static boolean startTestFromDashboard(SwerveSubsystem swerveSubsystem) {
        int moduleNumber = (int) SmartDashboard.getNumber(DASHBOARD_PREFIX + "ModuleNumber", 0);
        double testAngle = SmartDashboard.getNumber(DASHBOARD_PREFIX + "TestAngle", 90.0);
        int numberOfCycles = (int) SmartDashboard.getNumber(DASHBOARD_PREFIX + "NumberOfCycles", 10);
        double tolerance = SmartDashboard.getNumber(DASHBOARD_PREFIX + "AngleTolerance", 2.0);
        double maxWait = SmartDashboard.getNumber(DASHBOARD_PREFIX + "MaxWaitTime", 1.0);
        
        return startTest(swerveSubsystem, moduleNumber, testAngle, numberOfCycles, tolerance, maxWait);
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
     * @return true if the test was started successfully, false if parameters were invalid
     */
    public static boolean startTest(
            SwerveSubsystem swerveSubsystem,
            int moduleNumber,
            double testAngleDegrees,
            int numberOfCycles,
            double angleToleranceDegrees,
            double maxWaitTimeSeconds) {
        
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
        SwerveAngleDriftTestCommand testCommand = new SwerveAngleDriftTestCommand(
            swerveSubsystem, moduleNumber, testAngleDegrees, numberOfCycles, 
            angleToleranceDegrees, maxWaitTimeSeconds);
        testCommand.schedule();
        
        System.out.println("Starting drift test: Module " + moduleNumber + 
            ", Angle " + testAngleDegrees + "°, Cycles " + numberOfCycles);
        
        return true;
    }
    
    /**
     * Starts the drift test with default tolerance and timeout values.
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
        
        return startTest(swerveSubsystem, moduleNumber, testAngleDegrees, numberOfCycles, 2.0, 1.0);
    }
    
    /**
     * Checks SmartDashboard for drift test trigger and starts the test if requested.
     * Should be called periodically (e.g., in a subsystem's periodic() method).
     * 
     * @param swerveSubsystem The swerve subsystem to test
     */
    public static void checkAndStartTest(SwerveSubsystem swerveSubsystem) {
        boolean startTest = SmartDashboard.getBoolean(DASHBOARD_PREFIX + "StartTest", false);
        if (startTest) {
            // Reset the flag immediately to prevent multiple triggers
            SmartDashboard.putBoolean(DASHBOARD_PREFIX + "StartTest", false);
            startTestFromDashboard(swerveSubsystem);
        }
    }
}
