package frc.robot.Command;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Subsystems.SwerveSubsystem;

public class SimpleAutoAlign extends Command {
    
    private final SwerveSubsystem swerveSubsystem;

    //Target for how far away the robot should be from the hub
    private static final double TARGET_DISTANCE_METERS = 2.1;

    //Camera geometry
    public static final double CAMERA_HEIGHT_METERS = 0.5;
    public static final double APRIL_TAG_HEIGHT_METERS = 1.0;
    public static final double CAMERA_TILT_DEG = 0.000001;

    //Distance PID tuning
    public static final double DISTANCE_KP = 0;
    public static final double DISTANCE_KI = 0;
    public static final double DISTANCE_KD = 0;

    //Rotation PID tuning
    public static final double ROTATION_KP = 0;
    public static final double ROTATION_KI = 0;
    public static final double ROTATION_KD = 0;
    
    //Tolerance and 
    public static final double DISTANCE_TOLERANCE_METERS = 0.08;
    public static final double ROTATION_TOLERANCE_DEG = 1.5;
    public static final double MAX_FORWARD_SPEED_MPS = 1.25;
    public static final double MAX_ROTATION_SPEED_RAD_PER_SEC = 2.5;

    private final PIDController distanceController = new PIDController(DISTANCE_KP, DISTANCE_KI, DISTANCE_KD);
    private final PIDController rotationController = new PIDController(ROTATION_KP, ROTATION_KI, ROTATION_KD);
    


    public SimpleAutoAlign(SwerveSubsystem swerveSubsystem){
        this.swerveSubsystem = swerveSubsystem;
        addRequirements(swerveSubsystem);
    }

    private boolean canSeeTag() {
        double tv = NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("tv").getDouble(0.0);
        return tv > 0;
    }
    
    private int getTagId() {
        return (int) NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("tid").getDouble(0.0);
    }

    private boolean isSupportedTag(int tagId) {
        return tagId == 10 || tagId == 9 || tagId == 25 || tagId == 11 || tagId == 27 || tagId == 8 || tagId == 24;
    }

    private double getDesiredAlignmentAngle(int tagId) {
        if (tagId == 11 || tagId == 27) {
            return 20.0;
        }
        if (tagId == 8 || tagId == 24) {
            return -20.0;
        }
        return 0.0;
    }

    private double getDistanceToTarget() {
        double ty = NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("ty").getDouble(0.0); // vertical angle offset in degrees
        double angleToTargetRadians = Math.toRadians(CAMERA_TILT_DEG + ty);
        return (APRIL_TAG_HEIGHT_METERS - CAMERA_HEIGHT_METERS) / Math.tan(angleToTargetRadians);
    }

    private double getOffsetToTarget() {
        return NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("tx").getDouble(0.0);
    }

    @Override
    public void initialize() {
        distanceController.reset();
        rotationController.reset();
        distanceController.setTolerance(DISTANCE_TOLERANCE_METERS);
        rotationController.setTolerance(ROTATION_TOLERANCE_DEG);
    }

    @Override
    public void execute() {
        double distance = getDistanceToTarget();
        double offset = getOffsetToTarget();
        int tagId = getTagId();

        //only auto align if distance is valid and can see tag 10
        if (!canSeeTag() || !isSupportedTag(tagId) || distance < 0 || Double.isNaN(distance) || Double.isFinite(distance)) {

            swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(0,0,0), false);
            return;
        }
    
        double desiredAlignmentAngle = getDesiredAlignmentAngle(tagId);
        double driveSpeed = distanceController.calculate(distance, TARGET_DISTANCE_METERS);
        double rotationSpeed = rotationController.calculate(offset, desiredAlignmentAngle);

        if (distanceController.atSetpoint()){
            driveSpeed = 0.0;
        }
        if (rotationController.atSetpoint()){
            rotationSpeed = 0.0;
        }

        driveSpeed = MathUtil.clamp(driveSpeed, -MAX_FORWARD_SPEED_MPS, MAX_FORWARD_SPEED_MPS);
        rotationSpeed = MathUtil.clamp(rotationSpeed, -MAX_ROTATION_SPEED_RAD_PER_SEC, MAX_ROTATION_SPEED_RAD_PER_SEC);

        swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(driveSpeed, 0, rotationSpeed), false);

    }

    @Override
    public void end(boolean interrupted) {
        swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(0,0,0), false);
    }

    @Override
    public boolean isFinished() {
        return false;
    }

}