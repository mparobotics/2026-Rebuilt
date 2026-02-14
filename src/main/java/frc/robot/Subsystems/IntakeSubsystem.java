// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import edu.wpi.first.math.controller.ArmFeedforward;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.TunableControllers.TunableArmFeedforward;
import frc.robot.Constants.IntakeConstants;

import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;

public class IntakeSubsystem extends SubsystemBase {

  private final SparkMax intakeMotor = new SparkMax(IntakeConstants.INTAKE_ID, MotorType.kBrushless);
  private final SparkMax intakeArmMotor = new SparkMax(IntakeConstants.INTAKE_ARM_ID, MotorType.kBrushless);

  private RelativeEncoder intakeArmEncoder = intakeArmMotor.getEncoder();

  private PIDController intakeArmPID = new PIDController(IntakeConstants.INTAKE_ARM_kP, IntakeConstants.INTAKE_ARM_kI, IntakeConstants.INTAKE_ARM_kD);

  private ArmFeedforward intakeArmFeedForward = new ArmFeedforward(0,0,0);

  public double targetPosition;

  private boolean intakeOn = false;
  private boolean intakeUp = true;

  /** Creates a new IntakeSubsystem. */
  public IntakeSubsystem() {
    SparkMaxConfig intakeConfig = new SparkMaxConfig();
      intakeConfig.inverted(false);
      intakeConfig.idleMode(IdleMode.kCoast);

    intakeMotor.configure(intakeConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);

    SparkMaxConfig intakeArmConfig = new SparkMaxConfig();
      intakeArmConfig.inverted(false);
      intakeArmConfig.idleMode(IdleMode.kBrake);
      intakeArmConfig.encoder.positionConversionFactor(360/IntakeConstants.GEAR_RATIO);

    intakeArmMotor.configure(intakeArmConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    intakeArmEncoder.setPosition(IntakeConstants.INTAKE_ARM_RAISED_POSITION);
    targetPosition = IntakeConstants.INTAKE_ARM_RAISED_POSITION; // start with arm raised
  }

  public void toggleIntake() {
    if (!intakeOn) {
      intakeOn = true;
      intakeMotor.set(IntakeConstants.INTAKE_SPEED);
    }
    else {
      intakeOn = false;
      intakeMotor.set(0);
    }
  }
  
  public void setIntakePower(double power) {
    double clampedPower = Math.max(-1.0, Math.min(1.0, power));
    intakeOn = Math.abs(clampedPower) > 0.0;
    intakeMotor.set(clampedPower * IntakeConstants.INTAKE_SPEED);
  }
  

  public void setTargetPosition(double position) {
    targetPosition = Math.max(IntakeConstants.INTAKE_ARM_MINIMUM, Math.min(IntakeConstants.INTAKE_ARM_MAXIMUM, position));
  }

  public void raiseIntake() {
    setTargetPosition(IntakeConstants.INTAKE_ARM_RAISED_POSITION);
    intakeUp = true;
  }

  public void lowerIntake() {
    setTargetPosition(IntakeConstants.INTAKE_ARM_LOWERED_POSITION);
    intakeUp = false;
  }
  
  public void moveIntake() {
    if (intakeUp){
      lowerIntake();
    }
    else {
      raiseIntake();
    }
  }

  public double getArmPosition() {
    return intakeArmEncoder.getPosition() * 360;
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    double PIDOutput = intakeArmFeedForward.calculate(
      Units.degreesToRadians(intakeArmEncoder.getPosition()),0)
      + intakeArmPID.calculate(getArmPosition(), targetPosition);
    intakeArmMotor.set(PIDOutput);
  }
}
