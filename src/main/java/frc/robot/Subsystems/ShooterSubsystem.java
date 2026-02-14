// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;

import frc.robot.Constants.ShooterConstants;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;

import frc.robot.Constants.ShooterConstants;

public class ShooterSubsystem extends SubsystemBase {

  public boolean isShooterActive = false; //Shooter True

  SparkMax shooterMotor = new SparkMax(ShooterConstants.SHOOTER_ID, MotorType.kBrushless);
  SparkMax feederMotor = new SparkMax(ShooterConstants.FEEDER_ID, MotorType.kBrushless);
  SparkMax hoodMotor = new SparkMax(ShooterConstants.HOOD_ID, MotorType.kBrushless);
  
  private final PIDController hoodController = new PIDController(
      ShooterConstants.HOOD_KP,
      0.0,
      0.0
  );
  private double hoodTargetPosition = ShooterConstants.HOOD_ANGLE_LOW;
  private boolean hoodActive = false;

  public enum HoodAngle {
    LOW,
    HIGH
  }

  //LightSubsystem m_lightSubsystem = new LightSubsystem();

  public ShooterSubsystem() {
    SparkMaxConfig shootConfig = new SparkMaxConfig();
      shootConfig.inverted(false);
      shootConfig.idleMode(IdleMode.kCoast);

    SparkMaxConfig feedConfig = new SparkMaxConfig();
      feedConfig.inverted(false);
      feedConfig.idleMode(IdleMode.kBrake);

    SparkMaxConfig hoodConfig = new SparkMaxConfig();
      hoodConfig.inverted(false);
      hoodConfig.idleMode(IdleMode.kBrake);

    shooterMotor.configure(shootConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    feederMotor.configure(feedConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    hoodMotor.configure(hoodConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);

    hoodController.setTolerance(ShooterConstants.HOOD_TOLERANCE);
  }

    public void toggleShooter() {
      if (!isShooterActive) {
        isShooterActive = true;
        shooterMotor.set(ShooterConstants.SHOOTER_SPEED);
      }
      else {
        isShooterActive = false;
        shooterMotor.set(0);
      }
    }


    public void runShooter(boolean shooterOn) {
      if (shooterOn) {
        isShooterActive = true;
        shooterMotor.set(ShooterConstants.SHOOTER_SPEED);
      } else {
        isShooterActive = false;
        shooterMotor.set(0);
      }
    }


    public void runFeeder(boolean feederOn){
      runFeederSpeed(feederOn ? ShooterConstants.FEEDER_SPEED : 0);
    }

    public void runFeederSpeed(double speed) {
      feederMotor.set(speed);
    }

    public void setHoodAngle(HoodAngle angle) {
      switch (angle) {
        case LOW:
          hoodTargetPosition = ShooterConstants.HOOD_ANGLE_LOW;
          break;
        case HIGH:
          hoodTargetPosition = ShooterConstants.HOOD_ANGLE_HIGH;
          break;
        default:
          hoodTargetPosition = ShooterConstants.HOOD_ANGLE_HIGH;
      }
      hoodController.reset();
      hoodActive = true;
    }

    public double getHoodPosition() {
      return hoodMotor.getEncoder().getPosition();
    }

    public Command autoShoot() {
        return new InstantCommand(() -> {
          if (!isShooterActive) {
            isShooterActive = true;
            shooterMotor.set(ShooterConstants.SHOOTER_SPEED);
          }
          else {
            isShooterActive = false;
            shooterMotor.set(0);
          }
      }, this);
    }

    public Command autoFeed() {
      return new InstantCommand(() -> runFeeder(true), this);
    }

    public Command autoStopFeed() {
      return new InstantCommand(() -> runFeeder(false), this);
    }


  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    SmartDashboard.putBoolean("Is Shooter Active", isShooterActive);
    SmartDashboard.putNumber("Hood Target Position", hoodTargetPosition);
    SmartDashboard.putNumber("Hood Position", getHoodPosition());

    
    if (hoodActive) {
      double output = hoodController.calculate(getHoodPosition(), hoodTargetPosition);
      output = Math.max(-ShooterConstants.HOOD_MAX_OUTPUT, Math.min(ShooterConstants.HOOD_MAX_OUTPUT, output));

      if (hoodController.atSetpoint()) {
        hoodMotor.set(0);
        hoodActive = false;
      } else {
        hoodMotor.set(output);
      }
    } else {
      hoodMotor.set(0);
    }
  }
}