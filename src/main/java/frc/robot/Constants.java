// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;



import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;

/** Add your docs here. */
public final class Constants {


public static final double motorSpeedMultiplier = 0.5; // Used to scale down motor output if needed


// Swerve Constants
  public static final class SwerveConstants{
    public static final double inputDeadband = .1; // Deadzone for joystick inputs to prevent drift
    public static final int PIGEON_ID = 23; //CAN ID for Pigeon gyro sensor
    public static final boolean invertPigeon = false; // Whether to invert gyro readings

    /* Drivetrain Constants */
    public static final double halfTrackWidth = Units.inchesToMeters(28/2.0);//to find
    public static final double halfWheelBase = Units.inchesToMeters(28/2.0);//to find
    public static final double wheelDiameter = Units.inchesToMeters(4.0);
    public static final double wheelCircumference = wheelDiameter * Math.PI;
    public static final double driveBaseRadius = Math.hypot(halfTrackWidth/2, halfWheelBase/2);

    public static final double openLoopRamp = 0.25;
    public static final double closedLoopRamp = 0.0;

    public static final double driveGearRatio = (8.14 / 1.0); // 6.75:1 L2 Mk4 Modules
    //L1 is 8.14:1, L2 is 6.75:1, L3 is 6.12:1, L4 is 5.14:1
    public static final double angleGearRatio = (12.8 / 1.0); // 12.8:1 MK4 SDS Modules
    //SDS Mk4 is 12.8:1,  Mk4i is 21.4:1

    public static final SwerveDriveKinematics swerveKinematics =
    new SwerveDriveKinematics(
        new Translation2d(-halfTrackWidth, halfWheelBase), //Back Right
        new Translation2d(halfTrackWidth,halfWheelBase), // Front Right
        new Translation2d(halfTrackWidth,-halfWheelBase), // Front Left
        new Translation2d(-halfTrackWidth,-halfWheelBase)); // Back Left
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
    public static final double maxSpeed = 3; // meters per second
    public static final double maxAngularVelocity = maxSpeed/driveBaseRadius; //radians per second how fast the robot spin

    /* Neutral Modes */
    public static final IdleMode angleNeutralMode = IdleMode.kBrake;
    public static final IdleMode driveNeutralMode = IdleMode.kBrake;

    /* Motor Inverts */
    public static final boolean canCoderInvert = false;
    public static final boolean driveInvert = false;
    public static final boolean angleInvert = true;

    //Location of modules
    public static final Translation2d BACK_RIGHT = new Translation2d(-halfWheelBase, halfTrackWidth);
    public static final Translation2d FRONT_RIGHT = new Translation2d(halfWheelBase, halfTrackWidth);
    public static final Translation2d FRONT_LEFT = new Translation2d(halfWheelBase, -halfTrackWidth);
    public static final Translation2d BACK_LEFT = new Translation2d(-halfWheelBase, -halfTrackWidth);

    /* Module Specific Constants */
    public record ModuleData(
      int driveMotorID, int angleMotorID, int encoderID, double angleOffset, Translation2d location
    ){}

    public static ModuleData[] moduleData = {
      new ModuleData(11, 52, 19, 340.32, BACK_RIGHT), //Mod 0 Back right
      new ModuleData(17, 53, 22, 51.59, FRONT_RIGHT), //Mod 1 Front right
      new ModuleData(15, 16, 21, 130.16, FRONT_LEFT), //Mod 2 Front left
      new ModuleData(13, 12, 20, 118.47, BACK_LEFT) //Mod 3 Back left
    };
    
  }


public class FieldConstants {
      public static final double FIELD_LENGTH = 17.54824934;
      public static final double FIELD_WIDTH = 8.052;

      public static final Translation2d BLUE_REEF_CENTER = new Translation2d(4.48933684,4.02587697);

      public static final Rotation2d RIGHT_CORAL_STATION_ANGLE = Rotation2d.fromDegrees(234.011392);
      public static final Rotation2d LEFT_CORAL_STATION_ANGLE = Rotation2d.fromDegrees(-234.011392);

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
      public static final int SHOOTER_ID = 60; //Placeholder ID
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
    public static int INTAKE_ID = 60; // placeholder
    public static double INTAKE_SPEED = 50; //placeholder for percent power for intake

    public static int INTAKE_ARM_ID = 62; //placeholder
    public static double INTAKE_ARM_RAISED_POSITION = 90; //to do later
    public static double INTAKE_ARM_LOWERED_POSITION = 0;
    public static double INTAKE_ARM_MINIMUM = 0; // placeholders
    public static double INTAKE_ARM_MAXIMUM = 90;
    public static int GEAR_RATIO = 3;

    public static double INTAKE_ARM_kP = 0.01;
    public static double INTAKE_ARM_kI = 0;
    public static double INTAKE_ARM_kD = 0;
  }
}