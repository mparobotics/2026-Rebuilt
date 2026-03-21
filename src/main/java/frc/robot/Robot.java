// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.cscore.UsbCamera;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import frc.robot.Constants.AutoConstants;
import frc.robot.Tuning.TuningHelper;
import edu.wpi.first.wpilibj.DriverStation;


public class Robot extends TimedRobot {
  private Command m_autonomousCommand;

  private TuningHelper tuning;

  private final RobotContainer m_robotContainer;
  private final RobotSimulation m_robotSimulation;

  Thread visionThread;

  public Robot() {
    m_robotContainer = new RobotContainer();
    m_robotSimulation = new RobotSimulation(m_robotContainer);

    visionThread = new Thread(() -> {
      UsbCamera visionCam = CameraServer.startAutomaticCapture();
      visionCam.setResolution(640, 480);
    });
    visionThread.start();
  }

  @Override
  public void robotInit() {
    AutoConstants.initDashboard();
  }

  @Override
  public void robotPeriodic() {
    CommandScheduler.getInstance().run();
  }

  @Override
  public void disabledInit() {}

  @Override
  public void disabledPeriodic() {}

  @Override
  public void disabledExit() {}

  @Override
  public void autonomousInit() {
    m_autonomousCommand = m_robotContainer.getAutonomousCommand();

    if (m_autonomousCommand != null) {
      CommandScheduler.getInstance().schedule(m_autonomousCommand);
    }
  }

  @Override
  public void autonomousPeriodic() {}

  @Override
  public void autonomousExit() {}

  @Override
  public void teleopInit() {
    // Cancel any scheduled autonomous command when teleop starts.
    // This ensures only one command runs at a time.
    if (m_autonomousCommand != null) {
      m_autonomousCommand.cancel();
    }

  }

  @Override
  public void teleopPeriodic() {}

  @Override
  public void teleopExit() {}

  @Override
  public void testInit() {
    // Cancel all commands when entering test mode.
    CommandScheduler.getInstance().cancelAll();

    tuning = new TuningHelper(m_robotContainer.getIntakeSubsystem());
  }

  @Override
  public void testPeriodic() {
    tuning.periodic();
  }

  @Override
  public void testExit() {
    // clear the tuning helper
    tuning = null;
  }

  @Override
  public void simulationInit() {
    m_robotSimulation.simulationInit();
  }

  @Override
  public void simulationPeriodic() {
    m_robotSimulation.simulationPeriodic();
  }
}
