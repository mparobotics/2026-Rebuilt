// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj2.command.Command;
import frc.lib.SendableChooserUtil;
import frc.lib.test.DiagnosticTest;
import frc.lib.test.TestDashboard;
import frc.robot.Subsystems.CandleSubsystem;

/**
 * Diagnostic test for the CandleSubsystem LED states.
 * 
 * <p>This test allows developers to independently test the CandleSubsystem by setting
 * any LED state for a specified duration. This is useful for:
 * <ul>
 *   <li>Verifying LED hardware functionality</li>
 *   <li>Testing LED state configurations without other robot systems</li>
 *   <li>Validating visual feedback during development</li>
 *   <li>Debugging LED-related issues</li>
 * </ul>
 * 
 * <p>The test sets the selected LED state, runs for the specified duration, then
 * automatically turns the LEDs off. This provides a safe, isolated way to test
 * the CandleSubsystem independent of other robot subsystems.
 */
public class LedStateTestCommand extends Command implements DiagnosticTest {
    
    private final CandleSubsystem candleSubsystem;
    private SendableChooser<CandleSubsystem.LedStates> ledStateChooser;
    
    // Test parameters (read from SmartDashboard in initialize())
    private CandleSubsystem.LedStates selectedLedState;
    private double duration;
    
    // Test state
    private double startTime;
    private double actualDuration;
    
    /**
     * Creates a new LED State Test command.
     * 
     * @param candleSubsystem The candle subsystem to control
     */
    public LedStateTestCommand(CandleSubsystem candleSubsystem) {
        this.candleSubsystem = candleSubsystem;
        
        addRequirements(candleSubsystem);
    }
    
    @Override
    public String getTestName() {
        return "LED State Test";
    }
    
    @Override
    public String getTestDescription() {
        return "Tests CandleSubsystem LED states independently. Sets the selected LED state for a "
             + "specified duration, then turns LEDs off. Useful for verifying LED hardware functionality "
             + "and testing visual feedback without other robot systems.";
    }
    
    @Override
    public void initializeParameters() {
        // Set up duration parameter first
        TestDashboard.putParamDouble(this, "Duration", 3.0);
        
        // Set up SendableChooser dropdown for LedStates enum — stores enum values directly
        ledStateChooser = SendableChooserUtil.fromEnum(CandleSubsystem.LedStates.class);
        TestDashboard.putParamChooser(this, "LedState", ledStateChooser);
    }
    
    @Override
    public void initialize() {
        // Read parameters from SmartDashboard
        // Note: We retrieve the chooser from SmartDashboard because initializeParameters()
        // was called on a different (throwaway) instance. The chooser on SmartDashboard
        // contains the user's selection.
        // The chooser stores enum values directly — no string-to-enum conversion needed.
        selectedLedState = TestDashboard.getParamChooserSelected(
            this,
            "LedState",
            CandleSubsystem.LedStates.None
        );
        
        duration = TestDashboard.getParamDouble(this, "Duration", 3.0);
        
        // Validate duration
        if (duration <= 0) {
            System.err.println("Warning: Duration must be positive. Using default 3.0 seconds.");
            duration = 3.0;
        }
        
        // Initialize test state
        startTime = Timer.getFPGATimestamp();
        actualDuration = 0.0;
        
        // Set LED state
        candleSubsystem.changeState(selectedLedState);
        
        // Initialize result display
        TestDashboard.putResultString(this, "Status", "Running");
        TestDashboard.putResultString(this, "SelectedState", selectedLedState.name());
        TestDashboard.putResultDouble(this, "TargetDuration", duration);
        
        System.out.println("LED State Test started: State=" + selectedLedState.name() + ", Duration=" + duration + "s");
    }
    
    @Override
    public void execute() {
        // Test just waits - LED state is already set in initialize()
        // Update actual duration for display
        actualDuration = Timer.getFPGATimestamp() - startTime;
        TestDashboard.putResultDouble(this, "ElapsedTime", actualDuration);
    }
    
    @Override
    public boolean isFinished() {
        // Test completes when duration has elapsed
        return (Timer.getFPGATimestamp() - startTime) >= duration;
    }
    
    @Override
    public void end(boolean interrupted) {
        // Calculate actual duration
        actualDuration = Timer.getFPGATimestamp() - startTime;
        
        // Turn LEDs off
        candleSubsystem.changeState(CandleSubsystem.LedStates.None);
        
        // Update result display
        if (interrupted) {
            TestDashboard.putResultString(this, "Status", "Interrupted");
            System.out.println("LED State Test interrupted after " + String.format("%.2f", actualDuration) + "s");
        } else {
            TestDashboard.putResultString(this, "Status", "Complete");
            System.out.println("LED State Test completed: Ran for " + String.format("%.2f", actualDuration) + "s");
        }
        
        TestDashboard.putResultDouble(this, "ActualDuration", actualDuration);
    }
}
