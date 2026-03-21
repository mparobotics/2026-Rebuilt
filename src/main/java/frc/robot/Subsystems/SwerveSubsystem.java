// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import com.ctre.phoenix6.configs.Pigeon2Configuration;
import com.ctre.phoenix6.hardware.Pigeon2;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.commands.FollowPathCommand;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.pathplanner.lib.path.PathPlannerPath;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructArrayPublisher;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.LimelightHelpers;
import frc.robot.Constants;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.FieldConstants;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Constants.VisionConstants;
import frc.robot.Constants.SwerveConstants.ModuleData;
import frc.robot.SwerveModule;


// Manges swerve drivetrain hardware, odometry, and vision-assisted pose up dates.
public class SwerveSubsystem extends SubsystemBase {
  private final Pigeon2 pigeon;

  private SwerveDrivePoseEstimator odometry;
  private SwerveModule[] mSwerveMods;

  private Field2d field;
  private ChassisSpeeds lastCommandedSpeeds = new ChassisSpeeds();

  private double simYawDegrees = 0.0;
  private final double[] simWheelPositionsMeters = new double[4];
  private final Rotation2d[] simWheelAngles =
      new Rotation2d[] {new Rotation2d(), new Rotation2d(), new Rotation2d(), new Rotation2d()};


  private final StructArrayPublisher<SwerveModuleState> swerveDataPublisher = NetworkTableInstance.getDefault()
  .getStructArrayTopic("Swerve States", SwerveModuleState.struct).publish();

  private final StructArrayPublisher<SwerveModuleState> desiredSwerveDataPublisher = NetworkTableInstance.getDefault()
  .getStructArrayTopic("Desired Swerve States", SwerveModuleState.struct).publish();

  /** Creates a new SwerveSubsystem. */
  public SwerveSubsystem() { 
    //instantiates new pigeon gyro, wipes it, and zeros it
    pigeon = new Pigeon2(SwerveConstants.PIGEON_ID);
    pigeon.getConfigurator().apply(new Pigeon2Configuration()); 
    zeroGyro();

    //Creates all four swerve modules into a swerve drive
    mSwerveMods = new SwerveModule[4];
    for (int i = 0; i < 4; i++){
      ModuleData data = SwerveConstants.moduleData[i];
      mSwerveMods[i] = new SwerveModule(i, data);
    }

    //creates new swerve odometry (odometry is where the robot is on the field)
    odometry = new SwerveDrivePoseEstimator(Constants.SwerveConstants.swerveKinematics, getYaw(), getPositions(), new Pose2d());

    //puts out the field
    field = new Field2d();
    SmartDashboard.putData("Field", field);
    SmartDashboard.putBoolean("Vision Enabled", VisionConstants.VISION_ENABLED_DEFAULT);

    configurePathPlanner();
  }

  public void simulationReset() {
    if (!RobotBase.isSimulation()) {
      return;
    }

    simYawDegrees = getYaw().getDegrees();
    for (int i = 0; i < 4; i++) {
      simWheelPositionsMeters[i] = 0.0;
      simWheelAngles[i] = new Rotation2d();
    }

    pigeon.setYaw(simYawDegrees);
    SwerveModulePosition[] positions = new SwerveModulePosition[4];
    for (int i = 0; i < 4; i++) {
      positions[i] = new SwerveModulePosition(0.0, simWheelAngles[i]);
    }
    odometry.resetPosition(Rotation2d.fromDegrees(simYawDegrees), positions, new Pose2d());
  }


  private void configurePathPlanner(){
    AutoBuilder.configure(
    this::getPose,
    this::resetOdometry,
    this::getChassisSpeeds,
    (speeds, feedforwards)-> driveFromChassisSpeeds(speeds, false),
    AutoConstants.SWERVE_DRIVE_CONTROLLER,
    AutoConstants.ROBOT_CONFIG,
    FieldConstants::isRedAlliance,
    this);
  }

  public Command autoDrive(String filename){
    try{
      PathPlannerPath path = PathPlannerPath.fromPathFile(filename);
      if (AutoConstants.isRightSideAuto()){
        path = path.mirrorPath();
      }
      return new FollowPathCommand(
      path,
      this::getPose, 
      this::getChassisSpeeds,
      (speeds, feedforwards) -> driveFromChassisSpeeds(speeds, isVisionEnabled()),
      new PPHolonomicDriveController(
      new PIDConstants(SwerveConstants.driveKP, SwerveConstants.driveKI, SwerveConstants.driveKD),
      new PIDConstants(SwerveConstants.driveKP, SwerveConstants.driveKI, SwerveConstants.driveKD)), 
      Constants.AutoConstants.ROBOT_CONFIG,
      Constants.FieldConstants::isRedAlliance,
      this);
    }
    catch(Exception e){
      DriverStation.reportError("PATHPLANNER ERROR" + e.getMessage(), e.getStackTrace());
      return Commands.none();
    }
  }

