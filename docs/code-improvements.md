# Code Improvement Recommendations

This document tracks recommended code changes for team review and discussion. Each recommendation includes:
- **What**: Description of the change
- **Why**: Rationale and benefits
- **Where**: File and line numbers affected
- **Impact**: What needs to be updated if approved

---

## 1. Standardize Controller Mappings and Speed Values as Constants (Java Best Practices)

### What
Standardize all Xbox controller input mappings and speed multiplier values as `static final` constants with `UPPER_SNAKE_CASE` naming, and store all mappings (not just axes) as constants for consistency.

**Current code:**
```java
// Some stored as instance fields
private final int translationAxis = XboxController.Axis.kLeftY.value;
private final int strafeAxis = XboxController.Axis.kLeftX.value;
private final int rotationAxis = XboxController.Axis.kRightX.value;

// Some accessed directly in configureBindings()
driveController.button(Button.kY.value).onTrue(...);
driveController.axisGreaterThan(Axis.kLeftTrigger.value, 0.1).whileTrue(...);
driveController.axisGreaterThan(Axis.kRightTrigger.value, 0.1).whileTrue(...);
// In getSpeedMultiplier()
Button.kLeftStick.value
return driveController.getHID().getRawButton(Button.kLeftStick.value) ? 0.7 : 1;
```

**Proposed code:**
```java
// All controller mappings stored as static final constants
// Axis mappings
private static final int TRANSLATION_AXIS = XboxController.Axis.kLeftY.value;
private static final int STRAFE_AXIS = XboxController.Axis.kLeftX.value;
private static final int ROTATION_AXIS = XboxController.Axis.kRightX.value;

// Button mappings
private static final int ZERO_GYRO_BUTTON = Button.kY.value;
private static final int SPEED_MULTIPLIER_BUTTON = Button.kLeftStick.value;

// Trigger axis mappings
private static final int LEFT_TRIGGER_AXIS = Axis.kLeftTrigger.value;
private static final int RIGHT_TRIGGER_AXIS = Axis.kRightTrigger.value;

// Speed multiplier values
private static final double FULL_SPEED_MULTIPLIER = 1.0;
private static final double REDUCED_SPEED_MULTIPLIER = 0.7;
```

Then use constants in configureBindings()
```java
driveController.button(ZERO_GYRO_BUTTON).onTrue(new InstantCommand(() -> m_drive.zeroGyro(), m_drive));
driveController.axisGreaterThan(LEFT_TRIGGER_AXIS, 0.1).whileTrue(new AutoAlign(m_drive, true));
driveController.axisGreaterThan(RIGHT_TRIGGER_AXIS, 0.1).whileTrue(new AutoAlign(m_drive, false));
```

In TeleopSwerve command creation and getIsAutoAlignSupplier()
```java
// In TeleopSwerve command creation (line ~73)
() -> driveController.getRawAxis(RIGHT_TRIGGER_AXIS) > 0.1

// In getIsAutoAlignSupplier() method (line ~133)
return () -> driveController.getRawAxis(RIGHT_TRIGGER_AXIS) > 0.1;
```

And in getSpeedMultiplier()
```java
return driveController.getHID().getRawButton(SPEED_MULTIPLIER_BUTTON) 
    ? REDUCED_SPEED_MULTIPLIER 
    : FULL_SPEED_MULTIPLIER;
```

### Why
- **Consistency**: Currently, some inputs are stored as constants (axes) while others are accessed directly (buttons, triggers). Standardizing all mappings makes the code more maintainable
- **Eliminates magic numbers**: The speed multiplier values (0.7 and 1.0) are hardcoded magic numbers that should be named constants for clarity and maintainability
- **Single source of truth**: All controller mappings and speed values in one place makes it easy to see and change configuration
- **These should be static**: These values are constant and identical for every `RobotContainer` instance. Making them `static` is more efficient (stored once per class, not once per instance)
- **Follows Java naming conventions**: The Java style guide (Oracle, Google, etc.) specifies that `static final` constants should use `UPPER_SNAKE_CASE`
- **Clearer intent**: `static final` with `UPPER_SNAKE_CASE` makes it immediately obvious these are compile-time constants
- **Better documentation**: Constants at the top of the class serve as documentation of all controller mappings and speed values
- **Easier tuning**: Speed multiplier values can be adjusted in one place during testing

### Where
- **File**: `src/main/java/frc/robot/RobotContainer.java`
- **Current axis constants**: Lines 25-29 (declarations), Lines 57-59 (usage in `TeleopSwerve`)
- **Buttons to convert**: 
  - Line 47: `Button.kY.value` → `ZERO_GYRO_BUTTON`
  - Line 88: `Button.kLeftStick.value` → `SPEED_MULTIPLIER_BUTTON`
- **Triggers to convert**:
  - Line 56: `Axis.kLeftTrigger.value` → `LEFT_TRIGGER_AXIS`
  - Line 58: `Axis.kRightTrigger.value` → `RIGHT_TRIGGER_AXIS`
  - Line 73: `driveController.getRightTriggerAxis()` in TeleopSwerve command creation → `driveController.getRawAxis(RIGHT_TRIGGER_AXIS)`
  - Line 133: `driveController.getRightTriggerAxis()` in `getIsAutoAlignSupplier()` → `driveController.getRawAxis(RIGHT_TRIGGER_AXIS)`
