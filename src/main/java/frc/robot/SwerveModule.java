// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.hardware.CANcoder;
import com.revrobotics.REVLibError;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.spark.ClosedLoopSlot;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkFlexConfig;
import com.revrobotics.spark.config.SparkMaxConfig;

import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.units.Units;
import edu.wpi.first.wpilibj.Timer;
import frc.lib.CANSparkUtil;
import frc.lib.CANSparkUtil.Usage;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Constants.SwerveConstants.ModuleData;


/** A Single Swerve Module */
public class SwerveModule {
    public int moduleNumber;
    private double m_angleKP;
    private double m_angleKI;
    private double m_angleKD;

    private Rotation2d lastAngle;
    private Rotation2d angleOffset;

    private SparkMax angleMotor;
    private SparkFlex driveMotor;

    private RelativeEncoder driveEncoder;
    private RelativeEncoder integratedAngleEncoder;
  
    private CANcoder angleEncoder;

    private final SparkClosedLoopController driveController;
    private final SparkClosedLoopController angleController;

    private final SimpleMotorFeedforward feedforward =
    new SimpleMotorFeedforward(
        SwerveConstants.driveKS, SwerveConstants.driveKV, SwerveConstants.driveKA);
    //creates a feedforward for the swerve drive. feedforward does 90% of the work, estimating stuff
    //PID fixes the error
 
    /**
     * Constructs a swerve module with the specified module number and configuration data.
     * Initializes and configures the angle encoder (CANcoder), angle motor (SparkMax),
     * and drive motor (SparkFlex) according to the provided constants.
     * 
     * @param moduleNumber The module identifier (typically 0-3 for a 4-module swerve drive)
     * @param moduleConstants ModuleData record containing:
     *                        - driveMotorID: CAN ID of the drive motor (SparkFlex)
     *                        - angleMotorID: CAN ID of the angle motor (SparkMax)
     *                        - encoderID: CAN ID of the absolute angle encoder (CANcoder)
     *                        - angleOffset: Calibration offset in degrees to align encoder zero with module zero
     *                        - location: Physical position of the module relative to robot center (Translation2d)
     */
    public SwerveModule(int moduleNumber, ModuleData moduleConstants){
        this.moduleNumber = moduleNumber;
        this.m_angleKP = SwerveConstants.angleKP;
        this.m_angleKI = SwerveConstants.angleKI;
        this.m_angleKD = SwerveConstants.angleKD;

        // Calibration offset to align absolute encoder zero with module zero position.
        angleOffset = Rotation2d.fromDegrees(moduleConstants.angleOffset());
        
        /* Angle Encoder Configuration
         * The CANcoder is an absolute encoder that provides the module's angle even after power loss.
         * It's used to calibrate the integrated encoder on startup.
         */
        // Create CANcoder instance with the encoder CAN ID from module constants
        angleEncoder = new CANcoder(moduleConstants.encoderID());
        // Apply default configuration to the CANcoder (factory reset to known state)
        angleEncoder.getConfigurator().apply(new CANcoderConfiguration());
        // Set update frequency to 1 Hz (once per second) for absolute position readings.
        // The CANcoder (absolute encoder) is only used once during robot startup to calibrate
        // the integrated encoder (see resetToAbsolute() in configAngleMotor()). During normal
        // operation, getAngle() reads from the integrated encoder every 20ms loop cycle, not
        // the CANcoder. A low CANcoder update frequency reduces CAN bus traffic since we only
        // need the absolute position once at startup, not continuously.
        angleEncoder.getAbsolutePosition().setUpdateFrequency(1);

        /* Angle Motor Configuration
         * The angle motor rotates the swerve module to the desired orientation.
         * It uses a SparkMax with integrated encoder for position control.
         */
        // Create SparkMax motor controller for angle rotation (brushless motor)
        angleMotor = new SparkMax(moduleConstants.angleMotorID(), MotorType.kBrushless);
        // Get the integrated encoder (relative encoder) from the motor controller
        integratedAngleEncoder = angleMotor.getEncoder();
        // Get the closed-loop controller for position control (PID controller)
        angleController = angleMotor.getClosedLoopController();
        // Configure motor settings (current limits, PID, encoder conversion, etc.)
        configAngleMotor();

        /* Drive Motor Configuration
         * The drive motor provides forward/backward motion for the swerve module.
         * It uses a SparkFlex with integrated encoder for velocity control.
         */
        // Create SparkFlex motor controller for drive motion (brushless motor)
        driveMotor = new SparkFlex(moduleConstants.driveMotorID(), MotorType.kBrushless);
        // Get the integrated encoder (relative encoder) from the motor controller
        driveEncoder = driveMotor.getEncoder();
        // Get the closed-loop controller for velocity control (PID controller)
        driveController = driveMotor.getClosedLoopController();
        // Configure motor settings (current limits, PID, encoder conversion, etc.)
        configDriveMotor();

        // Initialize lastAngle to current module angle for optimization calculations
        lastAngle = getState().angle;
    }

