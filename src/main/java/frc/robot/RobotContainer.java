// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;


import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.XboxController.Axis;
import edu.wpi.first.wpilibj.XboxController.Button;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.Command.AutoAlign;
import frc.robot.Command.TeleopSwerve;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;

public class RobotContainer {

 
  // Xbox controller configuration for drive controls
  private final CommandXboxController driveController = new CommandXboxController(0); 
  // Left Stick Y = Forward/backward motion
  private final int translationAxis = XboxController.Axis.kLeftY.value;
  // Left Stick X = Side-to-side motion
  private final int strafeAxis = XboxController.Axis.kLeftX.value;
  // Right Stick X = Rotation/turning motion
  private final int rotationAxis = XboxController.Axis.kRightX.value;
  // Left Bumper = Toggle robot-oriented mode (default is field-oriented)
  private final Trigger robotCentric = new Trigger(driveController.leftBumper());

  // SwerveSubsystem instance for the drive subsystem
  private final SwerveSubsystem m_drive = new SwerveSubsystem();

  private final ShooterSubsystem m_shooter = new ShooterSubsystem();
  
  /**
   * Constructs the RobotContainer. Creates subsystems (which configure themselves)
   * and sets up command bindings to map controller inputs to commands.
   */
  public RobotContainer() {
    configureBindings();
  }

  /**
   * Configures command bindings for controller inputs.
   * Maps buttons and triggers to commands and sets the default drive command.
   */
  private void configureBindings() {

    driveController.button(Button.kX.value).onTrue(new InstantCommand(() -> m_drive.zeroGyro(), m_drive));
    
    driveController.button(Button.kY.value).onTrue(new InstantCommand(() -> m_shooter.toggleShooter(), m_shooter));

    driveController.button(Button.kB.value).whileTrue(new InstantCommand( () -> m_shooter.runFeeder(true), m_shooter));
    driveController.button(Button.kB.value).onFalse(new InstantCommand( () -> m_shooter.runFeeder(false), m_shooter));

    // Left Trigger = Auto-align to left scoring position
    driveController.axisGreaterThan(Axis.kLeftTrigger.value, 0.1).whileTrue(new AutoAlign(m_drive, true));
    // Right Trigger = Auto-align to right scoring position
    driveController.axisGreaterThan(Axis.kRightTrigger.value, 0.1).whileTrue(new AutoAlign(m_drive, false));

    // Default command runs continuously when no other command requires the subsystem.
    // It automatically pauses when commands like AutoAlign take control, then resumes
    // when they finish.
    m_drive.setDefaultCommand(
        new TeleopSwerve(
            // SwerveSubsystem - The drive subsystem to control
            m_drive,
            // translationSupplier - Forward/backward speed
            () -> -getSpeedMultiplier() * driveController.getRawAxis(translationAxis) * 0.5,
            // strafeSupplier - Side-to-side speed
            () -> -getSpeedMultiplier() * driveController.getRawAxis(strafeAxis) * 0.5,
            // rotationSupplier - Rotation speed
            () -> -driveController.getRawAxis(rotationAxis) * 0.5,
            // robotCentricSupplier - Robot-oriented (true) vs field-oriented (false)
            () -> robotCentric.getAsBoolean(),
            // isAutoAlignSupplier - Auto-align active flag
            () -> driveController.getRightTriggerAxis() > 0.1
        ));

  }

  /**
   * Determines if the driver has requested speed reduction for precise positioning
   * or delicate tasks.
   * @return Speed multiplier
   */
  private double getSpeedMultiplier(){
    // getHID() accesses the underlying XboxController to read button states directly.
    // CommandXboxController doesn't provide a method for stick button presses, so we use
    // the HID (Human Interface Device) object's getRawButton() method instead.
    return driveController.getHID().getRawButton(Button.kLeftStick.value)? 0.7: 1;
  }

  /**
   * Use this to pass the autonomous command to the main {@link Robot} class.
   *
   * @return the command to run in autonomous
   */
  public Command getAutonomousCommand() {
    return Commands.print("No autonomous command configured");
  }


}