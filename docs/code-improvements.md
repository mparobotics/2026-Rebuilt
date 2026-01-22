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

## 11. Eliminate Duplication in Swerve Module Position Constants (Code Organization)

**Note**: This recommendation has been merged with Recommendation #12. See Recommendation #12 for the comprehensive solution that addresses both module position duplication and index mapping issues.

---

## 12. Eliminate Order Dependency and Improve Module Configuration Safety (Code Safety & Organization)

### What
Refactor the swerve module configuration to use a `SwerveModuleIndex` enum for type-safe indexing, move `swerveKinematics` to `SwerveSubsystem` (derived from `moduleData`), and encapsulate array creation/access to eliminate implicit ordering dependencies. This addresses both the duplication issue from Recommendation #11 and the index mapping issues.

**Current problems:**
1. **Order dependency**: `swerveKinematics` and `moduleData` must be in the same order, but this is only enforced by comments
2. **Duplication**: `swerveKinematics` uses inline `Translation2d` calculations instead of deriving from `moduleData`
3. **Index confusion**: No type-safe way to access modules by position (must use magic numbers like `moduleData[2]`)
4. **Array ordering risks**: Multiple places create arrays that must match module order (getStates, getPositions, driveFromChassisSpeeds)

**Proposed solution:**
- Create `SwerveModuleIndex` enum for type-safe module indexing
- Move `swerveKinematics` from `Constants` to `SwerveSubsystem` and derive it from `moduleData` locations
- Use enum throughout for all module indexing operations
- Encapsulate array creation as private methods using enum iteration
- Make array-returning methods private (implementation detail for WPILib APIs)

### Changes to `Constants.java`

**1. Rename location constants to avoid confusion with enum** (update lines 103-106):
```java
//Location of modules - renamed with _LOCATION suffix to avoid confusion with SwerveModuleIndex enum
public static final Translation2d BACK_RIGHT_LOCATION  = new Translation2d(-halfWheelBase, -halfTrackWidth);
public static final Translation2d FRONT_RIGHT_LOCATION = new Translation2d(halfWheelBase, -halfTrackWidth);
public static final Translation2d FRONT_LEFT_LOCATION  = new Translation2d(halfWheelBase, halfTrackWidth);
public static final Translation2d BACK_LEFT_LOCATION   = new Translation2d(-halfWheelBase, halfTrackWidth);
```

**2. Add `SwerveModuleIndex` enum** (after line 106, before `ModuleData` record):
```java
/* Module Index Enum
 * Provides type-safe indexing for swerve modules, eliminating order dependency risks.
 * The enum order defines the canonical module ordering used throughout the codebase.
 */
public enum SwerveModuleIndex {
  BACK_RIGHT(0),
  FRONT_RIGHT(1),
  FRONT_LEFT(2),
  BACK_LEFT(3);
  
  public final int index;
  
  SwerveModuleIndex(int index) {
    this.index = index;
  }
}
```

**3. Refactor `moduleData` with explicit documentation** (replace lines 113-118):
```java
/* Module Specific Constants
 * Module order is defined by SwerveModuleIndex enum.
 * The order in moduleData[] MUST match the enum order.
 */
public record ModuleData(
  int driveMotorID, int angleMotorID, int encoderID, double angleOffset, Translation2d location
){}

// Module configuration data - explicitly assigned using enum indices for type safety
public static final ModuleData[] moduleData = new ModuleData[4];
static {
  moduleData[SwerveModuleIndex.BACK_RIGHT.index]  = new ModuleData(11, 14, 19, 159.87, BACK_RIGHT_LOCATION);
  moduleData[SwerveModuleIndex.FRONT_RIGHT.index] = new ModuleData(17, 18, 22, 232.39, FRONT_RIGHT_LOCATION);
  moduleData[SwerveModuleIndex.FRONT_LEFT.index]  = new ModuleData(15, 16, 21, 311.67, FRONT_LEFT_LOCATION);
  moduleData[SwerveModuleIndex.BACK_LEFT.index]   = new ModuleData(13, 12, 20, 299.45, BACK_LEFT_LOCATION);
}
```

**4. Remove `swerveKinematics` from Constants** (remove lines 47-56):
- The `swerveKinematics` static field will be moved to `SwerveSubsystem` and created from `moduleData` locations

### Changes to `SwerveSubsystem.java`

**1. Add `swerveKinematics` as instance field** (after line 33):
```java
private SwerveDrivePoseEstimator odometry;
private SwerveDriveKinematics swerveKinematics;  // NEW: Instance field, derived from moduleData
private SwerveModule[] mSwerveMods;
```

