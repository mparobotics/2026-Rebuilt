# Autonomous Mode Assessment — 2024 Infrastructure vs 2026 Gaps

## 1. Why This Matters

The drift and jitter issues are frustrating in teleop, but a human driver can compensate. In autonomous mode, the robot relies entirely on odometry and path-following software to navigate. If the drivebase has a persistent directional error (drift) or a module that oscillates (jitter), every path segment accumulates error. The strategy team's planned auto routine — shoot 8 preloaded fuel, drive to the depot for more, return to the shooting band and shoot again — requires precise navigation to multiple field locations within 20 seconds. Even small per-segment errors compound into missed alignment with the hub, failed depot pickups, and wasted time.

The 2024 team achieved a top-tier autonomous routine (one of the best at that year's competition). That was possible because of a **three-layer compensation system** that actively fought against drift and odometry error in real-time. The 2026 codebase is currently missing nearly all of this infrastructure.

## 2. How 2024 Autonomous Navigation Worked

The 2024 code did NOT blindly trust PathPlanner. It used three layers of compensation working together every 20ms cycle:

### Layer 1: Vision-Fused Pose Estimation (continuous during auto)

The `periodic()` method in `SwerveSubsystem` runs every robot cycle, **including during autonomous**. It continuously fuses encoder-based odometry with AprilTag vision from **two Limelights**:

```java
// 2024 SwerveSubsystem.periodic() — runs every 20ms, even during auto
odometry.update(getYaw(), getPositions());  // encoder + gyro update

// Fuse vision from two cameras
LimelightHelpers.PoseEstimate estimateA = LimelightHelpers.getBotPoseEstimate_wpiBlue("limelight-a");
LimelightHelpers.PoseEstimate estimateB = LimelightHelpers.getBotPoseEstimate_wpiBlue("limelight-b");

// If both cameras see one tag each, trust the combined estimate
if(estimateA.tagCount == 1 && estimateB.tagCount == 1 && AisValid && BisValid){
    odometry.addVisionMeasurement(estimateA.pose, estimateA.timestampSeconds);
    odometry.addVisionMeasurement(estimateB.pose, estimateB.timestampSeconds);
}
// If either camera sees 2+ tags, trust that camera alone
else if(AisValid && estimateA.tagCount >= 2){
    odometry.addVisionMeasurement(estimateA.pose, estimateA.timestampSeconds);
}

// Sanity check: snap position back if it drifts outside field bounds
keepOdometryOnField();
```

This means every time PathPlanner asked "where am I?" via `getPose()`, it got a **vision-corrected answer** — not just raw wheel odometry.

### Layer 2: PathPlanner Configuration with Active Correction

PathPlanner was configured with translation and rotation PID controllers, plus replanning:

```java
// 2024 Constants.AutoConstants
public static final HolonomicPathFollowerConfig pathConfig = new HolonomicPathFollowerConfig(
    new PIDConstants(5.0, 0.00001, 0.0),  // Translation PID (P=5.0, I=0.00001)
    new PIDConstants(5.0, 0.0005, 0.001), // Rotation PID (P=5.0, I=0.0005, D=0.001)
    SwerveConstants.maxSpeed,              // 5 m/s max module speed
    SwerveConstants.driveBaseRadius,       // drive base geometry
    new ReplanningConfig()                 // enables on-the-fly replanning when robot deviates
);
```

```java
// 2024 SwerveSubsystem.configPathPlanner()
AutoBuilder.configureHolonomic(
    this::getPose,               // ← pose supplier (returns vision-fused estimate)
    this::resetOdometry,         // ← pose reset
    this::getRobotRelativeSpeed, // ← current chassis speeds
    this::closedLoopDrive,       // ← drive consumer (closed-loop, not open-loop)
    AutoConstants.pathConfig,    // ← PID + replanning config
    () -> (DriverStation.getAlliance().get() == Alliance.Red),
    this
);
```

Key design decisions:
- **`this::getPose`** feeds the vision-corrected pose to PathPlanner, so its error calculations are based on the best available position estimate
- **`ReplanningConfig()`** (default) enables on-the-fly replanning — if the robot deviates from the planned path, PathPlanner regenerates the trajectory from the robot's current position rather than trying to drive back to the old trajectory
- **P=5.0 for both translation and rotation** — these are aggressive gains that correct for errors quickly
- **Non-zero I terms** — ensure even small persistent errors (like the drift you're experiencing) are eventually eliminated

### Layer 3: Closed-Loop Velocity Control During Auto

PathPlanner drove the robot through `closedLoopDrive()`, which used PID + feedforward motor control:

```java
// 2024 SwerveSubsystem
public void closedLoopDrive(ChassisSpeeds speeds){
    driveFromChassisSpeeds(speeds, false);  // false = closed-loop
}
```

In closed-loop mode, each drive motor uses its onboard PID controller + feedforward to hit the exact commanded velocity. In teleop, the robot uses open-loop (voltage percentage) for driver feel. The closed-loop mode during auto ensures each wheel actually spins at the speed PathPlanner requests, not just "approximately" that speed.

### The Complete Feedback Loop

Every 20ms during autonomous:

```
1. periodic() updates pose from wheel encoders + Pigeon gyro
2. periodic() fuses in AprilTag vision corrections from both Limelights
3. periodic() clamps pose to field boundaries (keepOdometryOnField)
4. PathPlanner calls getPose() → gets vision-corrected position
5. PathPlanner compares actual vs. desired position on the path
6. PathPlanner's PID controllers compute translational + rotational corrections
7. If deviation exceeds threshold, ReplanningConfig triggers a full path replan
8. closedLoopDrive() sends corrected velocities to each module
9. Drive motor onboard PID ensures actual wheel speeds match commanded speeds
10. Repeat
```

This is why the 2024 auto was so effective — the robot was **never more than one or two cycles behind** on correcting for any drift or disturbance.

## 3. What 2026 Has and What It's Missing

| Component | 2024 Status | 2026 Status | Severity |
|-----------|-------------|-------------|----------|
| `SwerveDrivePoseEstimator` (odometry + vision fusion) | ✅ Present | ✅ Present | — |
| Dual Limelight vision integration in `periodic()` | ✅ MegaTag1, dual cameras | ✅ MegaTag2, dual cameras | OK |
| `configPathPlanner()` / `AutoBuilder` setup | ✅ Called in constructor | ❌ **Missing entirely** | 🔴 CRITICAL |
| `AutoConstants` class (path PID, replanning config) | ✅ P=5.0 translation, P=5.0 rotation | ❌ **Doesn't exist** | 🔴 CRITICAL |
| `closedLoopDrive(ChassisSpeeds)` method | ✅ Present, used by PathPlanner | ❌ **Missing** | 🔴 CRITICAL |
| `getRobotRelativeSpeed()` method | ✅ Present | ❌ **Missing** | 🔴 CRITICAL |
| `followPathFromFile(String)` convenience method | ✅ Present | ❌ **Missing** | 🔴 CRITICAL |
| `startAutoAt(x, y, heading)` method | ✅ Sets starting pose + gyro | ❌ **Missing** | 🔴 CRITICAL |
| `backupCommand()` (dynamic path generation) | ✅ Present | ❌ **Missing** | MODERATE |
| Auto mode selector (`AutoModeSelector` class) | ✅ 11 routines (various strategies) | ❌ Returns `"No autonomous command configured"` | 🔴 CRITICAL |
| `keepOdometryOnField()` sanity check | ✅ Present | ❌ **Removed** | MODERATE |
| `isOdometryValid()` encoder error check | ✅ Present (skips update on error) | ❌ **Removed** | MODERATE |
| PathPlanner vendordep | ✅ Present | ⚠️ **Verify** — not imported in any Java file | ⚠️ Verify |

## 4. Impact of Current Drivebase Bugs on Autonomous

Even with the full PathPlanner infrastructure ported, the currently identified drivebase issues would severely degrade autonomous performance:

| Issue | Impact on Auto | Why Compensation Can't Fully Fix It |
|-------|---------------|-------------------------------------|
| **Bad angle offsets** (§2.3) | Robot physically drives at an angle to the intended direction | Vision corrects the pose *estimate*, but the correction command is also misdirected because the modules are pointing wrong. PathPlanner would see the error and try to correct, but each correction is itself slightly off. This creates jerky, oscillatory path following. |
| **Module jitter** (§2.3, §1.5) | Jittering module introduces vibration and inconsistent wheel contact | PathPlanner expects smooth module responses. A jittering module provides inconsistent wheel speed data, corrupting the odometry that feeds the pose estimator. Vision can partially compensate, but the robot will follow a wobbly path instead of a smooth one. |
| **CAN signal bug** (§9.1) | Possible stale encoder data during fast maneuvers | PathPlanner commands velocity changes every 20ms. If encoder data is delayed due to CAN congestion, the drive motor PID in closed-loop mode reacts to stale data, causing velocity oscillation. |

**For the planned autonomous routine:** The timing margin is tight — shoot 8 preloads, drive to depot, collect fuel, drive back, and shoot again, all in 20 seconds. Even if PathPlanner successfully compensates for drift, the compensation itself costs time — every correction is distance the robot travels that isn't along the optimal path. A clean drivebase that drives straight without correction saves ~0.2–0.5 seconds per path segment. Over the five segments of the planned routine (start → band, align + shoot, band → depot, depot → band, align + shoot), those savings add up to 1–2.5 seconds — often the difference between completing the depot cycle or running out of time after the first volley.

## 5. Recommended Path to a Competitive Autonomous

**Phase 1 — Fix the drivebase (prerequisite, do first):**
1. Clear WPILib Preferences and verify angle offsets (§2.3, §1.5)
2. Fix CANSparkUtil signal names (§9.1)
3. Verify angleInvert and Pigeon orientation (§1.3, §1.2)
4. Test: robot should drive straight with no drift and no jittering at rest

**Phase 2 — Port PathPlanner infrastructure from 2024:**
1. Verify PathPlanner is in `vendordeps/` (if not, add the PathPlannerLib JSON)
2. Create `AutoConstants` class in 2026 `Constants.java` with path-following PID constants — start with the 2024 values but adjust `maxSpeed` to 3 m/s (or increase `maxSpeed` once the drivebase is verified)
3. Add `closedLoopDrive()` and `getRobotRelativeSpeed()` to `SwerveSubsystem`
4. Add `configPathPlanner()` to `SwerveSubsystem` — **note: the PathPlanner API changed between 2024 and 2026**; `configureHolonomic()` was renamed and the configuration object changed. Use the 2026 PathPlanner docs for the exact API.
5. Add `followPathFromFile()` and `startAutoAt()` convenience methods
6. Re-add `keepOdometryOnField()` to `periodic()` as a safety net
7. Consider re-adding `isOdometryValid()` to skip odometry updates during encoder errors

**Phase 3 — Build and test the planned auto routine incrementally:**
1. Phase 1 only: drive from start into shooting band, align with hub, shoot 8 preloaded fuel. Validates path following and hub alignment.
2. Add Phase 2: after shooting, drive to depot and collect fuel. Validates multi-segment navigation.
3. Add Phase 3: drive back to shooting band, re-align, shoot depot fuel. Completes the full planned routine.
4. Use AdvantageScope or Shuffleboard to compare desired vs. actual path in real-time
5. Tune PathPlanner PID constants if the robot overshoots or oscillates during path following

**Phase 4 — Optimize for competition:**
1. Increase `maxSpeed` (currently capped at 3 m/s, 2024 used 5 m/s)
2. Remove the `* 0.5` speed multiplier in `RobotContainer`
3. Tune path constraints (max velocity, max acceleration) for each path segment
4. Consider adding path-specific vision rejection (e.g., ignore vision updates during fast maneuvers to avoid pose jumps)
