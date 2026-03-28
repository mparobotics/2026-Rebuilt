// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

public class CenterLemonAuto extends SequentialCommandGroup {

  public CenterLemonAuto(SwerveSubsystem drive, IntakeSubsystem intake, ShooterSubsystem shooter) {
    addCommands(
      new InstantCommand(()->drive.drive(0, 0.4,0, false), drive),
      Commands.waitSeconds(2),
      new InstantCommand(()->drive.drive(0,0,0, false),drive),
      
      Commands.runOnce(() -> shooter.setHoodAngle(ShooterSubsystem.HoodAngle.MED), shooter),
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
