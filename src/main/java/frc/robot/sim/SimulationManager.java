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

        // Step 1: Get velocities from joystick commands (x, y, rotation) via SwerveSubsystem
        // Joystick provides x (forward/back), y (strafe), and rotation speeds
        // These are converted to individual module states (speed and angle for each of 4 wheels)
        SwerveModuleState[] desiredStates = swerveSubsystem.getDesiredStates();
        ChassisSpeeds desiredChassisSpeeds;
        if (desiredStates != null && desiredStates.length == 4 && 
            desiredStates[0] != null && desiredStates[1] != null && 
            desiredStates[2] != null && desiredStates[3] != null) {
            // Convert desired module states back to chassis speeds (robot-level motion)
            desiredChassisSpeeds = swerveSubsystem.getKinematics().toChassisSpeeds(desiredStates);
        } else {
            // Fallback: if no desired states available, use zero speeds
            desiredChassisSpeeds = new ChassisSpeeds();
        }
        
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
        updateModuleEncoders(desiredChassisSpeeds, dt);
        
        // Step 5: Update odometry based on simulated sensors
        Rotation2d yaw = Rotation2d.fromDegrees(swerveSubsystem.getPigeon().getYaw().getValueAsDouble());
        SwerveModulePosition[] positions = swerveSubsystem.getPositions();
        swerveSubsystem.getOdometry().update(yaw, positions);
        
        // Step 6: Update Field2d visualization
        swerveSubsystem.getField().setRobotPose(swerveSubsystem.getOdometry().getEstimatedPosition());
    }
    
    /**
     * Updates simulated module encoders based on chassis motion.
     * For each module, calculates the expected encoder position change based on
     * the module's contribution to the overall motion.
     */
    private void updateModuleEncoders(ChassisSpeeds chassisSpeeds, double dt) {
        // Convert chassis speeds to individual module speeds using swerve kinematics
        // Each of the 4 wheels can have different speeds (e.g., when turning, outside wheels move faster)
        SwerveModuleState[] desiredStates = swerveSubsystem.getKinematics().toSwerveModuleStates(chassisSpeeds);
        
        SwerveModule[] modules = swerveSubsystem.getModules();
        for (int i = 0; i < modules.length; i++) {
            SwerveModule module = modules[i];
            SwerveModuleState desiredState = desiredStates[i];
            
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
