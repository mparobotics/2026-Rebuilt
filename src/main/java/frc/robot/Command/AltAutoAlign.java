package frc.robot.Command;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants.FieldConstants;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

/* Drives the robot in an orbit around the hub while continuously facing the hub center */

public class AltAutoAlign extends Command {

    private SwerveSubsystem swerveSubsystem;
    private ShooterSubsystem shooterSubsystem;

    private final PIDController headingController = new PIDController(kHeadingKp,0,0);
    private final PIDController radiusController = new PIDController(kRadialKp, kRadialKi, kRadialKd);

    private static final double kDesiredOrbitRadiusMeters = 2; //placeholder
    private static final double kMaxRadialSpeedMetersPerSecond = 1.0; // Max speed for correcting radius errors
    private static final double kRadialKp = 0.1; //P-gain for radial distance correction
    private static final double kRadialKi = 0.0;
    private static final double kRadialKd = 0.0;
    private static final double kHeadingKp = 0.1; //P-gain for yaw control that faces the hub

    public AltAutoAlign(SwerveSubsystem swerveSubsystem, ShooterSubsystem shooterSubsystem){
        this.swerveSubsystem = swerveSubsystem;
        this.shooterSubsystem = shooterSubsystem;
        addRequirements(swerveSubsystem, shooterSubsystem);
        headingController.enableContinuousInput(-Math.PI, Math.PI);
        radiusController.setSetpoint(kDesiredOrbitRadiusMeters);
    }

   @Override
   public void initialize(){
        headingController.reset(); //Reset yaw PID state every time the command starts
        radiusController.reset();
   }


    @Override
   public void execute(){
    Pose2d FieldPosition = swerveSubsystem.getPose();    //Get robot position on field

    Translation2d HubLocation = new Translation2d(4.61,4.03); //Hub location
    HubLocation = FieldConstants.flipForAlliance(HubLocation); //Mirror the hub point when we are Red

    Translation2d robotToHub = HubLocation.minus(FieldPosition.getTranslation()); //Vector from robot to hub.
    double radialDistance = robotToHub.getNorm(); 
    /*translation2d that points from the robot to the hub 
    * getNorm() returns the vector's magnitude (length)
    * this line computes how far the robot currently is from the hub
    */
    // Stop driving if odometry is incorrect
    if (radialDistance < 0.05){
        swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(), true);
        return;
    }

    Translation2d radialDirection = robotToHub.div(radialDistance); //Unit vector that always points toward the hub
    //Radial vector rotated 90 degrees counterclockwise

    double radialPidOutput = radiusController.calculate(radialDistance);

    double radialSpeed = MathUtil.clamp(
        -radialPidOutput,
        -kMaxRadialSpeedMetersPerSecond,
        kMaxRadialSpeedMetersPerSecond
    );

    Translation2d fieldRelativeVelocity = radialDirection.times(radialSpeed);

    double speedMagnitude = fieldRelativeVelocity.getNorm(); // Total requested speed
    if(speedMagnitude > SwerveConstants.maxSpeed){
        fieldRelativeVelocity = 
            fieldRelativeVelocity.times(SwerveConstants.maxSpeed / speedMagnitude);
        // respect drivetrain max velocity
    }

    double desiredHeadingRadians = radialDirection.getAngle().getRadians() + Math.PI / 2.0;
    
    //Face straight at the hub while moving
    double headingFeedforward = 0.0;
    if (radialDistance > 1e-3){
        headingFeedforward = (radialDirection.getY()*fieldRelativeVelocity.getX() 
        - radialDirection.getX() * fieldRelativeVelocity.getY()) / radialDistance;
    }

    double headingRate = MathUtil.clamp(
        headingFeedforward +
        headingController.calculate((FieldPosition.getRotation().getRadians()), desiredHeadingRadians),
        -SwerveConstants.maxAngularVelocity,
        SwerveConstants.maxAngularVelocity);
    // Yaw PID output limited to drivetrain capabilities

    ChassisSpeeds requestedSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(
        fieldRelativeVelocity.getX(), 
        fieldRelativeVelocity.getY(), 
        headingRate, 
        FieldPosition.getRotation());
    // Convert into chassis-relative speeds

    swerveSubsystem.driveFromChassisSpeeds(requestedSpeeds, false);
    // Command the swerve in closed loop

    shooterSubsystem.setHoodAngle(ShooterSubsystem.HoodAngle.MED);
    //put up hood angle

    shooterSubsystem.setShooterSpeed(ShooterConstants.SHOOTER_SPEED);
    //Start shooter motor
    
   }



    @Override
    public void end(boolean interrupted){
        swerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(), true);
        // Stop the drivetrain

    }

    @Override
    public boolean isFinished(){
        return false;
    // Driver holds the trigger to stay in auto align
    }
}
