// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.XboxController.Axis;
import edu.wpi.first.wpilibj.XboxController.Button;
import edu.wpi.first.cscore.HttpCamera;
import edu.wpi.first.cscore.UsbCamera;
import edu.wpi.first.cscore.VideoSource.ConnectionStrategy;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.Auto.LeftLemonAuto;
import frc.robot.Auto.LeftNeutralZoneAuto1;
import frc.robot.Auto.LeftNeutralZoneAuto2;
import frc.robot.Auto.RightNeutralZoneAuto1;
import frc.robot.Auto.RightLemonAuto;
import frc.robot.Auto.CenterLemonAuto;
import frc.robot.Auto.CenterToDepotAuto;
import frc.robot.Auto.DepotShootingAuto;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.VisionConstants;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Command.AltAutoAlign;
import frc.robot.Command.AutoAlign;
import frc.robot.Command.SimpleAutoAlign;
import frc.robot.Command.TeleopSwerve;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.UnusedAuto.DriveTestAuto;
import frc.robot.UnusedAuto.ShootEightAuto;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.DriverStation;


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

  private boolean lastHelmsRightBumperPressed = false;
  private double helmsRightBumperPressTimestampSec = 0.0;

  private final java.util.Map<String, HttpCamera> limelightCameras = new java.util.HashMap<>();
  private UsbCamera driverCamera;
  
  public RobotContainer() {
    startLimelightStreams();
    startDriverCameraStream();
    configureBindings();
  }

  private void startLimelightStreams() {
    SmartDashboard.putBoolean("Limelight Stream Enabled", VisionConstants.LIMELIGHT_STREAM_ENABLED_DEFAULT);

    if (RobotBase.isSimulation()) {
      return;
    }

    if (!SmartDashboard.getBoolean("Limelight Stream Enabled", VisionConstants.LIMELIGHT_STREAM_ENABLED_DEFAULT)) {
      return;
    }

    for (String limelightName : VisionConstants.LIMELIGHT_NAMES) {
      String url = VisionConstants.getLimelightStreamUrl(limelightName);
      SmartDashboard.putString("Vision/" + limelightName + "/StreamURL", url);

      HttpCamera camera = limelightCameras.computeIfAbsent(limelightName, (name) -> new HttpCamera(name, url));
      camera.setConnectionStrategy(ConnectionStrategy.kKeepOpen);
      CameraServer.startAutomaticCapture(camera);
    }
  }

  private void startDriverCameraStream() {
    SmartDashboard.putBoolean("Driver Camera Enabled", true);

    if (RobotBase.isSimulation()) {
      return;
    }

    if (!SmartDashboard.getBoolean("Driver Camera Enabled", true)) {
      return;
    }

    if (driverCamera != null) {
      return;
    }

    // Microsoft LifeCam HD-3000 (or any USB UVC camera) connected to the roboRIO.
    driverCamera = CameraServer.startAutomaticCapture("DriverCam", 0);
    driverCamera.setConnectionStrategy(ConnectionStrategy.kKeepOpen);
    driverCamera.setResolution(640, 480);
    driverCamera.setFPS(30);
  }

  private void configureBindings() {

    // Y Button = Zero gyro (reset heading to 0° or 180° based on alliance)
    driveController.button(Button.kY.value).onTrue(new InstantCommand(() -> m_drive.zeroGyro(), m_drive));

    //Back button (view) = resync integrated angle encoders to CANcoders (DISABLED ONLY)
    driveController.button(Button.kBack.value).onTrue(new InstantCommand(()->m_drive.resyncModuleEncoders(), m_drive));
    //Start Button (menu) = save current module offsets (DISABLED ONLY, wheels must be straight)
    driveController.button(Button.kStart.value).onTrue(new InstantCommand(()->m_drive.saveModuleOffsets(), m_drive));
    //xLock
    driveController.button(Button.kB.value).whileTrue(Commands.run(() -> m_drive.xLock(), m_drive));

    // SHOOTER CONTROLLER
    m_shooter.setDefaultCommand(
      Commands.run(
        () -> {          
          // Right stick Y controls shooter.
          // Invert so stick-up (negative on Xbox) produces positive motor output.
          double shooterAxis = -MathUtil.applyDeadband(
            helmsController.getRawAxis(Axis.kRightY.value),
            0.1);
          
          m_shooter.setShooterSpeed(shooterAxis * ShooterConstants.SHOOTER_SPEED);
              
          // Right bumper runs the indexer and kicker forward while held.
          // Left bumper runs the indexer and kicker in reverse while held.
          boolean leftBumperPressed = helmsController.getHID().getLeftBumper();
          boolean rightBumperPressed = helmsController.getHID().getRightBumper();
          if (rightBumperPressed && !lastHelmsRightBumperPressed) {
            helmsRightBumperPressTimestampSec = Timer.getFPGATimestamp();
          }

          boolean indexerEnabled =
            rightBumperPressed
              && (Timer.getFPGATimestamp() - helmsRightBumperPressTimestampSec) >= 1.0;

          double kickerSpeed = 0.0;
          double indexerSpeed = 0.0;
          double hopperSpeed = 0.0;

          if (leftBumperPressed) {
            kickerSpeed = -ShooterConstants.KICKER_SPEED;
            indexerSpeed = -ShooterConstants.INDEXER_SPEED;
            hopperSpeed = -ShooterConstants.HOPPER_SPEED;
          } else if (rightBumperPressed) {
            kickerSpeed = ShooterConstants.KICKER_SPEED;
            indexerSpeed = indexerEnabled ? ShooterConstants.INDEXER_SPEED : 0.0;
            hopperSpeed = indexerEnabled ? ShooterConstants.HOPPER_SPEED : 0.0;
          }

          m_shooter.setKickerSpeed(kickerSpeed);
          m_shooter.setIndexerSpeed(indexerSpeed);
          m_shooter.setHopperSpeed(hopperSpeed);
   
          lastHelmsRightBumperPressed = rightBumperPressed;
        },
        m_shooter));


    // Hood controls (helms controller).
    // Y = hood up (2 inches / max travel), B = hood down.
    helmsController.y().onTrue(new InstantCommand(() -> m_shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), m_shooter));
    helmsController.b().onTrue(new InstantCommand(() -> m_shooter.setHoodAngle(ShooterSubsystem.HoodAngle.MED), m_shooter));
    helmsController.a().onTrue(new InstantCommand(() -> m_shooter.setHoodAngle(ShooterSubsystem.HoodAngle.LOW), m_shooter));

    //Right Bumper = Simple Auto Align
    driveController.button(Button.kRightBumper.value).whileTrue(new SimpleAutoAlign(m_drive));

    // Default command runs continuously when no other command requires the subsystem.
    // It automatically pauses when commands like AutoAlign take control, then resumes
    // when they finish.
    m_drive.setDefaultCommand(
      new TeleopSwerve(
        // SwerveSubsystem - The drive subsystem to control
        m_drive,
        // translationSupplier - Forward/backward speed
        () -> -getSpeedMultiplier() * driveController.getRawAxis(translationAxis) * 1.0,
        // strafeSupplier - Side-to-side speed
        () -> -getSpeedMultiplier() * driveController.getRawAxis(strafeAxis) * 1.0,
        // rotationSupplier - Rotation speed
        () -> -driveController.getRawAxis(rotationAxis) * 0.5,
        // robotCentricSupplier - Robot-oriented (true) vs field-oriented (false)
        () -> robotCentric.getAsBoolean(),
        // isAutoAlignSupplier - Auto-align active flag
        () -> driveController.getRightTriggerAxis() > 0.1
      ));

    //INTAKE
    m_intake.setDefaultCommand(
      new RunCommand(
        () -> m_intake.setIntakePower(MathUtil.applyDeadband(helmsController.getLeftY(), 0.1)),
        m_intake));

    
    
    // Intake arm buttons.
    // Left Trigger = lower arm, Right Trigger = Raise arm.
    // Bound on both controllers so it works regardless of which one you're pressing.
    helmsController.axisGreaterThan(Axis.kRightTrigger.value, 0.1).onTrue(new InstantCommand(() -> m_intake.raiseIntake(), m_intake));
    helmsController.axisGreaterThan(Axis.kLeftTrigger.value, 0.1).onTrue(new InstantCommand(() -> m_intake.lowerIntake(), m_intake));
  }

  private double getSpeedMultiplier(){
    // getHID() accesses the underlying XboxController to read button states directly.
    // CommandXboxController doesn't provide a method for stick button presses, so we use
    // the HID (Human Interface Device) object's getRawButton() method instead.
    return driveController.getHID().getRawButton(Button.kLeftStick.value)? 1: 1;
  }
  
  public Command getAutonomousCommand() {
    AutoConstants.AutoMode selected = AutoConstants.getSelectedAutoMode();
    SmartDashboard.putString("Auto/Selected", selected.name());
    DriverStation.reportWarning("Auto selected: " + selected.name(), false);

    return switch (selected) {
      case None -> Commands.none();
      case LeftLemonAuto -> new LeftLemonAuto(m_drive, m_intake, m_shooter);
      case RightLemonAuto -> new RightLemonAuto(m_drive, m_intake, m_shooter);
      case RightNeutralZoneAuto1 -> new RightNeutralZoneAuto1 (m_drive, m_intake, m_shooter);
      case LeftNeutralZoneAuto2 -> new LeftNeutralZoneAuto2 (m_drive, m_intake, m_shooter);
      case LeftNeutralZoneAuto1 -> new LeftNeutralZoneAuto1(m_drive, m_intake, m_shooter);
      case CenterLemonAuto -> new CenterLemonAuto(m_drive, m_intake, m_shooter);
      case CenterToDepotAuto -> new CenterToDepotAuto(m_drive, m_intake, m_shooter);
      case DepotShootingAuto -> new DepotShootingAuto(m_drive, m_intake, m_shooter);

      
      default -> Commands.none();
    };
  }

  public SwerveSubsystem getDriveSubsystem() {
    return m_drive;
  }
}