**3. Update constructor to accept `moduleData` as parameter and use enum** (replace lines 59-78):
```java
/**
 * Creates a new SwerveSubsystem.
 * @param moduleData Array of module configuration data (one per module)
 */
public SwerveSubsystem(ModuleData[] moduleData) { 
  //instantiates new pigeon gyro, wipes it, and zeros it
  pigeon = new Pigeon2(SwerveConstants.PIGEON_ID);
  pigeon.getConfigurator().apply(new Pigeon2Configuration()); 
  zeroGyro();

  // Creates all four swerve modules using enum for type-safe indexing
  mSwerveMods = new SwerveModule[4];
  Translation2d[] moduleLocations = new Translation2d[4];
  
  for (SwerveModuleIndex moduleIndex : SwerveModuleIndex.values()) {
    ModuleData data = moduleData[moduleIndex.index];  // Use parameter instead of global constant
    mSwerveMods[moduleIndex.index] = new SwerveModule(moduleIndex.index, data);
    moduleLocations[moduleIndex.index] = data.location();
  }

  // Create SwerveDriveKinematics from module locations (ensures alignment with moduleData order)
  // Order is guaranteed by enum iteration order
  swerveKinematics = new SwerveDriveKinematics(moduleLocations);

  //creates new swerve odometry (odometry is where the robot is on the field)
  odometry = new SwerveDrivePoseEstimator(swerveKinematics, getYaw(), getPositions(), new Pose2d());

  //puts out the field
  field = new Field2d();
  SmartDashboard.putData("Field", field);
}
```

**3a. Update `RobotContainer` to pass `moduleData`** (update line 34 in `RobotContainer.java`):
```java
// SwerveSubsystem instance for the drive subsystem
private final SwerveSubsystem m_drive = new SwerveSubsystem(SwerveConstants.moduleData);
```

**4. Update `driveFromChassisSpeeds()` to use enum** (replace lines 119-128):
```java
public void driveFromChassisSpeeds(ChassisSpeeds driveSpeeds, boolean isOpenLoop){
  SwerveModuleState[] desiredStates = swerveKinematics.toSwerveModuleStates(driveSpeeds);
  SwerveDriveKinematics.desaturateWheelSpeeds(desiredStates, SwerveConstants.maxSpeed);

  desiredSwerveDataPublisher.set(desiredStates);

  // Use enum for type-safe indexing - ensures correct module-to-state mapping
  for (SwerveModuleIndex moduleIndex : SwerveModuleIndex.values()) {
    mSwerveMods[moduleIndex.index].setDesiredState(desiredStates[moduleIndex.index], false);
  }
}
```

**5. Update `getChassisSpeeds()` to use instance field** (replace line 131):
```java
public ChassisSpeeds getChassisSpeeds(){
  return swerveKinematics.toChassisSpeeds(getStates());  // Changed: use instance field
}
```

**6. Make `getStates()` private and use enum** (replace lines 143-149):
```java
/**
 * Gets current states of all swerve modules as an array.
 * Private method - array creation is an implementation detail for WPILib APIs.
 * Uses enum to ensure correct ordering.
 */
private SwerveModuleState[] getStates() {
  SwerveModuleState[] states = new SwerveModuleState[4];
  for (SwerveModuleIndex moduleIndex : SwerveModuleIndex.values()) {
    states[moduleIndex.index] = mSwerveMods[moduleIndex.index].getState();
  }
  return states;
}
```

**7. Make `getPositions()` private and use enum** (replace lines 151-157):
```java
/**
 * Gets current positions of all swerve modules as an array.
 * Private method - array creation is an implementation detail for WPILib APIs.
 * Uses enum to ensure correct ordering.
 */
private SwerveModulePosition[] getPositions(){
  SwerveModulePosition[] positions = new SwerveModulePosition[4];
  for (SwerveModuleIndex moduleIndex : SwerveModuleIndex.values()) {
    positions[moduleIndex.index] = mSwerveMods[moduleIndex.index].getPosition();
  }
  return positions;
}
```

**8. Remove `getEncoderRotations()` method** (delete lines 159-165):
- This method is unused and should be removed

### Why
- **Eliminates order dependency**: Enum defines canonical module ordering; `swerveKinematics` is derived from `moduleData`, ensuring they always match
- **Type safety**: Enum prevents index errors (compiler catches `SwerveModuleIndex.FRONT_LEFT.index` vs magic number `2`)
- **Single source of truth**: Enum order is the authoritative definition of module ordering
- **Better encapsulation**: Arrays are private implementation details; external code doesn't need to know about ordering
- **Guaranteed alignment**: `swerveKinematics` derived from `moduleData` locations ensures they're always in sync
- **Clearer code**: Enum names (`SwerveModuleIndex.FRONT_LEFT`) are self-documenting
- **Easier maintenance**: Change module order in one place (enum), everything else follows
- **Eliminates duplication**: `swerveKinematics` no longer duplicates position calculations
- **Dependency injection**: Passing `moduleData` as constructor parameter makes dependencies explicit, improves testability, and reduces coupling to `Constants` class
- **Avoids naming confusion**: Location constants renamed with `_LOCATION` suffix to distinguish from `SwerveModuleIndex` enum values (e.g., `BACK_RIGHT_LOCATION` vs `SwerveModuleIndex.BACK_RIGHT`)

