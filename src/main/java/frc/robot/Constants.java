// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.pathplanner.lib.config.ModuleConfig;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.Auto.LeftNeutralZoneAuto1;

/** Central location for robot-wide constants grouped by subsystem and feature */
public final class Constants {


public static final double motorSpeedMultiplier = 0.5; // Used to scale down motor output if needed


// Swerve Constants
public static final class SwerveConstants{
  public static final double inputDeadband = .1; // Deadzone for joystick inputs to prevent drift
  public static final int PIGEON_ID = 17; //CAN ID for Pigeon gyro sensor
  public static final boolean invertPigeon = false; // Whether to invert gyro readings

  /* Drivetrain Constants */
  public static final double halfTrackWidth = Units.inchesToMeters(27/2.0);//to find
  public static final double halfWheelBase = Units.inchesToMeters(27/2.0);//to find
  public static final double wheelDiameter = Units.inchesToMeters(4.0);
  public static final double wheelCircumference = wheelDiameter * Math.PI;
  //halfTrackWidth/halfwheelBase are already "half" distances, so don't divide again.
  //public static final double driveBaseRadius = Math.hypot(halfTrackWidth/2, halfWheelBase/2);
  public static final double driveBaseRadius = Math.hypot(halfWheelBase, halfTrackWidth);


  public static final double openLoopRamp = 0.25;
  public static final double closedLoopRamp = 0.0;

  public static final double driveGearRatio = (6.75 / 1.0); // 6.75:1 L2 Mk4 Modules
  //L1 is 8.14:1, L2 is 6.75:1, L3 is 6.12:1, L4 is 5.14:1
  public static final double angleGearRatio = (21.4 / 1.0); // 21.4:1 MK4i Modules
  //SDS Mk4 is 12.8:1,  Mk4i is 21.4:1

  public static final SwerveDriveKinematics swerveKinematics =
  new SwerveDriveKinematics(
      //WPILib coordinate system: +X = forward, +Y = left
      new Translation2d(halfTrackWidth, halfWheelBase), //Front left
      new Translation2d(halfTrackWidth, -halfWheelBase), //Front right
      new Translation2d(-halfTrackWidth, -halfWheelBase), //Back right
      new Translation2d(-halfTrackWidth, halfWheelBase)); //Back Left
  //translation 2d locates the swerve module in cords
  //https://docs.wpilib.org/en/stable/docs/software/kinematics-and-odometry/swerve-drive-kinematics.html
  //SwerveDrive Kinematics converts between a ChassisSpeeds object and several SwerveModuleState objects, 
  //which contains velocities and angles for each swerve module of a swerve drive robot.
      
  /* Swerve Voltage Compensation */
  public static final double voltageComp = 12.0;
       
  //Swerve Current Limiting for neos
  public static final int angleContinuousCurrentLimit = 20; //limits current draw of turning motor
  public static final int driveContinuousCurrentLimit = 40; //limits current draw of drive motor



  /* Drive Motor PID Values */
  public static final double driveKP = 0.1; //to tune
  public static final double driveKI = 0.0; //to tune
  public static final double driveKD = 0.0; //to tune 

  /* Drive Motor Characterization Values */
  //values to calculate the drive feedforward (KFF)
  public static final double driveKS = 0.667; //to calculate
  public static final double driveKV = 2.4; //to calculate
  public static final double driveKA = 0.5; //to calculate

  /* Angle Motor PID Values */
  public static final double angleKP = 0.01; //to tune
  public static final double angleKI = 0.0; //to tune, keep it at zero unless you see a persistent offset
  public static final double angleKD = 0.0; //to tune

  /* Drive Motor Conversion Factors */
  public static final double driveConversionPositionFactor =
  (wheelDiameter * Math.PI) / driveGearRatio;
  public static final double driveConversionVelocityFactor = driveConversionPositionFactor / 60.0;
  public static final double angleConversionFactor = 360.0 / angleGearRatio;

