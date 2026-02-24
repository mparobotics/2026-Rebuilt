# Autonomous Simulation Analysis

## Symptom

When switching to Autonomous mode in the simulator, the robot jumps to a position
and stops. PathPlanner's `FollowPathCommand` runs for **exactly one execute cycle**,
commands enormous speeds (7–10 m/s), then immediately finishes. TeleopSwerve (the
default command) takes over and the robot sits still.

This happens for both `DriveTestAuto` and `EightLemonAuto`.

Teleop and Test modes work correctly in simulation.

---

## Key Files

| File | Role |
|------|------|
| `src/main/java/frc/robot/Robot.java` | Entry point. Calls `CommandScheduler.run()` in `robotPeriodic()`, schedules auto command in `autonomousInit()`, calls `SimulationManager` in `simulationPeriodic()`. |
| `src/main/java/frc/robot/RobotContainer.java` | Creates subsystems, configures bindings. `getAutonomousCommand()` returns `EightLemonAuto` (hardcoded). Sets TeleopSwerve as default command on SwerveSubsystem. |
| `src/main/java/frc/robot/Subsystems/SwerveSubsystem.java` | Swerve drivetrain. Has `periodic()` (updates odometry), `hardResetPose()`, `startAutoAt()`, `autoDrive()`, `configurePathPlanner()`, `driveFromChassisSpeeds()`. Extends `SubsystemBase` (auto-registered with CommandScheduler). |
| `src/main/java/frc/robot/sim/SimulationManager.java` | Simulation physics. `simulationPeriodic()` integrates chassis speeds into `simPose`, writes pigeon sim state and encoder values. Consumes `pendingSimPoseReset` from SwerveSubsystem. |
| `src/main/java/frc/robot/Auto/EightLemonAuto.java` | `SequentialCommandGroup` containing `drive.startAutoAt(3.5355, 7.1300, 91.82)` then `drive.autoDrive("8FuelPath")`. |
| `src/main/java/frc/robot/Auto/DriveTestAuto.java` | `SequentialCommandGroup` containing `drive.startAutoAt(1.165, 6.000, 0.000)` then `drive.autoDrive("DriveTestPath")`. |
| `src/main/java/frc/robot/Constants.java` | Contains `SwerveConstants`, `AutoConstants` (with `ROBOT_CONFIG`, `SWERV_DRIVE_CONTROLLER`), `FieldConstants`. |

---

## How the Autonomous Command is Structured

`EightLemonAuto` is a `SequentialCommandGroup`:
1. `drive.startAutoAt(3.5355, 7.1300, 91.82)` — returns `runOnce(...)` (an InstantCommand) that calls `hardResetPose()` to set the robot's starting position and heading.
2. `drive.autoDrive("8FuelPath")` — returns `AutoBuilder.followPath(path)` which is PathPlanner's `FollowPathCommand`.

In `autonomousInit()`, the entire SequentialCommandGroup is scheduled via `CommandScheduler.getInstance().schedule(...)`.

### How PathPlanner is Configured

```java
// SwerveSubsystem.configurePathPlanner()
AutoBuilder.configure(
    this::getPose,          // pose supplier — reads from odometry
    this::resetOdometry,    // pose resetter — calls hardResetPose(pose, false)
    this::getChassisSpeeds, // chassis speeds supplier
    (speeds, feedforwards) -> driveFromChassisSpeeds(speeds, false),  // drive consumer (closed-loop)
    AutoConstants.SWERV_DRIVE_CONTROLLER,  // PPHolonomicDriveController with PID
    AutoConstants.ROBOT_CONFIG,            // RobotConfig with module locations
    FieldConstants::isRedAlliance,         // alliance flip supplier
    this                                   // subsystem requirement
);
```

### How `hardResetPose` Works

```java
private void hardResetPose(Pose2d newPose, boolean updatePigeon) {
    if (updatePigeon) {
        pigeon.setYaw(newPose.getRotation().getDegrees());
    }
    if (odometry != null) {
        Rotation2d gyroAngle = updatePigeon ? newPose.getRotation() : getYaw();
        odometry.resetPosition(gyroAngle, getPositions(), newPose);
    }
    pendingSimPoseReset = newPose;  // signals SimulationManager
}
```

### How `periodic()` Works

```java
public void periodic() {
    odometry.update(getYaw(), getPositions());  // reads pigeon + module encoders
    updateOdometryWithVision("limelight-a");
    updateOdometryWithVision("limelight-b");
    field.setRobotPose(getPose());
    robotPose.set(getPose());
    // ... SmartDashboard updates
}
```

### How SimulationManager Works

```java
public void simulationPeriodic() {
    // 1. Consume any pending pose reset from SwerveSubsystem
    Pose2d reset = swerveSubsystem.consumeSimPoseReset();
    if (reset != null) { simPose = reset; }

    // 2. Integrate chassis speeds into simPose
    simPose = simPose.exp(new Twist2d(vx*dt, vy*dt, omega*dt));

    // 3. Update pigeon sim state
    pigeonSimState.setRawYaw(simPose.getRotation().getDegrees());

    // 4. Update module encoder sim states
    updateModuleEncoders(desiredStates, dt);
}
```

