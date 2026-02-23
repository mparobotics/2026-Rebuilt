// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

//With PATHPLANNER
public class EightLemonAuto extends SequentialCommandGroup {
  public EightLemonAuto (SwerveSubsystem drive, ShooterSubsystem shooter, IntakeSubsystem intake){
    addCommands(
      drive.startAutoAt(3.53, 7.13, -130.45),
      drive.autoDrive("8FuelPath")
    );
  }
}
