// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.ParallelCommandGroup;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

// NOTE:  Consider using this command inline, rather than writing a subclass.  For more
// information, see:
// https://docs.wpilib.org/en/stable/docs/software/commandbased/convenience-features.html
public class LeftNeutralAuto extends SequentialCommandGroup {
  /** Creates a new LeftNeutralAuto. */
  SwerveSubsystem m_drive;
  IntakeSubsystem m_intake;
  ShooterSubsystem m_shooter;
  public LeftNeutralAuto(SwerveSubsystem drive, IntakeSubsystem intake, ShooterSubsystem shooter) {
    m_drive = drive;
    m_intake = intake;
    m_shooter = shooter; 
    // Add your commands in the addCommands() call, e.g.
    // addCommands(new FooCommand(), new BarCommand());
    addCommands(
      // lower intake
      new ParallelCommandGroup(
        m_drive.startAutoAt(3.549, 7.253, 11.000),
        Commands.runOnce(() -> m_intake.lowerIntake(), m_intake)
      ),
      
      // drive to scoring position, raise hood, spin up shooter
      new ParallelCommandGroup(
      Commands.run(() -> m_drive.autoDrive("neutral start-shoot"), m_drive),
      Commands.runOnce(() -> m_shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), m_shooter),
        Commands.run(() -> m_shooter.setShooterSpeed(ShooterConstants.SHOOTER_SPEED), m_shooter)
        .until(() -> m_shooter.getShooterVelocityRpm() >= ShooterConstants.SHOOTER_READY_RPM)
        .withTimeout(2.0)
      ),

      // shoot
      new ParallelCommandGroup(
          Commands.run(() -> m_shooter.runKicker(true), m_shooter),
          Commands.run(() -> m_shooter.runIndexer(true), m_shooter)
        ),

      new WaitCommand(3),

      //stop shooting, put down hood, start intake
      new ParallelCommandGroup(
          Commands.run(() -> m_shooter.runKicker(false), m_shooter),
          Commands.run(() -> m_shooter.runIndexer(false), m_shooter),
          Commands.run(() -> m_shooter.setHoodAngle(ShooterSubsystem.HoodAngle.LOW), m_shooter),
          Commands.run(() -> m_intake.runIntake(true), m_intake)
        ),

      // intake from middle
      Commands.run(() -> m_drive.autoDrive("neutral shoot-collect-shoot"), m_drive),

      // raise hood, then shoot
      Commands.runOnce(() -> m_shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), m_shooter),

      new ParallelCommandGroup(
        Commands.run(() -> m_shooter.runKicker(true), m_shooter),
          Commands.run(() -> m_shooter.runIndexer(true), m_shooter) 
      )
    );
  }
}
