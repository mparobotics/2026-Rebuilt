// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.wpilibj.XboxController.Axis;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.XboxController.Button;
import edu.wpi.first.cscore.HttpCamera;
import edu.wpi.first.cscore.UsbCamera;
import edu.wpi.first.cscore.VideoSource.ConnectionStrategy;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.VisionConstants;
import frc.robot.Command.TeleopSwerve;
import frc.robot.Subsystems.SwerveSubsystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.DriverStation;


public class RobotContainer {

 
  // Xbox controller configuration for drive controls
  private final CommandXboxController driveController = new CommandXboxController(0); 
  // Xbox controller configuration for helms controls
  private final CommandXboxController helmsController = new CommandXboxController(1);

  // Left Stick Y = Forward/backward motion
  private final int TRANSLATION_AXIS = XboxController.Axis.kLeftY.value;
  // Left Stick X = Side-to-side motion
  private final int STRAFE_AXIS = XboxController.Axis.kLeftX.value;
  // Right Stick X = Rotation/turning motion
  private final int ROTATION_AXIS = XboxController.Axis.kRightX.value;
  // Left Bumper = Toggle robot-oriented mode (default is field-oriented)
  private final Trigger robotCentric = new Trigger(driveController.leftBumper());

  // SwerveSubsystem instance for the drive subsystem
  private final SwerveSubsystem m_drive = new SwerveSubsystem();


  //Limelight
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

    // Default command runs continuously when no other command requires the subsystem.
    // It automatically pauses when commands like AutoAlign take control, then resumes
    // when they finish.
    m_drive.setDefaultCommand(
      new TeleopSwerve(
        // SwerveSubsystem - The drive subsystem to control
        m_drive,
        // translationSupplier - Forward/backward speed
        () -> -getSpeedMultiplier() * driveController.getRawAxis(TRANSLATION_AXIS) * 1.0,
        // strafeSupplier - Side-to-side speed
        () -> -getSpeedMultiplier() * driveController.getRawAxis(STRAFE_AXIS) * 1.0,
        // rotationSupplier - Rotation speed
        () -> -driveController.getRawAxis(ROTATION_AXIS) * 0.5,
        // robotCentricSupplier - Robot-oriented (true) vs field-oriented (false)
        () -> robotCentric.getAsBoolean(),
        // isAutoAlignSupplier - Auto-align active flag
        () -> driveController.getRightTriggerAxis() > 0.1
      ));
  };


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

      
      default -> Commands.none();
    };
  }

  public SwerveSubsystem getDriveSubsystem() {
    return m_drive;
  }
}
