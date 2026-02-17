# Diagnostic Testing Framework Design Proposal

## Executive Summary

This proposal outlines a framework for managing and executing diagnostic tests for the FRC robot. The design leverages WPILib's test mode to maintain clear separation between production and test code, provides a centralized registry for test discovery, and integrates with SmartDashboard for user-friendly test selection and execution.

## Research Summary: FRC Best Practices

Based on WPILib documentation and FRC community practices:

1. **Test Mode Architecture**: WPILib provides built-in `testInit()`, `testPeriodic()`, and `testEnd()` methods specifically for system verification. This provides natural separation from production code.

2. **Command-Based Testing**: Since FRC uses command-based architecture, diagnostic tests should be implemented as Commands. This allows:
   - Automatic lifecycle management via CommandScheduler
   - Easy integration with SmartDashboard using `SmartDashboard.putData()`
   - Consistent patterns with production code

3. **Dashboard Integration**: Two recommended approaches:
   - **Command Buttons**: Use `SmartDashboard.putData()` to expose commands as buttons (easiest method per WPILib docs)
   - **Dropdown Selection**: Use `SendableChooser` for test selection (more scalable for many tests)

4. **Code Organization**: 
   - Framework code in `frc.lib.test` package (reusable infrastructure)
   - Test implementations in `frc.robot.test` package (test-specific code)
   - Production code never imports test classes
   - Tests depend on framework (one-way dependency)

## Proposed Architecture

### 1. Core Components

#### `DiagnosticTest` Interface
- **Purpose**: Defines a common contract for all diagnostic tests
- **Design**: Interface (not abstract class) - tests already extend `Command`
- **Methods**:
  - `String getTestName()` - Display name for dashboard
  - `String getTestDescription()` - Optional description (default implementation provided)
  - `void initializeParameters()` - Initialize SmartDashboard parameters (optional, default empty)
  - `DiagnosticTestResult getResult()` - Get test result (optional, default returns null)
- **Rationale**: Allows tests to remain Commands while providing metadata and result reporting

#### `DiagnosticTestResult` Class
- **Purpose**: Standardized result reporting for tests
- **Fields**:
  - `TestStatus status` - Test completion status (SUCCESS, FAILED, CANCELLED, ERROR)
  - `String message` - Human-readable result message
  - `Map<String, MetricValue> metrics` - Key-value pairs for test metrics (supports Double, Integer, Boolean, String)
  - `List<String> warnings` - List of warnings encountered
- **Convenience Methods**:
  - `boolean passed()` - Returns true if status == SUCCESS (for backward compatibility)
- **Benefits**: Consistent result format, supports multiple metric types, distinguishes between failure and cancellation

#### `TestParameterHelper` Utility Class
- **Purpose**: Simplifies reading parameters from SmartDashboard
- **Methods**:
  - `static double getDouble(String key, double defaultValue)`
  - `static int getInt(String key, int defaultValue)`
  - `static boolean getBoolean(String key, boolean defaultValue)`
  - `static void putDouble(String key, double defaultValue)`
  - `static void putInt(String key, int defaultValue)`
  - `static void putBoolean(String key, boolean defaultValue)`
- **Benefits**: Reduces boilerplate, handles parameter initialization automatically

#### `DiagnosticTestRegistry`
- **Purpose**: Central registry of available diagnostic tests
- **Design Options**:
  - **Option A (Recommended)**: Enum-based registry
    - Compile-time registration
    - Type-safe
    - Easy to see all tests in one place
    - Each enum value has factory method to create test instance
  - **Option B**: Dynamic registration
    - Runtime discovery via reflection
    - More flexible but less type-safe
    - Harder to debug
- **Recommendation**: Start with Enum-based, can migrate to dynamic later if needed
- **Provides**: Test metadata, factory methods, test discovery

#### `DiagnosticTestManager`
- **Purpose**: Manages test lifecycle and SmartDashboard integration
- **Responsibilities**:
  - Initialize dashboard UI in `testInit()`
  - Initialize parameters for selected test
  - Poll dashboard for test selection/start requests in `testPeriodic()`
  - Read parameters from SmartDashboard when creating test
  - Coordinate test execution via CommandScheduler
  - Update status displays
  - Display test results in SmartDashboard
  - Cleanup in `testEnd()`
- **Lifetime**: Created in `testInit()`, used in `testPeriodic()`, cleaned up in `testEnd()`
- **Dependencies**: Only depends on `RobotContainer` (to access subsystems), no production code imports test classes
- **Result Display**: Automatically reads `getResult()` from completed tests and displays in SmartDashboard

### 2. Integration Points

#### Robot Class (`testInit`, `testPeriodic`, `testEnd`)
```java
private DiagnosticTestManager testManager;

@Override
public void testInit() {
    CommandScheduler.getInstance().cancelAll();
    testManager = new DiagnosticTestManager(m_robotContainer);
}

@Override
public void testPeriodic() {
    if (testManager != null) {
        testManager.periodic();
    }
}

@Override
public void testEnd() {
    if (testManager != null) {
        testManager.cleanup();
        testManager = null;
    }
}
```

