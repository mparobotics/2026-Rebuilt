package frc.robot.Auto;

import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Subsystems.SwerveSubsystem;

public class EightLemonAuto extends SequentialCommandGroup{
    public EightLemonAuto (SwerveSubsystem drive) {
        addCommands(
            drive.startAutoAt(7.13, 7.276, 180),
            drive.autoDrive("")
        );
    }

}
