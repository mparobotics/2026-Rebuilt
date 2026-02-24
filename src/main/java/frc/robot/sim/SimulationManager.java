// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.sim;

import com.ctre.phoenix6.hardware.Pigeon2;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.sim.Pigeon2SimState;
import com.ctre.phoenix6.sim.CANcoderSimState;
import com.revrobotics.RelativeEncoder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Twist2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import frc.robot.SwerveModule;
import frc.robot.Subsystems.SwerveSubsystem;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;

/**
 * A self-contained simulation manager for driver practice.
 * Updates Field2d and fakes gyro/encoder readings for swerve robots.
 * Completely optional — can be removed without touching your real code.
 */
public class SimulationManager {

    private final SwerveSubsystem swerveSubsystem;
    
    // Simulation state objects
    private Pigeon2SimState pigeonSimState;
    private CANcoderSimState[] cancoderSimStates;
    
    // Internal simulated pose
    private Pose2d simPose = new Pose2d();
    private double lastTime = 0;

    // Disabled-state tracking: used to detect the enabled→disabled transition
    // so we can zero out stale module desired states once (edge-triggered).
    // Starts true because the robot boots into disabled mode.
    private boolean wasDisabled = true;

    /**
     * Creates a new SimulationManager.
     * @param swerveSubsystem The swerve subsystem to simulate
     */
    public SimulationManager(SwerveSubsystem swerveSubsystem) {
        this.swerveSubsystem = swerveSubsystem;
        
        // Initialize simulation state objects
        pigeonSimState = swerveSubsystem.getPigeon().getSimState();
        
        SwerveModule[] modules = swerveSubsystem.getModules();
        cancoderSimStates = new CANcoderSimState[modules.length];
        for (int i = 0; i < modules.length; i++) {
            CANcoder cancoder = modules[i].getCanCoderDevice();
            cancoderSimStates[i] = cancoder.getSimState();
        }
        
        this.lastTime = Timer.getFPGATimestamp();
    }

    /**
     * Call this in Robot.java simulationPeriodic().
     * Updates simulated sensors based on module states and integrates robot motion.
     */
    public void simulationPeriodic() {
        // Calculate time delta for physics integration (distance = velocity × time)
        // This enables frame-rate independent motion simulation
        double currentTime = Timer.getFPGATimestamp();
        double dt = currentTime - lastTime;
        lastTime = currentTime;
        
        // Safety check: clamp invalid time deltas (negative, zero, or unreasonably large)
        // Fallback to 20ms (standard robot loop period) if calculation is invalid
        if (dt <= 0 || dt > 1.0) {
            dt = 0.02;
        }

        // Disabled-state guard: when the robot transitions from enabled to disabled,
        // the CommandScheduler stops running commands but each module's desiredState
        // field retains its last commanded velocity. Without this guard, SimulationManager
        // would keep integrating those stale speeds, causing the simulated robot to drift.
        // We clear once on the transition edge — zeroing drive speed while preserving
        // wheel angles (realistic: wheels stop spinning but hold their orientation).
        boolean isDisabled = DriverStation.isDisabled();
        if (isDisabled && !wasDisabled) {
            for (SwerveModule module : swerveSubsystem.getModules()) {
                Rotation2d currentAngle = module.getDesiredState().angle;
                module.setDesiredState(new SwerveModuleState(0.0, currentAngle), false);
            }
        }
        wasDisabled = isDisabled;

        // Step 1: Get desired module states (from normal driving or individual module commands)
        SwerveModuleState[] desiredStates = swerveSubsystem.getDesiredStates();

        // Convert to chassis speeds for robot pose/gyro updates
        // In test mode with single module, this will be zero (correct - robot doesn't move)
        ChassisSpeeds desiredChassisSpeeds = (desiredStates != null && desiredStates.length == 4)
            ? swerveSubsystem.getKinematics().toChassisSpeeds(desiredStates)
            : new ChassisSpeeds();
        
        // Step 2: Update robot pose by adding distance traveled (velocity × time) to current position
        // Chassis speeds represent overall robot motion (one body moving as a unit)
        // Motors don't actually move in sim, so we use desired speeds for simulation
        simPose = simPose.exp(new Twist2d(
            desiredChassisSpeeds.vxMetersPerSecond * dt,      // X distance = X velocity × time
            desiredChassisSpeeds.vyMetersPerSecond * dt,      // Y distance = Y velocity × time
            desiredChassisSpeeds.omegaRadiansPerSecond * dt   // Angular distance = angular velocity × time
        ));
        
        // Step 3: Update simulated Pigeon2 gyro
        pigeonSimState.setRawYaw(simPose.getRotation().getDegrees());
        
        // Step 4: Update simulated module encoders
        // Use desired states directly (not chassis speeds) to handle individual module commands
        updateModuleEncoders(desiredStates, dt);
        
        // Note: Odometry and Field2d are updated by SwerveSubsystem.periodic(), which runs
        // automatically for both real robot and simulation. No need to update them here!
        // The periodic() method reads the sensors we just simulated (gyro, encoders) and
        // updates odometry and Field2d accordingly.
    }
    
    /**
     * Updates simulated module encoders based on desired module states.
     * For each module, updates encoder positions to match the desired states.
     * This handles both normal driving (all modules coordinated) and test mode (individual module control).
     */
    private void updateModuleEncoders(SwerveModuleState[] desiredStates, double dt) {
        SwerveModule[] modules = swerveSubsystem.getModules();
        for (int i = 0; i < modules.length; i++) {
            SwerveModule module = modules[i];
            SwerveModuleState desiredState = desiredStates[i];
            
            if (desiredState == null) {
                continue;
            }

            // Update drive encoder position: distance = velocity × time
            // * The encoder position represents distance traveled along the ground (in meters)
            // Each wheel can have a different speed (e.g., outside wheels move faster when turning)
            // * Wheel's speed * elapsed time = wheel's distance traveled
            RelativeEncoder driveEncoder = module.getDriveEncoder();
            double currentPosition = driveEncoder.getPosition();  // Current distance in meters
            double deltaMeters = desiredState.speedMetersPerSecond * dt;  // Distance traveled = wheel speed × time
            double newPosition = currentPosition + deltaMeters;  // Total distance traveled
            
            // Update drive encoder in simulation
            // REV encoders: use setPosition() directly (works in simulation)
            driveEncoder.setPosition(newPosition);
            
            // Update angle encoder position (module rotation)
            // The angle encoder position is in degrees (due to conversion factor)
            RelativeEncoder angleEncoder = module.getAngleEncoder();
            double desiredAngleDegrees = desiredState.angle.getDegrees();
            
            // Update angle encoder to match desired angle
            angleEncoder.setPosition(desiredAngleDegrees);
            
            // Update CANcoder simulation (absolute encoder)
            if (cancoderSimStates[i] != null) {
                // CANcoder position is in rotations (0.0 to 1.0)
                double positionRotations = desiredState.angle.getRotations();
                cancoderSimStates[i].setRawPosition(positionRotations);
            }
        }
    }
}