- **Speed multiplier values to convert**:
  - Line 88: `0.7` → `REDUCED_SPEED_MULTIPLIER`
  - Line 88: `1` → `FULL_SPEED_MULTIPLIER`

### Impact
- Low risk change
- Only affects `RobotContainer.java`
- All references need to be updated to use the new constant names
- No functional changes - purely stylistic and organizational improvement
- Makes controller configuration easier to maintain and modify

### Decision Point
**Important**: This recommendation assumes these should be `static`. If the team decides to keep them as instance fields, then:
- The current `camelCase` naming (`translationAxis`, etc.) is actually **correct** per Java conventions
- However, making them `static` is still recommended since they're constant values that don't depend on the instance
- The consistency benefit (storing all mappings as constants) applies regardless of `static` vs instance

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] Implemented

---

## 2. Boolean Variable Naming Convention (Java Best Practices)

### What
Rename boolean-like variables to use the `is` prefix convention for better readability and consistency with Java/WPILib standards.

**Current code:**
```java
private final Trigger robotCentric = new Trigger(driveController.leftBumper());
```

**Proposed code:**
```java
private final Trigger isRobotCentric = new Trigger(driveController.leftBumper());
```

### Why
- **Follows Java naming conventions**: The `is` prefix is the standard convention for boolean variables and methods in Java
- **Consistency with Java standard library**: Examples include `String.isEmpty()`, `Optional.isPresent()`, `File.isDirectory()`, `Thread.isAlive()`
- **Consistency with WPILib**: Examples include `Command.isFinished()`, `DriverStation.isEnabled()`, `Subsystem.isScheduled()`
- **Better readability**: `isRobotCentric.getAsBoolean()` reads more naturally than `robotCentric.getAsBoolean()`
- **Clearer intent**: The `is` prefix immediately signals this represents a boolean state

### Examples from Java Standard Library
```java
String str = "hello";
str.isEmpty();        // boolean method

Optional<String> opt = Optional.of("value");
opt.isPresent();      // boolean method

File file = new File("path");
file.isFile();        // boolean method
file.isDirectory();   // boolean method
```

### Examples from WPILib
```java
Command command = new SomeCommand();
command.isFinished();    // boolean method - checks if command is done

DriverStation.isEnabled();  // boolean method - checks if robot is enabled
DriverStation.isTeleop();   // boolean method - checks if in teleop mode
```

### Where
- **File**: `src/main/java/frc/robot/RobotContainer.java`
- **Line**: 31 (declaration)
- **Also update**: Line 51 (usage in `TeleopSwerve` command creation)

### Impact
- Low risk change
- Only affects `RobotContainer.java`
- Single reference needs to be updated: `robotCentric.getAsBoolean()` → `isRobotCentric.getAsBoolean()`
- No functional changes - purely stylistic improvement

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] Implemented

---

## 3. Remove `m_` Prefix from Member Variables (Java Best Practices)

### What
Remove the `m_` prefix from all member variables to align with modern Java naming conventions and improve consistency across the codebase.

**Current code examples:**
```java
// RobotContainer.java
private final SwerveSubsystem m_drive = new SwerveSubsystem();

// TeleopSwerve.java
private SwerveSubsystem m_SwerveSubsystem;
private DoubleSupplier m_translationSupplier;

// Robot.java
private Command m_autonomousCommand;
private final RobotContainer m_robotContainer;

// SwerveModule.java
private double m_angleKP;
private double m_angleKI;
```

**Proposed code:**
```java
// RobotContainer.java
private final SwerveSubsystem drive = new SwerveSubsystem();

// TeleopSwerve.java
private SwerveSubsystem swerveSubsystem;
private DoubleSupplier translationSupplier;

// Robot.java
private Command autonomousCommand;
private final RobotContainer robotContainer;

// SwerveModule.java
private double angleKP;
private double angleKI;
```

### Why
- **Follows modern Java conventions**: The `m_` prefix (Hungarian notation) is not recommended in modern Java style guides (Google Java Style Guide, Oracle Code Conventions)
- **Inconsistent usage**: The codebase currently mixes `m_` prefix (e.g., `m_drive`) with non-prefixed names (e.g., `driveController`, `translationAxis`, `robotCentric`)
- **IDE support**: Modern IDEs can easily distinguish member variables from local variables through syntax highlighting and code navigation
- **Reduces visual clutter**: Removing prefixes makes code more readable
- **Consistency**: Standardizing on one naming style improves code maintainability

### Where
The `m_` prefix is currently used in the following files:

#### `RobotContainer.java`
- Line 33: `m_drive` → `drive`
- Lines 46, 48, 50, 53, 55: References to `m_drive` need updating
- Lines 43-44: Commented code references `m_ClimberSubsystem` (can be updated when uncommented)

#### `TeleopSwerve.java`
- Line 18: `m_SwerveSubsystem` → `swerveSubsystem`
- Lines 19-22: `m_translationSupplier`, `m_strafeSupplier`, `m_rotationSupplier`, `m_robotCentricSupplier` → remove `m_` prefix
- Multiple references throughout the file need updating (lines 35-40, 54, 57, 60, 67, 73)

