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

In TeleopSwerve command creation
```java
() -> driveController.getRawAxis(RIGHT_TRIGGER_AXIS) > 0.1
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
  - Line 49: `Axis.kLeftTrigger.value` → `LEFT_TRIGGER_AXIS`
  - Line 51: `Axis.kRightTrigger.value` → `RIGHT_TRIGGER_AXIS`
  - Line 66: `driveController.getRightTriggerAxis()` → `driveController.getRawAxis(RIGHT_TRIGGER_AXIS)`
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
- Line 12: `m_autonomousCommand` → `autonomousCommand`
- Line 14: `m_robotContainer` → `robotContainer`
- Lines 17, 36, 38, 39, 51, 52: References need updating

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

## Future Recommendations

_Additional code improvement recommendations will be added here as they are identified._