### Where
- **File**: `src/main/java/frc/robot/Constants.java`
  - Rename location constants to add `_LOCATION` suffix (lines 103-106)
  - Add `SwerveModuleIndex` enum (after line 106)
  - Update `moduleData` to use renamed location constants (lines 113-118)
  - Remove `swerveKinematics` static field (lines 47-56)
- **File**: `src/main/java/frc/robot/Subsystems/SwerveSubsystem.java`
  - Add `swerveKinematics` instance field (after line 33)
  - Update constructor to accept `moduleData` parameter (lines 59-78)
  - Update `driveFromChassisSpeeds()` (lines 119-128)
  - Update `getChassisSpeeds()` (line 131)
  - Make `getStates()` private and use enum (lines 143-149)
  - Make `getPositions()` private and use enum (lines 151-157)
  - Remove `getEncoderRotations()` (lines 159-165)
- **File**: `src/main/java/frc/robot/RobotContainer.java`
  - Update `SwerveSubsystem` instantiation to pass `moduleData` (line 34)

### Impact
- **Medium risk**: Refactoring affects core swerve drive initialization and array creation
- **No functional changes**: Behavior remains identical, only improves type safety and organization
- **Testing required**: Verify swerve drive works correctly in all modes (teleop, autonomous, odometry)
- **Breaking changes**: None - all changes are internal to the subsystem
- **Maintainability**: Significantly improves code safety and reduces risk of ordering bugs

### Testing Checklist
- [ ] Robot drives correctly in teleop
- [ ] Field-oriented drive works
- [ ] Robot-centric drive works
- [ ] Odometry updates correctly
- [ ] Vision pose estimation works
- [ ] NetworkTables publishers show correct data
- [ ] SmartDashboard displays module data correctly
- [ ] No compilation errors
- [ ] No runtime errors

### Implementation Notes
1. The enum order defines the canonical module ordering used throughout the codebase
2. `moduleData` array order must match enum order (enforced by comments and enum iteration)
3. `swerveKinematics` is created from `moduleData` locations, ensuring alignment
4. All array access uses enum indices for type safety
5. Array-returning methods are private (implementation detail for WPILib APIs)

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] In progress
- [ ] Implemented

---

## 13. Move SwerveModule to Subsystem Package (Code Organization)