  public Command startAutoAt(double x, double y, double direction){
    return runOnce(()->{
      double newY = y;
      if (AutoConstants.isRightSideAuto()){
        newY = FieldConstants.FIELD_WIDTH - y;
      }
      Pose2d startPose2d = FieldConstants.flipForAlliance(new Pose2d(x, newY, Rotation2d.fromDegrees(direction)));
      pigeon.setYaw(startPose2d.getRotation().getDegrees());
      odometry.resetPosition(startPose2d.getRotation(),getPositions(),startPose2d);
    });
  }
  


  private boolean isVisionEnabled() {
    return SmartDashboard.getBoolean("Vision Enabled", VisionConstants.VISION_ENABLED_DEFAULT);
  }

  private void updateOdometryWithVision (String limelightName){
      boolean doRejectUpdate = false;
      
      //pass raw yaw + rate instead of estimate here for accuracy
      LimelightHelpers.SetRobotOrientation(limelightName, getYaw().getDegrees(),pigeon.getAngularVelocityZWorld().getValueAsDouble(),0,0,0,0);
      LimelightHelpers.PoseEstimate mt2 = LimelightHelpers.getBotPoseEstimate_wpiBlue_MegaTag2(limelightName);

      if (mt2 == null){
        return;
      }
      if(Math.abs(pigeon.getAngularVelocityZWorld().getValueAsDouble()) > VisionConstants.MAX_VISION_ANGULAR_RATE_DEG_PER_SEC)
      {
        doRejectUpdate = true;
      }

      //reject poses that aren't inside the field
      boolean isInField = mt2.pose.getX() > 0 && mt2.pose.getX() < FieldConstants.FIELD_LENGTH
                        && mt2.pose.getY() > 0 && mt2.pose.getY() < FieldConstants.FIELD_WIDTH;
      //reject poses if LL is too far away from tag
      boolean isCloseEnough = mt2.avgTagDist < 10; //10 is high, check what we think distance should be

      if(mt2.tagCount <= 0 || isInField == false || isCloseEnough == false)
      {
        doRejectUpdate = true;
      }
      if(!doRejectUpdate)
      {
        odometry.setVisionMeasurementStdDevs(
            VecBuilder.fill(
                VisionConstants.VISION_STD_DEV_X_METERS,
                VisionConstants.VISION_STD_DEV_Y_METERS,
                VisionConstants.VISION_STD_DEV_THETA_RADIANS)); // need to measure
        odometry.addVisionMeasurement(
          mt2.pose,
          mt2.timestampSeconds);
      }

      SmartDashboard.putBoolean("Is Vision Good", !doRejectUpdate);
      SmartDashboard.putNumber("Vision/" + limelightName + "/TagCount", mt2.tagCount);
      SmartDashboard.putNumber("Vision/" + limelightName + "/AvgTagDist", mt2.avgTagDist);
      SmartDashboard.putNumber("Vision/" + limelightName + "/LatencyMs", mt2.latency);
    }


  public void drive(double xInput, double yInput, double rotationInput, boolean isFieldOriented){
    ChassisSpeeds desiredSpeeds;

    if(isFieldOriented){
      desiredSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(xInput, yInput, rotationInput, getYaw());
    }
    else{
      desiredSpeeds = new ChassisSpeeds(xInput, yInput, rotationInput);
    }
    driveFromChassisSpeeds(desiredSpeeds, true);
  }
 
  public void driveFromChassisSpeeds(ChassisSpeeds driveSpeeds, boolean isOpenLoop){
    lastCommandedSpeeds = driveSpeeds;
    SwerveModuleState[] desiredStates = SwerveConstants.swerveKinematics.toSwerveModuleStates(driveSpeeds);
    SwerveDriveKinematics.desaturateWheelSpeeds(desiredStates, SwerveConstants.maxSpeed);

    desiredSwerveDataPublisher.set(desiredStates);

    for (SwerveModule mod : mSwerveMods) {
      mod.setDesiredState(desiredStates[mod.moduleNumber], isOpenLoop); //NEED CONFIRM
    }
  }

  public ChassisSpeeds getChassisSpeeds(){
    return SwerveConstants.swerveKinematics.toChassisSpeeds(getStates());
  }

  public ChassisSpeeds getLastCommandedSpeeds() {
    return lastCommandedSpeeds;
  }

  public Pose2d getPose() {
    return odometry.getEstimatedPosition();
  }

  public void resetOdometry(Pose2d pose) {
    odometry.resetPosition(getYaw(), getPositions(), pose);
  }


