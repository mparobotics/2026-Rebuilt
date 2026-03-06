// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.UnusedAuto;

import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.IntakeConstants;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

public class OffsetDepotAuto extends SequentialCommandGroup {

  public OffsetDepotAuto(SwerveSubsystem drive, IntakeSubsystem intake, ShooterSubsystem shooter) {
    addCommands(
      drive.startAutoAt(3.555, 6.40, 0),
      drive.autoDrive("GoToDepot"),
      new InstantCommand(() -> intake.raiseIntake(), intake),
      new InstantCommand(() -> intake.setIntakePower(IntakeConstants.INTAKE_SPEED), intake),
      Commands.waitSeconds(3),
      new InstantCommand(() -> intake.setIntakePower(0), intake),
      drive.autoDrive("ShootAfterDepot"),
      new InstantCommand(() -> shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), shooter),
      new InstantCommand(() -> shooter.AutoToggleShoot(false)),
      Commands.waitSeconds(3),
      new InstantCommand(() -> shooter.AutoToggleKickIndex(false)),
      new InstantCommand(() -> intake.lowerIntake(), intake),
      new InstantCommand(() -> intake.raiseIntake(), intake),
      Commands.waitSeconds(1),
      new InstantCommand(() -> intake.lowerIntake(), intake),
      new InstantCommand(() -> intake.raiseIntake(), intake),
      Commands.waitSeconds(4),
      new InstantCommand(() -> shooter.AutoToggleShoot(true))
    );
  }
}
