// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.IntakeConstants;

import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkLowLevel.MotorType;

public class IntakeSubsystem extends SubsystemBase {

  private final SparkMax intakeMotor = new SparkMax(IntakeConstants.INTAKE_ID, MotorType.kBrushless);

  private boolean intakeOn = false;
  /** Creates a new IntakeSubsystem. */
  public IntakeSubsystem() {}

  public void toggleIntake() {
    intakeOn = !intakeOn;

    if (intakeOn) {
      intakeMotor.set(IntakeConstants.INTAKE_SPEED);
    }
    else {
      intakeMotor.set(0);
    }
  }
  
  @Override
  public void periodic() {
    // This method will be called once per scheduler run
  }
}
