// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import frc.robot.Constants.shooterConstants;

public class ShooterSubsystem extends SubsystemBase {

  
  public final double motorSpeed = 0.5; //Placeholder speed
  private boolean isFeederActive = false; //Feeder True

  SparkMax shooterMotor = new SparkMax((int) shooterConstants.SHOOTER_ID, MotorType.kBrushless);
  SparkMax feederMotor = new SparkMax(shooterConstants.FEEDER_ID, MotorType.kBrushless);

  LightSubsystem m_lightSubsystem = new LightSubsystem();

  public ShooterSubsystem() {}

    public Command ShootingControlCommand() {
      return run(
          () -> {
            shooterMotor.set(motorSpeed);
          });
    }

    public Command FeederControlCommand() {
      if (!isFeederActive) {
        isFeederActive = true;
        return run(
          () -> {
            feederMotor.set(motorSpeed);
          }
        );
      } else {
        isFeederActive = false;
        return run(
          
          () -> {
            feederMotor.set(0);
          }
        );
      }
    }
  @Override
  public void periodic() {
    // This method will be called once per scheduler run
  }
}