#### `AutoAlign.java`
- Line 13: `m_SwerveSubsystem` → `swerveSubsystem`
- Lines 38, 40, 44, 47: References to `m_SwerveSubsystem` need updating

#### `Robot.java`
- Line 12: `m_autonomousCommand` → `autonomousCommand` (declaration)
- Line 14: `m_robotContainer` → `robotContainer` (declaration)
- Line 17: `m_robotContainer = new RobotContainer()` (constructor initialization)
- Line 36: `m_autonomousCommand = m_robotContainer.getAutonomousCommand()` (autonomousInit)
- Line 38: `if (m_autonomousCommand != null)` (autonomousInit)
- Line 39: `CommandScheduler.getInstance().schedule(m_autonomousCommand)` (autonomousInit)
- Line 51: `if (m_autonomousCommand != null)` (teleopInit)
- Line 52: `m_autonomousCommand.cancel()` (teleopInit)

#### `SwerveModule.java`
- Lines 37-39: `m_angleKP`, `m_angleKI`, `m_angleKD` → remove `m_` prefix
- Lines 63-65, 194, 233: References need updating

### Impact
- **Medium scope change**: Affects 5 files with multiple references each
- **Low risk**: Purely stylistic - no functional changes
- **Search and replace**: Can be done systematically with find/replace tools
- **Testing**: Should verify all references are updated correctly

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] Implemented

---

## 4. Rewrite AutoAlign for New Game Requirements (Functional Update)

### What
The current `AutoAlign` command is obsolete for the new game. It needs to be rewritten to use April Tag vision for hub alignment instead of field-position-based calculations.

**Current code (obsolete):**
- Uses `FieldConstants.BLUE_REEF_CENTER` (2026 Reefscape game element)
- Calculates positions based on field coordinates
- Moves robot in X, Y, and rotation to reach scoring positions
- Triggered by left/right triggers for left/right scoring positions

**New requirements:**
- **Basic feature**: Use April Tag vision (via Limelight) to detect hub
  - Alert driver with LED lights when robot is at correct distance from hub
  - On button press, auto-align (rotate only) to align shooter with hub
  - Support fixed shooter wheel speed (requires specific distance from hub)
- **Advanced feature** (if time permits): On button press, drive robot into range AND rotate to face hub (full auto-positioning)
- **Continuous orientation**: While driver moves robot, continuously rotate to face hub (allows shooting while moving/collecting balls)

### Why
- **Game change**: The code references 2025 game elements (Reef, Coral Stations) that don't exist in the new game
- **Different approach needed**: New game uses April Tag vision for alignment, not field position calculations
- **New features required**: Distance detection and LED feedback are new requirements
- **Multiple movement modes needed**: Rotation-only for basic, full positioning for advanced feature
- **Infrastructure exists**: `LimelightHelpers` is already in the codebase and used for odometry

### What Can Be Reused
- **Full position control structure**: The existing X, Y, and rotation PID controllers (lines 15-17, 31-33) are valuable for the advanced feature that drives into range
- **PID controller pattern**: All three PID controllers (X, Y, rotation) can be reused
- **Command framework**: The `Command` base class and `SwerveSubsystem` integration
- **Vision infrastructure**: `LimelightHelpers` class already supports April Tag detection
- **ChassisSpeeds calculation**: The pattern of calculating desired speeds and using `driveFromChassisSpeeds()` is directly applicable

### What Needs to Change
1. **Input source**: Replace field position calculations with April Tag vision data
   - Current: `FieldConstants.BLUE_REEF_CENTER` and position math
   - New: `LimelightHelpers` methods to get target angle and distance from April Tag

2. **Multiple command variants needed**:
   - **Basic rotation-only**: For button-press alignment (rotation PID only, X=0, Y=0)
   - **Advanced full positioning**: For driving into range + rotating (all three PIDs, like current code)
   - **Continuous rotation**: For shooting while moving (rotation PID only, but allows driver X/Y input)

3. **Distance detection**: Add new feature
   - Calculate distance from April Tag to hub
   - Control LEDs based on distance (in range = green, out of range = red/yellow)

4. **Trigger mechanism**: Multiple trigger types needed
   - Basic: `onTrue()` - one-time rotation alignment on button press
   - Advanced: `onTrue()` - one-time full positioning on button press
   - Continuous: Default command or `whileTrue()` - continuous rotation while driver controls translation

### Where
- **File to rewrite**: `src/main/java/frc/robot/Command/AutoAlign.java`
- **File to update**: `src/main/java/frc/robot/RobotContainer.java`
  - Lines 48-51: Remove or update trigger bindings for old AutoAlign
  - Add new button binding for hub alignment
- **New subsystem needed**: LED control subsystem (if not already exists)
- **Constants to remove/update**: `src/main/java/frc/robot/Constants.java`
  - `FieldConstants.BLUE_REEF_CENTER` (obsolete)
  - `FieldConstants.RIGHT_CORAL_STATION_ANGLE` (obsolete)
  - `FieldConstants.LEFT_CORAL_STATION_ANGLE` (obsolete)
  - Add new constants for hub April Tag ID, optimal shooting distance, distance tolerance

