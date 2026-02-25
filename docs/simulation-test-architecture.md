# Simulation and Test Architecture - Complete Guide

**Date:** February 15, 2026  
**Branch:** `jmm-sim-test-support`  
**Status:** Design Review and Recommendations

---

## Table of Contents

1. [Quick Reference](#1-quick-reference)
2. [Executive Summary](#2-executive-summary)
3. [Recommended Code Changes](#3-recommended-code-changes)
4. [Architecture Diagrams](#4-architecture-diagrams)
5. [Control Flow Analysis](#5-control-flow-analysis)
6. [Implementation Assessment](#6-implementation-assessment)
7. [Design Principles](#7-design-principles)
8. [Known Issues and Future Improvements](#8-known-issues-and-future-improvements)
9. [Understanding Hardware Simulation](#9-understanding-hardware-simulation)
10. [Implementation Guidelines](#10-implementation-guidelines)
11. [Appendices](#11-appendices)

---

## 1. Quick Reference

**TL;DR:** Your implementation is fundamentally correct. Only minor improvements needed.

### ✅ What's Working Well

1. **Control flow is correct:** Commands → Subsystem → Modules → `setDesiredState()`
2. **Simulation is externalized:** All logic in `SimulationManager.java`
3. **Tests are externalized:** All logic in `test` package
4. **Capture point is correct:** Store desired states at `setDesiredState()` level
5. **Field2d works:** Same object for real robot and simulation

### ⚠️ Minor Issues to Fix

1. **Unused `pointInDirection()` method** - Already fixed to use `setDesiredState()`
2. **Documentation** - Add clarifying comments (see Recommended Code Changes)
3. **Diagnostics** - Add SmartDashboard output (optional but helpful)

### 🎯 Key Design Principles

1. **Single Entry Point:** All module commands flow through `setDesiredState()`
2. **Separation of Concerns:** Production, simulation, and test code in separate layers
3. **No Conditionals:** Production code never checks `RobotBase.isSimulation()`

### 🏗️ Architecture Overview

**Control Flow:**
```
User Input → TeleopSwerve → SwerveSubsystem.drive() 
    → driveFromChassisSpeeds() → module.setDesiredState()
    → setAngle() + setSpeed() → Motor Controllers
```

**Simulation Flow:**
```
module.setDesiredState() stores optimizedState
    ↓
SimulationManager reads getDesiredStates()
    ↓
Calculates ChassisSpeeds (kinematics)
    ↓
Integrates: simPose += speeds * deltaTime
    ↓
Updates: Pigeon2SimState, Encoder positions, CANcoder
    ↓
SwerveSubsystem.periodic() reads simulated sensors
    ↓
SwerveSubsystem.periodic() updates odometry and Field2d
```

**Test Flow:**
```
SmartDashboard trigger → SwerveDriftTestManager
    → Schedules SwerveAngleDriftTestCommand
    → Gets module via getModule()
    → Calls module.setDesiredState()
    → Reads via getState(), getCanCoder()
    → Records and analyzes results
```

---

## 2. Executive Summary

This document provides a comprehensive analysis of the current simulation and test architecture for the swerve drive robot code. After careful review of the control flow, **your current implementation is fundamentally sound and well-designed**. The architecture successfully separates concerns between production code, simulation logic, and test code.

**Key Findings:**
- ✅ Control flow from user input → commands → subsystem → modules is correct
- ✅ Simulation logic is properly externalized to `SimulationManager`
- ✅ Test logic is properly externalized to test packages
- ✅ The design handles both normal operation and individual module testing
- ⚠️ Minor improvements needed for consistency and completeness

**Assessment Summary:**

**What's Working Well:**
1. Control flow is correct: User input → Commands → Subsystem → Modules
2. Simulation is properly externalized: All simulation logic in `SimulationManager`
3. Test code is properly separated: Test commands in `test` package
4. Capture point is correct: Desired states stored at `setDesiredState()` level (not lower)
5. Field2d integration works correctly: Same object for real robot and simulation

**Minor Issues to Address:**
1. Unused `pointInDirection()` method - **Already fixed** to use `setDesiredState()`
2. Documentation could be clearer - Add comments about simulation architecture
3. Diagnostics could be better - Add SmartDashboard output for debugging

---

## 3. Recommended Code Changes

### 3.1 Fix or Remove `pointInDirection()` Method

**Status:** ✅ **Already Fixed** - The method now uses `setDesiredState()` internally.

**Location:** `SwerveModule.java:358-361`

**Current Implementation (Fixed):**
```java
/**
 * Points the wheel in a specific direction without changing drive speed.
 * <p>
 * This method rotates the wheel to the specified angle (in degrees) while keeping
 * the drive motor stopped. Useful for testing, calibration, or positioning the wheel
 * without moving the robot.
 * <p>
 * This method internally uses {@link #setDesiredState(SwerveModuleState, boolean)}
 * to ensure simulation and test code can track the commanded state. This maintains
 * consistency with the simulation architecture where all module commands flow through
 * {@code setDesiredState()}.
 * 
 * @param degrees The target wheel angle in degrees (0-360)
 */
public void pointInDirection(double degrees){
    // Use setDesiredState to maintain consistency with simulation
    // Speed = 0.0 (wheel doesn't drive), angle = desired direction, closed loop control
    setDesiredState(new SwerveModuleState(0.0, Rotation2d.fromDegrees(degrees)), false);
}
```

---

### 3.2 Improve Documentation in `setDesiredState()`

**Location:** `SwerveModule.java:140-158`

**Current Javadoc:**
```java
/**
 * Sets the module to the desired state (speed and angle).
 * <p>
 * This is the main method for controlling the swerve module. It optimizes the desired
 * state to minimize rotation distance, then sets both the wheel angle and drive speed.
 * 
 * @param desiredState The target module state (speed in m/s and wheel angle)
 * @param isOpenLoop If true, uses open loop control for drive motor; if false, uses closed loop velocity control
 */
```

**Recommended Addition:**
```java
/**
 * Sets the module to the desired state (speed and angle).
 * <p>
 * This is the main method for controlling the swerve module. It optimizes the desired
 * state to minimize rotation distance, then sets both the wheel angle and drive speed.
 * <p>
 * <b>IMPORTANT FOR SIMULATION/TESTING:</b> This method stores the optimized state in
 * the {@code desiredState} field, which is read by {@link frc.robot.sim.SimulationManager}
 * to simulate robot motion. All control commands (driving, testing, autonomous) must
 * flow through this method to ensure simulation works correctly.
 * <p>
 * <b>Control Flow:</b>
 * <ul>
 *   <li>Normal driving: TeleopSwerve → SwerveSubsystem.drive() → this method</li>
 *   <li>Test commands: TestCommand → this method (directly)</li>
 *   <li>Autonomous: Auto command → SwerveSubsystem → this method</li>
 * </ul>
 * 
 * @param desiredState The target module state (speed in m/s and wheel angle)
 * @param isOpenLoop If true, uses open loop control for drive motor; if false, uses closed loop velocity control
 */
public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop) {
    // Optimize the desired state to minimize rotation (flip wheel 180° if needed)
    SwerveModuleState optimizedState = optimize(desiredState, getAngle());
    // Store desired state for simulation access
    this.desiredState = optimizedState;
    // Set the wheel angle to the optimized direction
    setAngle(optimizedState);
    // Set the drive motor speed (open loop or closed loop based on parameter)
    setSpeed(optimizedState, isOpenLoop);      
}
```

---

### 3.3 Add Simulation Diagnostics to SmartDashboard

**Location:** `SimulationManager.java:66-111`

**Recommended Addition:**
```java
public void simulationPeriodic() {
    // ... existing code ...
    
    // === NEW: Add diagnostics to SmartDashboard ===
    publishSimulationDiagnostics(dt, desiredChassisSpeeds);
}

/**
 * Publishes simulation diagnostics to SmartDashboard for debugging.
 * This helps verify that simulation is working correctly.
 * 
 * @param dt The time delta for this simulation update
 * @param speeds The current chassis speeds
 */
private void publishSimulationDiagnostics(double dt, ChassisSpeeds speeds) {
    SmartDashboard.putString("Sim/Status", "Running");
    SmartDashboard.putNumber("Sim/DeltaTime", dt);
    SmartDashboard.putNumber("Sim/Pose/X", simPose.getX());
    SmartDashboard.putNumber("Sim/Pose/Y", simPose.getY());
    SmartDashboard.putNumber("Sim/Pose/Rotation", simPose.getRotation().getDegrees());
    SmartDashboard.putNumber("Sim/Speeds/VX", speeds.vxMetersPerSecond);
    SmartDashboard.putNumber("Sim/Speeds/VY", speeds.vyMetersPerSecond);
    SmartDashboard.putNumber("Sim/Speeds/Omega", speeds.omegaRadiansPerSecond);
}
```

**Don't forget to add the import:**
```java
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
```

---

### 3.4 Add Simulation Section Comment in SwerveModule

**Location:** `SwerveModule.java:488-492`

**Current Code:**
```java
// ============================================================================
// Simulation Support Methods
// These methods are only used by SimulationManager.
// They expose internal objects needed for simulating robot motion.
// ============================================================================
```

**Recommended Enhancement:**
```java
// ============================================================================
// Simulation Support Methods
// ============================================================================
// These methods are only used by SimulationManager to access internal
// hardware objects for simulation. They should NOT be used by production code.
//
// ARCHITECTURE NOTE:
// The simulation architecture works as follows:
// 1. Commands (driving, testing) call setDesiredState()
// 2. setDesiredState() stores the optimized state in desiredState field
// 3. SimulationManager reads desiredState via getDesiredState()
// 4. SimulationManager updates hardware simulation state objects based on the
//    desired states:
//    - CTRE devices (Pigeon2, CANcoder): Updates separate SimState objects
//      (e.g., pigeonSimState.setRawYaw(), cancoderSimState.setRawPosition())
//    - REV devices (RelativeEncoder): Updates encoder directly via setPosition()
//    When production code reads the real hardware objects in sim mode, they
//    automatically return values from these simulation states. The vendor libraries
//    (CTRE Phoenix, REV) internally check RobotBase.isSimulation() to determine
//    whether to read from actual hardware (real mode) or simulation state (sim mode).
// 5. Production code (SwerveSubsystem.periodic()) reads real hardware objects
//    (which return simulated values in sim mode) and updates odometry
// 6. Production code (SwerveSubsystem.periodic()) updates Field2d based on odometry
//    (which is updated using simulated sensor readings in sim mode)
//
// This design keeps simulation logic completely separate from production code.
// ============================================================================
```

---

### 3.5 Add Validation Comments in Robot.java

**Location:** `Robot.java:104-115`

**Current Code:**
```java
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
```

**Recommended Enhancement:**
```java
@Override
public void simulationInit() {
    // Initialize simulation manager for driver practice simulation
    // The SimulationManager handles all simulation logic:
    // - Reads desired module states from modules
    // - Integrates robot motion (position, rotation)
    // - Updates hardware simulations (gyro, encoders)
    // - Works for both normal driving and individual module testing
    simManager = new SimulationManager(m_robotContainer.getSwerveSubsystem());
}

@Override
public void simulationPeriodic() {
    // Run simulation manager every 20ms to update simulated hardware
    // This runs in addition to robotPeriodic() which runs commands and subsystems
    if (simManager != null) {
        simManager.simulationPeriodic();
    }
}
```

---

## 4. Architecture Diagrams

### 4.1 Overall System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                           Robot.java                                  │
│  ┌────────────────────┐         ┌───────────────────────┐           │
│  │  robotPeriodic()   │         │  simulationPeriodic() │           │
│  │  - Runs scheduler  │         │  - Updates simulation │           │
│  │  - Every 20ms      │         │  - Sim mode only      │           │
│  └─────────┬──────────┘         └──────────┬────────────┘           │
└────────────┼─────────────────────────────────┼────────────────────────┘
             ↓                                  ↓
┌────────────┼──────────────────────────────────┼────────────────────────┐
│            ↓                                   ↓                        │
│  ┌─────────────────────┐           ┌──────────────────────┐           │
│  │  Command Scheduler  │           │  SimulationManager   │           │
│  │  - Runs commands    │           │  - Reads states      │           │
│  │  - Updates subs     │           │  - Updates hardware  │           │
│  └─────────┬───────────┘           │    simulations       │           │
│            ↓                        └──────────┬───────────┘           │
│  ┌─────────────────────┐                      │                       │
│  │   TeleopSwerve      │                      │                       │
│  │   AutoAlign         │                      │                       │
│  │   DriftTestCommand  │                      │                       │
│  └─────────┬───────────┘                      │                       │
└────────────┼────────────────────────────────────────────────────────────┘
             ↓                                  ↑
┌────────────┼──────────────────────────────────┼────────────────────────┐
│            ↓                                  │ (reads)                │
│  ┌──────────────────────┐                     │                        │
│  │  SwerveSubsystem     │────────────────────→│                        │
│  │  - drive()           │  getDesiredStates() │                        │
│  │  - driveFromChassis..│                     │                        │
│  └──────────┬───────────┘                     │                        │
│             ↓                                 │                        │
│  ┌──────────────────────┐                     │                        │
│  │  SwerveModule (×4)   │────────────────────→│                        │
│  │  - setDesiredState() │  (exposes state)    │                        │
│  │  - setAngle()        │                     │                        │
│  │  - setSpeed()        │                     │                        │
│  │  - getState()        │←────────────────────┘                        │
│  │  - getDesiredState() │  (updates sensors)                           │
│  └──────────┬───────────┘                                              │
└─────────────┼──────────────────────────────────────────────────────────┘
              ↓
┌─────────────┼──────────────────────────────────────────────────────────┐
│  HARDWARE / SIMULATION                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐      │
│  │ Motor Controllers│  │ Encoders         │  │ Gyro (Pigeon2)   │      │
│  │ - Real: SparkMax │  │ - Real: Physical │  │ - Real: Physical │      │
│  │ - Sim: No-op     │  │ - Sim: Updated   │  │ - Sim: Updated   │      │
│  │                  │  │   by SimManager  │  │   by SimManager  │      │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘      │
└────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Normal Driving Data Flow

```
Step 1: USER INPUT
┌──────────────────┐
│  Xbox Controller │
│  - Left Stick Y  │  (forward/back)
│  - Left Stick X  │  (strafe)
│  - Right Stick X │  (rotation)
└────────┬─────────┘
         ↓
         
Step 2: COMMAND PROCESSING
┌────────────────────────────────────────────┐
│  TeleopSwerve.execute()                    │
│  - Read joystick values                    │
│  - Apply deadband (0.1)                    │
│  - Apply slew rate limiter (3.0 m/s²)      │
│  - Scale by max speed (3 m/s)              │
│  Output: xSpeed, ySpeed, rotSpeed          │
└────────┬───────────────────────────────────┘
         ↓
         
Step 3: CONVERT TO ROBOT SPEEDS
┌────────────────────────────────────────────┐
│  SwerveSubsystem.drive()                   │
│  - Field-oriented: transform by gyro       │
│  - Robot-oriented: use as-is               │
│  Output: ChassisSpeeds                     │
│    (vx, vy, omega)                         │
└────────┬───────────────────────────────────┘
         ↓
         
Step 4: CONVERT TO MODULE SPEEDS
┌────────────────────────────────────────────┐
│  SwerveSubsystem.driveFromChassisSpeeds()  │
│  - Use kinematics to convert:              │
│    ChassisSpeeds → SwerveModuleState[4]    │
│  - Desaturate wheel speeds                 │
│  Output: 4 module states                   │
│    [speed, angle] for each module          │
└────────┬───────────────────────────────────┘
         ↓
         
Step 5: OPTIMIZE AND COMMAND MODULES
┌────────────────────────────────────────────┐
│  SwerveModule.setDesiredState() [×4]       │
│  - Optimize (minimize rotation)            │
│  - Store optimized state ← SIMULATION READS│
│  - setAngle() → angle controller           │
│  - setSpeed() → drive controller           │
└────────┬───────────────────────────────────┘
         ↓
         
Step 6: HARDWARE EXECUTION
┌────────────────────────────────────────────┐
│  Motor Controllers                         │
│  - Real: Execute PID control               │
│  - Sim: No-op (motors don't move)          │
└────────────────────────────────────────────┘

PARALLEL: SIMULATION UPDATE (every 20ms)
┌────────────────────────────────────────────┐
│  SimulationManager.simulationPeriodic()    │
│  1. Read: getDesiredStates() from modules  │
│  2. Calculate: ChassisSpeeds               │
│  3. Integrate: pose += speeds × dt         │
│  4. Update: Pigeon2SimState (gyro)         │
│  5. Update: Encoder positions              │
│  6. Update: CANcoder positions             │
└────────────────────────────────────────────┘
         ↓
         
Step 7: SENSOR READBACK
┌────────────────────────────────────────────┐
│  SwerveSubsystem.periodic()                │
│  - Read encoders (real or simulated)       │
│  - Update odometry                         │
│  - Update Field2d visualization            │
│  - Publish to NetworkTables                │
└────────────────────────────────────────────┘
```

### 4.3 Test Mode Data Flow

```
Step 1: TEST INITIATION
┌────────────────────────────────────────────┐
│  SmartDashboard                            │
│  - User sets: Module #, Angle, Cycles      │
│  - User clicks: "Start Test"               │
└────────┬───────────────────────────────────┘
         ↓
┌────────────────────────────────────────────┐
│  SwerveSubsystem.periodic()                │
│  - Calls: SwerveDriftTestManager           │
│           .checkAndStartTest()             │
└────────┬───────────────────────────────────┘
         ↓
┌────────────────────────────────────────────┐
│  SwerveDriftTestManager.startTest()        │
│  - Validate parameters                     │
│  - Create SwerveAngleDriftTestCommand      │
│  - Schedule command                        │
└────────┬───────────────────────────────────┘
         ↓

Step 2: TEST EXECUTION (State Machine)
┌────────────────────────────────────────────┐
│  SwerveAngleDriftTestCommand.initialize()  │
│  - Get test module via getModule()         │
│  - Initialize state: MOVING_TO_TARGET      │
│  - Command: module.setDesiredState()       │
│    → [0 m/s, testAngle°]                   │
└────────┬───────────────────────────────────┘
         ↓ (every 20ms)
┌────────────────────────────────────────────┐
│  SwerveAngleDriftTestCommand.execute()     │
│                                            │
│  State: MOVING_TO_TARGET                   │
│  - Check: isAtAngle(testAngle) ?           │
│    - Yes → Record encoders                 │
│           → State: AT_TARGET               │
│    - No  → Wait (timeout if too long)      │
│                                            │
│  State: AT_TARGET                          │
│  - Hold for minHoldTime (0.5s)             │
│  - Then → State: MOVING_TO_ZERO            │
│         → Command: module.setDesiredState()│
│           → [0 m/s, 0°]                    │
│                                            │
│  State: MOVING_TO_ZERO                     │
│  - Check: isAtAngle(0) ?                   │
│    - Yes → Record encoders                 │
│           → State: AT_ZERO                 │
│    - No  → Wait (timeout if too long)      │
│                                            │
│  State: AT_ZERO                            │
│  - Hold for minHoldTime (0.5s)             │
│  - Complete cycle, check if more cycles    │
│    - More → Next cycle (back to TARGET)    │
│    - Done → State: COMPLETE                │
└────────┬───────────────────────────────────┘
         ↓
         
Step 3: TEST COMPLETION
┌────────────────────────────────────────────┐
│  SwerveAngleDriftTestCommand.end()         │
│  - Calculate drift statistics              │
│  - Print results to console                │
│  - Update SmartDashboard                   │
└────────────────────────────────────────────┘

PARALLEL: SIMULATION (if in sim mode)
┌────────────────────────────────────────────┐
│  SimulationManager sees:                   │
│  - Module 0: desired state from test       │
│  - Module 1-3: zero state (not moving)     │
│                                            │
│  Updates:                                  │
│  - Only Module 0's encoders                │
│  - Robot pose stays same (no chassis move) │
│                                            │
│  Test reads:                               │
│  - Simulated relative encoder              │
│  - Simulated absolute encoder              │
│  - Compares to detect drift                │
└────────────────────────────────────────────┘
```

### 4.4 Simulation Update Cycle (Detailed)

```
EVERY 20ms IN SIMULATION MODE:

┌─────────────────────────────────────────────────────────────┐
│  SimulationManager.simulationPeriodic()                     │
└─────────────────────────────────────────────────────────────┘
  │
  │  // 1. Calculate time delta
  ├─→ currentTime = Timer.getFPGATimestamp()
  ├─→ dt = currentTime - lastTime
  ├─→ if (dt invalid) → dt = 0.02  // safety
  │
  │  // 2. Read desired states from modules
  ├─→ desiredStates[4] = swerveSubsystem.getDesiredStates()
  │     ↓
  │     ┌─────────────────────────────────────┐
  │     │ For each module:                    │
  │     │   state = module.getDesiredState()  │
  │     │   (reads the field stored by        │
  │     │    setDesiredState() method)        │
  │     └─────────────────────────────────────┘
  │
  │  // 3. Calculate overall robot motion
  ├─→ ChassisSpeeds = kinematics.toChassisSpeeds(desiredStates)
  │     → vx (m/s) - forward/back speed
  │     → vy (m/s) - left/right speed  
  │     → omega (rad/s) - rotation speed
  │
  │  // 4. Integrate robot pose (physics)
  ├─→ deltaX = vx × dt
  ├─→ deltaY = vy × dt
  ├─→ deltaRotation = omega × dt
  ├─→ simPose = simPose.exp(Twist2d(deltaX, deltaY, deltaRotation))
  │
  │  // 5. Update gyro simulation
  ├─→ pigeonSimState.setRawYaw(simPose.getRotation().getDegrees())
  │
  │  // 6. Update each module's encoders
  ├─→ For each module i:
  │     │
  │     ├─→ // Update drive encoder (distance traveled)
  │     │   currentPos = driveEncoder.getPosition()
  │     │   deltaMeters = desiredStates[i].speed × dt
  │     │   newPos = currentPos + deltaMeters
  │     │   driveEncoder.setPosition(newPos)
  │     │
  │     ├─→ // Update angle encoder (wheel rotation)
  │     │   desiredAngle = desiredStates[i].angle.getDegrees()
  │     │   angleEncoder.setPosition(desiredAngle)
  │     │
  │     └─→ // Update CANcoder (absolute encoder)
  │         posRotations = desiredStates[i].angle.getRotations()
  │         cancoderSimState.setRawPosition(posRotations)
  │
  │  // 7. Production code now reads updated sensors
  ├─→ (Automatically happens in SwerveSubsystem.periodic())
  │     │
  │     ├─→ yaw = pigeon.getYaw()  // from simulation
  │     ├─→ positions = getPositions()  // from simulated encoders
  │     ├─→ odometry.update(yaw, positions)
  │     └─→ field.setRobotPose(odometry.getEstimatedPosition())
  │
  └─→ (Optional) publishSimulationDiagnostics()
        → SmartDashboard for debugging
```

### 4.5 Component Dependencies

```
┌─────────────────────────────────────────────────────────────┐
│                     PRODUCTION CODE                         │
│  (No knowledge of simulation or test infrastructure)        │
└─────────────────────────────────────────────────────────────┘
  │
  ├─→ SwerveModule
  │     - Public APIs:
  │       • setDesiredState(state, isOpenLoop)  ← ALL COMMANDS USE THIS
  │       • getState() → current module state
  │       • getPosition() → current module position
  │       • getCanCoder() → absolute encoder
  │     - Simulation accessors (marked with comments):
  │       • getDesiredState() → last commanded state
  │       • getCanCoderDevice() → hardware object
  │       • getDriveEncoder() → hardware object
  │       • getAngleEncoder() → hardware object
  │
  ├─→ SwerveSubsystem  
  │     - Public APIs:
  │       • drive(x, y, rot, fieldOriented)
  │       • driveFromChassisSpeeds(speeds, openLoop)
  │       • getModule(number) → specific module
  │       • getStates() → all module states
  │       • getPositions() → all module positions
  │     - Simulation accessors (marked with comments):
  │       • getDesiredStates() → commanded states
  │       • getField() → Field2d object
  │       • getPigeon() → gyro object
  │       • getModules() → array of modules
  │       • getOdometry() → pose estimator
  │       • getKinematics() → kinematics object
  │
  └─→ Commands (TeleopSwerve, AutoAlign, etc.)
        - Use public APIs only
        - No knowledge of simulation

┌─────────────────────────────────────────────────────────────┐
│                     SIMULATION CODE                         │
│  (Reads production code, updates hardware simulations)      │
└─────────────────────────────────────────────────────────────┘
  │
  └─→ SimulationManager
        - Called from Robot.simulationPeriodic()
        - Reads: getDesiredStates() from subsystem
        - Updates: Pigeon2SimState, encoder positions
        - No modification of production code

┌─────────────────────────────────────────────────────────────┐
│                      TEST CODE                              │
│  (Uses production APIs, schedules test commands)            │
└─────────────────────────────────────────────────────────────┘
  │
  ├─→ SwerveDriftTestManager
  │     - Static utility class
  │     - Manages test lifecycle
  │     - Integrates with SmartDashboard
  │
  ├─→ SwerveAngleDriftTestCommand
  │     - WPILib Command
  │     - Uses: module.setDesiredState()
  │     - Reads: module.getState(), module.getCanCoder()
  │
  └─→ SwerveModuleTestUtils
        - Helper methods for test code
        - Wraps public APIs
        - No production code pollution
```

### 4.6 Field2d Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│  HOW FIELD2D WORKS (SAME FOR REAL ROBOT AND SIMULATION)    │
└─────────────────────────────────────────────────────────────┘

Step 1: SENSORS PROVIDE DATA
┌──────────────────┐         ┌──────────────────┐
│  Real Robot      │         │  Simulation      │
├──────────────────┤         ├──────────────────┤
│ • Physical gyro  │         │ • Simulated gyro │
│ • Physical       │         │ • Simulated      │
│   encoders       │         │   encoders       │
└────────┬─────────┘         └────────┬─────────┘
         │                            │
         └──────────┬─────────────────┘
                    ↓
Step 2: ODOMETRY CALCULATES POSE
┌────────────────────────────────────────────┐
│  SwerveSubsystem.periodic()                │
│  (runs every 20ms in all modes)            │
│                                            │
│  yaw = pigeon.getYaw()                     │
│    → Real mode: reads physical sensor      │
│    → Sim mode: reads simulated sensor      │
│                                            │
│  positions = getPositions()                │
│    → Real mode: reads physical encoders    │
│    → Sim mode: reads simulated encoders    │
│                                            │
│  odometry.update(yaw, positions)           │
│    → Calculates robot pose on field        │
│    → Returns: Pose2d(x, y, rotation)       │
└────────┬───────────────────────────────────┘
         ↓
Step 3: FIELD2D DISPLAYS POSE
┌────────────────────────────────────────────┐
│  field.setRobotPose(odometry.getEstimated  │
│                     Position())            │
│    → Updates Field2d widget                │
│    → Displays in:                          │
│      - SmartDashboard                      │
│      - Shuffleboard                        │
│      - Glass                               │
│      - AdvantageScope                      │
│      - Simulation GUI                      │
└────────────────────────────────────────────┘

KEY INSIGHT: The SAME code path works for both real and sim!
- Real mode: sensors are physical → odometry is accurate → Field2d shows real pose
- Sim mode: sensors are simulated → odometry uses sim data → Field2d shows sim pose

NO SEPARATE SIMULATION LOGIC NEEDED FOR FIELD2D!
```

### 4.7 Why Current Design is Correct

```
┌─────────────────────────────────────────────────────────────┐
│  QUESTION: Should we capture at setAngle/setSpeed level?   │
└─────────────────────────────────────────────────────────────┘

OPTION A: Current Design (Capture at setDesiredState)
┌────────────────────────────────────────────┐
│  module.setDesiredState(state, openLoop)   │
│    ↓                                       │
│  optimizedState = optimize(state)          │
│  this.desiredState = optimizedState  ← STORE HERE
│    ↓                                       │
│  setAngle(optimizedState)                  │
│  setSpeed(optimizedState, openLoop)        │
└────────────────────────────────────────────┘
  ↓
✅ PROS:
  • Single source of truth
  • Captures optimized state (correct intent)
  • Simple to read and maintain
  • Works for all use cases

❌ CONS:
  • None identified


OPTION B: Alternative (Capture at setAngle/setSpeed)
┌────────────────────────────────────────────┐
│  module.setDesiredState(state, openLoop)   │
│    ↓                                       │
│  optimizedState = optimize(state)          │
│    ↓                                       │
│  setAngle(optimizedState)                  │
│    ↓                                       │
│    capturedAngle = ... ← STORE HERE        │
│    ↓                                       │
│  setSpeed(optimizedState, openLoop)        │
│    ↓                                       │
│    capturedSpeed = ... ← STORE HERE        │
└────────────────────────────────────────────┘
  ↓
❌ CONS:
  • Two separate pieces of data to track
  • Need to recombine into SwerveModuleState
  • More complex synchronization
  • Risk of desync if one is set without the other
  • More code to maintain

✅ PROS:
  • None that outweigh the cons


VERDICT: Current design (Option A) is correct! ✅
```

### 4.8 Test Mode State Machine

```
SwerveAngleDriftTestCommand State Machine
(Example: testAngle=90°, cycles=3)

  START
    ↓
┌───────────────────────┐
│  INITIALIZE           │
│  - Get test module    │
│  - cycle = 0          │
│  - Command: 90°       │
└───────┬───────────────┘
        ↓
   ╔════════════════╗
   ║ CYCLE 0        ║
   ╚════════════════╝
        ↓
┌───────────────────────┐
│  MOVING_TO_TARGET     │
│  - Wait for 90°       │
│  - Check every 20ms   │
│  - Timeout: 1.0s      │
└───────┬───────────────┘
        ↓ (reached 90° ± 2°)
┌───────────────────────┐
│  AT_TARGET            │
│  - Record encoders    │
│    • Relative: 90.1°  │
│    • Absolute: 90.0°  │
│    • Drift: 0.1°      │
│  - Hold: 0.5s         │
└───────┬───────────────┘
        ↓ (hold complete)
┌───────────────────────┐
│  MOVING_TO_ZERO       │
│  - Command: 0°        │
│  - Wait for 0°        │
│  - Check every 20ms   │
│  - Timeout: 1.0s      │
└───────┬───────────────┘
        ↓ (reached 0° ± 2°)
┌───────────────────────┐
│  AT_ZERO              │
│  - Record encoders    │
│    • Relative: 0.2°   │
│    • Absolute: 0.0°   │
│    • Drift: 0.2°      │
│  - Hold: 0.5s         │
│  - cycle++            │
└───────┬───────────────┘
        ↓
     cycle < 3?
        ├─ Yes ─→ Back to MOVING_TO_TARGET (next cycle)
        │
        └─ No
           ↓
   ╔════════════════╗
   ║ ALL CYCLES     ║
   ║ COMPLETE       ║
   ╚════════════════╝
        ↓
┌───────────────────────┐
│  COMPLETE             │
│  - Calculate stats    │
│  - Print results      │
│  - Update dashboard   │
└───────────────────────┘
        ↓
      END
```

### 4.9 Key Architecture Principles

```
┌─────────────────────────────────────────────────────────────┐
│  PRINCIPLE 1: SINGLE ENTRY POINT                            │
└─────────────────────────────────────────────────────────────┘

    ALL control commands flow through ONE method:

         ┌──────────────────────────┐
         │ setDesiredState(state)   │  ← SINGLE ENTRY POINT
         └────────────┬─────────────┘
                      ↓
         ┌────────────────────────────┐
         │ Stores: this.desiredState  │
         └────────────┬───────────────┘
                      ↓
         ┌────────────────────────────┐
         │ Calls: setAngle(), setSpeed│
         └────────────────────────────┘

    ✅ Makes simulation work automatically
    ✅ Ensures consistent behavior
    ✅ Single source of truth


┌─────────────────────────────────────────────────────────────┐
│  PRINCIPLE 2: SEPARATION OF CONCERNS                        │
└─────────────────────────────────────────────────────────────┘

    Production Code          Simulation Code       Test Code
         │                         │                    │
         │  No simulation          │  No production     │  No simulation
         │  knowledge              │  code changes      │  knowledge
         │                         │                    │
         ├─ SwerveModule           │                    │
         ├─ SwerveSubsystem   ←───┼─ Reads states      │
         ├─ Commands               │   via getters      │
         │                         │                    │
         │                         ├─ SimulationMgr     │
         │                         │   Updates sims     │
         │                         │                    │
         └─────────────────────────┴────────────────────┴─ TestManager
                                                            ├─ TestCommand
                                                            └─ TestUtils


┌─────────────────────────────────────────────────────────────┐
│  PRINCIPLE 3: NO CONDITIONALS IN PRODUCTION CODE            │
└─────────────────────────────────────────────────────────────┘

    ❌ DON'T DO THIS:
    
    public void setSpeed(...) {
        if (RobotBase.isSimulation()) {
            // simulation-specific logic
        }
        // production logic
    }

    ✅ DO THIS INSTEAD:

    // SwerveModule.java (production)
    public void setSpeed(...) {
        // Only production logic
        controller.setReference(...);
    }

    // SimulationManager.java (simulation)
    public void updateSimulation() {
        // Only simulation logic
        encoder.setPosition(...);
    }
```

---

## 5. Control Flow Analysis

### 5.1 Normal Driving Control Flow

The control flow for normal robot driving is correctly implemented:

```
User Controller Input
    ↓
RobotContainer (lines 98-112)
    - Reads joystick axes
    - Applies speed multipliers
    ↓
TeleopSwerve Command (lines 67-73)
    - Applies deadband
    - Applies slew rate limiting
    - Scales by max speed/angular velocity
    ↓
SwerveSubsystem.drive() (lines 113-123)
    - Converts to ChassisSpeeds (field or robot relative)
    - Calls driveFromChassisSpeeds()
    ↓
SwerveSubsystem.driveFromChassisSpeeds() (lines 125-134)
    - Converts ChassisSpeeds → SwerveModuleStates (via kinematics)
    - Desaturates wheel speeds
    - Calls setDesiredState() on each module
    ↓
SwerveModule.setDesiredState() (lines 149-158)
    - Optimizes state (minimize rotation)
    - Stores desired state for simulation
    - Calls setAngle() and setSpeed()
    ↓
SwerveModule.setAngle() (lines 323-332)
SwerveModule.setSpeed() (lines 292-309)
    - Sets motor controller references
    - Hardware executes the commands
```

### 5.2 Test Mode Control Flow

The control flow for individual module testing:

```
SmartDashboard/Test Manager
    ↓
SwerveDriftTestManager.startTest()
    - Validates parameters
    - Schedules SwerveAngleDriftTestCommand
    ↓
SwerveAngleDriftTestCommand
    - Gets specific module via SwerveSubsystem.getModule()
    - Calls module.setDesiredState() directly
    - Monitors progress via SwerveModuleTestUtils
    ↓
SwerveModule.setDesiredState()
    - Same path as normal driving
    - Optimizes, stores state, calls setAngle/setSpeed
```

**Key Insight:** Both normal driving and test mode use the same `setDesiredState()` entry point, ensuring consistent behavior.

---

## 6. Implementation Assessment

### 6.1 What Works Well ✅

#### A. Simulation Manager Design
**File:** `SimulationManager.java`

**Strengths:**
- ✅ **Externalized:** Simulation logic is completely separate from production code
- ✅ **Self-contained:** Manages all simulation state internally
- ✅ **Correct integration:** Called from `Robot.simulationPeriodic()`
- ✅ **Handles both use cases:** Works for normal driving AND individual module testing

**Key Design Decision:** The simulation reads `getDesiredStates()` from modules, which captures the optimized states after `setDesiredState()` is called. This is correct because:
1. The optimized state represents what the module is actually trying to achieve
2. It works for both coordinated driving (all modules) and individual module commands
3. Motors don't actually move in simulation, so we simulate based on commands

#### B. Test Architecture
**Files:** `SwerveAngleDriftTestCommand.java`, `SwerveDriftTestManager.java`, `SwerveModuleTestUtils.java`

**Strengths:**
- ✅ **Separation of concerns:** Test code is in separate package (`frc.robot.test`)
- ✅ **Minimal intrusion:** Uses existing public APIs (`setDesiredState`, `getState`, `getCanCoder`)
- ✅ **Manager pattern:** `SwerveDriftTestManager` handles test lifecycle
- ✅ **Utility class:** `SwerveModuleTestUtils` provides test helpers without polluting production code
- ✅ **Command framework integration:** Uses WPILib Command pattern correctly

#### C. Production Code Cleanliness
**Files:** `SwerveModule.java`, `SwerveSubsystem.java`

**Strengths:**
- ✅ **Clear separation:** Simulation support methods are clearly marked with comments
- ✅ **Minimal intrusion:** Only 4 accessor methods added for simulation
- ✅ **No conditional logic:** No `if (simulation)` checks in production code
- ✅ **Public API is clean:** Test code uses existing public methods where possible

### 6.2 Minor Issues and Improvements Needed ⚠️

#### Issue 1: `pointInDirection()` Not Integrated
**Status:** ✅ **Already Fixed** - Now uses `setDesiredState()` internally.

#### Issue 2: `resetToAbsolute()` Called Outside Normal Flow
**Location:** `SwerveModule.java:412-416`

**Analysis:** This is actually **correct** because:
- It's a calibration/initialization operation, not a control command
- Simulation handles encoder updates separately in `updateModuleEncoders()`
- The CANcoder simulation is updated based on desired angle, so this read-modify-write works correctly

**Recommendation:** No change needed, but document this in simulation comments

### 6.3 Current Capture Point Analysis

**Current Design:** The simulation captures desired states at the `SwerveModule.setDesiredState()` level (stored in `desiredState` field).

**Question:** Should we capture at the lower level (`setAngle` and `setSpeed` methods)?

**Answer:** **No, the current design is correct.** Here's why:

| Aspect | Current Design (setDesiredState) | Alternative (setAngle/setSpeed) |
|--------|----------------------------------|----------------------------------|
| **Optimization** | Captures optimized state (after minimize rotation) | Would need to capture separately and recombine |
| **Consistency** | Single source of truth | Two separate pieces of data |
| **Test compatibility** | Works perfectly for individual module tests | Would work but more complex |
| **Implementation** | Simple, clean | More code, more maintenance |
| **Correctness** | Represents actual module intent | Would need to handle desync issues |

**Recommendation:** Keep capturing at `setDesiredState()` level. This is the right architectural choice.

---

## 7. Design Principles

The current implementation follows excellent design principles that should be maintained:

### 7.1 Separation of Concerns

```
┌─────────────────────────────────────────────────────────────┐
│                    PRODUCTION CODE                           │
│  (SwerveModule, SwerveSubsystem, Commands)                   │
│  - No simulation-specific logic                              │
│  - No test-specific logic                                    │
│  - Only accessor methods for external systems                │
└─────────────────────────────────────────────────────────────┘
                           ↓ ↑
        ┌──────────────────┴──┴──────────────────┐
        ↓                                         ↓
┌───────────────────┐                  ┌──────────────────────┐
│  SIMULATION       │                  │  TEST CODE           │
│  (SimulationMgr)  │                  │  (test package)      │
│  - Reads states   │                  │  - Test commands     │
│  - Updates sims   │                  │  - Test managers     │
│  - Updates Field2d│                  │  - Test utilities    │
└───────────────────┘                  └──────────────────────┘
```

### 7.2 Data Flow for Simulation

```
[User Input] → [Commands] → [Subsystem] → [Modules]
                                              ↓
                                    setDesiredState()
                                              ↓
                              stores optimized state
                                              ↓
                                    setAngle/setSpeed
                                              ↓
                              Motor controllers
                                    (no-op in sim)
                                              
                                              
[SimulationManager.simulationPeriodic()]
    ↓
    Reads: getDesiredStates() from all modules
    ↓
    Calculates: ChassisSpeeds from desired states
    ↓
    Integrates: simPose += speed * deltaTime
    ↓
    Updates: Pigeon2 simulation (yaw)
    Updates: Module encoders (drive position, angle position)
    Updates: CANcoder simulation
    ↓
    SwerveSubsystem.periodic() reads simulated sensors (gyro, encoders)
    ↓
    SwerveSubsystem.periodic() updates: Odometry (line 227), Field2d (line 230)
```

### 7.3 Single Entry Point Principle

**All module control commands flow through `setDesiredState()`**
- Normal driving: ✅ Yes (via `driveFromChassisSpeeds`)
- Test commands: ✅ Yes (directly call `setDesiredState`)
- Future features: ⚠️ Must follow this pattern (e.g., `pointInDirection` now fixed)

---

## 8. Known Issues and Future Improvements

### 8.1 Issue: Angle Accumulation in Custom `optimize()` Method

**Location:** `SwerveModule.java:252-275`

**Problem:** The custom `optimize()` method can cause angle accumulation in simulation, where angles grow beyond 360° (e.g., 360°, 450°, 720°, etc.) instead of staying in the 0-360° range.

**Root Cause:** 
```java
// Line 273 in optimize()
double direction = currentAngle.getDegrees() + turnAmount;
return new SwerveModuleState (speed, Rotation2d.fromDegrees(direction));
```

This adds `turnAmount` to `currentAngle` without normalizing, which can produce angles like:
- Cycle 1: 0° → 90° ✓
- Cycle 2: 90° → 0° (but optimizes to 360°) 
- Cycle 3: 360° → 450° ❌
- Cycle 4: 450° → 720° ❌

**Symptoms:**
- In simulation/test mode, `DriftTest/Angle/Current` may show values like 360-450° instead of 0-90°
- Occurs intermittently depending on optimization path taken
- Does not affect real robot (encoders eventually wrap), but makes simulation/testing confusing

**Recommended Fix:** Replace custom `optimize()` with WPILib's built-in method:

```java
// Current custom implementation (252-275)
private SwerveModuleState optimize(SwerveModuleState desiredState, Rotation2d currentAngle){
    // 25 lines of custom optimization logic...
}

// Recommended: Use WPILib's built-in method
private SwerveModuleState optimize(SwerveModuleState desiredState, Rotation2d currentAngle){
    return SwerveModuleState.optimize(desiredState, currentAngle);
}

// Or better yet, call it directly in setDesiredState() and remove this method entirely:
public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop) {
    SwerveModuleState optimizedState = SwerveModuleState.optimize(desiredState, getAngle());
    this.desiredState = optimizedState;
    setAngle(optimizedState);
    setSpeed(optimizedState, isOpenLoop);      
}
```

**Why WPILib's Method Fixes It:**
- `Rotation2d` objects automatically normalize angles when constructed
- `Rotation2d.fromDegrees(450.0).getDegrees()` returns `90.0` (normalized)
- WPILib's implementation is well-tested and used by thousands of FRC teams
- Reduces custom code maintenance burden

**Benefits:**
1. ✅ Fixes angle accumulation bug in simulation/test
2. ✅ Removes 25 lines of custom code
3. ✅ Uses standard, well-tested FRC approach
4. ✅ Benefits from future WPILib improvements

**Priority:** Medium - Affects simulation/test clarity but not real robot operation

**Note:** This fix should be implemented in a separate PR focused on production code changes, not in the simulation/test support branch.

---

## 9. Understanding Hardware Simulation

**How do hardware objects know they're in simulation mode?**

This is a common question about WPILib simulation. The answer: **vendor libraries have built-in simulation support**.

### 9.1 The Magic Behind the Scenes

When you call methods on hardware objects like `Pigeon2.getYaw()` or `RelativeEncoder.getPosition()`, the vendor library code internally checks `RobotBase.isSimulation()` to determine what to do:

**Simplified example (conceptual, not actual library code):**

```java
// Inside the Pigeon2 class (CTRE Phoenix 6 library)
public class Pigeon2 {
    private Pigeon2SimState simState = new Pigeon2SimState();
    
    // GETTER: Read sensor value
    public StatusSignal<Double> getYaw() {
        if (RobotBase.isSimulation()) {
            // Simulation mode: return value from simulation state
            return simState.getSimulatedYaw();
        } else {
            // Real robot mode: read from actual hardware via CAN bus
            return readYawFromHardware();
        }
    }
    
    // SETTER: Set/calibrate sensor
    public StatusCode setYaw(double angle) {
        if (RobotBase.isSimulation()) {
            // Simulation mode: update simulation state
            simState.setSimulatedYaw(angle);
            return StatusCode.OK;
        } else {
            // Real robot mode: send command to hardware via CAN bus
            return sendYawCommandToHardware(angle);
        }
    }
    
    // Simulation code gets access to sim state for physics updates
    public Pigeon2SimState getSimState() {
        return simState;
    }
}
```

**Key insight:** Both getters AND setters check simulation mode internally!

### 9.2 How This Works in Your Code

**Step 1: SimulationManager gets simulation state objects**
```java
// In SimulationManager constructor
pigeonSimState = swerveSubsystem.getPigeon().getSimState();
```

**Step 2: SimulationManager updates simulation state (physics updates)**
```java
// In simulationPeriodic()
// This simulates the gyro changing due to robot rotation
pigeonSimState.setRawYaw(simPose.getRotation().getDegrees());
```

**Step 3: Production code reads hardware object (same code for real and sim)**
```java
// In SwerveSubsystem.periodic() - works in both modes!
Rotation2d yaw = pigeon.getYaw();  // Internally routes to sim state in sim mode
```

**Step 4: Production code can also set/calibrate hardware (same code for real and sim)**
```java
// In SwerveSubsystem.zeroGyro() - works in both modes!
pigeon.setYaw(0);  // Real: commands hardware; Sim: updates sim state internally
```

### 9.3 Two Types of Simulation Updates

There are **two ways** simulation state gets updated:

1. **Physics updates (SimulationManager does this):**
   - Encoders advance based on velocity × time
   - Gyro rotates based on robot rotation
   - Use `.getSimState()` to get the sim state object and update it

2. **Calibration/reset commands (Hardware object does this internally):**
   - `pigeon.setYaw(0)` - Reset gyro to zero
   - `encoder.setPosition(0)` - Reset encoder position
   - `module.resetToAbsolute()` - Calibrate to absolute encoder
   - These methods internally check `RobotBase.isSimulation()` and update sim state

**Why the difference?** Physics updates are continuous and based on calculations. Calibration commands are discrete user actions that should work the same way in both modes.

### 9.4 Different Vendor Implementations

**CTRE devices (Pigeon2, CANcoder):**
- Provide separate `SimState` objects (`Pigeon2SimState`, `CANcoderSimState`)
- You update the sim state object using methods like `setRawYaw()`, `setRawPosition()`
- Real hardware object automatically reads from sim state in simulation mode
- **Design philosophy**: Separate APIs for calibration (`setYaw()`) vs simulation (`setRawYaw()`)

**REV devices (SparkMax, SparkFlex encoders):**
- No separate sim state object
- You call `encoder.setPosition()` directly for both calibration and simulation
- The encoder object internally maintains simulation state
- When you read in sim mode, it returns the simulated value
- **Design philosophy**: Same API for calibration and simulation

**Note:** You could technically use `pigeon.setYaw()` instead of `pigeonSimState.setRawYaw()` in simulation code, as `setYaw()` internally checks simulation mode. However, using the `SimState` API is preferred because:
1. It follows CTRE's intended API pattern
2. It makes semantic intent clearer (physics updates vs calibration)
3. It's more explicit about simulation-specific code
4. It's more robust if vendor implementations change

### 9.5 Why This Design is Elegant

✅ **Production code is mode-agnostic** - Same code works for real robot and simulation
✅ **No conditionals needed** - Your code doesn't need `if (RobotBase.isSimulation())` checks
✅ **Simulation logic is isolated** - All sim updates happen in `SimulationManager`
✅ **Type-safe** - Hardware objects have the same type in both modes

### 9.6 Key Takeaway

**You never need to check `RobotBase.isSimulation()` in production code!** The vendor libraries handle the mode detection internally:

- **All getter methods** (reading sensors) automatically return simulated values in sim mode
- **All setter methods** (commanding/calibrating hardware) automatically update sim state in sim mode
- **SimulationManager** only needs to update physics-related changes (motion, velocity, etc.)
- **Production code** (like `zeroGyro()`, `resetToAbsolute()`) works identically in both modes

This is why your architecture works so well - the production code is truly identical for real and simulated robots, and the vendor libraries provide all the mode-switching logic internally.

---

## 10. Implementation Guidelines

### 10.1 Adding New Control Paths

**Rule:** All control commands must flow through `SwerveModule.setDesiredState()`

**Example: Adding a new "lock wheels" command**

```java
// ✅ CORRECT
public void lockWheels() {
    // Create states that form an X pattern
    SwerveModuleState[] lockStates = new SwerveModuleState[] {
        new SwerveModuleState(0, Rotation2d.fromDegrees(45)),   // Front-left
        new SwerveModuleState(0, Rotation2d.fromDegrees(-45)),  // Front-right
        new SwerveModuleState(0, Rotation2d.fromDegrees(-45)),  // Back-left
        new SwerveModuleState(0, Rotation2d.fromDegrees(45))    // Back-right
    };
    
    for (SwerveModule mod : mSwerveMods) {
        mod.setDesiredState(lockStates[mod.moduleNumber], false);
    }
}

// ❌ WRONG
public void lockWheels() {
    // Don't bypass setDesiredState!
    for (SwerveModule mod : mSwerveMods) {
        mod.pointInDirection(45);  // Bypasses desired state storage
    }
}
```

### 10.2 Adding New Test Commands

**Pattern to follow:**

1. Create test command class in `frc.robot.test` package
2. Use `SwerveModuleTestUtils` for helper methods
3. Access modules via `SwerveSubsystem.getModule()`
4. Send commands via `module.setDesiredState()`
5. Read state via public accessor methods
6. Create manager class for test lifecycle

**Example: Adding a new "drive motor velocity test"**

```java
// In frc/robot/test/SwerveVelocityTestCommand.java
public class SwerveVelocityTestCommand extends Command {
    private final SwerveModule testModule;
    private final double targetVelocity;
    
    @Override
    public void execute() {
        // Send command via setDesiredState
        testModule.setDesiredState(
            new SwerveModuleState(targetVelocity, Rotation2d.fromDegrees(0)),
            false  // closed loop
        );
        
        // Read actual velocity via public API
        double actualVelocity = testModule.getState().speedMetersPerSecond;
        
        // Record for analysis
        recordVelocityMeasurement(actualVelocity);
    }
}
```

### 10.3 Modifying Simulation Behavior

**Guideline:** Keep simulation logic in `SimulationManager`, don't modify production code.

**Example: Adding wheel slip simulation**

```java
// ✅ CORRECT: Add to SimulationManager
private void updateModuleEncoders(SwerveModuleState[] desiredStates, double dt) {
    for (int i = 0; i < modules.length; i++) {
        SwerveModuleState desiredState = desiredStates[i];
        
        // Add slip simulation (only in SimulationManager)
        double slipFactor = calculateSlipFactor(desiredState.speedMetersPerSecond);
        double actualSpeed = desiredState.speedMetersPerSecond * slipFactor;
        
        // Update encoder with slipped speed
        double deltaMeters = actualSpeed * dt;
        driveEncoder.setPosition(currentPosition + deltaMeters);
        
        // ... rest of simulation ...
    }
}

// ❌ WRONG: Don't add to SwerveModule
public void setSpeed(SwerveModuleState desiredState, boolean isOpenLoop) {
    if (RobotBase.isSimulation()) {
        // Don't add simulation-specific logic here!
    }
    // ... production code ...
}
```

### 10.4 Field2d Usage

**Current Design:** The `Field2d` object is used for both real robot visualization and simulation visualization.

**How it works:**

```java
// In SwerveSubsystem constructor
field = new Field2d();
SmartDashboard.putData("Field", field);

// In SwerveSubsystem.periodic() (runs on real robot)
field.setRobotPose(getPose());

// In SimulationManager (runs only in simulation)
// The Field2d is updated via the normal odometry flow:
// 1. SimulationManager updates encoder simulations
// 2. SwerveSubsystem.periodic() runs odometry.update()
// 3. SwerveSubsystem.periodic() updates field.setRobotPose()
```

**Key insight:** You don't need separate simulation logic for Field2d! The existing `SwerveSubsystem.periodic()` code handles it correctly because it reads from odometry, which in turn reads from encoders (simulated in sim mode, real in real mode).

**Verification:**
- ✅ Real robot: Encoders are real → Odometry is real → Field2d shows real position
- ✅ Simulation: Encoders are simulated → Odometry uses sim data → Field2d shows sim position

### 10.5 Common Questions Answered

**Q1: Should we capture at `setAngle`/`setSpeed` level instead of `setDesiredState`?**
**A:** No, current design is correct. `setDesiredState()` is the right level because:
- It captures the optimized state (after minimize rotation)
- Single source of truth
- Works for all use cases (driving, testing)
- Simpler implementation

**Q2: Do we need separate logic for Field2d in simulation?**
**A:** No, current design is correct. The same code works for both:
- Real robot: Real encoders → Real odometry → Field2d
- Simulation: Sim encoders → Sim odometry → Field2d
- No separate logic needed!

**Q3: Does `resetToAbsolute()` need special handling?**
**A:** No, current design is correct. It's a calibration operation, not a control command. Simulation handles it correctly.

**Q4: Can test commands work with simulation?**
**A:** Yes! Current design works perfectly:
- Test command calls `setDesiredState()` on one module
- Simulation sees that module's desired state
- Other modules have zero state (not moving)
- Simulation updates only the test module
- Test reads simulated encoders
- Everything works!

---

## 11. Appendices

### 11.1 File Organization

```
src/main/java/frc/robot/
├── Robot.java                    [Lifecycle, integrates SimulationManager]
├── RobotContainer.java           [Subsystem creation, command binding]
├── Constants.java                [Configuration constants]
├── SwerveModule.java             [✅ Production code with minimal sim accessors]
│
├── Subsystems/
│   └── SwerveSubsystem.java      [✅ Production code with minimal sim accessors]
│
├── Command/
│   └── TeleopSwerve.java         [✅ Production command]
│
├── sim/
│   └── SimulationManager.java    [✅ All simulation logic]
│
└── test/
    ├── SwerveDriftTestManager.java          [✅ Test lifecycle management]
    ├── SwerveAngleDriftTestCommand.java     [✅ Test command]
    └── SwerveModuleTestUtils.java           [✅ Test utilities]
```

### 11.2 Code Review Checklist

Use this checklist when adding new features:

**For new control features:**
- [ ] All module commands flow through `setDesiredState()`
- [ ] No direct calls to `setAngle()` or `setSpeed()` from outside SwerveModule
- [ ] No `if (simulation)` conditional logic in production code

**For new test commands:**
- [ ] Test code is in `frc.robot.test` package
- [ ] Uses existing public APIs where possible
- [ ] New accessors are clearly marked as "Simulation Support" or "Test Support"
- [ ] Test uses Command framework lifecycle
- [ ] Test integrates with SmartDashboard for control

**For simulation changes:**
- [ ] Changes are in `SimulationManager`, not production code
- [ ] Simulation reads desired states via `getDesiredStates()`
- [ ] Simulation updates hardware simulation objects (Pigeon2SimState, etc.)
- [ ] Production code remains unchanged

### 11.3 Testing Checklist

After implementing changes, verify:

**Simulation Testing:**
- [ ] Start robot in simulation mode
- [ ] Drive robot with controller - verify Field2d updates
- [ ] Check SmartDashboard for "Sim/" entries (if you added diagnostics)
- [ ] Run drift test from SmartDashboard
- [ ] Verify module angles update in simulation
- [ ] Verify test completes and prints results

**Real Robot Testing:**
- [ ] Deploy to real robot (or verify compilation)
- [ ] Verify normal driving works
- [ ] Verify test commands work (if deploying tests to robot)
- [ ] Verify no simulation code runs on real robot

**Code Quality:**
- [ ] No linter errors introduced
- [ ] All comments are clear and accurate
- [ ] No unused imports
- [ ] Javadoc is complete

### 11.4 Priority Action Items

**Must Do (Critical):**
1. ✅ **Review this document** - Understand the architecture
2. ✅ **`pointInDirection()` method** - Already fixed to use `setDesiredState()`

**Should Do (Important):**
3. 📝 **Improve documentation** - Add clarifying comments (see Section 3.2)
4. 📊 **Add diagnostics** - Add SmartDashboard output for debugging (see Section 3.3)

**Future Improvements (Separate PR):**
5. 🔧 **Replace custom `optimize()`** - Use WPILib's built-in method (fixes angle accumulation in sim) - See Section 8.1

**Nice to Have (Optional):**
6. 📋 **Add architecture comments** - Enhance section comments (see Sections 3.4-3.5)
7. 🔧 **Add TestManagerBase** - Create base class for future test managers (see Optional Enhancements)

### 11.5 Optional Enhancements

These are nice-to-have improvements but not critical:

#### Optional 1: Add TestManagerBase Pattern

**Location:** Create new file `src/main/java/frc/robot/test/TestManagerBase.java`

```java
// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.test;

/**
 * Base class for test managers.
 * Provides a consistent pattern for test infrastructure across different test types.
 * 
 * <p>Test managers follow these design principles:
 * <ul>
 *   <li>Test managers are static utility classes (no instances)</li>
 *   <li>Test managers use existing public APIs where possible</li>
 *   <li>Test managers integrate with SmartDashboard for control</li>
 *   <li>Test managers use Command framework for test execution</li>
 *   <li>Test code is kept separate from production code</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * public class MyTestManager extends TestManagerBase {
 *     public static void initializeDashboard() {
 *         SmartDashboard.putNumber("MyTest/Parameter", 0.0);
 *         SmartDashboard.putBoolean("MyTest/Start", false);
 *     }
 *     
 *     public static void checkAndStartTest(MySubsystem subsystem) {
 *         boolean start = SmartDashboard.getBoolean("MyTest/Start", false);
 *         if (start) {
 *             SmartDashboard.putBoolean("MyTest/Start", false);
 *             // Schedule test command...
 *         }
 *     }
 * }
 * </pre>
 */
public abstract class TestManagerBase {
    
    /**
     * Initializes SmartDashboard controls for this test.
     * Should be called once during robot initialization.
     * Subclasses must implement this method.
     */
    protected static void initializeDashboard() {
        throw new UnsupportedOperationException("Subclass must implement initializeDashboard()");
    }
    
    /**
     * Checks SmartDashboard for test trigger and starts test if requested.
     * Should be called periodically (e.g., from subsystem.periodic()).
     * Subclasses must implement this method.
     */
    protected static void checkAndStartTest() {
        throw new UnsupportedOperationException("Subclass must implement checkAndStartTest()");
    }
}
```

#### Optional 2: Add Simulation Recording

This is a more advanced feature that you might want later:

**Location:** Create new file `src/main/java/frc/robot/sim/SimulationRecorder.java`

```java
// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.sim;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.Timer;
import java.util.ArrayList;
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Records simulation state history for playback and analysis.
 * Useful for debugging simulation behavior and generating test data.
 */
public class SimulationRecorder {
    
    /**
     * A single frame of simulation data.
     */
    public static record SimulationFrame(
        double timestamp,
        Pose2d pose,
        ChassisSpeeds speeds,
        double[] moduleAngles,
        double[] moduleSpeeds
    ) {}
    
    private final List<SimulationFrame> frames = new ArrayList<>();
    private boolean recording = false;
    
    /**
     * Starts recording simulation frames.
     */
    public void startRecording() {
        recording = true;
        frames.clear();
    }
    
    /**
     * Stops recording simulation frames.
     */
    public void stopRecording() {
        recording = false;
    }
    
    /**
     * Records a single simulation frame.
     */
    public void recordFrame(Pose2d pose, ChassisSpeeds speeds, 
                           double[] moduleAngles, double[] moduleSpeeds) {
        if (!recording) {
            return;
        }
        
        double timestamp = Timer.getFPGATimestamp();
        frames.add(new SimulationFrame(timestamp, pose, speeds, moduleAngles, moduleSpeeds));
    }
    
    /**
     * Exports recorded frames to CSV file for analysis.
     * @param filename The output filename (e.g., "simulation_log.csv")
     */
    public void exportToCSV(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            // Write header
            writer.write("timestamp,pose_x,pose_y,pose_rotation,vx,vy,omega," +
                        "mod0_angle,mod1_angle,mod2_angle,mod3_angle," +
                        "mod0_speed,mod1_speed,mod2_speed,mod3_speed\n");
            
            // Write data
            for (SimulationFrame frame : frames) {
                writer.write(String.format("%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f," +
                                          "%.3f,%.3f,%.3f,%.3f," +
                                          "%.3f,%.3f,%.3f,%.3f\n",
                    frame.timestamp,
                    frame.pose.getX(), frame.pose.getY(), frame.pose.getRotation().getDegrees(),
                    frame.speeds.vxMetersPerSecond, frame.speeds.vyMetersPerSecond, 
                    frame.speeds.omegaRadiansPerSecond,
                    frame.moduleAngles[0], frame.moduleAngles[1], 
                    frame.moduleAngles[2], frame.moduleAngles[3],
                    frame.moduleSpeeds[0], frame.moduleSpeeds[1], 
                    frame.moduleSpeeds[2], frame.moduleSpeeds[3]
                ));
            }
            
            System.out.println("Exported " + frames.size() + " frames to " + filename);
        } catch (IOException e) {
            System.err.println("Failed to export simulation recording: " + e.getMessage());
        }
    }
    
    /**
     * Gets the number of recorded frames.
     */
    public int getFrameCount() {
        return frames.size();
    }
}
```

### 11.6 Further Reading

**WPILib Documentation:**

The official WPILib documentation is available at: https://docs.wpilib.org/

Key sections relevant to this architecture:
- **Command-based Programming** - Search for "Command-Based Programming" in the docs
- **Robot Simulation** - Search for "Robot Simulation" in the docs  
- **Swerve Drive Kinematics** - Search for "Swerve Drive Kinematics" in the docs
- **Unit Testing** - Search for "Unit Testing" in the docs

**Note:** WPILib documentation URLs change periodically. If specific links are needed, navigate from the main documentation site using the search function or table of contents.

---

## Summary

**Your current implementation is excellent.** The architecture is sound, the separation of concerns is clean, and the design choices are correct. The only changes needed are:
1. ✅ `pointInDirection()` method - Already fixed
2. Add a few clarifying comments (see Recommended Code Changes)
3. Optionally add diagnostics for debugging

**No major refactoring needed!** 🎉

---

**Document prepared by:** AI Assistant (Claude Sonnet 4.5)  
**Last Updated:** February 15, 2026
