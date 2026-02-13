// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import frc.robot.sim.SimulationManager;

/**
 * Main robot class that extends TimedRobot. This is the entry point for the robot program
 * and manages the robot lifecycle across different modes (disabled, autonomous, teleop, test).
 * 
 * <p>The Robot class:
 * <ul>
 *   <li>Creates and initializes the RobotContainer which sets up subsystems and command bindings</li>
 *   <li>Runs the CommandScheduler every 20ms to execute active commands and check button bindings</li>
 *   <li>Handles mode transitions (autonomous, teleop, test) and manages command lifecycle</li>
 * </ul>
 */
public class Robot extends TimedRobot {
  private Command m_autonomousCommand;

  private final RobotContainer m_robotContainer;

  // Simulation support
  private SimulationManager simManager;

  /**
   * Constructs the Robot. Initializes the RobotContainer which creates subsystems
   * (subsystems configure themselves) and sets up command bindings.
   */
  public Robot() {
    m_robotContainer = new RobotContainer();
  }

  @Override
  public void robotInit() {
  }

  /**
   * Called every 20ms during all robot modes. Runs the CommandScheduler which
   * executes active commands, checks button/trigger bindings, and updates subsystems.
   */
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
  }

  @Override
  public void testPeriodic() {}

  @Override
  public void testExit() {}

  @Override
  public void simulationInit() {
    // Initialize simulation manager for driver practice simulation
    simManager = new SimulationManager(m_robotContainer.getSwerveSubsystem());
  }

  @Override
  public void simulationPeriodic() {
    // Run simulation manager (handles both normal simulation and API testing)
    if (simManager != null) {
      simManager.simulationPeriodic();
    }
  }
}