### Impact
- **High priority**: Required for new game functionality
- **Medium scope**: Rewrite one command, update bindings, add LED control
- **Breaking change**: Old AutoAlign code will not work with new game
- **New dependencies**: May need to add LED subsystem if not already present
- **Testing required**: Vision-based alignment needs field testing with actual April Tags

### Suggested Implementation Approach

**Option 1: Single Command with Modes**
```java
// HubAlign command with mode parameter:
- Mode: ROTATION_ONLY, FULL_POSITIONING, CONTINUOUS_ROTATION
- Get April Tag data from Limelight (target angle, distance)
- Calculate distance to hub
- Control LEDs based on distance (in range = green, out of range = red/yellow)
- For ROTATION_ONLY: Only rotate (x=0, y=0, rotation=PID output)
- For FULL_POSITIONING: Drive into range + rotate (x=PID, y=PID, rotation=PID)
- For CONTINUOUS_ROTATION: Rotate while allowing driver X/Y input
```

**Option 2: Multiple Commands (Recommended)**
```java
// HubAlignRotation.java - Basic rotation-only alignment
- Get April Tag angle from Limelight
- Use rotation PID to align
- Set X=0, Y=0 in ChassisSpeeds

// HubAlignFull.java - Advanced full positioning
- Get April Tag angle and distance from Limelight
- Use X, Y, rotation PIDs to drive into range and align
- Similar structure to current AutoAlign.java

// HubAlignContinuous.java - Continuous rotation while moving
- Get April Tag angle from Limelight
- Use rotation PID for alignment
- Allow driver X/Y input (combine with driver translation)
- Set as default command or whileTrue() binding
```

**Note**: The existing `AutoAlign.java` structure is actually quite valuable for the advanced feature since it already implements full position control (X, Y, rotation) which is exactly what's needed for driving into range.

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] In progress
- [ ] Implemented

---

## 5. Extract Supplier Functions into Private Methods (Code Organization)

### What
Extract the inline lambda expressions used in `TeleopSwerve` command creation into well-named private methods that return suppliers. This improves readability, maintainability, and makes the code easier to understand.

**Current code:**
```java
m_drive.setDefaultCommand(
    new TeleopSwerve(
        // SwerveSubsystem - The drive subsystem to control
        m_drive,
        // translationSupplier - Forward/backward speed
        () -> -getSpeedMultiplier() * driveController.getRawAxis(translationAxis) * 0.5,
        // strafeSupplier - Side-to-side speed
        () -> -getSpeedMultiplier() * driveController.getRawAxis(strafeAxis) * 0.5,
        // rotationSupplier - Rotation speed
        () -> -driveController.getRawAxis(rotationAxis) * 0.5,
        // robotCentricSupplier - Robot-oriented (true) vs field-oriented (false)
        () -> robotCentric.getAsBoolean(),
        // isAutoAlignSupplier - Auto-align active flag
        () -> driveController.getRightTriggerAxis() > 0.1
    ));
```

**Proposed code:**
```java
m_drive.setDefaultCommand(
    new TeleopSwerve(
        m_drive,
        getTranslationSupplier(),
        getStrafeSupplier(),
        getRotationSupplier(),
        getRobotCentricSupplier(),
        getIsAutoAlignSupplier()
    ));
```

With the following private methods added:

```java
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Returns a supplier for forward/backward translation input from the controller.
 * @return DoubleSupplier where the value represents percent of maximum robot speed.
 *         Sign indicates direction (positive = forward, negative = backward).
 *         Note: Current implementation limits the range to -0.5 to 0.5 (50% of max speed).
 */
private DoubleSupplier getTranslationSupplier() {
  return () -> -getSpeedMultiplier() * driveController.getRawAxis(translationAxis) * 0.5;
}

/**
 * Returns a supplier for side-to-side strafe input from the controller.
 * @return DoubleSupplier where the value represents percent of maximum robot speed.
 *         Sign indicates direction (positive = right, negative = left).
 *         Note: Current implementation limits the range to -0.5 to 0.5 (50% of max speed).
 */
private DoubleSupplier getStrafeSupplier() {
  return () -> -getSpeedMultiplier() * driveController.getRawAxis(strafeAxis) * 0.5;
}

/**
 * Returns a supplier for rotation input from the controller.
 * @return DoubleSupplier where the value represents percent of maximum robot speed.
 *         Sign indicates direction (positive = clockwise, negative = counterclockwise).
 *         Note: Current implementation limits the range to -0.5 to 0.5 (50% of max speed).
 */
private DoubleSupplier getRotationSupplier() {
  return () -> -driveController.getRawAxis(rotationAxis) * 0.5;
}

/**
 * Returns a supplier indicating whether robot-oriented mode is active.
 * @return BooleanSupplier true if robot-oriented, false if field-oriented
 */
private BooleanSupplier getRobotCentricSupplier() {
  return () -> robotCentric.getAsBoolean();
}

/**
 * Returns a supplier indicating whether auto-align is currently active.
 * @return BooleanSupplier true if auto-align is active, false otherwise
 */
private BooleanSupplier getIsAutoAlignSupplier() {
  // The right trigger is an analog input (not a digital button) that returns values
  // between 0.0 (not pressed) and 1.0 (fully pressed) depending on how much it's pressed.
  // The 0.1 threshold acts as a deadband to filter out noise and accidental contact.
  return () -> driveController.getRightTriggerAxis() > 0.1;
}
```

