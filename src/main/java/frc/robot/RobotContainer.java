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
import frc.robot.Auto.DriveTestAuto;
import frc.robot.Auto.EightLemonAuto;
import frc.robot.Auto.TrenchToDepotAuto;
import frc.robot.Auto.CenterToDepotAuto;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Command.AutoAlign;
import frc.robot.Command.TeleopSwerve;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;
import edu.wpi.first.wpilibj.GenericHID;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import edu.wpi.first.wpilibj2.command.button.POVButton;


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

  //ShooterSubsystem for shooter
  private final ShooterSubsystem m_shooter = new ShooterSubsystem();
  
  public RobotContainer() {
    AutoConstants.initDashboard();
    configureBindings();
  }

  private void configureBindings() {

    // Y Button = Zero gyro (reset heading to 0° or 180° based on alliance)
    driveController.button(Button.kY.value).onTrue(new InstantCommand(() -> m_drive.zeroGyro(), m_drive));

    //Back button (view) = resync integrated angle encoders to CANcoders (DISABLED ONLY)
    driveController.button(Button.kBack.value).onTrue(new InstantCommand(()->m_drive.resyncModuleEncoders(), m_drive));
    //Start Button (menu) = save current module offsets (DISABLED ONLY, wheels must be straight)
    driveController.button(Button.kStart.value).onTrue(new InstantCommand(()->m_drive.saveModuleOffsets(), m_drive));
   


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

  private double getSpeedMultiplier(){
    // getHID() accesses the underlying XboxController to read button states directly.
    // CommandXboxController doesn't provide a method for stick button presses, so we use
    // the HID (Human Interface Device) object's getRawButton() method instead.
    return driveController.getHID().getRawButton(Button.kLeftStick.value)? 0.85: 1;
  }
  
  public Command getAutonomousCommand() {
    AutoConstants.AutoMode selected = AutoConstants.getSelectedAutoMode();

    return switch (selected) {
      case None -> Commands.none();
      case DriveTestAuto -> new DriveTestAuto(m_drive);
      case EightLemonAuto -> new EightLemonAuto(m_drive, m_shooter, m_intake);
      case TrenchToDepotAuto -> new TrenchToDepotAuto(m_drive);
      case CenterToDepotAuto -> new CenterToDepotAuto(m_drive);
      default -> Commands.none();
    };
  }

  public SwerveSubsystem getDriveSubsystem() {
    return m_drive;
  }

}
