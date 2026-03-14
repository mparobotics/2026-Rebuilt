// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;


public class RightNeutralZoneAuto extends SequentialCommandGroup {

  private static final double INTAKE_POWER = -1.0;

  public RightNeutralZoneAuto(SwerveSubsystem drive, IntakeSubsystem intake) {
    final double[] startYawRad = new double[1];

    addCommands(
      Commands.runOnce(intake::lowerIntake, intake),

      //Drive backwards 3.4m
      new InstantCommand(() -> drive.drive(-3.4, 0, 0, false), drive),
      Commands.waitSeconds(1),
      new InstantCommand(() -> drive.drive(0, 0, 0, false), drive),

      //Turn 90 degrees right
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(-90.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(-90.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),
      
      //Drive forward 3m
      Commands.runOnce(() -> intake.setIntakePower(INTAKE_POWER), intake),
      new InstantCommand(() -> drive.drive(3, 0, 0, false), drive),
      Commands.waitSeconds(1),
      new InstantCommand(() -> drive.drive(0, 0, 0, false), drive),
      
      // Turn 90 degrees left
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(90.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(90.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),
      
      //Drive forward 1m
      Commands.runOnce(() -> intake.setIntakePower(INTAKE_POWER), intake),
      new InstantCommand(() -> drive.drive(1, 0, 0, false), drive),
      Commands.waitSeconds(1),
      new InstantCommand(() -> drive.drive(0, 0, 0, false), drive),
      
      // Turn 90 degrees left
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(90.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(90.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),
      
      //Drive forward 3m
      new InstantCommand(() -> drive.drive(3, 0, 0, false), drive),
      Commands.waitSeconds(1),
      new InstantCommand(() -> drive.drive(0, 0, 0, false), drive),

      //Stop intake
      Commands.runOnce(() -> intake.setIntakePower(0.0), intake),
      Commands.runOnce(() -> drive.drive(0, 0, 0,false), drive),

      // Turn 90 degrees right
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(-90.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(-90.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),
      
      //Drive forward
      new InstantCommand(() -> drive.drive(1, 0, 0, false), drive),
      Commands.waitSeconds(1),
      new InstantCommand(() -> drive.drive(0, 0, 0, false), drive),

      // Turn 20 degrees right
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(-20.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(-20.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        })



    );
  }
}