### Why
- **Improved readability**: The `setDefaultCommand` call becomes much cleaner and easier to understand
- **Better documentation**: Each supplier method can have JavaDoc explaining its purpose and behavior
- **Easier maintenance**: Changes to supplier logic are isolated to a single method
- **Testability**: Individual suppliers can be tested independently if needed
- **Follows best practices**: Extracts complex expressions into well-named methods (Single Responsibility Principle)
- **Self-documenting code**: Method names clearly describe what each supplier provides

### Where
- **File**: `src/main/java/frc/robot/RobotContainer.java`
- **Location**: Lines 63-75 (the `setDefaultCommand` call in `configureBindings()`)
- **New methods**: Add after `getSpeedMultiplier()` method (around line 85)
- **Imports needed**: Add `import java.util.function.BooleanSupplier;` and `import java.util.function.DoubleSupplier;`

### Impact
- **Low risk change**: Purely organizational - no functional changes
- **Improves code quality**: Makes the code more maintainable and easier to understand
- **No breaking changes**: All existing functionality remains the same
- **Better documentation**: JavaDoc comments can explain the purpose and behavior of each supplier

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] Implemented

---

## 6. Remove Unnecessary Instance Variables in SwerveModule (Code Simplification)

### What
Remove the instance variables `m_angleKP`, `m_angleKI`, and `m_angleKD` from `SwerveModule.java` and directly reference `SwerveConstants` instead. These variables are unnecessary copies of constants that are never modified.

**Current code:**
```java
public class SwerveModule {
    private double m_angleKP;
    private double m_angleKI;
    private double m_angleKD;
    
    public SwerveModule(int moduleNumber, ModuleData moduleConstants){
        this.m_angleKP = SwerveConstants.angleKP;
        this.m_angleKI = SwerveConstants.angleKI;
        this.m_angleKD = SwerveConstants.angleKD;
        // ... rest of constructor
    }
    
    private void configAngleMotor(){
        // ... other config
        sparkMaxConfig.closedLoop.p(m_angleKP).i(m_angleKI).d(m_angleKD);
    }
}
```

**Proposed code:**
```java
public class SwerveModule {
    // Remove m_angleKP, m_angleKI, m_angleKD declarations
    
    public SwerveModule(int moduleNumber, ModuleData moduleConstants){
        // Remove the three assignment lines
        // ... rest of constructor
    }
    
    private void configAngleMotor(){
        // ... other config
        sparkMaxConfig.closedLoop.p(SwerveConstants.angleKP)
            .i(SwerveConstants.angleKI)
            .d(SwerveConstants.angleKD);
    }
}
```

### Why
- **Eliminates redundancy**: These instance variables are exact copies of `SwerveConstants` values that never change
- **Consistency**: Other constants in `configAngleMotor()` (like `angleContinuousCurrentLimit`, `angleInvert`, `angleNeutralMode`, etc.) are accessed directly from `SwerveConstants`
- **No per-module customization**: All modules use the same PID values, so there's no need for instance variables
- **Simpler code**: Fewer variables to maintain and understand
- **Single source of truth**: PID values are defined once in `SwerveConstants`, not duplicated in each module instance
- **Never modified**: These variables are set once in the constructor and never changed, making them unnecessary copies

### Where
- **File**: `src/main/java/frc/robot/SwerveModule.java`
- **Lines to remove**:
  - Lines 37-39: Variable declarations (`m_angleKP`, `m_angleKI`, `m_angleKD`)
  - Lines 76-78: Constructor assignments (`this.m_angleKP = SwerveConstants.angleKP;` etc.)
- **Line to update**:
  - Line 207: Change `sparkMaxConfig.closedLoop.p(m_angleKP).i(m_angleKI).d(m_angleKD);` to use `SwerveConstants.angleKP`, `SwerveConstants.angleKI`, `SwerveConstants.angleKD` directly

### Impact
- **Low risk change**: Purely removes redundant code
- **No functional changes**: Behavior remains identical
- **Reduces memory usage**: Eliminates 3 double values per module instance (minimal but cleaner)
- **Improves maintainability**: One less place to look when tuning PID values
- **Note**: There are commented-out lines (line 240) that reference these variables, but since they're not active code, they can remain as-is

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] Implemented

---

## 7. Consider Periodic Encoder Recalibration to Reduce Drift (Performance Improvement)

### What
Add periodic recalibration of the integrated encoder using the absolute encoder (CANcoder) to reduce drift that accumulates over time. Currently, recalibration only happens once at startup.

**Current behavior:**
- `resetToAbsolute()` is called once during module initialization (in `configAngleMotor()`)
- After startup, the integrated encoder tracks position changes but can accumulate drift
- No periodic correction from the absolute encoder

**Proposed behavior:**
- Periodically recalibrate the integrated encoder using the CANcoder's absolute position
- Only recalibrate when safe (module stationary or moving slowly) to avoid sudden position jumps
- Options: periodic recalibration (every few seconds) or threshold-based recalibration (when drift exceeds a limit)