  /* Swerve Profiling Values */
  public static final double maxSpeed = 5; // meters per second
  public static final double PathPlannerMaxSpeed = DCMotor.getNeoVortex(1).withReduction(driveGearRatio).freeSpeedRadPerSec*(wheelDiameter/2);
  public static final double maxAngularVelocity = maxSpeed/driveBaseRadius; //radians per second how fast the robot spin

  /* Neutral Modes */
  public static final IdleMode angleNeutralMode = IdleMode.kBrake;
  public static final IdleMode driveNeutralMode = IdleMode.kBrake;

  /* Motor Inverts */
  public static final boolean canCoderInvert = false;
  public static final boolean driveInvert = false;
  public static final boolean angleInvert = true;

  //Location of modules
  public static final Translation2d FRONT_LEFT = new Translation2d(halfWheelBase, halfTrackWidth);
  public static final Translation2d FRONT_RIGHT = new Translation2d(halfWheelBase, -halfTrackWidth);
  public static final Translation2d BACK_RIGHT = new Translation2d(-halfWheelBase, -halfTrackWidth);
  public static final Translation2d BACK_LEFT = new Translation2d(-halfWheelBase, halfTrackWidth);

  /* Module Specific Constants */
  public record ModuleData(
    int driveMotorID,
    int angleMotorID,
    int encoderID,
    double angleOffset,
    Translation2d location,
    boolean driveInvert,
    boolean angleInvert
  ){}

  public static ModuleData[] moduleData = {
    new ModuleData(6, 5, 7, 39.90, FRONT_LEFT, driveInvert, angleInvert), //Mod 0 Front left
    new ModuleData(9, 8, 10, 46.93, FRONT_RIGHT, driveInvert, angleInvert), //Mod 1 Front right
    new ModuleData(12, 11, 13, 42.09, BACK_RIGHT, driveInvert, angleInvert), //Mod 2 Back right
    new ModuleData(15, 14, 16, 7.11, BACK_LEFT, driveInvert, angleInvert) //Mod 3 Back left
  };
  
}


public static final class AutoConstants {
  private static boolean dashboardInitialized = false;

  public static final ModuleConfig MODULE_CONFIG = new ModuleConfig(SwerveConstants.wheelDiameter/2,
  SwerveConstants.PathPlannerMaxSpeed,
  1.2,
  DCMotor.getNeoVortex(1).withReduction(SwerveConstants.driveGearRatio),
  SwerveConstants.driveContinuousCurrentLimit,
  1);

  public static final RobotConfig ROBOT_CONFIG = new RobotConfig (52, 6.8, MODULE_CONFIG,
  SwerveConstants.FRONT_LEFT, SwerveConstants.FRONT_RIGHT, SwerveConstants.BACK_LEFT, SwerveConstants.BACK_RIGHT);

  public static final PPHolonomicDriveController SWERVE_DRIVE_CONTROLLER = new PPHolonomicDriveController(new PIDConstants(5.0,0.00001,0.0),
  new PIDConstants(5.0, 0.005, 0.001) );

  public enum AutoMode{
    None,
    DriveTestAuto,
    LeftLemonAuto,
    RightLemonAuto,
    LeftNeutralZoneAuto1,
    LeftNeutralZoneAuto2,
    RightNeutralZoneAuto,
    ShootEightAuto,
    CenterLemonAuto
  }

