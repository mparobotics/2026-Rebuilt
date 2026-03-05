// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

public class RealLemonAuto extends SequentialCommandGroup {

  public RealLemonAuto(SwerveSubsystem drive, IntakeSubsystem intake, ShooterSubsystem shooter) {
    final double[] startYawRad = new double[1];
    addCommands(
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
        Commands.run(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(25.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadiansPerSecond = MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
          drive.drive(0,0, omegaRadiansPerSecond, false);
        }, drive).until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(25.0);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(3.0);
        }),
      Commands.runOnce(() -> drive.drive(0, 0, 0, false), drive),
      Commands.runOnce(() -> shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), shooter),
      Commands.runOnce(() -> {
        shooter.runIndexer(false);
        shooter.runKicker(false);
      }, shooter),
      Commands.run(() -> shooter.setShooterSpeed(ShooterConstants.SHOOTER_SPEED), shooter)
        .until(() -> shooter.getShooterVelocityRpm() >= ShooterConstants.SHOOTER_READY_RPM)
        .withTimeout(2.0),

      Commands.sequence(
        // Start kicker first, then start indexer 1 second later (kicker keeps running).
        Commands.run(() -> {
          shooter.setShooterSpeed(ShooterConstants.SHOOTER_SPEED);
          shooter.setKickerSpeed(ShooterConstants.KICKER_SPEED);
          shooter.setIndexerSpeed(0.0);
        }, shooter).withTimeout(1.0),
        Commands.run(() -> {
          shooter.setShooterSpeed(ShooterConstants.SHOOTER_SPEED);
          shooter.setKickerSpeed(ShooterConstants.KICKER_SPEED);
          shooter.setIndexerSpeed(ShooterConstants.INDEXER_SPEED);
        }, shooter)
	    )
	  );
	}
}