#### SmartDashboard Layout
```
DiagnosticTests/
  ├── TestSelector/ (SendableChooser - dropdown with all available tests)
  ├── StartTest/ (Boolean - button that toggles to start selected test)
  ├── CurrentTest/ (String - name of currently running test)
  ├── TestStatus/ (String - status: Idle, Running, Complete, Error, Cancelled)
  ├── Parameters/
  │   └── [TestName]/
  │       ├── ModuleNumber/ (example parameter)
  │       ├── TestAngle/ (example parameter)
  │       └── NumberOfCycles/ (example parameter)
  └── Results/
      ├── LastTest/ (String - name of last completed test)
      ├── Passed/ (Boolean - did last test pass?)
      ├── Message/ (String - result message)
      ├── Metrics/ (Table - key-value pairs of numeric results)
      └── Warnings/ (String array - list of warnings)
```

**User Flow**:
1. User selects test from dropdown
2. Test parameters appear in `Parameters/[TestName]/` section
3. User configures parameters as needed
4. User clicks "StartTest" button
5. Manager detects button press, reads parameters, creates test instance, schedules it
6. Status updates automatically as test runs
7. Test completes and results appear in `Results/` section
8. User can run test again with different parameters

### 3. Design Principles

1. **Separation of Concerns**: 
   - Framework code in `frc.lib.test` package (reusable infrastructure)
   - Test implementations in `frc.robot.test` package (test-specific code)
   - Production code never imports test classes
   - Test code can access production subsystems (one-way dependency)
   - Tests depend on framework (one-way dependency: tests → framework)

2. **Extensibility**:
   - Easy to add new tests by implementing `DiagnosticTest` interface
   - Registry pattern allows dynamic or static test registration
   - No changes to production code when adding tests

3. **User Experience**:
   - Clear test selection via dropdown
   - One-click test execution
   - Real-time status updates
   - Test results visible in SmartDashboard

4. **Maintainability**:
   - Each test is self-contained
   - Common utilities in `SwerveModuleTestUtils` pattern
   - Clear naming conventions

## Example Code Structure

### DiagnosticTest Interface
```java
public interface DiagnosticTest {
    String getTestName();
    default String getTestDescription() {
        return "No description available";
    }
    default void initializeParameters() {
        // Override to set up SmartDashboard parameters
    }
    default DiagnosticTestResult getResult() {
        return null; // Override to return test results
    }
}
```

### TestStatus Enum
```java
public enum TestStatus {
    SUCCESS,      // Test completed and passed
    FAILED,       // Test completed but failed (e.g., drift exceeded threshold)
    CANCELLED,    // Test was interrupted/cancelled before completion
    ERROR         // Test encountered an exception or error
}
```

### MetricValue Class
```java
/**
 * Wrapper class for test metric values that preserves type information.
 * Supports Double, Integer, Boolean, and String types.
 */
public class MetricValue {
    private final Object value;
    private final MetricType type;  // enum: DOUBLE, INT, BOOLEAN, STRING
    
    // Factory methods
    public static MetricValue of(double value) { ... }
    public static MetricValue of(int value) { ... }
    public static MetricValue of(boolean value) { ... }
    public static MetricValue of(String value) { ... }
    
    // Type-safe getters (throw exception if wrong type)
    public double getDouble() { ... }
    public int getInt() { ... }
    public boolean getBoolean() { ... }
    public String getString() { ... }
    
    // Type checking
    public MetricType getType() { ... }
    public boolean isDouble() { ... }
    // ... similar for other types
}
```

### DiagnosticTestResult Class
```java
public class DiagnosticTestResult {
    private final TestStatus status;
    private final String message;
    private final Map<String, MetricValue> metrics;
    private final List<String> warnings;
    
    // Constructor and getters
    public TestStatus getStatus() { ... }
    public boolean passed() { return status == TestStatus.SUCCESS; }  // Convenience method
    public String getMessage() { ... }
    public Map<String, MetricValue> getMetrics() { ... }
    public List<String> getWarnings() { ... }
    
    // Factory methods
    public static DiagnosticTestResult pass(String message) { ... }
    public static DiagnosticTestResult fail(String message) { ... }
    public static DiagnosticTestResult cancelled(String message) { ... }
    public static DiagnosticTestResult error(String message) { ... }
    public static DiagnosticTestResult passWithMetrics(String message, Map<String, MetricValue> metrics) { ... }
    
    // Builder for complex results
    public static Builder builder() { ... }
}
```

