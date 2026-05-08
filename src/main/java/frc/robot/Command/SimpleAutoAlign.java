package frc.robot.Command;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.filter.SlewRateLimiter;
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
    public static final double CAMERA_TILT_DEG = 0.0;

    //Distance PID tuning
    public static final double DISTANCE_KP = 0.6;
    public static final double DISTANCE_KI = 0;
    public static final double DISTANCE_KD = 0;

    //Rotation PID tuning
    public static final double ROTATION_KP = 0.02;
    public static final double ROTATION_KI = 0;
    public static final double ROTATION_KD = 0;
    
    //Tolerance and 
    public static final double DISTANCE_TOLERANCE_METERS = 0.08;
    public static final double ROTATION_TOLERANCE_DEG = 1.5;
    public static final double MAX_FORWARD_SPEED_MPS = 0.75;
    public static final double MAX_ROTATION_SPEED_RAD_PER_SEC = 2.5;
    public static final double MIN_DISTANCE_CALC_ANGLE_DEG = 1.0;
    public static final int SETTLE_CYCLES_REQUIRED = 10;
    public static final double UNLOCK_DISTANCE_ERROR_METERS = 0.15;
    public static final double UNLOCK_ROTATION_ERROR_DEG = 3.0;
    public static final double DISTANCE_FILTER_ALPHA = 0.25;
    public static final double MAX_DISTANCE_ACCEL_MPS_PER_SEC = 1.5;

    private final PIDController distanceController = new PIDController(DISTANCE_KP, DISTANCE_KI, DISTANCE_KD);
    private final PIDController rotationController = new PIDController(ROTATION_KP, ROTATION_KI, ROTATION_KD);
    private int settledCycles = 0;
    private boolean alignmentLocked = false;
    private double filteredDistanceMeters = Double.NaN;
    private final SlewRateLimiter distanceSpeedLimiter = new SlewRateLimiter(MAX_DISTANCE_ACCEL_MPS_PER_SEC);



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

    private double getVerticalOffsetToTarget(){
        return NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("ty").getDouble(0.0);
    }
    private double getOffsetToTarget() {
        return NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("tx").getDouble(0.0);
    }

    private double getDistanceToTargetMeters(double tyDegrees){
        double angleToTargetDegrees = CAMERA_TILT_DEG + tyDegrees;
        if (Math.abs(angleToTargetDegrees)<MIN_DISTANCE_CALC_ANGLE_DEG){
            return Double.NaN;
        }
        double tangent = Math.tan(Math.toRadians(angleToTargetDegrees));
        if (Math.abs(tangent) < 1e-6){
            return Double.NaN;
        }
        double distanceMeters = (APRIL_TAG_HEIGHT_METERS - CAMERA_HEIGHT_METERS) / tangent;
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0.0){
            return Double.NaN;
        }
        return distanceMeters;
    }

    private double filterDistanceMeters(double distanceMeters){
        if (!Double.isFinite(filteredDistanceMeters)){
            filteredDistanceMeters = distanceMeters;
        } else {
            filteredDistanceMeters += DISTANCE_FILTER_ALPHA * (distanceMeters - filteredDistanceMeters);
        }
        return filteredDistanceMeters;
    }

    @Override
    public void initialize() {
        distanceController.reset();
        rotationController.reset();
        distanceSpeedLimiter.reset(0.0);
        filteredDistanceMeters = Double.NaN;
        settledCycles = 0;
        alignmentLocked = false;
        distanceController.setTolerance(DISTANCE_TOLERANCE_METERS);
        rotationController.setTolerance(ROTATION_TOLERANCE_DEG);
    }

    @Override
    public void execute() {
        double offset = getOffsetToTarget();
        int tagId = getTagId();

        //only auto align when we have a visible, supported tag
        if (!canSeeTag() || !isSupportedTag(tagId)){
            filteredDistanceMeters = Double.NaN;
            distanceSpeedLimiter.reset(0.0);
            settledCycles = 0;
            alignmentLocked = false;
            swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(0,0,0), false);
            return;
        }

        double ty = getVerticalOffsetToTarget();
        double distance = getDistanceToTargetMeters(ty);
        boolean distanceIsValid = Double.isFinite(distance);
        double filteredDistance = distanceIsValid ? filterDistanceMeters(distance) : Double.NaN;
    
        double desiredAlignmentAngle = getDesiredAlignmentAngle(tagId);
        double rotationSpeed = rotationController.calculate(offset, desiredAlignmentAngle);

        double rotationError = offset - desiredAlignmentAngle;
        double distanceError = distanceIsValid ? filteredDistance - TARGET_DISTANCE_METERS : Double.NaN;

        boolean withinRotationTolerance = Math.abs(rotationError) <= ROTATION_TOLERANCE_DEG;
        boolean withinDistanceTolerance = distanceIsValid && Math.abs(distanceError) <= DISTANCE_TOLERANCE_METERS;

        if (withinRotationTolerance && withinDistanceTolerance){
            settledCycles++;
        } else {
            settledCycles = 0;
        }

        alignmentLocked = settledCycles >= SETTLE_CYCLES_REQUIRED;
        if (alignmentLocked){
            distanceSpeedLimiter.reset(0.0);
            swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(0, 0, 0), false);
            return;
        }
        double driveSpeed = 0.0;

        if (distanceIsValid && Math.abs (rotationError) <= UNLOCK_ROTATION_ERROR_DEG &&! withinDistanceTolerance){
            driveSpeed = distanceController.calculate(0.0, distanceError);
        }
      

        driveSpeed = MathUtil.clamp(driveSpeed, -MAX_FORWARD_SPEED_MPS, MAX_FORWARD_SPEED_MPS);
        driveSpeed = distanceSpeedLimiter.calculate(driveSpeed);
        rotationSpeed = MathUtil.clamp(rotationSpeed, -MAX_ROTATION_SPEED_RAD_PER_SEC, MAX_ROTATION_SPEED_RAD_PER_SEC);

        swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(0, driveSpeed, rotationSpeed), false);

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