### What
Move `SwerveModule.java` from the `frc.robot` package to the `frc.robot.subsystem` package (after Recommendation #14 renames `Subsystems` to `subsystem`) to keep all swerve-related code together and improve code organization.

**Current structure:**
```
src/main/java/frc/robot/
  ├── SwerveModule.java          ← Currently here
  └── Subsystems/
      └── SwerveSubsystem.java   ← Uses SwerveModule
```

**Proposed structure (after package rename):**
```
src/main/java/frc/robot/
  └── subsystem/
      ├── SwerveModule.java      ← Move here
      └── SwerveSubsystem.java
```

**Required changes:**
1. Move file: `src/main/java/frc/robot/SwerveModule.java` → `src/main/java/frc/robot/subsystem/SwerveModule.java`
   - **Note**: This should be done after Recommendation #14 renames `Subsystems` to `subsystem`
2. Update package declaration in `SwerveModule.java`:
   ```java
   // Change from:
   package frc.robot;
   
   // To:
   package frc.robot.subsystem;  // Note: singular, lowercase
   ```
3. Update import in `SwerveSubsystem.java`:
   ```java
   // Change from:
   import frc.robot.SwerveModule;
   
   // To:
   import frc.robot.subsystem.SwerveModule;
   // Or simply remove the import since it's in the same package
   ```

### Why
- **Better organization**: Keeps all swerve-related code (subsystem and its components) in one location
- **Improved maintainability**: Easier to find and modify related code when it's grouped together
- **Follows Java best practices**: Related classes should be in the same package
- **Reduces coupling**: Since `SwerveModule` is only used by `SwerveSubsystem`, they should be in the same package
- **Clearer structure**: Makes it obvious that `SwerveModule` is a component of the swerve subsystem, not a standalone class

### Where
- **File to move**: `src/main/java/frc/robot/SwerveModule.java`
- **Target location**: `src/main/java/frc/robot/subsystem/SwerveModule.java` (after Recommendation #14 renames the package)
- **File to update**: `src/main/java/frc/robot/subsystem/SwerveSubsystem.java` (line 28: import statement)
- **Implementation order**: Should be done after Recommendation #14 (package naming fix) is completed

### Impact
- **Low risk**: `SwerveModule` is only used by `SwerveSubsystem`, so only one import needs to be updated
- **No functional changes**: This is purely a code organization change
- **Build system**: May need to refresh/rebuild the project after moving the file
- **IDE**: Most IDEs can handle this refactoring automatically (e.g., "Move" refactoring in IntelliJ/VS Code)

### Additional Considerations
- **Future subsystems**: If other subsystems are added that have component classes, they should follow the same pattern (component classes in the same package as the subsystem)
- **Shared components**: If `SwerveModule` were to be used by multiple subsystems in the future, it might make sense to keep it in a shared location, but currently it's only used by `SwerveSubsystem`
- **Implementation order**: This recommendation should be implemented **after** Recommendation #14 (package naming fix), since the package will be renamed from `Subsystems` to `subsystem` first
- **Package naming**: After Recommendation #14, the package will be `frc.robot.subsystem` (lowercase, singular), which aligns with Java conventions

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] In progress
- [ ] Implemented

---

## 14. Fix Package Naming Convention (Java Standards Compliance)

### What
Rename all package directories and package declarations to use lowercase letters and singular form, following the official Java naming convention (JLS §6). Package names should be all lowercase ASCII letters, and should use singular form for consistency with Java standard library conventions.

**Current packages (incorrect):**
- `frc.robot.Command` → Should be `frc.robot.command` (lowercase, singular)
- `frc.robot.Subsystems` → Should be `frc.robot.subsystem` (lowercase, singular - note: changed from plural to singular for consistency)
- `frc.lib.TunableControllers` → Should be `frc.lib.tunablecontroller` (lowercase, singular)

**Proposed packages (correct):**
- `frc.robot.command` (singular - each class represents one command)
- `frc.robot.subsystem` (singular - each class represents one subsystem)
- `frc.lib.tunablecontroller` (singular - each class represents one controller type)

**Required changes:**

1. **Rename directories:**
   - `src/main/java/frc/robot/Command/` → `src/main/java/frc/robot/command/`
   - `src/main/java/frc/robot/Subsystems/` → `src/main/java/frc/robot/subsystem/` (note: singular, not plural)
   - `src/main/java/frc/lib/TunableControllers/` → `src/main/java/frc/lib/tunablecontroller/` (note: singular, not plural)

2. **Update package declarations in all affected files:**
   ```java
   // Command package files:
   // Change from: package frc.robot.Command;
   // To: package frc.robot.command;
   
   // Subsystems package files:
   // Change from: package frc.robot.Subsystems;
   // To: package frc.robot.subsystem;  // Note: singular, not "subsystems"
   
   // TunableControllers package files:
   // Change from: package frc.lib.TunableControllers;
   // To: package frc.lib.tunablecontroller;  // Note: singular, not "tunablecontrollers"
   ```

3. **Update all import statements:**
   ```java
   // Change from: import frc.robot.Command.TeleopSwerve;
   // To: import frc.robot.command.TeleopSwerve;
   
   // Change from: import frc.robot.Subsystems.SwerveSubsystem;
   // To: import frc.robot.subsystem.SwerveSubsystem;  // Note: singular
   
   // Change from: import frc.lib.TunableControllers.TunablePID;
   // To: import frc.lib.tunablecontroller.TunablePID;  // Note: singular
   ```

**Files affected:**
- `src/main/java/frc/robot/Command/TeleopSwerve.java`
- `src/main/java/frc/robot/Command/AutoAlign.java`
- `src/main/java/frc/robot/Subsystems/SwerveSubsystem.java`
- `src/main/java/frc/lib/TunableControllers/*.java` (5 files)
- Any files that import from these packages (e.g., `RobotContainer.java`, `SwerveModule.java`)

### Why
- **Java Language Specification compliance**: JLS §6 specifies package names should be all lowercase ASCII letters
- **Industry standard**: Oracle's Code Conventions and most Java style guides require lowercase package names
- **Singular form convention**: Java standard library uses singular package names (`java.util`, `java.lang`, `java.io`), and WPILib follows this pattern (`edu.wpi.first.wpilibj2.command`). Each class represents one command or one subsystem, so singular is more appropriate
- **Consistency**: Using singular for both `command` and `subsystem` creates a consistent naming pattern across the codebase
- **Tool compatibility**: Many build tools, IDEs, and documentation generators assume lowercase package names
- **Cross-platform compatibility**: Case-sensitive filesystems (Linux) can cause issues with mixed-case package names
- **Professional code quality**: Following established conventions makes code more maintainable and easier for new developers to understand
- **Alignment with WPILib**: WPILib uses `edu.wpi.first.wpilibj2.command` (singular), so using `frc.robot.command` and `frc.robot.subsystem` aligns with this pattern

### Where
- **Directories to rename:**
  - `src/main/java/frc/robot/Command/` → `src/main/java/frc/robot/command/`
  - `src/main/java/frc/robot/Subsystems/` → `src/main/java/frc/robot/subsystem/` (singular)
  - `src/main/java/frc/lib/TunableControllers/` → `src/main/java/frc/lib/tunablecontroller/` (singular)
- **Files to update**: All Java files in these packages plus any files that import from them

### Impact
- **Medium risk**: This is a refactoring that affects multiple files and imports
- **Build system**: May need to clean and rebuild the project after renaming
- **Version control**: Git should track the renames, but verify that file history is preserved
- **IDE**: Most IDEs (IntelliJ, VS Code, Eclipse) can handle package renaming automatically via refactoring tools
- **Testing**: Verify that all imports resolve correctly and the project builds successfully

### Additional Considerations
- **Singular vs. plural**: The recommendation uses **singular** form (`subsystem`, not `subsystems`) to match Java standard library conventions and WPILib patterns. Each class represents one subsystem or one command, so singular is semantically correct
- **Refactoring tools**: Use IDE refactoring features (e.g., "Rename Package" in IntelliJ) to automatically update all references
- **Git handling**: Git typically handles directory renames well, but verify file history is preserved
- **Case-sensitive filesystems**: On Linux/Mac, the directory rename is case-sensitive and may require special handling
- **Build.gradle**: Verify that build configuration files don't have hardcoded package paths
- **Documentation**: Update any documentation that references the old package names

### Implementation Steps
1. Use IDE refactoring tool to rename packages (recommended - automatically updates all references)
2. Or manually:
   - Rename directories
   - Update package declarations in all files
   - Update all import statements
   - Clean and rebuild project
   - Verify all tests pass

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] In progress
- [ ] Implemented

