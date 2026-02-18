// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.lib.test.DiagnosticTest;
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
    
    private static final String PARAM_PREFIX = "DiagnosticTests/Parameters/LED State Test/";
    private static final String RESULT_PREFIX = "LEDStateTest/";
    
    private final CandleSubsystem candleSubsystem;
    private final SendableChooser<String> ledStateChooser;
    
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
        this.ledStateChooser = new SendableChooser<>();
        
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
        // Set up SendableChooser dropdown for LedStates enum
        // Add all enum values as options
        CandleSubsystem.LedStates[] states = CandleSubsystem.LedStates.values();
        if (states.length > 0) {
            ledStateChooser.setDefaultOption(states[0].name(), states[0].name());
            for (int i = 1; i < states.length; i++) {
                ledStateChooser.addOption(states[i].name(), states[i].name());
            }
        }
        SmartDashboard.putData(PARAM_PREFIX + "LedState", ledStateChooser);
        
        // Set up duration parameter
        SmartDashboard.putNumber(PARAM_PREFIX + "Duration", 3.0);
    }
    
    @Override
    public void initialize() {
        // Read parameters from SmartDashboard
        // Note: We retrieve the chooser from SmartDashboard because initializeParameters()
        // was called on a different (throwaway) instance. The chooser on SmartDashboard
        // contains the user's selection.
        SendableChooser<String> chooser = (SendableChooser<String>) SmartDashboard.getData(PARAM_PREFIX + "LedState");
        String selectedStateName = null;
        if (chooser != null) {
            selectedStateName = chooser.getSelected();
        }
        if (selectedStateName == null) {
            selectedStateName = CandleSubsystem.LedStates.None.name();
        }
        
        // Convert string to enum
        try {
            selectedLedState = CandleSubsystem.LedStates.valueOf(selectedStateName);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid LED state: " + selectedStateName + ". Using None.");
            selectedLedState = CandleSubsystem.LedStates.None;
        }
        
        duration = SmartDashboard.getNumber(PARAM_PREFIX + "Duration", 3.0);
        
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
        SmartDashboard.putString(RESULT_PREFIX + "Status", "Running");
        SmartDashboard.putString(RESULT_PREFIX + "SelectedState", selectedLedState.name());
        SmartDashboard.putNumber(RESULT_PREFIX + "TargetDuration", duration);
        
        System.out.println("LED State Test started: State=" + selectedLedState.name() + ", Duration=" + duration + "s");
    }
    
    @Override
    public void execute() {
        // Test just waits - LED state is already set in initialize()
        // Update actual duration for display
        actualDuration = Timer.getFPGATimestamp() - startTime;
        SmartDashboard.putNumber(RESULT_PREFIX + "ElapsedTime", actualDuration);
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
            SmartDashboard.putString(RESULT_PREFIX + "Status", "Interrupted");
            System.out.println("LED State Test interrupted after " + String.format("%.2f", actualDuration) + "s");
        } else {
            SmartDashboard.putString(RESULT_PREFIX + "Status", "Complete");
            System.out.println("LED State Test completed: Ran for " + String.format("%.2f", actualDuration) + "s");
        }
        
        SmartDashboard.putNumber(RESULT_PREFIX + "ActualDuration", actualDuration);
    }
}
