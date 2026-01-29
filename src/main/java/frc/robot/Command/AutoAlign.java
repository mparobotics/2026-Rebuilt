package frc.robot.Command;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants.FieldConstants;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.SwerveSubsystem;

public class AutoAlign extends Command {

    private SwerveSubsystem m_SwerveSubsystem;
    private boolean m_orbitLeft = true; 
    //true = counterclockwise arc, false = clockwise arc


    //Orbit tuning constants (NEED CHANGE - kDesiredOrbitRadiusMeters, kTangentialSpeedMetersPerSecond)
    private static final double kDesiredOrbitRadiusMeters = 3.5; //How far from the hub we want the robot to be
    private static final double kTangentialSpeedMetersPerSecond = 1.25; // Constant speed for sliding around the hub
    private static final double kMaxRadialSpeedMetersPerSecond = 1.0; // Max speed for correcting radius errors
    private static final double kRadialKp = 1.6; //P-gain for radial distance correction
    private static final double kHeadingKp = 4.5; //P-gain for yaw control that faces the hub

    //PID that holds the robot's yaw pointed at the hub while driving the arc
    private final PIDController m_headingController = new PIDController(kHeadingKp,0,0);

    public AutoAlign(SwerveSubsystem SwerveSubsystem){
        this.m_SwerveSubsystem = SwerveSubsystem;
        addRequirements(m_SwerveSubsystem);
        m_headingController.enableContinuousInput(-Math.PI, Math.PI);
        // Continuous input so heading errors are around ±π
    }

    public AutoAlign(SwerveSubsystem SwerveSubsystem, boolean orbitLeft){
        this(SwerveSubsystem); //reuse the constructor for setup
        this.m_orbitLeft = orbitLeft;
    }



    @Override
    public void initialize(){
        m_headingController.reset(); //Reset yaw PID state every time the command starts
    }


    @Override
    public void execute(){
        Pose2d FieldPosition = m_SwerveSubsystem.getPose();    //Get robot position on field

        Translation2d HubLocation = new Translation2d(4.61,4.03); //Hub location
        HubLocation = FieldConstants.flipForAlliance(HubLocation); //Mirror the hub point when we are Red

        Translation2d robotToHub = HubLocation.minus(FieldPosition.getTranslation()); //Vector pointing at hub ???
        double radialDistance = robotToHub.getNorm(); 
        /*translation2d that points from the robot to the hub 
         * getNorm() returns the vector's magnitude (length)
         * this line computes how far the robot currently is from the hub
        */

        // Stop driving if odometry is incorrect
        if (radialDistance < 0.05){
            m_SwerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(), true);
            return;
        }

        Translation2d radialDirection = robotToHub.div(radialDistance); //Unit vector that always points toward the hub
        Translation2d tangentialDirection = new Translation2d(-radialDirection.getY(), radialDirection.getX());
        //Radial vector rotated 90 degrees counterclockwise

        if(!m_orbitLeft){
            tangentialDirection = tangentialDirection.times(-1);
            //flip the tangent so we can orbit clockwise when needed
        }

        double radiusError = radialDistance - kDesiredOrbitRadiusMeters; //Positive -> slid too far away
        double radialSpeed = MathUtil.clamp(
            radiusError * kRadialKp, 
            -kMaxRadialSpeedMetersPerSecond, 
            kMaxRadialSpeedMetersPerSecond);
        //P loop to correct the radius


        Translation2d tangentialVelocity = tangentialDirection.times(kTangentialSpeedMetersPerSecond);
        // Constant arc speed
        Translation2d radialVelocity = radialDirection.times(radialSpeed); 
        // Radius correction
        Translation2d fieldRelativeVelocity = tangentialVelocity.plus(radialVelocity);
        //Motion wanted in field coordinates


        double speedMagnitude = fieldRelativeVelocity.getNorm(); // Total requested speed
        if(speedMagnitude > SwerveConstants.maxSpeed){
            fieldRelativeVelocity = 
                fieldRelativeVelocity.times(SwerveConstants.maxSpeed / speedMagnitude);
            // respect drivetrain max velocity
        }


        double desiredHeadingRadians = radialDirection.getAngle().getRadians();
        //Face stright at the hub while moving
        double headingRate = MathUtil.clamp(
            m_headingController.calculate((FieldPosition.getRotation().getRadians()), desiredHeadingRadians),
            -SwerveConstants.maxAngularVelocity,
            SwerveConstants.maxAngularVelocity);
        // Yaw PID output limited to drivetrain capabilities

        
        ChassisSpeeds requestedSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(
            fieldRelativeVelocity.getX(), 
            fieldRelativeVelocity.getY(), 
            headingRate, 
            FieldPosition.getRotation());
        // Convert into chassis-relative speeds


        m_SwerveSubsystem.driveFromChassisSpeeds(requestedSpeeds, false);
        // Command the swerve in closed loop
        
    }

    @Override
    public void end(boolean interrupted){
        m_SwerveSubsystem.driveFromChassisSpeeds(new ChassisSpeeds(), true);
        // Stop the drivetrain

    }

    @Override
    public boolean isFinished(){
        return false;
    // Driver holds the trigger to stay in auto align
    }

}
