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
import frc.robot.Constants.SwerveConstants;

/** Central location for robot-wide constants grouped by subsystem and feature */
public final class Constants {

  public static final class SwerveConstants {
    private static final boolean isCompDrivebase = true;

    
    /* Global Swerve Constants */
    public static final double inputDeadband = .1; // Deadzone for joystick inputs to prevent drift

    /* Inverts */
    public static final boolean invertPigeon = false; //gyro readings
    public static final boolean canCoderInvert = false; //CANcoder readings
    public static final boolean driveInvert = false; //drive motor
    public static final boolean angleInvert = true; //angle motor


    /* Global Drivetrain Definitions */
    public static final double wheelCircumference;
    public static final double driveBaseRadius;
    
    /* Drive Motor Conversion Factors */
    public static final double driveConversionPositionFactor;
    public static final double driveConversionVelocityFactor;
    public static final double angleConversionFactor;
    
    /* Motor Idle Modes */
    public static final IdleMode angleNeutralMode = IdleMode.kBrake;
    public static final IdleMode driveNeutralMode = IdleMode.kBrake;

    /* Location of modules (make sure this and moduleData are in same order) */
    public static final Translation2d FRONT_LEFT;
    public static final Translation2d FRONT_RIGHT;
    public static final Translation2d BACK_RIGHT;
    public static final Translation2d BACK_LEFT;

    /* Translation2d - Kinematics */
    /* SwerveDrive Kinematics converts between a ChassisSpeeds object and several SwerveModuleState objects, 
     * which contains velocities and angles for each swerve module of a swerve drive robot.
     * make sure things in this section line up if the robot is driving weird check this first
     * more info see https://www.notion.so/Swerve-Module-Positioning-Translation2d-2ec665d348f580e48612c9a5bd315fb7 
     * and https://docs.wpilib.org/en/stable/docs/software/kinematics-and-odometry/swerve-drive-kinematics.html
     */
    public static final SwerveDriveKinematics swerveKinematics;
    public static final ModuleData[] moduleData;

    /* Swerve Specific Definitions */

    public static final int PIGEON_ID; //CAN ID for Pigeon

    /* Drivetrain Dimention Constants */
    public static final double halfTrackWidth;
    public static final double halfWheelBase;
    public static final double wheelDiameter;
    
    public static final double driveGearRatio;
    //L2 Mk4 Modules are 6.75:1, L1 is 8.14:1, L2 is 6.75:1, L3 is 6.12:1, L4 is 5.14:1
    public static final double angleGearRatio;
    //MK4i Modules are 21.4:1, SDS Mk4 is 12.8:1,  Mk4i is 21.4:1
    
    /* Swerve Profiling Values */
    public static final double maxSpeed; // meters per second
    public static final double maxAngularVelocity; //radians per second how fast the robot spin
    
    /* Motor Config */
    public static final double voltageComp; //Swerve Voltage Compensation
    public static final int angleContinuousCurrentLimit; //limits current draw of turning motor
    public static final int driveContinuousCurrentLimit; //limits current draw of drive motor
    
    /* PID Values */
    
    /* Drive Motor PIDF Values */
    public static final double driveKP; //Proportional
    public static final double driveKI; //Integral, keep it at zero unless you see a persistent offset
    public static final double driveKD; //Derivitive
    //Feedforward - uses precalculated values
    public static final double driveKS;
    public static final double driveKV;
    public static final double driveKA;

    /* Angle Motor PID Values */
    public static final double angleKP; //Proportional
    public static final double angleKI; //Integral, keep it at zero unless you see a persistent offset
    public static final double angleKD; //Derivitive

    /* ModuleData Record */
    public record ModuleData(
      int driveMotorID,
      int angleMotorID,
      int encoderID,
      double angleOffset,
      Translation2d location,
      boolean driveInvert,
      boolean angleInvert
    ){}

