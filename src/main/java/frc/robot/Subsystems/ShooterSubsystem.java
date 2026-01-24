// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import frc.robot.Constants.shooterConstants;

public class ShooterSubsystem extends SubsystemBase {

  
  public final double motorSpeed = 0.0; //Placeholder speed
  private boolean isFeederActive = false; //Feeder True

  SparkMax shooterMotor = new SparkMax((int) shooterConstants.SHOOTER_ID, MotorType.kBrushless);
  SparkMax feederMotor = new SparkMax(shooterConstants.FEEDER_ID, MotorType.kBrushless);

  LightSubsystem m_lightSubsystem = new LightSubsystem();

  public ShooterSubsystem() {}

    public Command ShootingControlCommand() {
      m_lightSubsystem.blink(255, 139, 0, 0.5); // Orange blink while shooting
      return runOnce(
          () -> {
            shooterMotor.set(MathUtil.applyDeadband(motorSpeed, 0.1));
          });
    }

    public Command FeederControlCommand() {
      if (!isFeederActive) {
        m_lightSubsystem.setAll(255, 0, 0);
        isFeederActive = true;
        return run(
          () -> {
            feederMotor.set(MathUtil.applyDeadband(motorSpeed, 0.1));
          }
        );
      } else {
        m_lightSubsystem.off();
        isFeederActive = false;
        return run(
          
          () -> {
            feederMotor.set(MathUtil.applyDeadband(0, 0.1));
          }
        );
      }
    }
  @Override
  public void periodic() {
    // This method will be called once per scheduler run
  }
}