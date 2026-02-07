// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

import frc.robot.SwerveModule;
import frc.robot.Subsystems.SwerveSubsystem;

/**
 * Utility class for testing swerve modules.
 * Provides helper methods for test code to interact with swerve modules
 * without adding test-specific methods to production classes.
 */
public class SwerveModuleTestUtils {

    /**
     * Checks if a swerve module is at the specified angle within tolerance.
     * 
     * This method is used in test commands to determine when a module has reached
     * its target position. It compares the current relative encoder position to the
     * target angle, accounting for the circular nature of angles (e.g., 359° is close to 1°).
     * 
     * <p>This method is designed to be called repeatedly in a control loop (e.g., every 20ms)
     * until the module reaches the target position. Once this returns true, test code can
     * record encoder measurements and proceed to the next test phase.
     * 
     * @param module The swerve module to check
     * @param targetDegrees The target angle in degrees (0-360)
     * @param toleranceDegrees The acceptable error in degrees
     * @return true if the module is within tolerance of the target angle
     */
    public static boolean isAtAngle(SwerveModule module, double targetDegrees, double toleranceDegrees) {
        double currentDegrees = getRelativeEncoderDegrees(module);
        double error = Math.abs(Math.IEEEremainder(currentDegrees - targetDegrees, 360.0));
        return error <= toleranceDegrees;
    }

    /**
     * Gets the relative encoder position in degrees.
     * 
     * The relative encoder (integrated encoder) is the encoder built into the motor controller.
     * It measures changes in position relative to a starting point and can drift over time.
     * 
     * @param module The swerve module
     * @return The relative encoder position in degrees
     */
    public static double getRelativeEncoderDegrees(SwerveModule module) {
        return module.getRawTurnEncoder(); // Directly uses the public method
    }

    /**
     * Gets the absolute encoder position in degrees.
     * 
     * The absolute encoder (CANcoder) retains its position even after power loss.
     * It's used as a ground truth reference to detect drift in the relative encoder.
     * 
     * @param module The swerve module
     * @return The absolute encoder position in degrees
     */
    public static double getAbsoluteEncoderDegrees(SwerveModule module) {
        return module.getCanCoder().getDegrees(); // Directly uses the public method
    }

    /**
     * Gets a specific swerve module from the swerve subsystem.
     * 
     * @param swerveSubsystem The swerve subsystem containing the modules
     * @param moduleNumber The module number (0-3)
     * @return The SwerveModule instance, or null if moduleNumber is invalid
     */
    public static SwerveModule getModule(SwerveSubsystem swerveSubsystem, int moduleNumber) {
        return swerveSubsystem.getModule(moduleNumber);
    }
}