---

## 15. Use enableContinuousInput for Swerve Module Angle Control (Performance Improvement)

### What
Replace REV SparkMax's built-in position controller with WPILib's `PIDController` for **the angle motor only** (not the drive motor), and enable continuous input to handle angle wrapping correctly. This prevents modules from taking long rotation paths when angles wrap around (e.g., rotating 340° instead of 20° when going from 350° to 10°).

**Important**: Only the **angle motor controller** should be changed. The drive motor controller should remain unchanged and continue using REV's built-in velocity control.

**Current code:**
```java
// SwerveModule.java
private final SparkClosedLoopController angleController; // REV built-in controller for ANGLE motor
private final SparkClosedLoopController driveController; // REV built-in controller for DRIVE motor (unchanged)

public SwerveModule(...) {
    // ...
    angleController = angleMotor.getClosedLoopController(); // ANGLE motor controller
    // REV controller doesn't support continuous input
    configAngleMotor();
    
    driveController = driveMotor.getClosedLoopController(); // DRIVE motor controller (stays as-is)
    configDriveMotor();
}

private void setAngle(SwerveModuleState desiredState) {
    // ...
    angleController.setReference(angle.getDegrees(), ControlType.kPosition);
    // Problem: If current angle is 350° and target is 10°, 
    // controller sees error of -340° and rotates the long way
}
```

**Proposed code:**
```java
// SwerveModule.java
import edu.wpi.first.math.controller.PIDController;

// ANGLE motor: Use WPILib PIDController with continuous input support
private final SparkClosedLoopController angleControllerREV; // Kept for compatibility (not used)
private final PIDController angleController; // WPILib controller with continuous input

// DRIVE motor: Keep REV's built-in controller (unchanged)
private final SparkClosedLoopController driveController; // REV built-in controller (no changes)

public SwerveModule(...) {
    // ...
    // ANGLE motor: Switch to WPILib PIDController
    angleControllerREV = angleMotor.getClosedLoopController();
    // Create WPILib PIDController for angle control
    angleController = new PIDController(m_angleKP, m_angleKI, m_angleKD);
    // Enable continuous input to handle angle wrapping
    angleController.enableContinuousInput(-180.0, 180.0);
    // Clamp output to reasonable percent output range
    angleController.setOutputRange(-1.0, 1.0);
    configAngleMotor();
    
    // DRIVE motor: Keep existing REV controller (no changes)
    driveController = driveMotor.getClosedLoopController();
    configDriveMotor(); // No changes needed here
}

private void setAngle(SwerveModuleState desiredState) {
    // ...
    double currentAngleDegrees = getAngle().getDegrees();
    double targetAngleDegrees = angle.getDegrees();
    
    // Calculate PID output using continuous input (handles wrapping automatically)
    double output = angleController.calculate(currentAngleDegrees, targetAngleDegrees);
    
    // Set ANGLE motor using percent output from PID controller
    angleMotor.set(output);
    // Now: If current is 350° and target is 10°, 
    // continuous input treats them as 20° apart (shortest path)
}

// setSpeed() method remains unchanged - still uses REV's driveController
```

