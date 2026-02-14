// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;


import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.XboxController.Axis;
import edu.wpi.first.wpilibj.XboxController.Button;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Command.AutoAlign;
import frc.robot.Command.TeleopSwerve;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;

public class RobotContainer {

 
  // Xbox controller configuration for drive controls
  private final CommandXboxController driveController = new CommandXboxController(0); 
  // Xbox controller configuration for helms controls
  private final CommandXboxController helmsController = new CommandXboxController(1);

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

  // IntakeSubsystem for intake
  private final IntakeSubsystem m_intake = new IntakeSubsystem();

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


    // SHOOTER CONTROLLER
    helmsController.axisGreaterThan(Axis.kRightTrigger.value, 0.1)
        .whileTrue(Commands.startEnd(
            () -> m_shooter.runShooter(true),
            () -> m_shooter.runShooter(false),
            m_shooter));

    m_shooter.setDefaultCommand(
        Commands.run(
            () -> {
              double feederAxis = helmsController.getRawAxis(Axis.kRightY.value);
              double feederSpeed = 0.0;
              if (Math.abs(feederAxis) > 0.1) {
                feederSpeed = -Math.signum(feederAxis) * ShooterConstants.FEEDER_SPEED;
              }
              m_shooter.runFeederSpeed(feederSpeed);
            },
            m_shooter));

    helmsController.button(Button.kB.value).onTrue(new InstantCommand(() -> m_shooter.setHoodAngle(ShooterSubsystem.HoodAngle.LOW), m_shooter));
    helmsController.button(Button.kY.value).onTrue(new InstantCommand(() -> m_shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), m_shooter));

    
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

    //INTAKE
    // raises the intake using the A button on the helms controller
    m_intake.setDefaultCommand(
        new RunCommand(
            () -> m_intake.setIntakePower(-MathUtil.applyDeadband(helmsController.getLeftY(), 0.1)),
            m_intake));
    
    
    //lowers the intake using the A button on the helms controller
    helmsController.button(Button.kA.value).onTrue(
       new InstantCommand(() -> m_intake.raiseIntake(), m_intake)
    );

    // lowers the intake using the X button on the helms controller
    helmsController.button(Button.kX.value).onTrue(
        new InstantCommand(() -> m_intake.lowerIntake(), m_intake)
    );
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
   * Gets the swerve subsystem instance.
   * Used for test code that needs access to the swerve subsystem.
   *
   * @return The SwerveSubsystem instance
   */
  public SwerveSubsystem getSwerveSubsystem() {
    return m_drive;
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