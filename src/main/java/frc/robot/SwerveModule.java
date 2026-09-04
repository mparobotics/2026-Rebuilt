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
import edu.wpi.first.wpilibj.Preferences;
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
    private final String angleOffsetPreferenceKey;
    private final boolean driveInvert;
    private final boolean angleInvert;

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
 
    public SwerveModule(int moduleNumber, ModuleData moduleConstants){
        this.moduleNumber = moduleNumber;
        this.angleOffsetPreferenceKey = "Swerve/Module" + moduleNumber + "/AngleOffsetDegrees";
        this.driveInvert = moduleConstants.driveInvert();
        this.angleInvert = moduleConstants.angleInvert();
        this.m_angleKP = SwerveConstants.angleKP;
        this.m_angleKI = SwerveConstants.angleKI;
        this.m_angleKD = SwerveConstants.angleKD;

        // Calibration offset to align absolute encoder zero with module zero position.
        double storedOffset =
            Preferences.getDouble(angleOffsetPreferenceKey, moduleConstants.angleOffset());
        angleOffset = Rotation2d.fromDegrees(normalizeDegrees(storedOffset));
        

        // Create CANcoder instance with the encoder CAN ID from module constants
        angleEncoder = new CANcoder(moduleConstants.encoderID());
        // Apply default configuration to the CANcoder (factory reset to known state)
        angleEncoder.getConfigurator().apply(new CANcoderConfiguration());
        angleEncoder.getAbsolutePosition().setUpdateFrequency(1);

        // Create SparkMax motor controller for angle rotation (brushless motor)
        angleMotor = new SparkMax(moduleConstants.angleMotorID(), MotorType.kBrushless);
        // Get the integrated encoder (relative encoder) from the motor controller
        integratedAngleEncoder = angleMotor.getEncoder();
        // Get the closed-loop controller for position control (PID controller)
        angleController = angleMotor.getClosedLoopController();
        // Configure motor settings (current limits, PID, encoder conversion, etc.)
        configAngleMotor();

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

    public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop) {
        // Optimize the desired state to minimize rotation (flip wheel 180° if needed)
        SwerveModuleState optimizedState = optimize(desiredState, getAngle());
        // Set the wheel angle to the optimized direction
        setAngle(optimizedState);
        // Set the drive motor speed (open loop or closed loop based on parameter)
        setSpeed(optimizedState, isOpenLoop);      
    }
    public SwerveModuleState getState(){
        return new SwerveModuleState(driveEncoder.getVelocity(),  getAngle()); 
    }
    public SwerveModulePosition getPosition(){
        return new SwerveModulePosition(driveEncoder.getPosition(),  getAngle()); 
    } 
    public double getRawDriveEncoder(){
        return driveEncoder.getPosition();
    }
    public double getRawTurnEncoder(){
        return integratedAngleEncoder.getPosition();
    }
    public Rotation2d getCanCoder(){
        return Rotation2d.fromRotations(angleEncoder.getAbsolutePosition().getValue().in(Units.Rotations));
    }
    public boolean isEncoderDataValid(){
        return driveMotor.getLastError() == REVLibError.kOk && angleMotor.getLastError() == REVLibError.kOk;
    }
    
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

    private void setSpeed(SwerveModuleState desiredState, boolean isOpenLoop){
        if (isOpenLoop) {
            double percentOutput = desiredState.speedMetersPerSecond / Constants.SwerveConstants.maxSpeed;
            driveMotor.set(percentOutput);
        }
        else{
            driveController.setSetpoint(
                desiredState.speedMetersPerSecond, 
                ControlType.kVelocity,
                ClosedLoopSlot.kSlot0,
                feedforward.calculate(desiredState.speedMetersPerSecond));
        }
    }

    private void setAngle(SwerveModuleState desiredState){
        // If speed is very low (≤1% of max), keep last angle to avoid unnecessary rotation
        // This prevents jittery behavior and reduces wear when robot is barely moving
        Rotation2d angle = (Math.abs(desiredState.speedMetersPerSecond) <= (Constants.SwerveConstants.maxSpeed * 0.01))
            ? lastAngle : desiredState.angle;
        // Set the angle motor to rotate to the target angle (position control)
        angleController.setSetpoint(angle.getDegrees(), ControlType.kPosition);
        // Update lastAngle for next optimization cycle
        lastAngle = angle; 
    }


    private Rotation2d getAngle(){
        return Rotation2d.fromDegrees(integratedAngleEncoder.getPosition());
    }
    

    public void pointInDirection(double degrees){
        angleController.setSetpoint(degrees, ControlType.kPosition);
        lastAngle = Rotation2d.fromDegrees(degrees);
    }
    
    private void configAngleMotor(){
        SparkMaxConfig sparkMaxConfig = new SparkMaxConfig();
        // Factory reset is commented out - only needed if motor needs to be reset to defaults
        //angleMotor.restoreFactoryDefaults();
        
        // Limit CAN bus usage to position data only (reduces CAN bus traffic)
        CANSparkUtil.setSparkBusUsage(sparkMaxConfig, Usage.kPositionOnly);
        // Set maximum current draw to protect motor and wiring
        sparkMaxConfig.smartCurrentLimit(SwerveConstants.angleContinuousCurrentLimit);
        // Set motor direction (may need to be inverted based on physical mounting)
        sparkMaxConfig.inverted(angleInvert);
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

    private void resetToAbsolute() {
        double absolutePosition = getCanCoder().getDegrees() - angleOffset.getDegrees();
        integratedAngleEncoder.setPosition(absolutePosition); //may need to change 

      }

    //Re-synchronizes the integrated encoder with the CANcoder using the stored offset.
    public void resyncToAbsolute(){
        resetToAbsolute();
    }

    public void saveCanCoderZero(){
        saveCanCoderOffset(Rotation2d.fromDegrees(0.0));
    }

    public void saveCanCoderOffset(Rotation2d desiredAngle){
        double absolute = getCanCoder().getDegrees();
        double newOffset = normalizeDegrees(absolute - desiredAngle.getDegrees());
        Preferences.setDouble(angleOffsetPreferenceKey, newOffset);
        angleOffset = Rotation2d.fromDegrees(newOffset);
        resetToAbsolute();
    }

    private double normalizeDegrees(double degrees){
        double normalized = degrees % 360.0;
        if (normalized < 0){
            normalized += 360.0;
        }
        return normalized;
    }

    private void configDriveMotor(){
        SparkFlexConfig sparkFlexConfig = new SparkFlexConfig();
        // Factory reset is commented out - only needed if motor needs to be reset to defaults
        //driveMotor.restoreFactoryDefaults();
        
        // Use full CAN bus bandwidth for drive motor (needs velocity and position data frequently)
        CANSparkUtil.setSparkBusUsage(sparkFlexConfig, Usage.kAll);
        // Set maximum current draw to protect motor and wiring
        sparkFlexConfig.smartCurrentLimit(SwerveConstants.driveContinuousCurrentLimit);
        // Set motor direction (may need to be inverted based on physical mounting)
        sparkFlexConfig.inverted(driveInvert);
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
