package frc.robot.Command;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Subsystems.SwerveSubsystem;

public class SimpleAutoAlign extends Command {
    
    private SwerveSubsystem swerveSubsystem;

    //target for how far away robot should be from hub
    private final double targetDistance = 5; //in meters - temporary need to check

    private final double cameraHeight = 0.5; //need to measure
    private final double aprilTagHeight = 1; //need to measure
    private final double cameraTilt = 0.001; //so math does not end up dividing by 0
    
    private final PIDController distanceController = new PIDController(0,0,0); //tune this
    private final PIDController rotationController = new PIDController(0,0,0); //tune this

    public SimpleAutoAlign(SwerveSubsystem swerveSubsystem){
        this.swerveSubsystem = swerveSubsystem;
        addRequirements(swerveSubsystem);
    }

    private boolean canSeeTag() {
        double tv = NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("tv").getDouble(0.0);
        return tv > 0;
    }
    
    private int getTagId() {
        double tid = NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("tid").getDouble(0.0);
        int tidInt = (int) tid;
        return tidInt;
    }

    private double getDistanceToTarget() {
        double ty = NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("ty").getDouble(0.0); // vertical angle offset in degrees
        double angleToTargetRadians = Math.toRadians(cameraTilt + ty);

        double distance = (aprilTagHeight - cameraHeight) / Math.tan(angleToTargetRadians);
        return distance;
    }

       private double getOffsetToTarget() {
        double xDist = NetworkTableInstance.getDefault().getTable("limelight-a").getEntry("tx").getDouble(0.0);
        return xDist;
    }

    @Override
    public void initialize() {
        distanceController.reset();
        rotationController.reset();
    }

    @Override
    public void execute() {

        double distance = getDistanceToTarget();
        //when i say offset i mean rotation offset
        double offset = getOffsetToTarget();

        //only auto align if distance is valid and can see tag 10
        if (!canSeeTag() || getTagId() != 10 || distance < 0) {
            swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(0,0,0), false);
            return;
        }
    
        double driveSpeed = distanceController.calculate(distance, targetDistance);
        double rotationSpeed = rotationController.calculate(offset, 0); //zero is facing straight from ll

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