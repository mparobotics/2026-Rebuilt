// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.lib.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import edu.wpi.first.wpilibj2.command.Command;
import frc.lib.test.DiagnosticTest;
import frc.lib.test.TestDashboard;
import frc.robot.test.SwerveAngleDriftTestCommand;

/**
 * Basic validation tests for diagnostic test framework functionality.
 * These tests check for obvious runtime errors like null pointer exceptions,
 * parameter validation, and basic logic correctness.
 *
 * Note: These tests don't require hardware and can be run on any machine.
 */
class DiagnosticTestValidationTest {

    /** Set to true to enable verbose output for debugging. */
    private static final boolean DEBUG = false;

    /**
     * Simple test command that validates parameters.  This allows testing parameter
     * validation logic without requiring the full command or hardware.
     */
    private static class ParameterValidationTestCommand extends Command implements DiagnosticTest {
        private enum ValidationState {
            VALID,
            COMPLETE  // Set when validation fails
        }

        private ValidationState state = ValidationState.VALID;

        @Override
        public String getTestName() {
            return "Parameter Validation Test";
        }

        @Override
        public void initializeParameters() {
            // Set up SmartDashboard parameters with default values (same as SwerveAngleDriftTestCommand)
            TestDashboard.putParamInt(this, "ModuleNumber", 0);
            TestDashboard.putParamDouble(this, "Angle", 90.0);
            TestDashboard.putParamInt(this, "NumberOfCycles", 10);
            TestDashboard.putParamDouble(this, "AngleTolerance", 2.0);
            TestDashboard.putParamDouble(this, "MaxWaitTime", 1.0);
            TestDashboard.putParamDouble(this, "MinHoldTime", 0.5);
        }

        @Override
        public void initialize() {
            // Read parameters from SmartDashboard (same as SwerveAngleDriftTestCommand)
            int moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);
            double testAngleDegrees = TestDashboard.getParamDouble(this, "Angle", 90.0);
            int numberOfCycles = TestDashboard.getParamInt(this, "NumberOfCycles", 10);

            // Validate parameters (same validation logic as SwerveAngleDriftTestCommand)
            if (moduleNumber < 0 || moduleNumber > 3) {
                if (DEBUG) System.err.println("ERROR: Invalid module number: " + moduleNumber + ". Must be 0-3.");
                state = ValidationState.COMPLETE;
                return;
            }
            if (numberOfCycles < 1) {
                if (DEBUG) System.err.println("ERROR: Number of cycles must be at least 1. Got: " + numberOfCycles);
                state = ValidationState.COMPLETE;
                return;
            }
            if (testAngleDegrees < 0 || testAngleDegrees >= 360) {
                if (DEBUG) System.err.println("WARNING: Test angle should be 0-360 degrees. Using: " + testAngleDegrees);
            }

            // Simulate module null check (in real command, this would call swerveSubsystem.getModule())
            // For testing, we'll simulate this by checking if moduleNumber is valid but module is null
            // In this simple test, we'll just validate the parameters themselves
            state = ValidationState.VALID;
        }

        @Override
        public boolean isFinished() {
            return state == ValidationState.COMPLETE;
        }