### Why
- **Prevents inefficient rotations**: Without continuous input, modules may rotate 340° instead of 20° when angles wrap around (e.g., 350° → 10°)
- **Reduces wear**: Shorter rotation paths reduce mechanical wear on swerve modules
- **Improves responsiveness**: Modules reach target angles faster by taking the shortest path
- **Eliminates oscillation**: Prevents modules from oscillating near ±180° boundaries
- **Standard practice**: Many FRC teams use continuous input for swerve angle control to handle circular angle space correctly
- **Currently only used in AutoAlign**: The codebase already uses `enableContinuousInput` in `AutoAlign.java` (line 46), but standard teleop mode doesn't benefit from it

### Where
- **File**: `src/main/java/frc/robot/SwerveModule.java`
- **Changes needed (ANGLE motor only)**:
  - **Line ~22**: Add import for `edu.wpi.first.math.controller.PIDController`
  - **Line ~53**: Replace `angleController` declaration with both REV and WPILib controllers
  - **Lines ~108-114**: Create WPILib PIDController and enable continuous input in constructor
  - **Lines ~310-338**: Update `setAngle()` method to use WPILib PIDController with percent output
  - **Lines ~345-348**: Update `pointInDirection()` method similarly
  - **Line ~373**: Remove PID configuration from `configAngleMotor()` (now handled by WPILib controller)
- **No changes needed**:
  - **Drive motor controller**: Keep `driveController` and `setSpeed()` method unchanged
  - **configDriveMotor()**: No modifications needed - drive motor continues using REV's built-in velocity control

### Impact
- **Medium priority**: Addresses performance and wear issues in swerve drive angle control
- **Scope**: Only affects the **angle motor controller**. The drive motor controller remains unchanged and continues using REV's built-in velocity control with feedforward
- **PID tuning may be needed**: Current `angleKP = 0.01` was tuned for REV's position control. With percent output control, gains may need adjustment:
  - Start with current values and test
  - If modules rotate too slowly, increase `angleKP`
  - If modules overshoot or oscillate, decrease `angleKP` or increase `angleKD`
- **Control mode change**: Switches **angle motor** from position control (REV built-in) to percent output control (WPILib PID)
- **Drive motor unchanged**: Drive motor continues using REV's velocity control with feedforward - no changes needed
- **Active in all modes**: Once implemented, continuous input benefits teleop, autonomous, and any mode using swerve drive
- **No breaking changes**: Existing functionality remains the same, just improved angle handling

### Implementation Considerations

**PID Gain Tuning:**
- The current `angleKP = 0.01` was tuned for REV's position control mode
- With percent output control, gains typically need to be higher
- Recommended starting point: Try `angleKP = 0.1` to `0.5` and tune from there
- Test with modules at various angles, especially near ±180° boundaries
- Verify modules take shortest rotation path (e.g., 350° → 10° should rotate 20°, not 340°)

**Output Clamping:**
- WPILib PIDController output should be clamped to ±1.0 (100% motor output)
- Use `angleController.setOutputRange(-1.0, 1.0)` to prevent excessive motor commands
- Consider adding velocity limiting if needed for smoother motion

**Testing Checklist:**
- [ ] Verify modules rotate correctly at all angles
- [ ] Test angle wrapping scenarios (350° → 10°, -179° → 179°)
- [ ] Confirm modules take shortest rotation path
- [ ] Check for oscillation or overshoot near target angles
- [ ] Verify PID gains are appropriate for percent output control
- [ ] Test in both teleop and autonomous modes

### Related Code
- **AutoAlign.java (line 46)**: Already uses `enableContinuousInput` for rotation controller
- **SwerveConstants.angleKP/KI/KD**: PID gains that may need retuning after this change

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] In progress
- [ ] Implemented

---

## 16. Use WPILib's SwerveModuleState.optimize() Instead of Custom Implementation (Code Standardization)

### What
Replace the custom `optimize()` method in `SwerveModule.java` with WPILib's built-in `SwerveModuleState.optimize()` static method. This ensures consistency with WPILib's standard swerve drive implementations and trajectory-following commands.

