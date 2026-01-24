// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkLowLevel.MotorType;


public class ShooterSubsystem extends SubsystemBase {

  public final int ID = 0; //Placeholder ID
  public final double motorSpeed = 0.0; //Placeholder speed

  SparkMax shooterMotor = new SparkMax((int) ID, MotorType.kBrushless);

  lightSubsystem m_lightSubsystem = new lightSubsystem();


  public ShooterSubsystem() {}


    public Command ShootingControlCommand() {
      m_lightSubsystem.blink(255, 139, 0, 0.5); // Orange blink while shooting
      return runOnce(
          () -> {
            shooterMotor.set(MathUtil.applyDeadband(motorSpeed, 0.1));
          });
    }
  @Override
  public void periodic() {
    // This method will be called once per scheduler run
  }
}