### TestParameterHelper Utility
```java
public class TestParameterHelper {
    // Initialize parameter with default value (creates SmartDashboard entry)
    public static void putDouble(String key, double defaultValue) {
        SmartDashboard.putNumber(key, defaultValue);
    }
    
    // Read parameter from SmartDashboard
    public static double getDouble(String key, double defaultValue) {
        return SmartDashboard.getNumber(key, defaultValue);
    }
    
    // Similar methods for int, boolean, String
}
```

### DiagnosticTestRegistry (Enum-based)
```java
public enum DiagnosticTestRegistry {
    SWERVE_ANGLE_DRIFT("Swerve Angle Drift Test", 
        "Tests encoder drift by rotating module...") {
        @Override
        public DiagnosticTest createTest(RobotContainer robotContainer) {
            // Parameters are read from SmartDashboard in test's initialize()
            return new SwerveAngleDriftTestCommand(robotContainer.getSwerveSubsystem());
        }
    };
    
    // ... enum implementation
}
```

### SwerveAngleDriftTestCommand Integration (Simplified)
```java
public class SwerveAngleDriftTestCommand extends Command implements DiagnosticTest {
    private static final String PARAM_PREFIX = "DiagnosticTests/Parameters/Swerve Angle Drift Test/";
    private DiagnosticTestResult result;
    private SwerveSubsystem swerve;
    private int moduleNumber;
    private double testAngle;
    private int cycles;
    private int currentCycle = 0;
    private boolean testComplete = false;
    
    public SwerveAngleDriftTestCommand(SwerveSubsystem swerve) {
        this.swerve = swerve;
        addRequirements(swerve);
    }
    
    @Override
    public void initializeParameters() {
        // Set up default parameters in SmartDashboard
        TestParameterHelper.putInt(PARAM_PREFIX + "ModuleNumber", 0);
        TestParameterHelper.putDouble(PARAM_PREFIX + "TestAngle", 90.0);
        TestParameterHelper.putInt(PARAM_PREFIX + "NumberOfCycles", 10);
    }
    
    @Override
    public void initialize() {
        // Read parameters from SmartDashboard using TestParameterHelper
        // Initialize test state variables
        // Start the first test cycle or begin test execution
    }
    
    @Override
    public void execute() {
        // ACTUAL TEST LOGIC GOES HERE
        // This runs every 20ms while the test is active
        // - Check conditions (e.g., has module reached target angle?)
        // - Record measurements
        // - Manage test state machine (moving to target, holding, moving to zero, etc.)
        // - Transition between states
        // - Update cycle counter or test progress
    }
    
    @Override
    public boolean isFinished() {
        // Return true when test is complete (all cycles finished, duration elapsed, etc.)
        return testComplete;
    }
    
    @Override
    public void end(boolean interrupted) {
        // Stop any robot motion
        // Create DiagnosticTestResult with metrics and pass/fail status
        // Store result for getResult() to return
    }
    
    @Override
    public String getTestName() {
        return "Swerve Angle Drift Test";
    }
    
    @Override
    public DiagnosticTestResult getResult() {
        return result;
    }
}
```

### Example: Creating a New Test (Minimal Effort)
```java
public class SimpleMotorTest extends Command implements DiagnosticTest {
    private static final String PARAM_PREFIX = "DiagnosticTests/Parameters/Simple Motor Test/";
    private final SwerveSubsystem swerve;
    private DiagnosticTestResult result;
    
    // Test state
    private double targetSpeed;
    private double duration;
    private double startTime;
    private double maxSpeed = 0.0;
    private double totalSpeed = 0.0;
    private int sampleCount = 0;
    
    public SimpleMotorTest(SwerveSubsystem swerve) {
        this.swerve = swerve;
        addRequirements(swerve);
    }
    
    @Override
    public void initializeParameters() {
        TestParameterHelper.putDouble(PARAM_PREFIX + "TargetSpeed", 0.5);
        TestParameterHelper.putDouble(PARAM_PREFIX + "Duration", 2.0);
    }
    
    @Override
    public void initialize() {
        // Read parameters from SmartDashboard using TestParameterHelper
        // Initialize test state variables (startTime, counters, etc.)
        // Start motor or begin test execution
    }
    
    @Override
    public void execute() {
        // ACTUAL TEST LOGIC GOES HERE
        // This runs every 20ms while the test is active
        // - Measure current motor speed or other sensor values
        // - Track metrics (max speed, average speed, etc.)
        // - Update test state
    }
    
    @Override
    public boolean isFinished() {
        // Return true when test duration has elapsed or test conditions are met
        return Timer.getFPGATimestamp() - startTime >= duration;
    }
    
    @Override
    public void end(boolean interrupted) {
        // Stop any robot motion
        // Create DiagnosticTestResult with collected metrics
        // Store result for getResult() to return
    }
    
    @Override
    public String getTestName() { return "Simple Motor Test"; }
    
    @Override
    public DiagnosticTestResult getResult() { return result; }
}
```

