// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

// NOTE:  Consider using this command inline, rather than writing a subclass.  For more
// information, see:
// https://docs.wpilib.org/en/stable/docs/software/commandbased/convenience-features.html
public class DepotAuto extends SequentialCommandGroup { 
  public DepotAuto(SwerveSubsystem m_drive, ShooterSubsystem m_shooter, IntakeSubsystem m_intake) {
    addCommands(
      m_drive.startAutoAt(0, 0, 0), // placeholders
      new InstantCommand(() -> m_intake.lowerIntake()),
      new InstantCommand(() -> m_intake.intakeOn()),
      m_drive.autoDrive("intake from depot"),
      new InstantCommand(() -> m_intake.intakeOff()),
      new InstantCommand(() -> m_intake.raiseIntake()),
      new InstantCommand (() -> m_shooter.runShooter(true)),
      new WaitCommand(1.85),
      m_drive.autoDrive("depot-score (close)"),
      new InstantCommand(() -> m_shooter.runFeeder(true))
    );
  }
}
