// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.simulation.BatterySim;
import edu.wpi.first.wpilibj.simulation.DriverStationSim;
import edu.wpi.first.wpilibj.simulation.RoboRioSim;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.SwerveSubsystem;

/**
 * Desktop simulation hooks.
 *
 * <p>This class is called from {@link Robot#simulationInit()} and {@link Robot#simulationPeriodic()}
 * so the "WPILib: Simulate Robot Code" action in VS Code can run your robot with a simple physics
 * model.
 */
public class RobotSimulation {
  private final SwerveSubsystem drive;
  private double lastTimestampSeconds = Timer.getFPGATimestamp();

  public RobotSimulation(RobotContainer robotContainer) {
    this.drive = robotContainer.getDriveSubsystem();
  }

  public void simulationInit() {
    if (!RobotBase.isSimulation()) {
      return;
    }

    // Leave the robot disabled by default so the Sim GUI Driver Station can control mode
    // (Disabled / Auto / Teleop).
    DriverStationSim.setDsAttached(true);
    DriverStationSim.setEnabled(false);
    DriverStationSim.setAutonomous(false);
    DriverStationSim.setTest(false);
    DriverStationSim.notifyNewData();
    drive.simulationReset();
    lastTimestampSeconds = Timer.getFPGATimestamp();
  }

  public void simulationPeriodic() {
    if (!RobotBase.isSimulation()) {
      return;
    }

    final double now = Timer.getFPGATimestamp();
    final double dtSeconds = MathUtil.clamp(now - lastTimestampSeconds, 0.0, 0.05);
    lastTimestampSeconds = now;

    drive.simulationUpdate(dtSeconds);

    var speeds = drive.getLastCommandedSpeeds();
    double driveFraction =
        Math.hypot(speeds.vxMetersPerSecond, speeds.vyMetersPerSecond) / SwerveConstants.maxSpeed;
    double rotateFraction =
        Math.abs(speeds.omegaRadiansPerSecond) / SwerveConstants.maxAngularVelocity;
    double estimatedCurrentAmps = 8.0 + 80.0 * MathUtil.clamp(driveFraction, 0.0, 1.0)
        + 40.0 * MathUtil.clamp(rotateFraction, 0.0, 1.0);

    RoboRioSim.setVInVoltage(BatterySim.calculateDefaultBatteryLoadedVoltage(estimatedCurrentAmps));
  }
}