### Robot Class Integration
```java
private DiagnosticTestManager testManager;

@Override
public void testInit() {
    CommandScheduler.getInstance().cancelAll();
    testManager = new DiagnosticTestManager(m_robotContainer);
}

@Override
public void testPeriodic() {
    if (testManager != null) {
        testManager.periodic();
    }
}

@Override
public void testEnd() {
    if (testManager != null) {
        testManager.cleanup();
        testManager = null;
    }
}
```

### Optional Finite State Machine (FSM) Support for Complex Tests

Many diagnostic tests follow a state machine pattern (e.g., move to position → hold → measure → repeat). While simple tests don't need state machines, complex multi-phase tests benefit from structured state management.

#### When to Use FSM Support

**Use FSM support when your test has:**
- Multiple distinct phases (e.g., moving, holding, measuring)
- Timeouts or waiting conditions
- Repeated cycles or iterations
- Complex state transitions

**Skip FSM support for:**
- Simple duration-based tests (run motor for X seconds)
- Single-phase tests (set position → measure → done)
- Continuous measurement tests

#### StateMachineTestCommand Base Class

The framework provides an optional base class that handles common FSM boilerplate:

**Design Decision: Minimal Base Class**
- Provides only essential timing helpers to reduce boilerplate
- Keeps framework lightweight - simple tests don't pay unnecessary cost
- Complex tests can add their own logging/validation as needed
- Examples demonstrate best practices for common patterns

```java
/**
 * Optional base class for tests that use finite state machines.
 * Provides state tracking, timing, and transition helpers.
 * 
 * Simple tests can extend Command directly - this is only for complex multi-phase tests.
 * 
 * Tests define their own state enums and use the provided helpers for timing and transitions.
 * 
 * Design Philosophy: Minimal but useful. Provides timing helpers to reduce boilerplate,
 * but doesn't enforce a specific FSM pattern. Test authors have full control over state
 * management while benefiting from common timing utilities.
 */
public abstract class StateMachineTestCommand extends Command implements DiagnosticTest {
    
    /**
     * Timestamp when current state was entered. Protected so subclasses can access it.
     */
    protected double stateStartTime = 0.0;
    
    /**
     * Optional flag to enable state transition logging for debugging.
     * Set to true in initialize() if you want automatic logging of state transitions.
     */
    protected boolean enableStateLogging = false;
    
    /**
     * Gets the elapsed time since entering the current state.
     * Subclasses should call this after updating their stateStartTime.
     */
    protected double getStateElapsedTime() {
        return Timer.getFPGATimestamp() - stateStartTime;
    }
    
    /**
     * Helper method to record a state transition timestamp.
     * Subclasses should call this when transitioning states.
     * 
     * Optionally logs the transition if enableStateLogging is true.
     */
    protected void recordStateTransition() {
        stateStartTime = Timer.getFPGATimestamp();
    }
    
    /**
     * Optional helper method for state transitions that includes logging.
     * Subclasses can use this instead of manually calling recordStateTransition()
     * if they want automatic logging.
     * 
     * @param oldState The state being exited (for logging)
     * @param newState The state being entered (for logging)
     */
    protected void transitionTo(Object oldState, Object newState) {
        if (enableStateLogging) {
            System.out.println(String.format("State transition: %s -> %s", oldState, newState));
        }
        recordStateTransition();
    }
    
    @Override
    public void initialize() {
        recordStateTransition(); // Record initialization time
        // Subclasses should call super.initialize() then do their setup
    }
}
```