**Current code:**
```java
// SwerveModule.java (lines 239-262)
private SwerveModuleState optimize(SwerveModuleState desiredState, Rotation2d currentAngle){
    // Calculate the angular difference between desired and current angle
    double difference = desiredState.angle.getDegrees() - currentAngle.getDegrees();
    // Normalize to -180° to +180° range (shortest rotation path)
    double turnAmount = Math.IEEEremainder(difference,360);

    double speed = desiredState.speedMetersPerSecond;

    // If rotation needed is more than 90°, flip wheel 180° and reverse speed
    if (turnAmount > 90){
        turnAmount -= 180;
        speed *= -1;
    }
    // Same optimization for negative rotation angles
    if (turnAmount < -90){
        turnAmount += 180;
        speed *= -1;
    }

    // Calculate final optimized angle by adding adjusted turn amount to current angle
    double direction = currentAngle.getDegrees() + turnAmount;
    return new SwerveModuleState (speed, Rotation2d.fromDegrees(direction)); 
}

// Usage in setDesiredState() (line 140)
SwerveModuleState optimizedState = optimize(desiredState, getAngle());
```

**Proposed code:**
```java
// Remove the custom optimize() method entirely

// Update setDesiredState() to use WPILib's static method
public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop) {
    // Use WPILib's standard optimize method
    SwerveModuleState optimizedState = SwerveModuleState.optimize(desiredState, getAngle());
    // Set the wheel angle to the optimized direction
    setAngle(optimizedState);
    // Set the drive motor speed (open loop or closed loop based on parameter)
    setSpeed(optimizedState, isOpenLoop);      
}
```

### Why
- **Standard WPILib implementation**: `SwerveModuleState.optimize()` is the official WPILib utility method designed specifically for swerve module optimization. It's part of the standard kinematics library and is used throughout WPILib's own swerve drive examples
- **Battle-tested**: WPILib's method has been tested extensively by thousands of FRC teams and is used in official WPILib trajectory-following commands like `SwerveControllerCommand` and `HolonomicDriveController`
- **Consistency with autonomous**: When using WPILib's trajectory following or holonomic drive controller for autonomous, those classes output `SwerveModuleState[]` arrays that are designed to work with `SwerveModuleState.optimize()`. Using the same method ensures consistent behavior between teleop and autonomous
- **Reduces maintenance burden**: Custom implementations add technical debt. Using the standard library method:
  - Eliminates custom code to maintain
  - Benefits from WPILib's bug fixes and improvements
  - Is easier for new team members to understand
- **Simpler implementation**: WPILib's version is more concise (~5 lines vs ~23 lines) with fewer branches, reducing the chance of logic errors. While both implementations are functionally equivalent, WPILib's version uses `Rotation2d` arithmetic throughout (staying in rotation space) rather than converting to/from degrees, which is more robust and consistent with WPILib's design patterns
- **Reduces numerical precision issues**: The custom implementation converts `Rotation2d` → degrees (via `getDegrees()`) → arithmetic in degrees → back to `Rotation2d` (via `fromDegrees()`). This conversion chain happens every 20ms (~50 times/second, ~6000-9000 times per match). Each conversion introduces small rounding errors:
  - `getDegrees()` uses `atan2(sin, cos) * 180/π` (finite precision)
  - `fromDegrees()` converts back using `degrees * π/180` then recomputes sin/cos
  - These errors are small (~1e-15 radians typically), but over thousands of iterations could theoretically accumulate or cause jitter near threshold boundaries (especially around ±90°)
  - WPILib's version stays in `Rotation2d` space (sin/cos representation) throughout the optimization, avoiding unnecessary conversions and reducing numerical drift
  - **Note**: The system still has necessary conversions at boundaries (encoder → `Rotation2d` in `getAngle()`, `Rotation2d` → degrees in `setAngle()` for motor controller), but eliminating the extra conversions in the optimization loop reduces potential error accumulation
- **Works with enableContinuousInput**: This change complements Recommendation #15 (using `enableContinuousInput`). Both work together to ensure proper angle handling:
  - `SwerveModuleState.optimize()` minimizes rotation distance at the state level
  - `enableContinuousInput()` ensures the PID controller handles angle wrapping correctly

### Where
- **File**: `src/main/java/frc/robot/SwerveModule.java`
- **Lines to remove**: 226-262 (entire custom `optimize()` method)
- **Line to update**: 140 (change `optimize(desiredState, getAngle())` to `SwerveModuleState.optimize(desiredState, getAngle())`)

### Impact
- **Low risk change**: WPILib's `optimize()` method performs the same optimization logic (minimizes rotation by potentially flipping wheel 180° and reversing speed)
- **No functional changes expected**: The behavior should be identical or very similar to the custom implementation
- **Reduces code complexity**: Removes ~37 lines of custom code
- **Improves maintainability**: One less custom method to maintain and debug
- **Better compatibility**: Ensures compatibility with WPILib's trajectory-following features if used in the future

### Implementation Considerations

**Method Signature:**
- WPILib's method: `SwerveModuleState.optimize(SwerveModuleState desiredState, Rotation2d currentAngle)`
- Your current method: `optimize(SwerveModuleState desiredState, Rotation2d currentAngle)`
- The signatures match, so the change is straightforward