  private static SendableChooser<Boolean> sideChooser = new SendableChooser<Boolean>();
  private static SendableChooser<AutoMode> autoModeChooser = new SendableChooser<AutoMode>();
  public static void initDashboard() {
    if (dashboardInitialized) {
      return;
    }
    dashboardInitialized = true;

    sideChooser.addOption("RIGHT", true);
    sideChooser.setDefaultOption("LEFT", false);

    autoModeChooser.setDefaultOption("LeftLemonAuto", AutoMode.LeftLemonAuto);
    autoModeChooser.addOption("None", AutoMode.None);
    autoModeChooser.addOption("DriveTestAuto", AutoMode.DriveTestAuto);
    autoModeChooser.addOption("ShootEightAuto", AutoMode.ShootEightAuto);
    autoModeChooser.addOption("RightLemonAuto", AutoMode.RightLemonAuto);
    autoModeChooser.addOption("LeftLemonAuto", AutoMode.LeftLemonAuto);
    autoModeChooser.addOption("RightNeutralZoneAuto", AutoMode.RightNeutralZoneAuto);
    autoModeChooser.addOption("LeftNeutralZoneAuto1", AutoMode.LeftNeutralZoneAuto1);
    autoModeChooser.addOption("LeftNeutralZoneAuto2", AutoMode.LeftNeutralZoneAuto2);
    autoModeChooser.addOption("CenterLemonAuto", AutoMode.CenterLemonAuto);

    SmartDashboard.putData("Auto Starting Location", sideChooser);
    SmartDashboard.putData("Auto Mode", autoModeChooser);
  }
  
  public static AutoMode getSelectedAutoMode(){
    initDashboard();
    AutoMode selection = autoModeChooser.getSelected();
    return selection != null ? selection : AutoMode.LeftLemonAuto;
  }
  public static boolean isRightSideAuto(){
    initDashboard();
    return Boolean.TRUE.equals(sideChooser.getSelected());
  }
}


public static final class FieldConstants {
  public static final double FIELD_LENGTH = 16.54;
  public static final double FIELD_WIDTH = 8.07;

  public static final Translation2d HUB_CENTER = new Translation2d(4.61,4.03);

  /**
   * If true, the robot will behave as if it is always on the Blue alliance (no field mirroring),
   * even when connected to FMS / Driver Station reports Red.1
   */
  public static final boolean FORCE_BLUE_ALLIANCE = true;

  public static boolean isRedAlliance(){
    if (FORCE_BLUE_ALLIANCE) {
        return false;
    }
    // Default to Blue when alliance is unknown (common in sim/practice).
    return DriverStation.getAlliance().isPresent() && DriverStation.getAlliance().get() == Alliance.Red;
  }
    
  public static Rotation2d flipForAlliance(Rotation2d rotation){
    if(isRedAlliance()){
        return Rotation2d.fromDegrees(rotation.getDegrees() + 180);
    }else{
        return rotation;
    }
  }
  public static Translation2d flipForAlliance(Translation2d pos){
    if(isRedAlliance()){
        return new Translation2d(FIELD_LENGTH - pos.getX(), FIELD_WIDTH - pos.getY());
    }else{
        return pos;
    }
  }
  public static Pose2d flipForAlliance(Pose2d pose){
    return new Pose2d(flipForAlliance(pose.getTranslation()), flipForAlliance(pose.getRotation()));
  }
    
}

/** Vision constants (Limelight, etc). */
public static final class VisionConstants {
  public static final String[] LIMELIGHT_NAMES = {"limelight-a", "limelight-b"};

  // Limelight MJPEG stream endpoints.
  // Using fixed IPs avoids mDNS/DNS resolution issues on the roboRIO.
  public static final String LIMELIGHT_A_STREAM_URL = "http://10.39.26.4:5801/stream.mjpg";
  public static final String LIMELIGHT_B_STREAM_URL = "http://10.39.26.5:5801/stream.mjpg";
  public static final boolean LIMELIGHT_STREAM_ENABLED_DEFAULT = true;

  public static final boolean VISION_ENABLED_DEFAULT = true;
  public static final double MAX_VISION_ANGULAR_RATE_DEG_PER_SEC = 720.0;

  /** Standard deviations for vision measurements: (x meters, y meters, theta radians). */
  //Rotation (Radians) should be handled by pigion so deviation is high
  public static final double VISION_STD_DEV_X_METERS = 0.4;
  public static final double VISION_STD_DEV_Y_METERS = 0.4;
  public static final double VISION_STD_DEV_THETA_RADIANS = 99999.0;