---

## Diagnostic Method

We added temporary logging (still present, prefixed `[SWERVE-DEBUG]`, `[SIM-DEBUG]`,
`[DRIVE-DEBUG]`) to:

- `hardResetPose` — logs pose and updatePigeon flag
- `driveFromChassisSpeeds` — logs vx/vy/omega and openLoop flag (first 20 cycles)
- `resetOdometry` — logs pose + stack trace
- `consumeSimPoseReset` — logs consumed pose
- `SimulationManager.simulationPeriodic()` — logs dt, desired speeds, simPose, odoPose (first 10 cycles after reset)

---

## Confirmed Bugs Found and Fixed

### Bug 1: Wrong Field Dimensions — FIXED ✅

| Source | Field Length | Field Width |
|--------|-------------|-------------|
| `FieldConstants` (was) | **17.548** | **8.052** |
| PathPlanner `FlippingUtil` default | 16.54 | 8.07 |
| `navgrid.json` in this project | 16.54 | 8.07 |
| 2026 official field drawings | 16.54 | 8.07 |

**Impact**: When flipping for red alliance, the robot was placed ~1 meter away in X
from where PathPlanner expected the path to start.

**Fix applied**: Updated `FieldConstants.FIELD_LENGTH` to `16.5410` and
`FIELD_WIDTH` to `8.0693`.

**Result**: Position now matches PathPlanner's expectation (confirmed by diagnostic
output: `hardResetPose: pose=Pose2d(Translation2d(X: 13.00, Y: 0.94), ...)`).

### Bug 2: EightLemonAuto Starting Rotation Mismatch — FIXED ✅

`EightLemonAuto.startAutoAt` used heading `-130.45°`, but the 8FuelPath's
`idealStartingState.rotation` is `91.82°`. These must match so PathPlanner can use
its pre-computed ideal trajectory.

**Fix applied**: Changed `startAutoAt(3.5355, 7.1300, 91.82)` to match the path file.

**Result**: After alliance-flipping, the heading is now 271.82° for red, which matches
PathPlanner's mirrored path.

### Bug 3: Pigeon `setYaw` / `setRawYaw` Doubling in Simulation — ATTEMPTED, REVERTED

In CTRE Phoenix 6 simulation, `pigeon.setYaw(X)` creates an internal offset and
`pigeonSimState.setRawYaw(Y)` sets the raw value. When both are used with the same
target, the reported yaw doubles.

**Fix attempted**: Guard `pigeon.setYaw()` with `RobotBase.isReal()`.

**Result**: The heading doubling appeared to stop in diagnostic output, but the overall
issue was not resolved. **Reverted** to avoid leaking simulation-specific branching
logic into production code. `hardResetPose` now unconditionally calls `pigeon.setYaw()`.
Any pigeon simulation quirks should be handled entirely within `SimulationManager`.

### Bug 4: Module Order Mismatch in RobotConfig — FIXED ✅

`SwerveDriveKinematics` uses order FL, FR, BR, BL but `AutoConstants.ROBOT_CONFIG` had
FL, FR, BL, BR (back modules swapped).

**Fix applied**: Changed `ROBOT_CONFIG` to match kinematics order.

**Result**: No observable change (robot is square — swapped modules have identical
positions). Fix is correct but had no practical effect.

### Bug 5: Pigeon Sim Timing — rawYaw Not Set Before periodic() — ATTEMPTED, REVERTED

Hypothesis was that `periodic()` reads a stale pigeon value before `SimulationManager`
can update it.

**Fix attempted**: Call `pigeon.getSimState().setRawYaw(heading)` inside `hardResetPose`
when in simulation.

**Result**: No effect — `odoPose` rotation was still 0.00° at cycle 0. Either CTRE sim
state doesn't take effect immediately, or execution order differs from assumption.
**Reverted** to keep sim logic out of production code.

---

## Current Status

After five fix attempts, the core symptom is unchanged: PathPlanner commands huge speeds
for exactly one cycle, then finishes.

### What We Know For Certain (from diagnostic output)

1. `hardResetPose` fires correctly with pose `(13.00, 0.94, 271.82°)`.
2. `SimulationManager` consumes the pose reset and sets `simPose` correctly.
3. PathPlanner's first `execute()` commands enormous speeds: `vx=7.136 vy=7.647 omega=-10.064`.
4. PathPlanner's second cycle commands `vx=0 vy=0 omega=0` — the command has finished.
5. From cycle 2 onward, `openLoop=true` — TeleopSwerve (default command) has taken over.
6. `odoPose` rotation is 0.00° at cycle 0 despite `hardResetPose` setting it to 271.82°.

### What We Don't Know

1. **Why is `odoPose` 0° at cycle 0?** Our attempts to fix the pigeon value before
   `periodic()` had no effect. We don't know the actual execution order — specifically
   whether `periodic()` even runs between `startAutoAt` and PathPlanner's first execute.