  public SwerveModuleState[] getStates() {
    SwerveModuleState[] states = new SwerveModuleState[4];
    for (SwerveModule mod : mSwerveMods) {
      states[mod.moduleNumber] = mod.getState();
    }
    return states;
  }

  public SwerveModulePosition[] getPositions(){
    SwerveModulePosition[] positions = new SwerveModulePosition[4];
    for (SwerveModule mod : mSwerveMods){
        positions[mod.moduleNumber] = mod.getPosition();
    }
    return positions;
}
  
  public double[] getEncoderRotations() {
    double[] distances = new double[4];
    for (SwerveModule mod : mSwerveMods){
      distances[mod.moduleNumber] = mod.getRawDriveEncoder() / SwerveConstants.wheelCircumference;
    }
    return distances;
  }

  public void zeroGyro() {
    if (FieldConstants.isRedAlliance()){
      pigeon.setYaw(180);
    }
    else {
      pigeon.setYaw(0);
    }
  }

  public Rotation2d getYaw() {
    //fancy if else loop again
    return (Constants.SwerveConstants.invertPigeon)
        ? Rotation2d.fromDegrees(360 - pigeon.getYaw().getValueAsDouble())
        : Rotation2d.fromDegrees(pigeon.getYaw().getValueAsDouble());
  }

  public void resyncModuleEncoders(){
    if(!DriverStation.isDisabled()){
      DriverStation.reportWarning
        ("Attempted to resync swerve module encoders while robot is enabled. Disable before resyncing",  
        false); //NEED CONFIRM
      return;
    }
    for (SwerveModule mod : mSwerveMods){
      mod.resyncToAbsolute();
    }
  }

    public void saveModuleOffsets(){
    saveModuleOffsets(new Rotation2d());
  }
  public void saveModuleOffsets(Rotation2d desiredAngle){
    if(!DriverStation.isDisabled()){
      DriverStation.reportWarning(
          "Attempted to save swerve module offsets while robot is enabled. Disable before calibrating.",
          false);
      return;
    }
    for (SwerveModule mod : mSwerveMods){
      mod.saveCanCoderOffset(desiredAngle);
    }
  }



  @Override
  public void periodic() {
    if (!RobotBase.isSimulation()) {
      odometry.update(getYaw(), getPositions());
      if (isVisionEnabled()) {
        for (String limelightName : VisionConstants.LIMELIGHT_NAMES) {
          updateOdometryWithVision(limelightName);
        }
      }
    }
    //sends robot pose to network tables
    field.setRobotPose(getPose());

    SmartDashboard.putNumber("Pigeon Yaw",  pigeon.getYaw().getValueAsDouble());

    for (SwerveModule mod : mSwerveMods) {

      double canCoderDegrees = mod.getCanCoder().getDegrees();

      SmartDashboard.putNumber(
          "Mod " + mod.moduleNumber + " Cancoder", mod.getCanCoder().getDegrees());
      SmartDashboard.putNumber(
          "Mod " + mod.moduleNumber + " Integrated", mod.getState().angle.getDegrees());
      SmartDashboard.putNumber(
          "Mod " + mod.moduleNumber + " Velocity", mod.getState().speedMetersPerSecond);
      SmartDashboard.putNumber(
          "Mod " + mod.moduleNumber + " New Cancoder Offset", 
        canCoderDegrees < 0 ? 360 + canCoderDegrees : canCoderDegrees);
  }
  swerveDataPublisher.set(getStates());
}

  /**
   * Simple swerve simulation: integrates the last commanded chassis speeds into wheel positions and
   * a yaw angle, then updates odometry from those simulated sensors.
   */
  public void simulationUpdate(double dtSeconds) {
    if (!RobotBase.isSimulation()) {
      return;
    }

    ChassisSpeeds speeds = DriverStation.isDisabled() ? new ChassisSpeeds() : lastCommandedSpeeds;

    simYawDegrees += Math.toDegrees(speeds.omegaRadiansPerSecond * dtSeconds);
    pigeon.setYaw(simYawDegrees);

    SwerveModuleState[] states = SwerveConstants.swerveKinematics.toSwerveModuleStates(speeds);
    SwerveDriveKinematics.desaturateWheelSpeeds(states, SwerveConstants.maxSpeed);

    SwerveModulePosition[] positions = new SwerveModulePosition[4];
    for (int i = 0; i < 4; i++) {
      simWheelPositionsMeters[i] += states[i].speedMetersPerSecond * dtSeconds;
      simWheelAngles[i] = states[i].angle;
      positions[i] = new SwerveModulePosition(simWheelPositionsMeters[i], simWheelAngles[i]);
    }

    odometry.update(Rotation2d.fromDegrees(simYawDegrees), positions);
  }

}
