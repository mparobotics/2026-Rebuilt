package frc.robot.Command;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants.FieldConstants;
import frc.robot.Subsystems.SwerveSubsystem;

public class AutoAlign extends Command {


    private SwerveSubsystem m_SwerveSubsystem;
    public AutoAlign(SwerveSubsystem SwerveSubsystem){
        this.m_SwerveSubsystem = SwerveSubsystem;
        addRequirements(m_SwerveSubsystem);
    }



    @Override
    public void initialize(){

    }


    @Override
    public void execute(){
        Pose2d FieldPosition = m_SwerveSubsystem.getPose();    //Get robot position on field, in variable
        Translation2d HubLocation = new Translation2d(12, 13); //Hub location -> CHANGE
        
    }

    @Override
    public void end(boolean interrupted){

    }

    @Override
    public boolean isFinished(){
        return false;
    }

}