    /**
     * Sets the module to the desired state (speed and angle).
     * <p>
     * This is the main method for controlling the swerve module. It optimizes the desired
     * state to minimize rotation distance, then sets both the wheel angle and drive speed.
     * 
     * @param desiredState The target module state (speed in m/s and wheel angle)
     * @param isOpenLoop If true, uses open loop control for drive motor; if false, uses closed loop velocity control
     */
    public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop) {
        // Optimize the desired state to minimize rotation (flip wheel 180° if needed)
        SwerveModuleState optimizedState = optimize(desiredState, getAngle());
        // Set the wheel angle to the optimized direction
        setAngle(optimizedState);
        // Set the drive motor speed (open loop or closed loop based on parameter)
        setSpeed(optimizedState, isOpenLoop);      
    }

    /**
     * Gets the current state of the swerve module.
     * @return SwerveModuleState containing the current drive velocity (in meters per second)
     *         and module angle (Rotation2d)
     */
    public SwerveModuleState getState(){
        return new SwerveModuleState(driveEncoder.getVelocity(),  getAngle()); 
    }
    
    /**
     * Gets the current position of the swerve module.
     * @return SwerveModulePosition containing the current drive encoder position (in meters)
     *         and module angle (Rotation2d)
     */
    public SwerveModulePosition getPosition(){
        return new SwerveModulePosition(driveEncoder.getPosition(),  getAngle()); 
    }
    
    /**
     * Gets the raw drive encoder position in encoder units (not converted to meters).
     * @return Raw encoder position value from the drive motor's integrated encoder
     */
    public double getRawDriveEncoder(){
        return driveEncoder.getPosition();
    }
    
    /**
     * Gets the raw turn encoder position in encoder units (not converted to degrees).
     * @return Raw encoder position value from the angle motor's integrated encoder
     */
    public double getRawTurnEncoder(){
        return integratedAngleEncoder.getPosition();
    }

    /**
     * Gets the current **absolute encoder** (CANcoder) position.
     * <p>
     * The CANcoder is an **absolute encoder** that retains its position even after power loss.
     * This method reads the raw absolute position from the CANcoder and converts it to a
     * Rotation2d representing the module's wheel angle.
     * <p>
     * Used primarily during module initialization in {@link #resetToAbsolute()} to calibrate
     * the integrated encoder. Also used for debugging/logging to display the absolute encoder
     * value on SmartDashboard for diagnostics.
     * 
     * @return The current absolute encoder position as a Rotation2d
     */
    public Rotation2d getCanCoder(){
        return Rotation2d.fromRotations(angleEncoder.getAbsolutePosition().getValue().in(Units.Rotations));
    }

    /**
     * Checks if encoder data from both motors is valid (no errors).
     * <p>
     * Encoder errors can occur when:
     * <ul>
     *   <li>CAN bus communication fails (disconnected cable, CAN bus overload, electrical interference)</li>
     *   <li>Motor controller configuration errors (invalid parameters, failed configuration write)</li>
     *   <li>Encoder hardware failure (damaged encoder, loose connections, sensor malfunction)</li>
     *   <li>Motor controller fault conditions (overcurrent, overvoltage, thermal shutdown)</li>
     * </ul>
     * <p>
     * When errors are detected, consider:
     * <ul>
     *   <li>Logging the error to SmartDashboard or Driver Station for diagnostics</li>
     *   <li>Disabling the affected module to prevent unpredictable behavior</li>
     *   <li>Using fallback behavior (e.g., last known good encoder value, or disabling that module)</li>
     *   <li>Attempting recovery (re-initialization, reconfiguration, or recalibration)</li>
     * </ul>
     * <p>
     * <b>Note:</b> This method is currently not called anywhere in the codebase. Consider adding
     * periodic error checking in {@code SwerveSubsystem.periodic()} to monitor module health.
     * 
     * @return true if both drive motor and angle motor have no errors, false otherwise
     */
    public boolean isEncoderDataValid(){
        return driveMotor.getLastError() == REVLibError.kOk && angleMotor.getLastError() == REVLibError.kOk;
    }
    
    /**
     * Optimizes the desired module state to minimize rotation distance.
     * <p>
     * Swerve modules can achieve the same direction of travel by rotating the wheel
     * 180 degrees and reversing the drive speed. This method checks if the required
     * rotation is greater than 90 degrees, and if so, flips the wheel direction
     * and reverses speed to reduce the rotation needed. This minimizes wear and
     * improves response time.
     * 
     * @param desiredState The target module state (speed and angle)
     * @param currentAngle The current module wheel angle
     * @return Optimized module state that achieves the same direction with minimal rotation
     */
    private SwerveModuleState optimize(SwerveModuleState desiredState, Rotation2d currentAngle){
        // Calculate the angular difference between desired and current angle
        double difference = desiredState.angle.getDegrees() - currentAngle.getDegrees();
        // Normalize to -180° to +180° range (shortest rotation path)
        double turnAmount = Math.IEEEremainder(difference,360);

        double speed = desiredState.speedMetersPerSecond;

        // If rotation needed is more than 90°, flip wheel 180° and reverse speed
        // This reduces rotation distance (e.g., 120° turn becomes 60° turn)
        if (turnAmount > 90){
            turnAmount -= 180;
            speed *= -1;
        }
        // Same optimization for negative rotation angles
        if (turnAmount < -90){
            turnAmount += 180;
            speed *= -1;
        }

        // Calculate final optimized angle by adding adjusted turn amount to current angle
        double direction = currentAngle.getDegrees() + turnAmount;
        return new SwerveModuleState (speed, Rotation2d.fromDegrees(direction)); 
    }

    /**
     * Sets the drive motor speed to achieve the desired velocity.
     * <p>
     * This is a private helper method used by {@link #setDesiredState(SwerveModuleState, boolean)}.
     * Use {@code setDesiredState()} to control the module - do not call this method directly.
     * <p>
     * Supports two control modes:
     * <ul>
     *   <li><b>Open loop</b>: Direct percent output control (no feedback, less accurate)</li>
     *   <li><b>Closed loop</b>: Velocity control with PID and feedforward (uses encoder feedback, more accurate)</li>
     * </ul>
     * 
     * @param desiredState The target module state containing the desired speed in meters per second
     * @param isOpenLoop If true, uses open loop control; if false, uses closed loop velocity control
     */
    private void setSpeed(SwerveModuleState desiredState, boolean isOpenLoop){
        if (isOpenLoop) {
            // Open loop: Convert desired speed to percent output (-1.0 to 1.0)
            // No encoder feedback - motor runs at fixed percentage regardless of actual speed
            double percentOutput = desiredState.speedMetersPerSecond / Constants.SwerveConstants.maxSpeed;
            driveMotor.set(percentOutput);
        }
        else{
            // Closed loop: Use PID controller with feedforward for accurate velocity control
            // Feedforward estimates motor output needed for desired speed (90% of work)
            // PID controller corrects for any error between desired and actual speed
            driveController.setReference(
                desiredState.speedMetersPerSecond, 
                ControlType.kVelocity,
                ClosedLoopSlot.kSlot0,
                feedforward.calculate(desiredState.speedMetersPerSecond));
        }
    }

    /**
     * Sets the wheel angle to the desired direction.
     * <p>
     * This is a private helper method used by {@link #setDesiredState(SwerveModuleState, boolean)}.
     * Use {@code setDesiredState()} to control the module - do not call this method directly.
     * <p>
     * When the robot is moving very slowly (≤1% of max speed), the wheel angle is kept
     * at the last position to prevent unnecessary rotation and reduce wear. When moving
     * at significant speed, the wheel rotates to the desired angle.
     * 
     * @param desiredState The target module state containing the desired wheel angle
     */
    private void setAngle(SwerveModuleState desiredState){
        // If speed is very low (≤1% of max), keep last angle to avoid unnecessary rotation
        // This prevents jittery behavior and reduces wear when robot is barely moving
        Rotation2d angle = (Math.abs(desiredState.speedMetersPerSecond) <= (Constants.SwerveConstants.maxSpeed * 0.01))
            ? lastAngle : desiredState.angle;
        // Set the angle motor to rotate to the target angle (position control)
        angleController.setReference(angle.getDegrees(), ControlType.kPosition);
        // Update lastAngle for next optimization cycle
        lastAngle = angle; 
    }

    /**
     * Gets the current wheel angle from the **integrated encoder**.
     * <p>
     * This method reads the angle motor's integrated encoder position and converts it
     * to a Rotation2d representing the current wheel orientation.
     * 
     * @return The current wheel angle as a Rotation2d
     */
    private Rotation2d getAngle(){
        return Rotation2d.fromDegrees(integratedAngleEncoder.getPosition());
    }
    
    /**
     * Points the wheel in a specific direction without changing drive speed.
     * <p>
     * This method rotates the wheel to the specified angle (in degrees) while keeping
     * the drive motor stopped. Useful for testing, calibration, or positioning the wheel
     * without moving the robot. Unlike {@link #setDesiredState(SwerveModuleState, boolean)},
     * this method only controls the angle motor, not the drive motor.
     * <p>
     * <b>Note:</b> This method is currently not called anywhere in the codebase.
     * 
     * @param degrees The target wheel angle in degrees (0-360)
     */
    public void pointInDirection(double degrees){
        angleController.setReference(degrees, ControlType.kPosition);
        lastAngle = Rotation2d.fromDegrees(degrees);
    }
    
    /**
     * Configures the angle motor (SparkMax) with all necessary settings for position control.
     * Called once during module initialization in the constructor. Configures current limits,
     * motor inversion, brake mode, encoder conversion factors, PID values, and voltage
     * compensation. After configuration, calibrates the integrated encoder to the absolute
     * encoder (CANcoder) position.
     */
    private void configAngleMotor(){
        SparkMaxConfig sparkMaxConfig = new SparkMaxConfig();
        // Factory reset is commented out - only needed if motor needs to be reset to defaults
        //angleMotor.restoreFactoryDefaults();
        
        // Limit CAN bus usage to position data only (reduces CAN bus traffic)
        CANSparkUtil.setSparkBusUsage(sparkMaxConfig, Usage.kPositionOnly);
        // Set maximum current draw to protect motor and wiring
        sparkMaxConfig.smartCurrentLimit(SwerveConstants.angleContinuousCurrentLimit);
        // Set motor direction (may need to be inverted based on physical mounting)
        sparkMaxConfig.inverted(SwerveConstants.angleInvert);
        // Set idle mode: brake (holds position) or coast (free rotation)
        sparkMaxConfig.idleMode(SwerveConstants.angleNeutralMode);
        // Convert encoder counts to degrees so encoder position matches module rotation angle
        sparkMaxConfig.encoder.positionConversionFactor(SwerveConstants.angleConversionFactor);
        // Configure PID controller for position control (no feedforward used)
        sparkMaxConfig.closedLoop.p(m_angleKP).i(m_angleKI).d(m_angleKD);
       // angleController.setFF(m_angleKFF);
        // Compensate for battery voltage variations to maintain consistent motor performance
        sparkMaxConfig.voltageCompensation(SwerveConstants.voltageComp);
        // Apply configuration to motor controller: reset safe parameters on hardware first,
        // then apply config settings, and save to flash memory so settings persist after power cycle
        angleMotor.configure(sparkMaxConfig,ResetMode.kResetSafeParameters,PersistMode.kPersistParameters);

        // Wait 1 second for configuration to be applied and motor to stabilize
        Timer.delay(1.0);
        // Calibrate integrated encoder to absolute encoder position (sets starting position)
        resetToAbsolute();
    }

    /**
     * Calibrates the integrated encoder to match the absolute encoder (CANcoder) position.
     * <p>
     * This method reads the absolute encoder position, subtracts the calibration offset
     * (angleOffset), and sets the integrated encoder to this value. This ensures the
     * integrated encoder starts at the correct position even after power loss, since the
     * absolute encoder retains its position while the integrated encoder resets to zero.
     * <p>
     * Called once during module initialization in {@link #configAngleMotor()} after motor
     * configuration is complete. This establishes the starting position for the integrated
     * encoder, which is then used for all subsequent angle readings during normal operation.
     */
    private void resetToAbsolute() {
        double absolutePosition = getCanCoder().getDegrees() - angleOffset.getDegrees();
        integratedAngleEncoder.setPosition(absolutePosition); //may need to change 

      }

    /**
     * Configures the drive motor (SparkFlex) with all necessary settings for velocity control.
     * Called once during module initialization in the constructor. Configures current limits,
     * motor inversion, brake mode, encoder conversion factors, PID values, and voltage
     * compensation. After configuration, resets the drive encoder position to zero.
     */
    private void configDriveMotor(){
        SparkFlexConfig sparkFlexConfig = new SparkFlexConfig();
        // Factory reset is commented out - only needed if motor needs to be reset to defaults
        //driveMotor.restoreFactoryDefaults();
        
        // Use full CAN bus bandwidth for drive motor (needs velocity and position data frequently)
        CANSparkUtil.setSparkBusUsage(sparkFlexConfig, Usage.kAll);
        // Set maximum current draw to protect motor and wiring
        sparkFlexConfig.smartCurrentLimit(SwerveConstants.driveContinuousCurrentLimit);
        // Set motor direction (may need to be inverted based on physical mounting)
        sparkFlexConfig.inverted(SwerveConstants.driveInvert);
        // Set idle mode: brake (holds position) or coast (free rotation)
        sparkFlexConfig.idleMode(SwerveConstants.driveNeutralMode);
        // Convert encoder counts to meters per second for velocity readings
        sparkFlexConfig.encoder.velocityConversionFactor(SwerveConstants.driveConversionVelocityFactor);
        // Convert encoder counts to meters traveled for position readings
        sparkFlexConfig.encoder.positionConversionFactor(SwerveConstants.driveConversionPositionFactor);
        // Configure PID controller for velocity control (no feedforward used)
        sparkFlexConfig.closedLoop
            .p(SwerveConstants.driveKP)
            .i(SwerveConstants.driveKI)
            .d(SwerveConstants.driveKD);
        // Compensate for battery voltage variations to maintain consistent motor performance
        sparkFlexConfig.voltageCompensation(SwerveConstants.voltageComp);
        // Apply configuration to motor controller: reset safe parameters on hardware first,
        // then apply config settings, and save to flash memory so settings persist after power cycle
        driveMotor.configure(sparkFlexConfig,ResetMode.kResetSafeParameters,PersistMode.kPersistParameters);
        // Reset encoder position to zero (sets starting position for odometry)
        driveEncoder.setPosition(0.0);
    }
}