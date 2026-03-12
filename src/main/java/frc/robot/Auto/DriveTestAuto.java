// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Subsystems.SwerveSubsystem;

/*
public class DriveTestAuto extends SequentialCommandGroup {
  public DriveTestAuto (SwerveSubsystem drive) {
        addCommands(
            new InstantCommand(() -> drive.drive(0.5,0,0, false), drive),
            Commands.waitSeconds(2),
            new InstantCommand(() -> drive.drive(0,0,0, false), drive)
        );
    }
}
*/


public class DriveTestAuto extends SequentialCommandGroup {
  public DriveTestAuto (SwerveSubsystem drive){
    addCommands(
      drive.startAutoAt(3.628, 7.434, 0.864),
      drive.autoDrive("Trench"),
      drive.autoDrive("Neutral")
    );
  }
}