2. **Why does `FollowPathCommand` finish after exactly 1 cycle?** We hypothesized NaN
   trajectory time but have NOT confirmed this. Could be NaN, could be a very short
   time, could be another early-termination condition.

3. **What is the actual execution order within `CommandScheduler.run()`?** When a
   `SequentialCommandGroup` contains an `InstantCommand` (via `runOnce`) followed by
   `FollowPathCommand`, does the scheduler run both within the same `run()` call?
   If so, `periodic()` does NOT run between `startAutoAt` and PathPlanner's first
   `execute()` — which would invalidate our theory about periodic() corrupting the pose.

### Key Architectural Concern

We want to avoid leaking simulation-specific code into production robot code. The
`hardResetPose` method should remain clean production code with no `isReal()`/
`isSimulation()` branching. Simulation-specific pigeon/encoder handling belongs
entirely in `SimulationManager`.

---

## Latest Console Output (for reference)

```
Selected auto mode: EightLemonAuto
[SWERVE-DEBUG] autoDrive: loaded path '8FuelPath' successfully
[SWERVE-DEBUG] hardResetPose: pose=Pose2d(Translation2d(X: 13.00, Y: 0.94), Rotation2d(Rads: 4.74, Deg: 271.82)) updatePigeon=true
[SIM-DEBUG] consumeSimPoseReset: Pose2d(Translation2d(X: 13.00, Y: 0.94), Rotation2d(Rads: 4.74, Deg: 271.82))
[SIM-DEBUG] cycle=0  dt=0.0712  desiredVx=0.000  desiredVy=0.000  desiredOmega=0.000  simPose=Pose2d(Translation2d(X: 13.00, Y: 0.94), Rotation2d(Rads: 4.74, Deg: 271.82))  odoPose=Pose2d(Translation2d(X: 13.00, Y: 0.94), Rotation2d(Rads: 0.00, Deg: 0.00))
[DRIVE-DEBUG] cycle=0  vx=7.136  vy=7.647  omega=-10.064  openLoop=false
[DRIVE-DEBUG] cycle=1  vx=0.000  vy=0.000  omega=0.000  openLoop=false
[SIM-DEBUG] cycle=1  dt=0.0026  desiredVx=0.000  desiredVy=0.000  desiredOmega=0.000  simPose=Pose2d(Translation2d(X: 13.00, Y: 0.94), Rotation2d(Rads: -1.54, Deg: -88.18))  odoPose=Pose2d(Translation2d(X: 13.00, Y: 0.94), Rotation2d(Rads: 0.00, Deg: 0.00))
[DRIVE-DEBUG] cycle=2  vx=0.000  vy=-0.000  omega=0.000  openLoop=true
[SIM-DEBUG] cycle=2  dt=0.0050  desiredVx=0.000  desiredVy=0.000  desiredOmega=0.000  simPose=Pose2d(Translation2d(X: 13.00, Y: 0.94), Rotation2d(Rads: -1.54, Deg: -88.18))  odoPose=Pose2d(Translation2d(X: 13.00, Y: 0.94), Rotation2d(Rads: -1.54, Deg: -88.18))
```

Note: `odoPose` rotation is 0.00° at cycles 0 and 1, then jumps to -88.18° at cycle 2.
PathPlanner has already finished by cycle 1.

---

## Summary of All Fix Attempts

| # | Issue | Status | Result | Files |
|---|-------|--------|--------|-------|
| 1 | Field dimensions (17.548 → 16.54) | ✅ Fixed | Position now correct | `Constants.java` |
| 2 | EightLemonAuto rotation (-130.45° → 91.82°) | ✅ Fixed | Heading now matches path | `EightLemonAuto.java` |
| 3 | Pigeon setYaw/setRawYaw doubling in sim | ⏪ Reverted | Appeared to help in isolation; reverted to keep sim logic out of production code | `SwerveSubsystem.java` |
| 4 | Module order mismatch in RobotConfig | ✅ Fixed | No effect (square robot) | `Constants.java` |
| 5 | Pigeon sim timing: rawYaw in hardResetPose | ⏪ Reverted | No effect; reverted to keep sim logic out of production code | `SwerveSubsystem.java` |

## Existing Temporary Diagnostic Logging

The following temporary logging is still in the code and should be replaced/enhanced
by the instrumentation effort:

- `SwerveSubsystem.hardResetPose` — `[SWERVE-DEBUG]` prefix
- `SwerveSubsystem.autoDrive` — `[SWERVE-DEBUG]` prefix
- `SwerveSubsystem.driveFromChassisSpeeds` — `[DRIVE-DEBUG]` prefix (first 20 cycles)
- `SwerveSubsystem.resetOdometry` — `[SWERVE-DEBUG]` prefix + stack trace
- `SwerveSubsystem.consumeSimPoseReset` — plain println
- `SimulationManager.simulationPeriodic` — `[SIM-DEBUG]` prefix (first 10 cycles after reset)
