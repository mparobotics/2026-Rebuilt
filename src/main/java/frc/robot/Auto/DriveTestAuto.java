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

public class DriveTestAuto extends SequentialCommandGroup {
  public DriveTestAuto(SwerveSubsystem drive, IntakeSubsystem intake, ShooterSubsystem shooter) {
    addRequirements(drive, intake, shooter);

    addCommands(
      drive.startAutoAt(1.984, 7.199, -90),
      drive.autoDrive("Path")
    );
  }
}