**Usage Notes**:
- Subclasses define their own state enums (no base enum required)
- Subclasses manage their own state transitions (base class doesn't enforce a pattern)
- Timing helpers reduce boilerplate for common patterns
- Optional logging can be enabled per test if needed
- Examples show common patterns (state entry actions, transition validation, etc.)

#### Example: FSM-Based Test

```java
public class SwerveAngleDriftTestCommand extends StateMachineTestCommand {
    // Define test-specific states (can use BaseTestState or define custom states)
    private enum TestState {
        INITIALIZING,
        MOVING_TO_TARGET,
        HOLDING_AT_TARGET,
        MOVING_TO_ZERO,
        HOLDING_AT_ZERO,
        COMPLETE
    }
    
    private TestState currentState = TestState.INITIALIZING;
    private SwerveModule module;
    private double testAngle;
    private int cycles;
    private int currentCycle = 0;
    
    // Override base class state tracking to use our custom enum
    // Base class provides stateStartTime and getStateElapsedTime() helpers
    
    @Override
    public void initialize() {
        super.initialize(); // Initialize FSM base class (sets stateStartTime)
        // Read parameters from SmartDashboard
        // Initialize test state
        transitionTo(TestState.MOVING_TO_TARGET);
    }
    
    @Override
    public void execute() {
        double elapsed = getStateElapsedTime(); // Use base class helper
        
        switch (currentState) {
            case MOVING_TO_TARGET:
                if (isAtAngle(testAngle)) {
                    transitionTo(TestState.HOLDING_AT_TARGET);
                } else if (elapsed > timeout) {
                    transitionTo(TestState.HOLDING_AT_TARGET);
                }
                break;
                
            case HOLDING_AT_TARGET:
                if (elapsed >= holdTime) {
                    recordMeasurement();
                    transitionTo(TestState.MOVING_TO_ZERO);
                }
                break;
                
            case MOVING_TO_ZERO:
                if (isAtAngle(0.0)) {
                    transitionTo(TestState.HOLDING_AT_ZERO);
                } else if (elapsed > timeout) {
                    transitionTo(TestState.HOLDING_AT_ZERO);
                }
                break;
                
            case HOLDING_AT_ZERO:
                if (elapsed >= holdTime) {
                    completeCycle();
                    if (currentCycle >= cycles) {
                        transitionTo(TestState.COMPLETE);
                    } else {
                        transitionTo(TestState.MOVING_TO_TARGET);
                    }
                }
                break;
                
            case COMPLETE:
                break;
        }
    }
    
    /**
     * Transitions to a new state and records the transition time.
     * Uses base class helper to track state timing.
     */
    private void transitionTo(TestState newState) {
        currentState = newState;
        recordStateTransition(); // Use base class helper
        onStateEntered(newState);
    }
    
    /**
     * Called when entering a new state. Perform state-specific actions here.
     */
    
    private void onStateEntered(TestState state) {
        switch (state) {
            case MOVING_TO_TARGET:
                module.setDesiredState(new SwerveModuleState(0.0, Rotation2d.fromDegrees(testAngle)), false);
                break;
            case MOVING_TO_ZERO:
                module.setDesiredState(new SwerveModuleState(0.0, Rotation2d.fromDegrees(0.0)), false);
                break;
            // ... other state entry actions
        }
    }
    
    @Override
    public boolean isFinished() {
        return currentState == TestState.COMPLETE;
    }
    
    // ... rest of test implementation (getResult(), getTestName(), etc.)
}
```

#### Benefits of FSM Support

- **Reduces Boilerplate**: No need to manually track state, timestamps, or transitions
- **Standardized Patterns**: Consistent state management across complex tests
- **Easier Debugging**: State transitions are explicit and traceable
- **Optional**: Simple tests don't pay the cost - only use when needed

#### When NOT to Use FSM Support

For simple tests, extending `Command` directly is perfectly fine:

```java
public class SimpleMotorTest extends Command implements DiagnosticTest {
    private double startTime;
    private double duration;
    
    @Override
    public void initialize() {
        startTime = Timer.getFPGATimestamp();
        motor.set(0.5);
    }
    
    @Override
    public void execute() {
        // Just measure - no state machine needed
        recordMeasurement();
    }
    
    @Override
    public boolean isFinished() {
        return Timer.getFPGATimestamp() - startTime >= duration;
    }
}
```

## Relationship to Existing Code

### Current Test Infrastructure
- ✅ `SwerveAngleDriftTestCommand` - Well-designed test command
- ✅ `SwerveDriftTestManager` - Provides dashboard integration
- ✅ `SwerveModuleTestUtils` - Utility helpers for tests
- ✅ Test code in `frc.robot.test` package

### How Framework Integrates

**Decision: Framework Replaces SwerveDriftTestManager** ✅
- Framework provides unified test management
- `SwerveDriftTestManager` will be removed after migration
- All tests use the same framework pattern
- Better consistency and maintainability

### Impact on Existing Test
- `SwerveAngleDriftTestCommand` needs to:
  1. Extend `StateMachineTestCommand` instead of `Command` directly
  2. Implement `DiagnosticTest` interface (add methods)
  3. Add `initializeParameters()` to set up SmartDashboard parameters
  4. Read parameters from SmartDashboard in `initialize()` using `TestParameterHelper`
  5. Add `getResult()` to return `DiagnosticTestResult` with `TestStatus` and `MetricValue` metrics
  6. Use base class timing helpers (`getStateElapsedTime()`, `recordStateTransition()`)
  7. Remove direct SmartDashboard parameter setup (moved to `initializeParameters()`)
  8. Convert result metrics to use `MetricValue` wrapper
- Test logic remains mostly unchanged
- State machine pattern simplified with base class helpers
- Parameter management moves to framework pattern
- Result reporting becomes standardized with status enum and typed metrics
- `SwerveDriftTestManager` can be removed after migration

## Implementation Plan

### Phase 1: Core Framework
1. Create `DiagnosticTest` interface
2. Create `DiagnosticTestResult` class
3. Create `TestParameterHelper` utility class
4. Create `DiagnosticTestRegistry` (Enum-based)
5. Create `DiagnosticTestManager` with parameter and result handling
6. Integrate into `Robot.testInit/testPeriodic/testEnd`
7. Test with minimal example

### Phase 2: Migration
1. Make `SwerveAngleDriftTestCommand` implement `DiagnosticTest`
2. Add `initializeParameters()` method
3. Modify to read parameters from SmartDashboard using `TestParameterHelper`
4. Add `getResult()` method to return `DiagnosticTestResult`
5. Register in `DiagnosticTestRegistry`
6. Test end-to-end flow (parameter configuration, execution, result display)
7. Remove `SwerveDriftTestManager` (replaced by framework)

### Phase 3: Documentation & Examples
1. Create example test templates
2. Document test creation workflow
3. Add inline code comments and JavaDoc
4. Create README for test authors

## Design Decisions & Alternatives

### Test Result Status: Enum vs Boolean

**Decision: TestStatus Enum** ✅

**Alternatives Considered**:
1. `boolean passed` - Simple but doesn't distinguish failure modes (rejected)
2. `TestStatus` enum - Provides clear distinction between SUCCESS, FAILED, CANCELLED, ERROR (chosen)

**Rationale**: A simple `boolean passed` field doesn't distinguish between different failure modes:
- Test completed but failed (e.g., drift exceeded threshold) → `FAILED`
- Test was interrupted/cancelled → `CANCELLED`
- Test encountered an exception → `ERROR`
- Test completed successfully → `SUCCESS`

**Benefits**:
- Clear distinction between failure modes
- Better error tracking and debugging
- Supports cancelled test handling
- Backward compatible via `passed()` convenience method

**Tradeoffs**:
- More complex than boolean, but provides essential information
- Requires enum definition, but improves code clarity
- Slightly more verbose, but self-documenting

**Implementation**:
- `TestStatus` enum with values: SUCCESS, FAILED, CANCELLED, ERROR
- `DiagnosticTestResult` uses `TestStatus status` instead of `boolean passed`
- Factory methods: `pass()`, `fail()`, `cancelled()`, `error()`
- Convenience method: `boolean passed()` returns `status == TestStatus.SUCCESS`

### Metrics Storage: Type Safety vs Flexibility

**Decision: MetricValue Wrapper Class** ✅

**Alternatives Considered**:
1. `Map<String, Object>` - Flexible but no type safety
2. Separate maps per type - Type-safe but complex API
3. `MetricValue` wrapper - Balanced approach (chosen)
4. Builder pattern - Type-safe but more complex implementation

**Rationale**: `MetricValue` wrapper provides the best balance:
- **Type Safety**: Preserves type information, validates at runtime
- **Simplicity**: Single map, easy to iterate
- **Flexibility**: Supports Double, Integer, Boolean, String
- **Extensibility**: Can add formatting/display helpers later
- **Self-Documenting**: Type info preserved in the value

**Tradeoffs**:
- Slightly more complex than `Map<String, Object>` (requires wrapper class)
- More type-safe than `Object` but requires runtime type checking
- Simpler API than separate maps per type
- Less compile-time safety than builder pattern, but more convenient

**Implementation**:
- `MetricValue` class with factory methods: `of(double)`, `of(int)`, `of(boolean)`, `of(String)`
- Type-safe getters: `getDouble()`, `getInt()`, `getBoolean()`, `getString()`
- `Map<String, MetricValue> metrics` in `DiagnosticTestResult`

### StateMachineTestCommand: Minimal vs Full FSM Framework

**Decision: Minimal Base Class with Optional Helpers** ✅

**Alternatives Considered**:
1. Minimal (timing only) - Chosen
2. Add state transition logging - Optional helper provided
3. Add state entry/exit callbacks - Too complex, not needed
4. Add transition validation - Too complex, not needed
5. Full FSM framework - Overkill for FRC tests

**Rationale**: Keep it simple and lightweight:
- **Minimal Overhead**: Simple tests don't pay unnecessary cost
- **Essential Helpers**: Timing helpers reduce common boilerplate
- **Full Control**: Test authors manage their own state transitions
- **Optional Features**: Logging helper available but not required
- **Examples Show Patterns**: Best practices demonstrated in examples

**Tradeoffs**:
- Less structure than full FSM framework, but more flexible
- Test authors write more code than with callbacks, but have full control
- No built-in validation, but tests can add their own if needed
- Examples demonstrate common patterns instead of enforcing them

**Implementation**:
- Base class provides: `stateStartTime`, `getStateElapsedTime()`, `recordStateTransition()`
- Optional `enableStateLogging` flag for debugging
- Optional `transitionTo(oldState, newState)` helper with logging
- Subclasses define their own state enums and manage transitions
- Examples show common patterns (state entry actions, validation, etc.)

### Cancelled Test Handling

**Decision: Use TestStatus.CANCELLED** ✅

**Considerations**:
- Tests can be cancelled/interrupted at any time
- May have partial results that are still useful
- Should distinguish cancellation from failure
- Framework should handle cancellation gracefully

**Implementation**:
- `TestStatus.CANCELLED` status for interrupted tests
- Tests can return partial results if available
- Framework checks `getResult()` after test ends (whether completed or cancelled)
- Results display clearly indicates cancellation status
- Warnings can include cancellation reason if available

**Best Practices for Test Authors**:
- In `end(boolean interrupted)`, check `interrupted` parameter
- If interrupted, create result with `TestStatus.CANCELLED`
- Include partial metrics if available (e.g., "Completed 3 of 10 cycles")
- Add warning explaining why test was cancelled if known

### Package Organization: Framework vs Tests

**Decision: Framework in `frc.lib.test`, Tests in `frc.robot.test`** ✅

**Alternatives Considered**:
1. Everything in `frc.robot.test` - Simple but mixes framework with tests (rejected)
2. Framework in `frc.robot.test.framework` - Clear separation but deeper nesting (rejected)
3. Framework in `frc.lib.test` - Framework as reusable library code (chosen)
4. Framework in `frc.lib.test.framework` - Very clear but deeper nesting (rejected)

**Rationale**: Separating framework from tests provides:
- **Clear Separation**: Framework is reusable infrastructure, tests are test-specific implementations
- **Follows Existing Pattern**: `frc.lib` already contains library/utility code (e.g., `LimelightHelpers`, `CANSparkUtil`)
- **Dependency Clarity**: Tests depend on framework (one-way: `frc.robot.test` → `frc.lib.test`)
- **Reusability**: Framework could be reused across projects or extracted as a library
- **Organization**: Makes it clear what's framework vs what's a specific test

**Package Structure**:
```
frc.lib.test/
  ├── DiagnosticTest (interface)
  ├── DiagnosticTestResult
  ├── TestStatus (enum)
  ├── MetricValue
  ├── MetricType (enum)
  ├── TestParameterHelper
  ├── DiagnosticTestRegistry (enum)
  ├── DiagnosticTestManager
  └── StateMachineTestCommand (base class)

frc.robot.test/
  ├── SwerveAngleDriftTestCommand
  ├── SwerveModuleTestUtils
  └── [other test implementations]
```

**Dependencies**:
- `frc.robot.test` imports from `frc.lib.test` (tests use framework)
- `frc.lib.test` does NOT import from `frc.robot.test` (framework doesn't know about tests)
- Production code (`frc.robot.*`) does NOT import from either test package

### Registry Pattern: Enum vs Dynamic

**Decision: Enum-Based** ✅
- ✅ Compile-time safety
- ✅ Easy to see all tests in one place
- ✅ IDE autocomplete support
- ✅ Simple factory methods
- ✅ Adding a test is just adding an enum value (minimal effort)
- ❌ Requires code change to add test (acceptable trade-off for safety)

**Rationale**: Simpler, safer, and adding a test is just adding an enum value - not a significant burden. The type safety and IDE support are worth it.

### Test Instantiation: Factory vs Direct Construction

**Decision: Factory Method** ✅
- Each enum value has a `createTest(RobotContainer)` method
- Allows tests to access subsystems via RobotContainer
- Parameters read from SmartDashboard when test is created
- ✅ Flexible - can create different test configurations
- ✅ Tests don't need to know about RobotContainer
- ✅ Parameters come from SmartDashboard, not hardcoded

**Rationale**: Factory method pattern provides better flexibility and dependency management. Parameters are read from SmartDashboard, not hardcoded.

### Dashboard UI: Dropdown vs Buttons

**Decision: Dropdown + Start Button** ✅
- ✅ Scales well to many tests
- ✅ Clear selection process
- ✅ Can show test descriptions
- ✅ Single start mechanism
- ✅ Parameters appear dynamically based on selected test

**Rationale**: Dropdown scales better as you add more tests. Parameters can be shown contextually for the selected test.

## Migration Strategy

### Current State
- `SwerveAngleDriftTestCommand` exists and works
- `SwerveDriftTestManager` provides dashboard integration
- Test code is in `frc.robot.test` package ✅

### Migration Approach

**Decision: Full Replacement** ✅
1. Create framework components
2. Make `SwerveAngleDriftTestCommand` implement `DiagnosticTest` interface
3. Migrate parameter handling to use `TestParameterHelper`
4. Add result reporting using `DiagnosticTestResult`
5. Register in `DiagnosticTestRegistry`
6. Remove `SwerveDriftTestManager` (no longer needed)
7. Framework becomes the only way to run tests

**Rationale**: Clean break, no legacy code to maintain, consistent approach from the start.

## Design Decisions (Finalized)

### 1. Test Parameters: Configurable via SmartDashboard ✅
- Parameters are read from SmartDashboard when test is created
- Framework provides helper methods to read parameters with defaults
- Tests define their parameter keys in a standardized location
- Parameters appear in SmartDashboard under `DiagnosticTests/Parameters/[TestName]/`

### 2. Test Results: Standardized Reporting ✅
- Framework provides `DiagnosticTestResult` class for structured results
- Tests can report pass/fail, messages, and data
- Results automatically displayed in SmartDashboard
- Results summary view shows key metrics

### 3. Multiple Test Execution: One at a Time ✅
- Only one test runs at a time (simpler, safer)
- Framework cancels previous test if new one is started
- Can be extended later if needed

### 4. Migration: Replace SwerveDriftTestManager ✅
- Framework replaces `SwerveDriftTestManager`
- Unified approach for all tests
- `SwerveDriftTestManager` will be removed after migration

## Benefits

1. **Scalability**: Easy to add new diagnostic tests (just add enum value)
2. **Consistency**: All tests follow same pattern and interface
3. **Discoverability**: All tests visible in one place (dropdown)
4. **Maintainability**: Clear separation, easy to understand
5. **User-Friendly**: Simple dashboard interface for pit crew
6. **Type Safety**: Enum-based registry catches errors at compile time
7. **Separation**: Test code completely isolated from production code
8. **Easy Test Creation**: Minimal boilerplate - just implement interface, use helper utilities
9. **Standardized Results**: Consistent result format makes it easy to compare tests
10. **Parameter Management**: Framework handles parameter initialization and reading
11. **Low Learning Curve**: Simple patterns, clear examples, helper utilities reduce complexity

## Making Test Creation Easy

### Key Simplifications

1. **TestParameterHelper**: One-line parameter setup and reading
   ```java
   TestParameterHelper.putDouble("key", 90.0);  // Initialize
   double value = TestParameterHelper.getDouble("key", 90.0);  // Read
   ```

2. **DiagnosticTestResult**: Simple factory methods for results
   ```java
   result = DiagnosticTestResult.pass("Test passed!");
   result = DiagnosticTestResult.fail("Test failed: reason");
   result = DiagnosticTestResult.cancelled("Test was cancelled");
   result = DiagnosticTestResult.error("Test encountered error: ...");
   
   // With metrics (supports multiple types)
   Map<String, MetricValue> metrics = new HashMap<>();
   metrics.put("speed", MetricValue.of(5.2));  // Double
   metrics.put("cycles", MetricValue.of(10));   // Integer
   metrics.put("passed", MetricValue.of(true)); // Boolean
   result = DiagnosticTestResult.passWithMetrics("Passed", metrics);
   ```

3. **Minimal Interface**: Only 2-4 methods to implement
   - `getTestName()` - Required
   - `getTestDescription()` - Optional (has default)
   - `initializeParameters()` - Optional (has default)
   - `getResult()` - Optional (has default)

4. **Clear Examples**: Template code for common patterns
   - Simple pass/fail test
   - Test with parameters
   - Test with metrics
   - Test with warnings

5. **Automatic Dashboard Integration**: Framework handles:
   - Parameter display
   - Result display
   - Status updates
   - Test selection UI

### Test Creation Workflow

1. **Create test class**: Extend `Command`, implement `DiagnosticTest`
2. **Add metadata**: Implement `getTestName()` and optionally `getTestDescription()`
3. **Define parameters**: Override `initializeParameters()` to set up SmartDashboard
4. **Read parameters**: Use `TestParameterHelper` in `initialize()` or constructor
5. **Report results**: Set result in `end()` method using `DiagnosticTestResult`
6. **Register test**: Add enum value in `DiagnosticTestRegistry`

That's it! Framework handles the rest.

## Summary: Why This Design is Easy to Use

### For Test Authors (Writing Tests)
- **Minimal Interface**: Only 2-4 methods to implement (most have defaults)
- **Helper Utilities**: `TestParameterHelper` eliminates boilerplate for parameters
- **Simple Results**: `DiagnosticTestResult` factory methods make reporting trivial
- **Clear Examples**: Template code shows exactly what to do
- **No Framework Knowledge Needed**: Just implement interface, use helpers, done

### For Test Operators (Running Tests)
- **One Place for Everything**: All tests in dropdown, all parameters visible
- **No Configuration Files**: Everything in SmartDashboard, visual and immediate
- **Clear Status**: Always know what's running, what passed/failed
- **Standardized Results**: Same format for all tests, easy to understand
- **No Learning Curve**: Select test, set parameters, click start

### For Framework Maintainers
- **Enum-Based Registry**: All tests visible in one place, type-safe
- **Clear Separation**: Test code isolated, no production dependencies
- **Extensible**: Easy to add features (test suites, result storage, etc.)
- **Well-Documented**: Clear patterns, examples, and JavaDoc

### Key Simplifications
1. **Parameter Management**: Framework handles SmartDashboard setup/reading
2. **Result Display**: Framework automatically shows results in dashboard
3. **Test Discovery**: Enum registry makes all tests visible automatically
4. **Lifecycle Management**: CommandScheduler handles test execution
5. **Error Handling**: Framework provides consistent error reporting

This design prioritizes **ease of use** and **low learning curve** while maintaining **type safety** and **code quality**.
