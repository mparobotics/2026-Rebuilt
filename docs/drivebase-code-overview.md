# FRC Swerve Drive Code Overview

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Command-Based Framework](#command-based-framework)
3. [File Structure & Responsibilities](#file-structure--responsibilities)
4. [How Swerve Drive Works](#how-swerve-drive-works)
5. [Code Flow: From Input to Motors](#code-flow-from-input-to-motors)
6. [Key Components Explained](#key-components-explained)
7. [Important Constants](#important-constants)

---

## Architecture Overview

This robot code uses **WPILib's Command-Based Framework**, which is a design pattern that organizes robot code into:
- **Subsystems**: Represent physical components of the robot (like the drivetrain)
- **Commands**: Represent actions the robot can perform (like driving)
- **RobotContainer**: Connects joystick inputs to commands

This architecture makes the code modular, testable, and easier to maintain.

---

## Command-Based Framework

### Core Concepts

1. **Subsystem**: A class that represents a physical component of the robot
   - Example: `SwerveSubsystem` represents the entire swerve drivetrain
   - Subsystems can only be controlled by one command at a time (enforced by WPILib)

2. **Command**: A class that represents an action
   - Example: `TeleopSwerve` is a command that drives the robot based on joystick input
   - Commands can require subsystems (preventing conflicts)

3. **CommandScheduler**: Automatically runs all active commands
   - Runs every 20ms (50 times per second)
   - Handles starting/stopping commands based on triggers

4. **Default Command**: A command that runs continuously when no other command requires the subsystem
   - `TeleopSwerve` is the default command for `SwerveSubsystem`

---

## File Structure & Responsibilities

### Entry Point Files

#### `Main.java`
- **Purpose**: Entry point of the program
- **What it does**: Starts the robot program by creating a `Robot` instance
- **Key Line**: `RobotBase.startRobot(Robot::new)`

#### `Robot.java`
- **Purpose**: Main robot class that extends `TimedRobot`
- **What it does**: 
  - Initializes `RobotContainer` in `robotInit()`
  - Runs the `CommandScheduler` every 20ms in `robotPeriodic()`
  - Handles different robot states (disabled, teleop, test, autonomous)
- **Key Methods**:
  - `robotInit()`: Called once when robot starts
  - `robotPeriodic()`: Called every 20ms - runs the command scheduler
  - `teleopInit()`: Called when teleop mode starts

### Configuration Files

#### `RobotContainer.java`
- **Purpose**: Configures the robot's subsystems and command bindings
- **What it does**:
  - Creates the `SwerveSubsystem` instance
  - Creates the Xbox controller
  - Maps joystick inputs to commands
  - Sets up the default command (`TeleopSwerve`)
- **Key Features**:
  - Button bindings (Y button zeros gyro, triggers activate auto-align)
  - Speed multipliers (left stick button = 70% speed)
  - Field-oriented vs robot-oriented mode toggle

#### `Constants.java`
- **Purpose**: Stores all configuration constants
- **Contains**:
  - **SwerveConstants**: Physical dimensions, motor IDs, PID values, gear ratios
  - **FieldConstants**: Field dimensions, alliance-specific transformations
- **Why it exists**: Makes tuning easier - change values in one place

### Subsystem Files

#### `SwerveSubsystem.java`
- **Purpose**: Manages the entire swerve drivetrain
- **What it does**:
  - Creates and manages 4 `SwerveModule` objects
  - Handles odometry (tracking robot position on field)
  - Converts joystick inputs to motor commands using kinematics
  - Integrates vision data from Limelight cameras
- **Key Methods**:
  - `drive()`: Main method to drive the robot (field-oriented or robot-oriented)
  - `driveFromChassisSpeeds()`: Drives using pre-calculated speeds
  - `getPose()`: Returns current robot position on field
  - `zeroGyro()`: Resets gyro to 0° (or 180° for red alliance)

#### `SwerveModule.java`
- **Purpose**: Represents a single swerve module (one wheel + turning mechanism)
- **What it does**:
  - Controls 2 motors per module:
    - **Drive Motor**: Makes the wheel spin (forward/backward)
    - **Angle Motor**: Rotates the wheel assembly (left/right)
  - Optimizes wheel angles (prevents wheels from turning more than 90°)
  - Manages encoders (position sensors)
- **Key Methods**:
  - `setDesiredState()`: Sets target speed and angle for the module
  - `optimize()`: Ensures wheel doesn't turn more than 90° (can reverse instead)
  - `getState()`: Returns current speed and angle

### Command Files

#### `TeleopSwerve.java`
- **Purpose**: Command that drives the robot during teleop
- **What it does**:
  - Reads joystick values
  - Applies deadband (ignores small joystick movements)
  - Applies slew rate limiting (smooth acceleration)
  - Converts joystick values to robot speeds
  - Calls `SwerveSubsystem.drive()` to actually move the robot
- **Runs**: Continuously as the default command

#### `AutoAlign.java`
- **Purpose**: Automatically aligns robot to scoring position
- **What it does**:
  - Uses PID controllers to move robot to calculated position
  - Calculates target position based on field geometry
  - Aligns to specific angle (60° increments)
- **Triggered**: When left or right trigger is pressed

---

## How Swerve Drive Works

### What is Swerve Drive?

Swerve drive is a type of drivetrain where each wheel can:
1. **Rotate** (turn left/right) - controlled by the angle motor
2. **Spin** (forward/backward) - controlled by the drive motor

This allows the robot to:
- Move in any direction without turning
- Rotate while moving
- Combine translation and rotation smoothly

### The Math Behind It

1. **Kinematics**: Converts desired robot movement → individual wheel speeds/angles
   - Uses `SwerveDriveKinematics` class from WPILib
   - Takes `ChassisSpeeds` (x, y, rotation) → `SwerveModuleState[]` (4 wheel states)

2. **Odometry**: Tracks robot position on field
   - Uses encoder positions + gyro angle
   - Updates every 20ms
   - Can be corrected with vision data (Limelight)

3. **Field-Oriented Control**: 
   - Forward = away from driver station (regardless of robot orientation)
   - Uses gyro to know which way robot is facing
   - Converts field-relative commands to robot-relative

---

## Code Flow: From Input to Motors

Here's the complete flow when you move the joystick:

```
1. Driver moves joystick
   ↓
2. RobotContainer reads joystick values
   ↓
3. TeleopSwerve command executes (default command)
   ↓
4. TeleopSwerve applies deadband and slew rate limiting
   ↓
5. TeleopSwerve calls SwerveSubsystem.drive()
   ↓
6. SwerveSubsystem.drive() converts to ChassisSpeeds
   (field-oriented or robot-oriented)
   ↓
7. SwerveSubsystem uses SwerveDriveKinematics to calculate
   individual wheel speeds and angles
   ↓
8. For each of 4 modules:
   - SwerveModule.optimize() ensures efficient wheel angle
   - SwerveModule.setAngle() sets angle motor position
   - SwerveModule.setSpeed() sets drive motor velocity
   ↓
9. Motors move!
```

### Example: Moving Forward

1. Driver pushes left stick forward → `translationAxis` = 0.8
2. `TeleopSwerve.execute()` multiplies by `maxSpeed` (3 m/s) → 2.4 m/s
3. `SwerveSubsystem.drive()` creates `ChassisSpeeds(2.4, 0, 0)`
4. Kinematics calculates: all 4 wheels point forward, all spin at 2.4 m/s
5. Each module sets its angle to 0° and speed to 2.4 m/s
6. Robot moves forward!

---

## Key Components Explained

### Gyro (Pigeon2)

- **What**: IMU sensor that measures robot rotation
- **Used for**: 
  - Field-oriented control (knowing which way is "forward")
  - Odometry (tracking robot heading)
- **Location**: `SwerveSubsystem` - CAN ID 23

### Odometry

- **What**: System that tracks robot position on field
- **How it works**:
  1. Starts at (0, 0) when robot turns on
  2. Updates every 20ms using:
     - Encoder positions (how far wheels moved)
     - Gyro angle (which way robot is facing)
  3. Can be corrected with vision (Limelight sees AprilTags)
- **Class**: `SwerveDrivePoseEstimator`

### Kinematics

- **What**: Math that converts robot movement → wheel movements
- **Input**: `ChassisSpeeds` (x, y, rotation)
- **Output**: `SwerveModuleState[]` (speed + angle for each wheel)
- **Class**: `SwerveDriveKinematics`
- **Why needed**: Robot moves as a whole, but we control 4 wheels independently

### Module Optimization

- **Problem**: Wheel might need to turn 180° to point the right way
- **Solution**: Instead, reverse the wheel direction and turn only 0°
- **Example**: 
  - Desired: 180° angle, forward speed
  - Optimized: 0° angle, reverse speed (same result, less turning)

### Vision Integration

- **What**: Uses Limelight cameras to see AprilTags on field
- **Purpose**: Corrects odometry (more accurate than encoders alone)
- **How**: 
  - Limelight sees tag → calculates robot pose
  - If gyro isn't spinning too fast and tags are visible → update odometry
- **Location**: `SwerveSubsystem.updateOdometryWithVision()`

---

## Important Constants

### Physical Dimensions
- `halfTrackWidth`: Half the distance between left/right wheels (11 inches)
- `halfWheelBase`: Half the distance between front/back wheels (11 inches)
- `wheelDiameter`: 4 inches
- **Why important**: Used in kinematics calculations

### Motor Configuration
- **Drive Motors**: SparkFlex (CAN IDs: 11, 13, 15, 17)
- **Angle Motors**: SparkMax (CAN IDs: 12, 14, 16, 18)
- **Encoders**: CANcoders (CAN IDs: 19, 20, 21, 22)
- **Gear Ratios**: 
  - Drive: 8.14:1 (L1 gear ratio)
  - Angle: 21.4:1 (Mk4i module)

### Performance Limits
- `maxSpeed`: 3 m/s (maximum forward speed)
- `maxAngularVelocity`: Calculated from maxSpeed and robot size
- `inputDeadband`: 0.1 (ignores joystick movements smaller than this)

### PID Values
- **Drive Motor**: KP=0.1 (velocity control)
- **Angle Motor**: KP=0.01 (position control)
- **Note**: Many values marked "to tune" - these need adjustment for your robot

### Module Offsets
- Each module has an `angleOffset` (in degrees)
- **Why**: Absolute encoder (CANcoder) might not be perfectly aligned
- **Values**: 
  - Module 0: 158.02°
  - Module 1: 234.97°
  - Module 2: 311.40°
  - Module 3: 299.80°

---

## Key WPILib Classes Used

### Command Framework
- `Command`: Base class for all commands
- `SubsystemBase`: Base class for subsystems
- `CommandScheduler`: Runs commands automatically
- `CommandXboxController`: Xbox controller with command integration

### Swerve Drive
- `SwerveDriveKinematics`: Converts chassis speeds to module states
- `SwerveDrivePoseEstimator`: Tracks robot position
- `SwerveModuleState`: Speed + angle for one module
- `ChassisSpeeds`: Desired robot movement (x, y, rotation)

### Math & Geometry
- `Pose2d`: Robot position + rotation on field
- `Translation2d`: X, Y position
- `Rotation2d`: Angle (in radians or degrees)
- `MathUtil`: Utility functions (deadband, clamp, etc.)

### Hardware
- `Pigeon2`: Gyro sensor (CTRE)
- `SparkMax` / `SparkFlex`: Motor controllers (REV)
- `CANcoder`: Absolute encoder (CTRE)

---

## Common Tasks & Where to Find Code

### Change Maximum Speed
- **Location**: `Constants.SwerveConstants.maxSpeed`
- **Current**: 3 m/s

### Adjust Joystick Sensitivity
- **Location**: `RobotContainer.java` lines 41-43
- **Current**: Multiplied by 0.5 (50% speed)

### Tune PID Values
- **Drive Motor**: `Constants.SwerveConstants.driveKP/KI/KD`
- **Angle Motor**: `Constants.SwerveConstants.angleKP/KI/KD`

### Change Module CAN IDs
- **Location**: `Constants.SwerveConstants.moduleData` array
- **Format**: `new ModuleData(driveID, angleID, encoderID, offset, location)`

### Add New Command
1. Create new class extending `Command`
2. Add to `RobotContainer.configureBindings()`
3. Bind to button/trigger

### Debug Robot Position
- **Location**: SmartDashboard → "Field" widget
- **Shows**: Robot position on field visualization

---

## Tips for Understanding the Code

1. **Start with RobotContainer**: This shows how everything connects
2. **Follow the data flow**: Joystick → Command → Subsystem → Module → Motor
3. **Read the comments**: Many files have helpful inline comments
4. **Use SmartDashboard**: Check values in real-time while robot runs
5. **Test incrementally**: Test one module, then all four, then full drive

---

## Next Steps

1. **Tune PID values**: Current values are placeholders
2. **Calibrate module offsets**: May need adjustment after mechanical changes
3. **Characterize drive motors**: Calculate feedforward values (KS, KV, KA)
4. **Test vision integration**: Verify Limelight cameras are working
5. **Add autonomous**: Create path-following commands

---

## Additional Resources

- [WPILib Documentation](https://docs.wpilib.org/en/latest/)
- [Swerve Drive Kinematics](https://docs.wpilib.org/en/latest/docs/software/kinematics-and-odometry/swerve-drive-kinematics.html)
- [Command-Based Programming](https://docs.wpilib.org/en/latest/docs/software/commandbased/index.html)
- [Swerve Drive Odometry](https://docs.wpilib.org/en/latest/docs/software/kinematics-and-odometry/swerve-drive-odometry.html)
