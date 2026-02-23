// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.SwerveSubsystem;

public class TrenchToDepotAuto extends SequentialCommandGroup {
  public TrenchToDepotAuto (SwerveSubsystem drive){
    final double[] startYawRad = new double[1];
      addCommands(
        drive.startAutoAt(4.61, 6.9, 0),
        new InstantCommand(()->drive.drive(-0.5,0,0, false), drive),
        Commands.waitSeconds(2),
        new InstantCommand(()->drive.drive(0,0,0, false),drive),

      //Turn 40 degrees left (counterclockwise)
        Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(40.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(40.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),
        new InstantCommand(() -> drive.drive(0,0,0, false), drive),
        Commands.waitSeconds(2),

      //Turn back 40 degrees right (clockwise) to the starting heading
        Commands.run(() -> {
          double targetYawRad = startYawRad[0];
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(
              errorRad * 4.0,
              -SwerveConstants.maxAngularVelocity,
              SwerveConstants.maxAngularVelocity);
          drive.drive(0, 0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0];
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),
        new InstantCommand(() -> drive.drive(0,0,0, false), drive),


        //SHOOT


        //Move to the right (infront of the depot)
          new InstantCommand(() -> drive.drive(0, -0.4, 0, false), drive),
          Commands.waitSeconds(2),
          new InstantCommand(() -> drive.drive(0,0,0, false), drive),

        //Turn 180 degrees
        Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(()->{
          double targetYawRad = startYawRad[0] + Math.PI;
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0,omegaRadiansPerSecond, false);
        }, drive).until(()->{
          double targetYawRad = startYawRad[0] + Math.PI;
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),

        //Move forward to the depot
        new InstantCommand(() -> drive.drive(0.7,0,0, false), drive),
        Commands.waitSeconds(2),
        new InstantCommand(() -> drive.drive(0,0,0, false), drive),
        Commands.waitSeconds(2),


        //INTAKE


        // Back up ~0.5m, then turn 180 degrees
        new InstantCommand(() -> drive.drive(-0.5,0,0, false), drive),
        Commands.waitSeconds(1),
        new InstantCommand(() -> drive.drive(0,0,0, false), drive),
        Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(()->{
          double targetYawRad = startYawRad[0] + Math.PI;
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0,omegaRadiansPerSecond, false);
        }, drive).until(()->{
          double targetYawRad = startYawRad[0] + Math.PI;
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),

        new InstantCommand(() -> drive.drive(0.4, 0, 0, false), drive),
        Commands.waitSeconds(2),
        new InstantCommand(() -> drive.drive(0,0,0, false), drive),

        //Turn 40 degrees left (counterclockwise)
        Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(40.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(40.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),
        new InstantCommand(() -> drive.drive(0,0,0, false), drive)

        );
  }
}