    static {
      if(isCompDrivebase){
        PIGEON_ID = 17; //CAN ID for Pigeon

        /* Drivetrain Dimention Constants */
        halfTrackWidth = Units.inchesToMeters(27/2.0);
        halfWheelBase = Units.inchesToMeters(27/2.0);
        wheelDiameter = Units.inchesToMeters(4.0);

        driveGearRatio = (6.75 / 1.0); // L2 Mk4 Modules are 6.75:1
        //L1 is 8.14:1, L2 is 6.75:1, L3 is 6.12:1, L4 is 5.14:1
        angleGearRatio = (21.4 / 1.0); // 21.4:1 MK4i Modules
        //SDS Mk4 is 12.8:1,  Mk4i is 21.4:1

        /* Swerve Profiling Values */
        maxSpeed = 3; // meters per second

        /* Motor Config */
        voltageComp = 12.0; //Swerve Voltage Compensation
        angleContinuousCurrentLimit = 20; //limits current draw of turning motor
        driveContinuousCurrentLimit = 40; //limits current draw of drive motor

        /* PID Values */

        /* Drive Motor PIDF Values */
        driveKP = 0.1; //Proportional
        driveKI = 0.0; //Integral, keep it at zero unless you see a persistent offset
        driveKD = 0.0; //Derivitive
        //Feedforward - uses precalculated values
        driveKS = 0.667;
        driveKV = 2.4;
        driveKA = 0.5;

        /* Angle Motor PID Values */
        angleKP = 0.01; //Proportional
        angleKI = 0.0; //Integral, keep it at zero unless you see a persistent offset
        angleKD = 0.0; //Derivitive

        /* Location of modules (make sure this and moduleData are in same order) */
        FRONT_LEFT = new Translation2d(halfWheelBase, halfTrackWidth);
        FRONT_RIGHT = new Translation2d(halfWheelBase, -halfTrackWidth);
        BACK_RIGHT = new Translation2d(-halfWheelBase, -halfTrackWidth);
        BACK_LEFT = new Translation2d(-halfWheelBase, halfTrackWidth);

        /* Translation2d - Kinematics */
        /* SwerveDrive Kinematics converts between a ChassisSpeeds object and several SwerveModuleState objects, 
        * which contains velocities and angles for each swerve module of a swerve drive robot.
        * make sure things in this section line up if the robot is driving weird check this first
        * more info see https://www.notion.so/Swerve-Module-Positioning-Translation2d-2ec665d348f580e48612c9a5bd315fb7 
        * and https://docs.wpilib.org/en/stable/docs/software/kinematics-and-odometry/swerve-drive-kinematics.html
        */
        new SwerveDriveKinematics(FRONT_LEFT, FRONT_RIGHT, BACK_RIGHT, BACK_LEFT);

        moduleData = new ModuleData[] {
          new ModuleData(6, 5, 7, 31.46, FRONT_LEFT, driveInvert, angleInvert), //Mod 0 Front left
          new ModuleData(9, 8, 10, 49.57, FRONT_RIGHT, driveInvert, angleInvert), //Mod 1 Front right
          new ModuleData(12, 11, 13, 33.13, BACK_RIGHT, driveInvert, angleInvert), //Mod 2 Back right
          new ModuleData(15, 14, 16, 8.52, BACK_LEFT, driveInvert, angleInvert) //Mod 3 Back left
        };

    } else {
        
        PIGEON_ID = 23; //CAN ID for Pigeon

        /* Drivetrain Dimention Constants */
        halfTrackWidth = Units.inchesToMeters(28/2.0);
        halfWheelBase = Units.inchesToMeters(28/2.0);
        wheelDiameter = Units.inchesToMeters(4.0);

        driveGearRatio = (8.14 / 1.0); 
        // L2 Mk4 Modules are 6.75:1 L1 is 8.14:1, L2 is 6.75:1, L3 is 6.12:1, L4 is 5.14:1
        angleGearRatio = (12.8 / 1.0); 
        // MK4i Modules are 21.4:1 SDS Mk4 is 12.8:1, Mk4i is 21.4:1

        /* Swerve Profiling Values */
        maxSpeed = 3; // meters per second

        /* Motor Config */
        voltageComp = 12.0; //Swerve Voltage Compensation
        angleContinuousCurrentLimit = 20; //limits current draw of turning motor
        driveContinuousCurrentLimit = 40; //limits current draw of drive motor

        /* PID Values */

        /* Drive Motor PIDF Values */
        driveKP = 0.1; //Proportional
        driveKI = 0.0; //Integral, keep it at zero unless you see a persistent offset
        driveKD = 0.0; //Derivitive
        //Feedforward - uses precalculated values
        driveKS = 0.667;
        driveKV = 2.4;
        driveKA = 0.5;

        /* Angle Motor PID Values */
        angleKP = 0.01; //Proportional
        angleKI = 0.0; //Integral, keep it at zero unless you see a persistent offset
        angleKD = 0.0; //Derivitive

        /* Location of modules (make sure this and moduleData are in same order) */
        FRONT_LEFT = new Translation2d(halfWheelBase, halfTrackWidth);
        FRONT_RIGHT = new Translation2d(halfWheelBase, -halfTrackWidth);
        BACK_RIGHT = new Translation2d(-halfWheelBase, -halfTrackWidth);
        BACK_LEFT = new Translation2d(-halfWheelBase, halfTrackWidth);

        swerveKinematics = new SwerveDriveKinematics(BACK_RIGHT, FRONT_RIGHT, FRONT_LEFT, BACK_LEFT);
        
        moduleData = new ModuleData[] {
          new ModuleData(11, 52, 19, 156.09, BACK_RIGHT, driveInvert, angleInvert), //Mod 0 Front left
          new ModuleData(17, 53, 22, 50.80, FRONT_RIGHT, driveInvert, angleInvert), //Mod 1 Front right
          new ModuleData(15, 16, 21, 132.53, FRONT_LEFT, driveInvert, angleInvert), //Mod 2 Back right
          new ModuleData(13, 12, 20, 115.40, BACK_LEFT, driveInvert, angleInvert) //Mod 3 Back left
        };
      }

      /* Derived Global Constants */
      wheelCircumference = wheelDiameter * Math.PI;
      driveBaseRadius = Math.hypot(halfWheelBase, halfTrackWidth);
      maxAngularVelocity = maxSpeed / driveBaseRadius;
      driveConversionPositionFactor = (wheelDiameter * Math.PI) / driveGearRatio;
      driveConversionVelocityFactor = driveConversionPositionFactor / 60.0;
      angleConversionFactor = 360.0 / angleGearRatio;

      }
    }
  
  

public static final class AutoConstants {
  public static final ModuleConfig MODULE_CONFIG = new ModuleConfig(SwerveConstants.wheelDiameter/2,
  SwerveConstants.maxSpeed,
  1.2,
  DCMotor.getNeoVortex(1).withReduction(SwerveConstants.driveGearRatio),
  SwerveConstants.driveContinuousCurrentLimit,
  1);

