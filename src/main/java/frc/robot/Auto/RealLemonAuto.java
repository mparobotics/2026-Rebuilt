// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import frc.robot.Constants.IntakeConstants;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

public class RealLemonAuto extends SequentialCommandGroup {

  public RealLemonAuto(IntakeSubsystem intake, ShooterSubsystem shooter) {
    addCommands(
      new InstantCommand(() -> shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), shooter),
      new InstantCommand(() -> shooter.AutoToggleShootKick(false)),
      Commands.waitSeconds(3),
      new InstantCommand(() -> intake.lowerIntake(), intake),
      new InstantCommand(() -> intake.raiseIntake(), intake)
    );
  }
}