  public static String getLimelightStreamUrl(String limelightName) {
    switch (limelightName) {
      case "limelight-a":
        return LIMELIGHT_A_STREAM_URL;
      case "limelight-b":
        return LIMELIGHT_B_STREAM_URL;
      default:
        // Fallback for any future Limelight names.
        return "http://" + limelightName + ".local:5801/stream.mjpg";
    }
  }
}

/* Shooter Constants */
public static final class ShooterConstants {
  public static final int SHOOTER_ID = 22;
  public static final int KICKER_ID = 21;
  public static final int HOOD_ID = 20;
  public static final int INDEXER_ID = 23;

  // Percent output caps ([-1..1]). Higher = faster spin-up but more current draw.
  public static final double SHOOTER_SPEED = 0.6;
  public static final double KICKER_SPEED = 0.6;
  public static final double INDEXER_SPEED = 0.4; //placeholder

  // Shooter readiness (SparkMax encoder velocity is RPM). Tune on the real robot.
  public static final double SHOOTER_READY_RPM = 3000.0;

  // Electrical limits/compensation.
  public static final double SHOOTER_VOLTAGE_COMP = 12.0;
  public static final int SHOOTER_CURRENT_LIMIT_AMPS = 60;
  public static final int KICKER_CURRENT_LIMIT_AMPS = 60;

  // Hood position units are motor rotations (NEO internal encoder).
  // Max travel is 3 rotations = 1080 degrees.
  public static final double HOOD_MIN_ROTATIONS = 0.0;
  public static final double HOOD_MED_ROTATIONS = 20.0;
  public static final double HOOD_MAX_ROTATIONS = 27.0;

  // Preset positions.
  public static final double HOOD_ANGLE_LOW = HOOD_MIN_ROTATIONS;
  public static final double HOOD_ANGLE_MED = HOOD_MED_ROTATIONS;
  public static final double HOOD_ANGLE_HIGH = HOOD_MAX_ROTATIONS; // "up" (about 2 inches)
  public static final double HOOD_KP = 0.1;
  public static final double HOOD_MAX_OUTPUT = 0.4;
  public static final double HOOD_TOLERANCE = 0.02;
}

public static final class IntakeConstants {
  // Must be unique across *all* CAN devices (SparkMax/SparkFlex/etc).
  // These were previously colliding with ShooterConstants IDs (60/62) and causing robot init to crash.
  public static int INTAKE_ID = 19;
  // SparkMax.set(...) expects [-1.0, 1.0] percent output.
  public static double INTAKE_SPEED = 0.90; // max percent output for intake motor

  public static int INTAKE_ARM_ID = 18;
  public static int GEAR_RATIO = 25;

  //Intake arm position units are degrees
  public static final double INTAKE_ARM_MIN_DEG = 20.0;
  public static final double INTAKE_ARM_MAX_DEG = 90.0;

  //Preset positions
  public static final double INTAKE_ARM_LOWERED_POSITION = INTAKE_ARM_MIN_DEG;
  public static final double INTAKE_ARM_RAISED_POSITION = INTAKE_ARM_MAX_DEG;

  //PID constants for intake arm (degrees).
  public static final double INTAKE_ARM_kP = 6.0;
  public static final double INTAKE_ARM_kI = 1.5;
  public static final double INTAKE_ARM_kD = 0.15;
  public static final double INTAKE_ARM_TOLERANCE_DEG = 2.0;

  //Feedforward constants for intake arm
  public static final double INTAKE_ARM_kS = 0.0;
  public static final double INTAKE_ARM_kG = 0.0;
  public static final double INTAKE_ARM_kV = 0.0;
  public static final double INTAKE_ARM_kA = 0.0;

  //Percent output cap (0..1) for gentler motion
  //duty-cycle / percent output for SparkMax.set(...), which expects a value in [-1.0, 1.0]
  public static final double INTAKE_ARM_MAX_OUTPUT = 0.20;
  public static final double INTAKE_ARM_MIN_OUTPUT = -0.10;
}

public static final class CANdleConstants {
  public static final int CANDLE_ID = 18; //Placeholder ID

  }
}
