package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import frc.robot.Subsystems.ShooterSubsystem;
import frc.robot.Subsystems.SwerveSubsystem;

public class EightLemonAuto extends SequentialCommandGroup{
    public EightLemonAuto (SwerveSubsystem drive, ShooterSubsystem shoot) {
        addCommands(
            drive.startAutoAt(3, 7.276, 180), //placeholder numbers from wherever we start auto
            drive.autoDrive("EightLemonPath"),
            shoot.autoShoot(),
            new WaitCommand(3.0),
            shoot.autoFeed(),
            new WaitCommand(3.0),
            shoot.autoStopFeed()
        );
    }

}
