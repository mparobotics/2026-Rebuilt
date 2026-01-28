// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;

import frc.robot.Constants.shooterConstants;

public class ShooterSubsystem extends SubsystemBase {

  
  public final double motorSpeed = 1; //Placeholder speed
  private boolean isShooterActive = false; //Shooter True

  SparkMax shooterMotor = new SparkMax(shooterConstants.SHOOTER_ID, MotorType.kBrushless);
  SparkMax feederMotor = new SparkMax(shooterConstants.FEEDER_ID, MotorType.kBrushless);

  LightSubsystem m_lightSubsystem = new LightSubsystem();

  public ShooterSubsystem() {
    SparkMaxConfig shootConfig = new SparkMaxConfig();
      shootConfig.inverted(false);
      shootConfig.idleMode(IdleMode.kCoast);

    SparkMaxConfig feedConfig = new SparkMaxConfig();
      feedConfig.inverted(false);
      feedConfig.idleMode(IdleMode.kBrake);

    shooterMotor.configure(shootConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    feederMotor.configure(feedConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
  }

    public Command RunFeeder() {
      return runOnce(
          () -> {
            feederMotor.set(motorSpeed);
          });
    }
    public Command StopFeeder() {
      return runOnce(
          () -> {
            feederMotor.set(0);
          });
    }

    public Command ShootingControlCommand() {
      if (isShooterActive = false) {
        isShooterActive = true;
        return runOnce(
          () -> {
            shooterMotor.set(motorSpeed);
          }
        );
      } else {
        isShooterActive = false;
        return runOnce(
          () -> {
            feederMotor.set(0);
          }
        );
      }
    }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    SmartDashboard.putBoolean("Is Shooter Active", isShooterActive);
  }
}