**Testing Checklist:**
- [ ] Verify modules still optimize correctly (take shortest rotation path)
- [ ] Test angle wrapping scenarios (350° → 10°, -179° → 179°)
- [ ] Confirm modules flip wheel 180° when appropriate (>90° rotation)
- [ ] Test in both teleop and autonomous modes
- [ ] Verify no regressions in module behavior

**Related to Recommendation #15:**
- This change works well with implementing `enableContinuousInput()` for angle PID control
- Both changes address angle handling: `optimize()` at the state level, `enableContinuousInput()` at the PID level
- Consider implementing both recommendations together for comprehensive angle handling improvements

### Additional Notes
- **Why custom implementation might exist**: Custom implementations are sometimes created before teams discover WPILib's built-in method, or to address specific edge cases. However, WPILib's version is generally preferred
- **If custom logic is needed**: If there's a specific reason the custom implementation differs from WPILib's (e.g., different optimization criteria), document why and consider whether the custom logic is actually necessary
- **WPILib examples**: All official WPILib swerve drive examples use `SwerveModuleState.optimize()`, reinforcing that it's the standard approach

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] In progress
- [ ] Implemented

---

## 17. Remove C++ Dependencies from WPILibNewCommands Vendordeps (Java-Only Project Configuration)

### What
Remove the `cppDependencies` section (including `binaryPlatforms`) from `WPILibNewCommands.json` vendordeps file since this is a Java-only project and doesn't need C++ dependencies.

**Current code:**
```json
{
  "javaDependencies": [
    {
      "groupId": "edu.wpi.first.wpilibNewCommands",
      "artifactId": "wpilibNewCommands-java",
      "version": "wpilib"
    }
  ],
  "jniDependencies": [],
  "cppDependencies": [
    {
      "groupId": "edu.wpi.first.wpilibNewCommands",
      "artifactId": "wpilibNewCommands-cpp",
      "version": "wpilib",
      "libName": "wpilibNewCommands",
      "headerClassifier": "headers",
      "sourcesClassifier": "sources",
      "sharedLibrary": true,
      "skipInvalidPlatforms": true,
      "binaryPlatforms": [
        "linuxsystemcore",
        "linuxathena",
        "linuxarm32",
        "linuxarm64",
        "windowsx86-64",
        "windowsx86",
        "linuxx86-64",
        "osxuniversal"
      ]
    }
  ]
}
```

**Proposed code:**
```json
{
  "javaDependencies": [
    {
      "groupId": "edu.wpi.first.wpilibNewCommands",
      "artifactId": "wpilibNewCommands-java",
      "version": "wpilib"
    }
  ],
  "jniDependencies": []
}
```

### Why
- **Java-only project**: Since this project uses Java and not C++, the `cppDependencies` section is unnecessary
- **Reduces confusion**: Removing C++ dependencies makes it clear this is a Java-only configuration
- **Cleaner configuration**: Only includes dependencies that are actually used
- **Faster builds**: Gradle won't attempt to download C++ binaries that aren't needed
- **Consistency**: Other vendordeps files (like `REVLib.json` and `Phoenix6-frc2026-latest.json`) include both `cppDependencies` and `jniDependencies` because they provide native libraries for Java via JNI. However, `WPILibNewCommands` doesn't need JNI (as indicated by empty `jniDependencies`), so it also doesn't need C++ dependencies
- **No functional impact**: Removing unused C++ dependencies doesn't affect Java functionality

### Where
- **File**: `vendordeps/WPILibNewCommands.json`
- **Lines to remove**: 17-37 (entire `cppDependencies` section including `binaryPlatforms` array)

### Impact
- **Low risk change**: Purely removes unused configuration
- **No functional changes**: Java dependencies remain unchanged
- **Build system**: Gradle will no longer attempt to download C++ binaries for WPILib New Commands
- **Cleaner configuration**: File only contains dependencies relevant to Java project

### Additional Notes
- **When C++ dependencies are needed**: If the project later adds C++ code, the `cppDependencies` section can be re-added
- **JNI vs C++ dependencies**: 
  - `jniDependencies`: Native libraries used by Java via JNI (Java Native Interface) - needed for Java projects that use native code
  - `cppDependencies`: C++ libraries and headers - only needed for C++ projects
  - Since `jniDependencies` is empty for WPILib New Commands, it's a pure Java library and doesn't need C++ dependencies either
- **Other vendordeps**: Files like `REVLib.json` include both because they provide native drivers that Java accesses via JNI, but WPILib New Commands is a pure Java library

### Status
- [ ] Pending team review
- [ ] Approved
- [ ] Rejected
- [ ] Implemented

---

## Future Recommendations

_Additional code improvement recommendations will be added here as they are identified._
