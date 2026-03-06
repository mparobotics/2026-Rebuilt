// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
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

  private final RelativeEncoder intakeArmEncoder = intakeArmMotor.getEncoder();

  private final PIDController intakeArmController = new PIDController(
    IntakeConstants.INTAKE_ARM_kP,
    IntakeConstants.INTAKE_ARM_kI,
    IntakeConstants.INTAKE_ARM_kD);
  
  private double intakeArmTargetDeg = IntakeConstants.INTAKE_ARM_RAISED_POSITION;
  private boolean intakeArmActive = false;

  private boolean intakeOn = false;
  private boolean intakeUp = true;

  public enum IntakeArmAngle {
    DOWN,
    UP
  }

  /** Creates a new IntakeSubsystem. */
  public IntakeSubsystem() {
    SparkMaxConfig intakeConfig = new SparkMaxConfig();
      intakeConfig.inverted(false);
      intakeConfig.idleMode(IdleMode.kCoast);

    intakeMotor.configure(intakeConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);

    SparkMaxConfig intakeArmConfig = new SparkMaxConfig();
      intakeArmConfig.inverted(true);
      intakeArmConfig.idleMode(IdleMode.kBrake);
      
      intakeArmConfig.encoder.positionConversionFactor(360.0 / IntakeConstants.GEAR_RATIO);

    intakeArmMotor.configure(intakeArmConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    //On enable, assume the arm starts raised at 90 degrees
    intakeArmEncoder.setPosition(IntakeConstants.INTAKE_ARM_RAISED_POSITION);
    intakeArmController.setTolerance(IntakeConstants.INTAKE_ARM_TOLERANCE_DEG);
    intakeArmTargetDeg = IntakeConstants.INTAKE_ARM_RAISED_POSITION;
    intakeArmActive = false;
  }

  public void toggleIntake() {
    if (!intakeOn) {
      intakeOn = false;
      intakeMotor.set(IntakeConstants.INTAKE_SPEED);
    }
    else {
      intakeOn = true;
      intakeMotor.set(0);
    }
  }
  
  public void setIntakePower(double power) {
    double clampedPower = Math.max(-1.0, Math.min(1.0, power));
    intakeOn = Math.abs(clampedPower) > 0.0;
    intakeMotor.set(clampedPower * IntakeConstants.INTAKE_SPEED);
  }
  

  public void setIntakeArmAngle(IntakeArmAngle angle){
    switch (angle){
      case DOWN:
      intakeArmTargetDeg = IntakeConstants.INTAKE_ARM_LOWERED_POSITION;
      intakeUp = false;
      break;
      case UP:
      default:
      intakeArmTargetDeg = IntakeConstants.INTAKE_ARM_RAISED_POSITION;
      intakeUp = true;
      break;
    }

    intakeArmTargetDeg = Math.max(
      IntakeConstants.INTAKE_ARM_MIN_DEG, 
      Math.min(IntakeConstants.INTAKE_ARM_MAX_DEG, intakeArmTargetDeg));
    intakeArmController.reset();
    intakeArmActive = true;
  }

  public void raiseIntake() {
    setIntakeArmAngle (IntakeArmAngle.UP);
  }

  public void lowerIntake() {
    setIntakeArmAngle(IntakeArmAngle.DOWN);
  }
  
  public void moveIntake() {
    if (intakeUp){
      lowerIntake();
    }
    else {
      raiseIntake();
    }
  }

  public double getArmPositionDeg() {
    return intakeArmEncoder.getPosition();
  }

  @Override
  public void periodic() {
    double currentDeg = getArmPositionDeg();

    SmartDashboard.putNumber("IntakeArm/TargetDeg", intakeArmTargetDeg);
    SmartDashboard.putNumber("IntakeArm/PostionDeg", currentDeg);
    SmartDashboard.putBoolean("IntakeArm/Active", intakeArmActive);

    if (intakeArmActive){
      double output = intakeArmController.calculate(currentDeg, intakeArmTargetDeg);
      output = Math.max(IntakeConstants.INTAKE_ARM_MIN_OUTPUT, Math.min(IntakeConstants.INTAKE_ARM_MAX_OUTPUT, output));

    if (intakeArmController.atSetpoint()){
      intakeArmMotor.set(0.0);
      intakeArmActive = false;
    } else {
      intakeArmMotor.set(output);
    }
  } else{
      intakeArmMotor.set(0.0);
    }
  }
}