### Why
- **Reduces drift**: Relative encoders can accumulate small errors over time due to mechanical slippage, electrical noise, or other factors
- **Improves accuracy**: Periodic recalibration ensures the integrated encoder stays aligned with the absolute encoder's ground truth
- **Already monitoring**: The code already logs both encoder values to SmartDashboard (lines 182-184 in `SwerveSubsystem`), suggesting drift monitoring is a concern
- **Low overhead**: CANcoder is already configured and available; just needs periodic reading
- **Common practice**: Many teams implement periodic recalibration to maintain encoder accuracy

### Implementation Considerations

**When to recalibrate:**
- **Option 1**: Only when module is stationary (angular velocity near zero)
- **Option 2**: Only when robot is disabled
- **Option 3**: Periodic recalibration (e.g., every 2-5 seconds) when safe
- **Option 4**: Threshold-based (recalibrate when drift exceeds threshold, e.g., 2-5 degrees)

**CANcoder update frequency:**
- Currently set to 1 Hz (line 97 in `SwerveModule.java`)
- For periodic recalibration, consider increasing to 4-10 Hz to get more frequent absolute position updates
- Trade-off: Higher frequency = more CAN bus traffic but better recalibration responsiveness

**Safety considerations:**
- Must check that module is not actively turning before recalibrating
- Sudden position jumps during active movement could cause instability
- Consider adding a velocity check: only recalibrate when `Math.abs(angularVelocity) < threshold`

### Proposed Implementation

**Step 1: Add public recalibration method to SwerveModule**
```java
/**
 * Recalibrates the integrated encoder using the absolute encoder position.
 * Should only be called when the module is stationary or moving slowly to avoid
 * sudden position jumps.
 */
public void recalibrateFromAbsolute() {
    resetToAbsolute();
}
```

**Step 2: Add periodic recalibration logic to SwerveSubsystem.periodic()**
```java
// Option A: Periodic recalibration (every 2 seconds)
private int recalibrationCounter = 0;
@Override
public void periodic() {
    // ... existing code ...
    
    // Recalibrate every 2 seconds (100 cycles at 20ms = 2 seconds)
    recalibrationCounter++;
    if (recalibrationCounter >= 100) {
        recalibrationCounter = 0;
        for (SwerveModule mod : mSwerveMods) {
            // Only recalibrate if module is not actively turning
            // (check if angular velocity is near zero)
            mod.recalibrateFromAbsolute();
        }
    }
}

// Option B: Threshold-based recalibration
@Override
public void periodic() {
    // ... existing code ...
    
    for (SwerveModule mod : mSwerveMods) {
        double integratedAngle = mod.getState().angle.getDegrees();
        double absoluteAngle = mod.getCanCoder().getDegrees();
        double drift = Math.abs(Math.IEEEremainder(integratedAngle - absoluteAngle, 360));
        
        // Recalibrate if drift exceeds threshold (e.g., 3 degrees)
        if (drift > 3.0) {
            mod.recalibrateFromAbsolute();
        }
    }
}
```

**Step 3: Consider increasing CANcoder update frequency**
```java
// In SwerveModule constructor, line 97
// Increase from 1 Hz to 4-10 Hz for periodic recalibration
angleEncoder.getAbsolutePosition().setUpdateFrequency(4); // or 10
```

### Where
- **File to modify**: `src/main/java/frc/robot/SwerveModule.java`
  - Add public `recalibrateFromAbsolute()` method (around line 194)
  - Optionally increase CANcoder update frequency (line 97)
- **File to modify**: `src/main/java/frc/robot/Subsystems/SwerveSubsystem.java`
  - Add recalibration logic to `periodic()` method (around line 172)
- **Monitoring**: Already in place (lines 182-184 log both encoder values to SmartDashboard)

### Impact
- **Medium priority**: Addresses encoder drift issue that may be affecting accuracy
- **Low risk**: Can be implemented incrementally and tested
- **Performance consideration**: Periodic CANcoder reads add minimal CAN bus traffic
- **Testing required**: Should verify recalibration doesn't cause instability or sudden movements
- **Tuning needed**: Recalibration frequency and drift threshold may need adjustment based on observed drift behavior

### Decision Points
- **Recalibration frequency**: How often to recalibrate? (Periodic vs threshold-based)
- **Safety check**: Should recalibration only happen when stationary, or can it happen during slow movement?
- **CANcoder frequency**: Is 1 Hz sufficient, or should it be increased for periodic recalibration?
- **Drift threshold**: If using threshold-based approach, what drift amount triggers recalibration?

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] In progress
- [ ] Implemented

---

## 8. Add configAngleEncoder() Method for Consistency (Code Organization)

### What
Add a `configAngleEncoder()` private method to match the existing pattern used for `configAngleMotor()` and `configDriveMotor()`. Move the angle encoder (CANcoder) configuration code from the constructor into this dedicated method.

**Current code:**
```java
public SwerveModule(int moduleNumber, ModuleData moduleConstants){
    // ... other initialization ...
    
    /* Angle Encoder Configuration */
    angleEncoder = new CANcoder(moduleConstants.encoderID());
    angleEncoder.getConfigurator().apply(new CANcoderConfiguration());
    angleEncoder.getAbsolutePosition().setUpdateFrequency(1);
    
    /* Angle Motor Configuration */
    angleMotor = new SparkMax(...);
    integratedAngleEncoder = angleMotor.getEncoder();
    angleController = angleMotor.getClosedLoopController();
    configAngleMotor();  // Configuration in separate method
    
    /* Drive Motor Configuration */
    driveMotor = new SparkFlex(...);
    driveEncoder = driveMotor.getEncoder();
    driveController = driveMotor.getClosedLoopController();
    configDriveMotor();  // Configuration in separate method
}
```

