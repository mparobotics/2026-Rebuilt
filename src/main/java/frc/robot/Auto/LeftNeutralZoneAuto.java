// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Subsystems.SwerveSubsystem;


public class LeftNeutralZoneAuto extends SequentialCommandGroup {
  public LeftNeutralZoneAuto (SwerveSubsystem drive) {
        addCommands(
            new InstantCommand(() -> drive.drive(0.5,0,0, false), drive),
            Commands.waitSeconds(2),
            new InstantCommand(() -> drive.drive(0,0,0, false), drive)
        );
    }
}