  public static final RobotConfig ROBOT_CONFIG = new RobotConfig (52, 6.8, MODULE_CONFIG,
  SwerveConstants.FRONT_LEFT, SwerveConstants.FRONT_RIGHT, SwerveConstants.BACK_LEFT, SwerveConstants.BACK_RIGHT);

  public static final PPHolonomicDriveController SWERVE_DRIVE_CONTROLLER = new PPHolonomicDriveController(new PIDConstants(5.0,0.00001,0.0),
  new PIDConstants(5.0, 0.005, 0.001) );

  public enum AutoMode{
    DriveTestAuto,
    EightLemonAuto
  }

  private static SendableChooser<Boolean> sideChooser = new SendableChooser<Boolean>();
  private static SendableChooser<AutoMode> autoModeChooser = new SendableChooser<AutoMode>();
  static{
    sideChooser.addOption("RIGHT", true);
    sideChooser.setDefaultOption("LEFT", false);

    for(AutoMode mode : AutoMode.values()){
      autoModeChooser.addOption(mode.toString(), mode);
    }

    autoModeChooser.setDefaultOption(AutoMode.DriveTestAuto.toString(), AutoMode.DriveTestAuto);
    SmartDashboard.putData("Auto Starting Location", sideChooser);
    SmartDashboard.putData("Auto Mode", autoModeChooser);
  }
  
  public static AutoMode getSelectedAutoMode(){
    AutoMode selection = autoModeChooser.getSelected();
    return selection != null ? selection : AutoMode.DriveTestAuto;
  }
  public static boolean isRightSideAuto(){
    return Boolean.TRUE.equals(sideChooser.getSelected());
  }
}


public class FieldConstants {
      public static final double FIELD_LENGTH = 17.54824934;
      public static final double FIELD_WIDTH = 8.052;

      public static final Translation2d HUB_CENTER = new Translation2d(4.61,4.03);

      public static boolean isRedAlliance(){
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
  /* Shooter Constants */
  public class ShooterConstants {
      public static final int SHOOTER_ID = 70; //Placeholder ID
      public static final int FEEDER_ID = 61; //Feeder ID
      public static final int HOOD_ID = 62; //Hood ID (NEED CHANGE)

      public static final double SHOOTER_SPEED = 0.5; //Placeholder speed
      public static final double FEEDER_SPEED = 0.5; 

      public static final double HOOD_ANGLE_LOW = 0.0;
      public static final double HOOD_ANGLE_HIGH = 0.5;
      public static final double HOOD_KP = 1.2;
      public static final double HOOD_MAX_OUTPUT = 0.4;
      public static final double HOOD_TOLERANCE = 0.02;
  }
  public class IntakeConstants {
    // Must be unique across *all* CAN devices (SparkMax/SparkFlex/etc).
    // These were previously colliding with ShooterConstants IDs (60/62) and causing robot init to crash.
    public static int INTAKE_ID = 63; // TODO: set to your intake motor CAN ID
    public static double INTAKE_SPEED = 50; //placeholder for percent power for intake

    public static int INTAKE_ARM_ID = 64; // TODO: set to your intake arm motor CAN ID
    public static double INTAKE_ARM_RAISED_POSITION = 90; //to do later
    public static double INTAKE_ARM_LOWERED_POSITION = 0;
    public static double INTAKE_ARM_MINIMUM = 0; // placeholders
    public static double INTAKE_ARM_MAXIMUM = 90;
    public static int GEAR_RATIO = 3;

    public static double INTAKE_ARM_kP = 0.01;
    public static double INTAKE_ARM_kI = 0;
    public static double INTAKE_ARM_kD = 0;
  }

  public class CANdleConstants {
    public static final int CANDLE_ID = 18; //Placeholder ID
  }
}