**Proposed code:**
```java
public SwerveModule(int moduleNumber, ModuleData moduleConstants){
    // ... other initialization ...
    
    /* Angle Encoder Configuration */
    angleEncoder = new CANcoder(moduleConstants.encoderID());
    configAngleEncoder();  // Configuration in separate method (consistent pattern)
    
    /* Angle Motor Configuration */
    angleMotor = new SparkMax(...);
    integratedAngleEncoder = angleMotor.getEncoder();
    angleController = angleMotor.getClosedLoopController();
    configAngleMotor();
    
    /* Drive Motor Configuration */
    driveMotor = new SparkFlex(...);
    driveEncoder = driveMotor.getEncoder();
    driveController = driveMotor.getClosedLoopController();
    configDriveMotor();
}

/**
 * Configures the angle encoder (CANcoder) with all necessary settings.
 * Called once during module initialization in the constructor.
 */
private void configAngleEncoder() {
    // Apply default configuration to the CANcoder (factory reset to known state)
    angleEncoder.getConfigurator().apply(new CANcoderConfiguration());
    // Set update frequency to 1 Hz (once per second) for absolute position readings.
    // The CANcoder (absolute encoder) is only used once during robot startup to calibrate
    // the integrated encoder (see resetToAbsolute() in configAngleMotor()). During normal
    // operation, getAngle() reads from the integrated encoder every 20ms loop cycle, not
    // the CANcoder. A low CANcoder update frequency reduces CAN bus traffic since we only
    // need the absolute position once at startup, not continuously.
    angleEncoder.getAbsolutePosition().setUpdateFrequency(1);
}
```

### Why
- **Consistency**: All three components (angle encoder, angle motor, drive motor) follow the same pattern: object creation in constructor, configuration in dedicated method
- **Encapsulation**: Configuration logic is isolated in a dedicated method, making it easier to find and modify
- **Maintainability**: All encoder configuration settings are in one place, easier to understand and modify
- **Readability**: Constructor shows high-level initialization flow; details are in configuration methods
- **Follows existing pattern**: Matches the established pattern used for motor configuration

### Where
- **File**: `src/main/java/frc/robot/SwerveModule.java`
- **Lines to move**: Lines 89-97 (CANcoder configuration) from constructor to new method
- **New method**: Add `configAngleEncoder()` method after `getCanCoder()` method (around line 217)
- **Constructor update**: Line 88 creates the CANcoder, then call `configAngleEncoder()` on line 89

### Impact
- **Low risk change**: Purely organizational - no functional changes
- **Improves consistency**: All three components now follow the same configuration pattern
- **Better code organization**: Configuration logic is properly encapsulated
- **No breaking changes**: All existing functionality remains the same

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] Implemented

---

## 9. Remove Unused Raw Encoder Methods (Code Cleanup)

### What
Remove unused methods that return raw encoder values. These methods are not called anywhere in the codebase and appear to be leftover diagnostic/debugging code.

**Methods to remove:**
1. `SwerveModule.getRawDriveEncoder()` - Returns raw drive encoder position in encoder units
2. `SwerveModule.getRawTurnEncoder()` - Returns raw turn encoder position in encoder units
3. `SwerveSubsystem.getEncoderRotations()` - Uses `getRawDriveEncoder()` to calculate wheel rotations
4. `SwerveModule.pointInDirection(double)` - Points the wheel in a specific direction without changing drive speed

**Current code:**
```java
// SwerveModule.java (lines 147-161)
public double getRawDriveEncoder(){
    return driveEncoder.getPosition();
}

public double getRawTurnEncoder(){
    return integratedAngleEncoder.getPosition();
}

// SwerveSubsystem.java (lines 145-151)
public double[] getEncoderRotations() {
    double[] distances = new double[4];
    for (SwerveModule mod : mSwerveMods){
      distances[mod.moduleNumber] = mod.getRawDriveEncoder() / SwerveConstants.wheelCircumference;
    }
    return distances;
}

// SwerveModule.java (lines 316-329)
public void pointInDirection(double degrees){
    angleController.setReference(degrees, ControlType.kPosition);
    lastAngle = Rotation2d.fromDegrees(degrees);
}
```

**Proposed code:**
- Remove all three methods entirely

### Why
- **Dead code**: These methods are not called anywhere in the codebase
- **Code cleanliness**: Removing unused code reduces maintenance burden and confusion
- **Clarity**: Eliminates methods that might mislead developers into thinking they're used
- **No functional impact**: Since they're unused, removing them has no effect on robot behavior
- **Potential future confusion**: If these methods are needed later, they can be re-added with proper documentation and usage

### Where
- **File**: `src/main/java/frc/robot/SwerveModule.java`
  - **Lines to remove**: 147-153 (`getRawDriveEncoder()` method)
  - **Lines to remove**: 155-161 (`getRawTurnEncoder()` method)
  - **Lines to remove**: 316-329 (`pointInDirection()` method)