        @Override
        public void execute() {
            // No-op for this test command
        }
    }

    /**
     * Tests that TestCycleResult record can be created with valid data.
     * This verifies the record structure and drift calculation logic.
     */
    @Test
    void testCycleResultCreation() {
        // Test with typical values
        SwerveAngleDriftTestCommand.TestCycleResult result =
            SwerveAngleDriftTestCommand.TestCycleResult.create(
                1,  // cycleNumber
                90.0, 100.0, false,  // relativeAtTarget, absoluteAtTarget, timeoutAtTarget
                0.0, 5.0, false      // relativeAtZero, absoluteAtZero, timeoutAtZero
            );

        assertNotNull(result);
        assertEquals(1, result.cycleNumber());
        assertEquals(90.0, result.relativeEncoderAtTarget());
        assertEquals(100.0, result.absoluteEncoderAtTarget());
        assertEquals(0.0, result.relativeEncoderAtZero());
        assertEquals(5.0, result.absoluteEncoderAtZero());
        assertFalse(result.wasTimeoutAtTarget());
        assertFalse(result.wasTimeoutAtZero());

        // Verify drift calculation (should be -10.0 degrees at target, -5.0 at zero)
        assertEquals(-10.0, result.driftAtTarget(), 0.001);
        assertEquals(-5.0, result.driftAtZero(), 0.001);
    }

    /**
     * Tests drift calculation with angles that wrap around 360 degrees.
     */
    @Test
    void testCycleResultDriftCalculationWithWrapAround() {
        // Test case: relative encoder at 350°, absolute at 10°
        // The drift should be calculated correctly accounting for wrap-around
        SwerveAngleDriftTestCommand.TestCycleResult result =
            SwerveAngleDriftTestCommand.TestCycleResult.create(
                1,
                350.0, 10.0, false,  // 350° - 10° = 340°, but normalized should be -20°
                0.0, 0.0, false
            );

        // IEEEremainder(350 - 10, 360) = IEEEremainder(340, 360) = -20
        assertEquals(-20.0, result.driftAtTarget(), 0.001);
    }

    /**
     * Tests that parameter validation works correctly.
     * Uses a simple local test command.  This test verifies that invalid parameters
     * cause the command to finish immediately.
     */
    @Test
    void testParameterValidation() {
        // Test 1: Invalid module number (< 0)
        ParameterValidationTestCommand testCommand = new ParameterValidationTestCommand();
        testCommand.initializeParameters();
        TestDashboard.putParamInt(testCommand, "ModuleNumber", -1);
        TestDashboard.putParamInt(testCommand, "NumberOfCycles", 10);
        TestDashboard.putParamDouble(testCommand, "Angle", 90.0);
        testCommand.initialize();
        assertTrue(testCommand.isFinished(), "Command should finish immediately with invalid module number < 0");

        // Test 2: Invalid module number (> 3)
        testCommand = new ParameterValidationTestCommand();
        testCommand.initializeParameters();
        TestDashboard.putParamInt(testCommand, "ModuleNumber", 4);
        TestDashboard.putParamInt(testCommand, "NumberOfCycles", 10);
        TestDashboard.putParamDouble(testCommand, "Angle", 90.0);
        testCommand.initialize();
        assertTrue(testCommand.isFinished(), "Command should finish immediately with invalid module number > 3");

        // Test 3: Invalid number of cycles (< 1)
        testCommand = new ParameterValidationTestCommand();
        testCommand.initializeParameters();
        TestDashboard.putParamInt(testCommand, "ModuleNumber", 0);
        TestDashboard.putParamInt(testCommand, "NumberOfCycles", 0);
        TestDashboard.putParamDouble(testCommand, "Angle", 90.0);
        testCommand.initialize();
        assertTrue(testCommand.isFinished(), "Command should finish immediately with numberOfCycles < 1");

        // Test 4: Valid parameters should pass validation
        testCommand = new ParameterValidationTestCommand();
        testCommand.initializeParameters();
        TestDashboard.putParamInt(testCommand, "ModuleNumber", 0);
        TestDashboard.putParamInt(testCommand, "NumberOfCycles", 10);
        TestDashboard.putParamDouble(testCommand, "Angle", 90.0);
        testCommand.initialize();
        assertFalse(testCommand.isFinished(), "Command should continue with valid parameters");
    }

    /**
     * Tests that SmartDashboard parameter initialization works correctly.
     * Verifies that initializeParameters() sets up all required parameters with correct default values.
     */
    @Test
    void testDashboardInitialization() {
        ParameterValidationTestCommand testCommand = new ParameterValidationTestCommand();

        // Call initializeParameters() as the DiagnosticTestManager framework would
        testCommand.initializeParameters();

        // Verify all parameters are initialized with correct default values
        assertEquals(0, TestDashboard.getParamInt(testCommand, "ModuleNumber", -999),
            "ModuleNumber should default to 0");
        assertEquals(90.0, TestDashboard.getParamDouble(testCommand, "Angle", -999.0), 0.001,
            "Angle should default to 90.0 degrees");
        assertEquals(10, TestDashboard.getParamInt(testCommand, "NumberOfCycles", -999),
            "NumberOfCycles should default to 10");
        assertEquals(2.0, TestDashboard.getParamDouble(testCommand, "AngleTolerance", -999.0), 0.001,
            "AngleTolerance should default to 2.0 degrees");
        assertEquals(1.0, TestDashboard.getParamDouble(testCommand, "MaxWaitTime", -999.0), 0.001,
            "MaxWaitTime should default to 1.0 seconds");
        assertEquals(0.5, TestDashboard.getParamDouble(testCommand, "MinHoldTime", -999.0), 0.001,
            "MinHoldTime should default to 0.5 seconds");
    }
}
