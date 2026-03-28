// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

public class ShootEightAuto extends SequentialCommandGroup {

  public ShootEightAuto(SwerveSubsystem drive, IntakeSubsystem intake, ShooterSubsystem shooter) {
    final double[] startYawRad = new double[1];
    addCommands(
      Commands.runOnce(() -> shooter.setHoodAngle(ShooterSubsystem.HoodAngle.LOW), shooter),
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