- **File**: `src/main/java/frc/robot/Subsystems/SwerveSubsystem.java`
  - **Lines to remove**: 145-151 (`getEncoderRotations()` method)

### Impact
- **Low risk change**: Removing unused code has no functional impact
- **Code reduction**: Removes ~25 lines of unused code (including `pointInDirection()`)
- **No breaking changes**: Since these methods aren't called, removing them won't break anything
- **Cleaner codebase**: Makes it clearer which methods are actually used

### Note
If these methods were intended for future use (e.g., diagnostics, debugging, or planned features), consider:
- **Option 1**: Remove them now and re-add with proper documentation when needed
- **Option 2**: Keep them but add JavaDoc comments noting they're currently unused and intended for future diagnostics
- **Option 3**: If keeping for diagnostics, add calls to them in `SwerveSubsystem.periodic()` to log values to SmartDashboard

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] Implemented

---

## 10. Use or Remove isEncoderDataValid() Method (Error Handling)

### What
The `isEncoderDataValid()` method in `SwerveModule` checks for motor encoder errors but is currently not called anywhere. Either implement error monitoring using this method, or remove it if error checking is not needed.

**Current code:**
```java
// SwerveModule.java (lines 163-189)
/**
 * Checks if encoder data from both motors is valid (no errors).
 * ...
 * <b>Note:</b> This method is currently not called anywhere in the codebase.
 */
public boolean isEncoderDataValid(){
    return driveMotor.getLastError() == REVLibError.kOk && angleMotor.getLastError() == REVLibError.kOk;
}
```

**Option 1: Implement error monitoring (Recommended)**
Add periodic error checking in `SwerveSubsystem.periodic()` to monitor module health and log errors.

**Option 2: Remove unused method**
If error monitoring is not needed, remove the method entirely.

### Why
- **Error detection**: Motor encoder errors can indicate hardware problems (CAN bus issues, loose connections, motor controller faults) that should be detected and logged
- **Diagnostics**: Monitoring encoder validity helps identify problems during matches or practice
- **Code clarity**: Either use the method for its intended purpose or remove it to avoid confusion
- **Proactive maintenance**: Early detection of encoder errors can prevent unpredictable robot behavior

### Option 1: Implement Error Monitoring (Recommended)

**Proposed code for SwerveSubsystem.periodic():**
```java
@Override
public void periodic() {
    odometry.update(getYaw(), getPositions());
    updateOdometryWithVision("limelight-a");
    updateOdometryWithVision("limelight-b");
    field.setRobotPose(getPose());

    SmartDashboard.putNumber("Pigeon Yaw", pigeon.getYaw().getValueAsDouble());

    for (SwerveModule mod : mSwerveMods) {
        // Log encoder values (existing code)
        SmartDashboard.putNumber(
            "Mod " + mod.moduleNumber + " Cancoder", mod.getCanCoder().getDegrees());
        SmartDashboard.putNumber(
            "Mod " + mod.moduleNumber + " Integrated", mod.getState().angle.getDegrees());
        SmartDashboard.putNumber(
            "Mod " + mod.moduleNumber + " Velocity", mod.getState().speedMetersPerSecond);
        
        // NEW: Check for encoder errors
        boolean isValid = mod.isEncoderDataValid();
        SmartDashboard.putBoolean(
            "Mod " + mod.moduleNumber + " Encoder Valid", isValid);
        
        // Optional: Log error details if invalid
        if (!isValid) {
            System.err.println("WARNING: Module " + mod.moduleNumber + 
                " encoder error detected!");
        }
    }
    swerveDataPublisher.set(getStates());
}
```

**Additional enhancements (optional):**
- Disable modules with encoder errors to prevent unpredictable behavior
- Add error counters to track how often errors occur
- Send error notifications to Driver Station
- Attempt automatic recovery (re-initialization) for transient errors

### Option 2: Remove Unused Method

**Proposed code:**
- Remove the `isEncoderDataValid()` method from `SwerveModule.java` (lines 163-189)
- Remove the import for `REVLibError` if it's not used elsewhere

### Where
- **File to modify**: `src/main/java/frc/robot/Subsystems/SwerveSubsystem.java`
  - **Location**: `periodic()` method (around line 172)
  - Add error checking loop after existing SmartDashboard logging
- **File to modify (if removing)**: `src/main/java/frc/robot/SwerveModule.java`
  - **Lines to remove**: 163-189 (`isEncoderDataValid()` method)
  - **Import to check**: Line 9 (`import com.revrobotics.REVLibError;`) - remove if unused elsewhere

### Impact
- **Option 1 (Implement monitoring)**: 
  - Low risk, adds diagnostic capability
  - Minimal performance impact (boolean check per module per cycle)
  - Improves robot reliability and troubleshooting
- **Option 2 (Remove method)**:
  - Low risk, removes unused code
  - No functional impact since method is unused
  - Reduces code complexity

### Decision Points
- **Is error monitoring needed?** If yes, implement Option 1
- **What action should be taken on errors?** Log only, disable module, attempt recovery?
- **How frequently should errors be checked?** Every cycle (20ms) or periodically?
- **Should errors be logged to Driver Station?** Or just SmartDashboard?

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] In progress
- [ ] Implemented

---

## Future Recommendations

_Additional code improvement recommendations will be added here as they are identified._
