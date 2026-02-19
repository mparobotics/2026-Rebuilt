# 2024 → 2026 Swerve Codebase Comparison Report

## Executive Summary

*For coaches, mentors, and team leads — the key findings at a glance.*

---

### Part A: Teleop Issues — Drift and Jitter

**The two reported problems:**
1. The robot doesn't drive straight — it drifts/pulls to one side.
2. One swerve module jitters — a single angle motor oscillates back and forth when the robot is at rest.

**Root causes found:**

A systematic comparison of every drive-related file between the working 2024 codebase and the current 2026 codebase identified **three code-level issues** that are the most likely causes:

| # | Issue | Impact | Fix Difficulty |
|---|-------|--------|----------------|
| 1 | **Stale calibration data overriding module angle offsets.** The 2026 code added a "Preferences" system that saves angle offsets to the roboRIO. Once a value is stored in Preferences, it **silently overrides** any subsequent changes to `Constants.java` on every boot — meaning the team can update Constants all day and the robot will never use the new values. The team has reported needing to recalibrate angle offsets repeatedly, which is a strong indicator that this is happening: the offsets aren't actually "changing," the correct values in Constants are simply being ignored in favor of stale Preferences data. This is the single most likely cause of *both* the drift and the single-module jitter. | 🔴 CRITICAL | Easy — clear stored Preferences and reboot |
| 2 | **CAN bus optimization is completely broken.** A library migration error caused the CAN frame-rate configuration to target analog sensor signals (which don't exist on these motors) instead of the integrated encoder signals. The optimization code runs without errors but does nothing. | 🔴 CRITICAL (code bug) | Easy — rename 2 method calls in `CANSparkUtil.java` |
| 3 | **Angle offsets need physical verification — after clearing Preferences.** The team has calibrated offsets multiple times using a metal alignment rod, but those values may never have taken effect because stored Preferences override Constants. After clearing Preferences (fix #1), verify one more time that the defaults in `Constants.java` are correct. | ⚠️ Verify | Medium — requires physical access to the robot |

**Recommended teleop fixes (priority order):**

1. Connect to the roboRIO and clear all `Swerve/Module*/AngleOffsetDegrees` Preferences entries. Reboot. Verify drift and jitter are resolved.
2. If issues persist, physically verify angle offsets: point all wheels straight forward, read CANcoder values, compare to `Constants.java` defaults.
3. Fix the `CANSparkUtil.java` CAN signal names (2 lines of code).
4. Verify `angleInvert = true` is correct for all four Mk4i modules.
5. Verify Pigeon gyro orientation matches expectations.

**These teleop fixes are prerequisites for autonomous development.** Do not attempt to build auto routines until the robot drives straight and the jitter is resolved.

---

### Part B: Autonomous Issues — Missing Infrastructure and Degraded Pose Accuracy

**The critical discovery:**

The 2024 team's top-tier autonomous routine (one of the best at that year's competition) was powered by a **three-layer navigation correction system**. This system continuously corrected the robot's position and path during autonomous. **Nearly all of this system is missing from the 2026 codebase.**

A human driver can compensate for drift and jitter during teleop. In autonomous mode, the robot relies entirely on software to navigate — there is no human in the loop. The strategy team's planned auto routine requires three phases: (1) drive into the shooting band and shoot 8 preloaded fuel, (2) drive to the depot and collect more fuel, (3) drive back into the shooting band and shoot again. Each phase requires precise navigation and hub alignment. If the drivebase drifts and the software can't detect and correct for it, the robot will miss its targets — arriving at the wrong position in the shooting band, misaligning with the hub, or failing to reach the depot accurately.

**What the 2024 autonomous system did (and what 2026 is missing):**

| Navigation Layer | What It Did | 2026 Status |
|-----------------|-------------|-------------|
| **Vision-corrected positioning** | Every 20ms, fused wheel encoder data with AprilTag vision from *two* Limelight cameras. The robot always knew where it was, even if wheels slipped. Included safety checks: skipped updates on encoder errors (`isOdometryValid`), clamped position to field boundaries (`keepOdometryOnField`), required multi-tag sightings for trust. | ⚠️ **Partially present but degraded.** Vision fusion exists but safety checks were removed. The encoder error guard and field boundary clamp are gone. The vision trust model is more permissive (accepts single-tag sightings more readily). See §3.3. |
| **PathPlanner with active correction** | Configured PathPlanner with aggressive PID controllers (P=5.0) and on-the-fly replanning. When the robot deviated from its path, PathPlanner recalculated the trajectory from the robot's current position. | ❌ **Completely missing.** No PathPlanner configuration, no path-following PID constants, no auto builder setup. `getAutonomousCommand()` returns `"No autonomous command configured"`. |
| **Closed-loop motor control** | During auto, drive motors used PID + feedforward to hit exact commanded velocities (not just voltage percentages). This made motor response precise and repeatable. | ❌ **Missing.** The `closedLoopDrive()` method doesn't exist. |

**Additional auto-specific concerns found in the comparison:**

| Issue | Teleop Severity | Auto Severity | Why It's Worse for Auto |
|-------|----------------|---------------|------------------------|
| CAN signal bug in `CANSparkUtil` (§9.1) | 🔴 CRITICAL | 🔴 CRITICAL (amplified) | Auto uses closed-loop motor control, which requires accurate encoder data every 20ms. Stale data causes PID oscillation. |
| Removed `isOdometryValid()` check (§3.3) | MINOR | 🔴 CRITICAL | Bad encoder data corrupts the pose estimate that PathPlanner relies on for navigation. |
| Removed `keepOdometryOnField()` (§3.3) | MINOR | 🔴 CRITICAL | A runaway pose estimate could cause PathPlanner to generate paths that drive the robot into walls. |
| `maxSpeed` reduced to 3 m/s (§1.8) | MINOR | MODERATE | Caps PathPlanner at 60% of the 2024 speed. The planned auto (shoot preloads → depot → shoot again) requires multiple cross-field segments in 20 seconds — may not complete at this reduced speed. |
| MegaTag2 vision changes (§12) | Not relevant | MODERATE | Different vision trust model — may accept lower-quality pose estimates during fast maneuvers. Needs tuning. |
| 0.5 joystick multiplier (§5.1) | MODERATE | N/A | Only affects joystick input — PathPlanner bypasses it entirely. Not an auto concern. |

**Recommended autonomous action plan:**

*Phase 1 — Fix the drivebase first (see Part A above)*

*Phase 2 — Restore pose estimation safety and calibration (1 session):*
1. Re-add `isOdometryValid()` to skip odometry updates when encoder data is bad.
2. Re-add `keepOdometryOnField()` to clamp the pose estimate to field boundaries.
3. Fix the CAN signal names in `CANSparkUtil.java`.
4. **Measure actual wheel diameter** — the 2024 team calibrated theirs (3.91" vs. the 4.00" factory spec, a 2.4% difference that causes ~12 cm of odometry drift per 5 m). Port the `MeasureWheelDiameter` command or manually measure, and update `wheelDiameter` in `Constants.java`. See §8.1.

*Phase 3 — Port PathPlanner infrastructure from 2024 (1–2 sessions):*
4. Verify PathPlanner is in `vendordeps/` (if not, add the PathPlannerLib JSON).
5. Create `AutoConstants` class with path-following PID constants (start with 2024 values).
6. Add `closedLoopDrive()`, `getRobotRelativeSpeed()`, `followPathFromFile()`, and `startAutoAt()` to `SwerveSubsystem`.
7. Configure `AutoBuilder` in `SwerveSubsystem` constructor — **note:** the PathPlanner API changed between 2024 and 2026; `configureHolonomic()` was renamed. Use the 2026 PathPlanner docs.

*Phase 4 — Build and test auto routines incrementally:*
8. Start with Phase 1 only: drive from starting position into the shooting band, align with hub, and shoot 8 preloaded fuel. This validates PathPlanner path following and hub alignment.
9. Add Phase 2: after shooting, drive to the depot and collect fuel. This validates multi-segment path following and depot navigation.
10. Add Phase 3: drive back into the shooting band, re-align, and shoot depot fuel. This validates the full planned auto routine. Use AdvantageScope to compare desired vs. actual path in real-time at each phase.
11. Increase `maxSpeed` from 3 to 4–5 m/s once path following is accurate at lower speeds.

---

### Using AI to Accelerate These Fixes

With competition approaching, the autonomous porting work (Phase 3–4 above) is significant — multiple methods across multiple files, with API migrations where 2024 method names no longer exist in 2026 libraries. Section §14 provides detailed recommendations for using AI tools responsibly to accelerate this work.

The recommended approach is **not** "vibe coding" (asking AI to write code and deploying it unreviewed). Instead, the team should use AI as a **migration accelerator**: provide the working 2024 code as context, ask AI to produce the 2026 equivalent, then review the output side-by-side with the original before deploying. Key use cases include having AI explain unfamiliar 2024 code, translate specific methods to the 2026 API, and review ported code for the exact type of subtle API mapping errors found in this report (like the CANSparkUtil signal-name bug). See §14 for the full workflow, specific prompt examples, and a "what not to do" checklist.

---

### What Was NOT a Problem

The comparison also confirmed that many aspects of the 2024→2026 migration were done correctly:
- Motor PID values, feedforward constants, and idle modes were all migrated properly
- The REVLib imperative→declarative configuration migration was done correctly for all motor settings
- The custom `optimize()` method is mathematically equivalent to the 2024 version
- The Pigeon gyro inversion change is consistent with the API method change
- The swerve kinematics module ordering is internally consistent
- The drive and angle motor configurations match their 2024 equivalents (aside from intentional hardware changes)

---

## Confirmation

1. ✅ Both `2024-Season/` and `2026-Season/` directories are accessible.
2. ✅ All file paths match the actual structure. Verified:
   - 2024: `src/main/java/frc/robot/subsystems/SwerveModule.java`, `SwerveSubsystem.java`; `frc/robot/commands/TeleopSwerve.java`; `frc/lib/OnboardModuleState.java`, `CANSparkUtil.java`, `SwerveModuleConstants.java`
   - 2026: `src/main/java/frc/robot/SwerveModule.java`; `frc/robot/Subsystems/SwerveSubsystem.java`; `frc/robot/Command/TeleopSwerve.java`, `AutoAlign.java`; `frc/lib/CANSparkUtil.java`

---

## Dependency File Scan

### Imports traced from every listed file

All `import frc.*` statements were scanned. The project-local dependency files are:

| File | Imported by |
|------|------------|
| `frc.lib.CANSparkUtil` | SwerveModule (both years) |
| `frc.lib.OnboardModuleState` | SwerveModule 2024, TeleopSwerve 2024 |
| `frc.lib.SwerveModuleConstants` | SwerveModule 2024, Constants 2024 |
| `frc.lib.LimelightHelpers` | SwerveSubsystem (both years) |
| `frc.robot.Constants.SwerveConstants.ModuleData` | SwerveModule 2026, SwerveSubsystem 2026 |
| `frc.robot.Command.AutoAlign` | RobotContainer 2026 |

No additional transitive `frc.*` imports were found beyond these.

---

## 1. Constants.java

**Files:** `2024-Season/src/main/java/frc/robot/Constants.java` vs `2026-Season/src/main/java/frc/robot/Constants.java`

### 1.1 Numerical Constants — Side-by-Side

| Constant | 2024 Value | 2026 Value | Changed? |
|----------|-----------|-----------|----------|
| inputDeadband | 0.1 | 0.1 | No |
| PIGEON_ID | 17 | 17 | No |
| **invertPigeon** | **true** | **false** | **YES** (see §1.2) |
| halfTrackWidth | `inchesToMeters(21.0/2.0)` ≈ 0.2667 m | `inchesToMeters(27/2.0)` ≈ 0.3429 m | YES — different robot frame |
| halfWheelBase | `inchesToMeters(21.0/2.0)` ≈ 0.2667 m | `inchesToMeters(27/2.0)` ≈ 0.3429 m | YES — different robot frame |
| wheelDiameter | 0.0992 m (direct) | `inchesToMeters(4.0)` ≈ 0.1016 m | YES — 2024 was calibrated, 2026 is nominal |
| **driveGearRatio** | 8.14 (Mk4 L1) | 6.75 (Mk4 L2) | YES — different modules |
| **angleGearRatio** | 12.8 (Mk4) | 21.4 (Mk4i) | YES — different modules |
| driveConversionPositionFactor | wheelCirc / 8.14 ≈ 0.0383 | wheelCirc / 6.75 ≈ 0.0473 | YES — follows from above |
| driveConversionVelocityFactor | above / 60 | above / 60 | YES — follows from above |
| angleConversionFactor | 360 / 12.8 = 28.125 | 360 / 21.4 ≈ 16.822 | YES — follows from above |
| **maxSpeed** | **5** m/s | **3** m/s | **YES** |
| maxAngularVelocity | 5/driveBaseRadius ≈ 13.3 rad/s | 3/driveBaseRadius ≈ 6.19 rad/s | YES — follows from above |
| voltageComp | 12.0 | 12.0 | No |
| angleContinuousCurrentLimit | 20 A | 20 A | No |
| **driveContinuousCurrentLimit** | **50 A** | **40 A** | **YES ⚠️** |
| driveKP | 0.1 | 0.1 | No |
| driveKI | 0.0 | 0.0 | No |
| driveKD | 0.0 | 0.0 | No |
| driveKFF | 0.0 | *(not defined)* | Removed — was 0.0, default is 0.0, no impact |
| driveKS | 0.667 | 0.667 | No |
| **driveKV** | **2.44** | **2.4** | **YES** (minor) |
| driveKA | 0.5 | 0.5 | No |
| angleKP | 0.01 | 0.01 | No |
| angleKI | 0.0 | 0.0 | No |
| angleKD | 0.0 | 0.0 | No |
| angleKFF | 0.0 | *(not defined)* | Removed — was 0.0, no impact |
| angleNeutralMode | kBrake | kBrake | No |
| driveNeutralMode | kBrake | kBrake | No |
| openLoopRamp | 0.25 | 0.25 | No (neither year actually applies it — see §2.5) |
| closedLoopRamp | 0.0 | 0.0 | No |
| **driveInvert** | false | false | No |
| **angleInvert** | **false** | **true** | **YES ⚠️** (expected for Mk4→Mk4i) |
| canCoderInvert | false | false | No |

### 1.2 Pigeon Gyro Inversion — **invertPigeon changed from `true` to `false`**

**File:** Constants.java, `SwerveConstants` class  
**2024:** `invertPigeon = true` (line 168)  
**2026:** `invertPigeon = false` (line 30)

**Analysis:** This change is **correct and expected** given that the yaw reading method also changed:

- **2024** `SwerveSubsystem.getYawAsDouble()` uses `pigeon.getAngle()` — this is the WPILib Gyro interface, which returns **clockwise-positive** (negated Phoenix6 yaw). With `invertPigeon = true`, the code computes `360 - pigeon.getAngle()`, which double-negates back to **counterclockwise-positive** (matching WPILib's coordinate system).
- **2026** `SwerveSubsystem.getYaw()` uses `pigeon.getYaw().getValueAsDouble()` — this is the Phoenix6 native API, which returns **counterclockwise-positive** directly. With `invertPigeon = false`, no inversion is applied.

Both produce the **same effective sign convention** (CCW-positive). No bug here.

**Severity:** Not a bug — the flag change is consistent with the API method change.

**⚠️ However — verify the Pigeon is physically mounted with the same orientation as in 2024.** If the Pigeon is mounted upside-down or rotated compared to 2024, the yaw sign could be wrong, which would break field-oriented driving and cause the robot to steer in unexpected directions.

### 1.3 angleInvert Changed: `false` → `true`

**File:** Constants.java, `SwerveConstants` class  
**2024:** `angleInvert = false` (line 243)  
**2026:** `angleInvert = true` (line 104)

**Impact:** This inversion is applied to the angle motors (steering motors). It changed because the module type changed from **SDS Mk4** (12.8:1 gear ratio, `angleGearRatio = 12.8`) to **SDS Mk4i** (21.4:1 gear ratio, `angleGearRatio = 21.4`). The Mk4i has a different internal gear train that requires the motor to spin in the opposite direction compared to the Mk4.

**Severity:** Expected hardware change — **but verify this is correct for your specific Mk4i modules.** If even one module is a Mk4 (not Mk4i), or if the motor is wired differently, the inversion would be wrong for that module and could cause it to fight the PID controller (the jittering symptom).

**Recommendation:** With the robot on blocks, command a known angle (e.g., 90°) and verify all four modules rotate in the correct direction and reach the target.

### 1.4 SwerveDriveKinematics Module Ordering

**File:** Constants.java, `swerveKinematics`

**2024 (line 196–201):**
```java
new SwerveDriveKinematics(
    new Translation2d(halfWheelBase, halfTrackWidth),   // [0] Front Left
    new Translation2d(-halfWheelBase, halfTrackWidth),  // [1] Back Left
    new Translation2d(-halfWheelBase, -halfTrackWidth), // [2] Back Right
    new Translation2d(halfWheelBase, -halfTrackWidth)   // [3] Front Right
);
```
Module order: **FL, BL, BR, FR** → indices 0, 1, 2, 3

**2026 (line 50–56):**
```java
new SwerveDriveKinematics(
    new Translation2d(halfTrackWidth, halfWheelBase),    // [0] Front Left
    new Translation2d(halfTrackWidth, -halfWheelBase),   // [1] Front Right
    new Translation2d(-halfTrackWidth, -halfWheelBase),  // [2] Back Right
    new Translation2d(-halfTrackWidth, halfWheelBase)    // [3] Back Left
);
```
Module order: **FL, FR, BR, BL** → indices 0, 1, 2, 3

**Analysis:** The module ordering changed. This is fine **as long as** the `moduleData` array (which assigns CAN IDs and offsets to each index) matches the new kinematics ordering. Verified:

```java
// 2026 moduleData (line 117-122):
moduleData[0] = Front Left  (CAN IDs 6,5,7)   → kinematics[0] = Front Left  ✓
moduleData[1] = Front Right  (CAN IDs 9,8,10)  → kinematics[1] = Front Right ✓
moduleData[2] = Back Right   (CAN IDs 12,11,13)→ kinematics[2] = Back Right  ✓
moduleData[3] = Back Left    (CAN IDs 15,14,16)→ kinematics[3] = Back Left   ✓
```

The ordering is internally consistent within 2026. **No bug here.**

**⚠️ Note:** The 2026 code swaps the X and Y arguments in the Translation2d constructor (`halfTrackWidth, halfWheelBase` instead of `halfWheelBase, halfTrackWidth`). In WPILib, Translation2d is (X = forward, Y = left). The 2024 code correctly uses `halfWheelBase` (forward) as X and `halfTrackWidth` (left) as Y. The 2026 code swaps these. **Because the 2026 robot is square** (halfTrackWidth == halfWheelBase == 27/2 inches), this swap has no numerical effect. But it's a conceptual error that would cause problems if the robot were not square.

**Severity:** MINOR (no effect on square robot, but indicates misunderstanding)

### 1.5 Module-Specific Constants (Angle Offsets)

**File:** Constants.java, module definitions

| Module | 2024 CAN IDs (D/A/E) | 2024 Offset | 2026 CAN IDs (D/A/E) | 2026 Offset | 2026 Position |
|--------|----------------------|-------------|----------------------|-------------|---------------|
| Mod 0 | 3 / 2 / 11 | 160.2° (Rotation2d) | 6 / 5 / 7 | 31.46° (double) | Front Left |
| Mod 1 | 5 / 4 / 12 | 117.2° (Rotation2d) | 9 / 8 / 10 | 49.57° (double) | Front Right |
| Mod 2 | 7 / 6 / 13 | 141.0° (Rotation2d) | 12 / 11 / 13 | 33.13° (double) | Back Right |
| Mod 3 | 9 / 8 / 14 | −138.0° (Rotation2d) | 15 / 14 / 16 | 8.52° (double) | Back Left |

Different robot, different CAN IDs and offsets — expected. The offset type changed from `Rotation2d` to `double` (degrees), which is handled by the new `ModuleData` record.

**Key concern:** The 2026 code introduces a **WPILib Preferences override** for angle offsets (see §2.3). If stale or incorrect values exist in the roboRIO's Preferences storage, they will silently override the defaults above. **This is a likely cause of module-specific issues.**

**Severity:** CRITICAL risk if Preferences data is stale (see §2.3)

### 1.6 driveContinuousCurrentLimit: 50A → 40A

**File:** Constants.java  
**2024:** `driveContinuousCurrentLimit = 50` (line 209)  
**2026:** `driveContinuousCurrentLimit = 40` (line 67)

**Impact:** The drive motors in 2026 are limited to 40A instead of 50A. This reduces maximum torque by ~20%, which limits acceleration and peak speed under load. This would not cause the robot to drift, but it reduces overall drive performance.

**Severity:** MODERATE — reduces performance but doesn't cause drift or jitter.

### 1.7 driveKV: 2.44 → 2.4

**File:** Constants.java  
**2024:** `driveKV = 2.44` (line 221)  
**2026:** `driveKV = 2.4` (line 79)

**Impact:** Negligible. The feedforward voltage-velocity constant changed by ~1.6%. This slightly affects closed-loop velocity control accuracy but would not cause drift.

**Severity:** MINOR

### 1.8 maxSpeed: 5 → 3 m/s

**File:** Constants.java  
**2024:** `maxSpeed = 5` (line 192)  
**2026:** `maxSpeed = 3` (line 94)

**Impact:** This affects:
1. Open-loop speed scaling in `setSpeed()`: `percentOutput = desiredSpeed / maxSpeed`
2. The low-speed angle-lock threshold in `setAngle()`: 0.05 m/s (2024) vs 0.03 m/s (2026)
3. Wheel speed desaturation in `driveFromChassisSpeeds()`

Combined with the `* 0.5` multiplier in 2026's RobotContainer (see §5.1), the effective maximum teleop speed is 3 × 0.5 = **1.5 m/s** in 2026 vs **5 m/s** in 2024. This is likely intentional for safety during testing.

**Severity (teleop):** MINOR — intentional, does not cause drift.

**Severity (autonomous):** MODERATE — `maxSpeed` is passed to PathPlanner's `HolonomicPathFollowerConfig` as the maximum module speed. At 3 m/s, PathPlanner will constrain all path segments to this speed, making the robot significantly slower during auto. The 2024 robot ran at up to 5 m/s. The planned auto routine (shoot preloads → depot → shoot again) requires at least three cross-field path segments plus two alignment/shooting phases — all in 20 seconds. This 40% speed reduction could make it impossible to complete the full cycle. This should be increased once the drivebase is verified to be working correctly.

---

## 2. SwerveModule.java

**Files:** `2024-Season/src/main/java/frc/robot/subsystems/SwerveModule.java` vs `2026-Season/src/main/java/frc/robot/SwerveModule.java`

### 2.1 REVLib Migration Motor Config Checklist — CRITICAL

This is the most important comparison. Every 2024 motor setting must have an equivalent in 2026.

#### Angle Motor Configuration

| Setting | 2024 Code | 2026 Code | Status |
|---------|-----------|-----------|--------|
| Factory reset | `angleMotor.restoreFactoryDefaults()` | `ResetMode.kResetSafeParameters` | ✅ Equivalent |
| CAN bus optimization | `CANSparkUtil.setCANSparkBusUsage(angleMotor, kPositionOnly)` | `CANSparkUtil.setSparkBusUsage(sparkMaxConfig, kPositionOnly)` | ⚠️ **Wrong signals** — see §9.1 |
| Current limit | `angleMotor.setSmartCurrentLimit(20)` | `sparkMaxConfig.smartCurrentLimit(20)` | ✅ Equivalent |
| Inversion | `angleMotor.setInverted(false)` | `sparkMaxConfig.inverted(true)` | ✅ Value changed (Mk4→Mk4i) |
| Idle mode | `angleMotor.setIdleMode(kBrake)` | `sparkMaxConfig.idleMode(kBrake)` | ✅ Equivalent |
| Position conversion | `integratedAngleEncoder.setPositionConversionFactor(28.125)` | `sparkMaxConfig.encoder.positionConversionFactor(16.822)` | ✅ Values differ (gear ratio change) |
| PID P | `angleController.setP(0.01)` | `sparkMaxConfig.closedLoop.p(0.01)` | ✅ Equivalent |
| PID I | `angleController.setI(0.0)` | `sparkMaxConfig.closedLoop.i(0.0)` | ✅ Equivalent |
| PID D | `angleController.setD(0.0)` | `sparkMaxConfig.closedLoop.d(0.0)` | ✅ Equivalent |
| PID FF | `angleController.setFF(0.0)` | *(commented out)* | ✅ No impact — was 0.0, default is 0.0 |
| Voltage compensation | `angleMotor.enableVoltageCompensation(12.0)` | `sparkMaxConfig.voltageCompensation(12.0)` | ✅ Equivalent |
| Burn to flash | `angleMotor.burnFlash()` | `PersistMode.kPersistParameters` | ✅ Equivalent |
| Reset to absolute | Called after `Timer.delay(1.0)` | Called after `Timer.delay(1.0)` | ✅ Equivalent |

**Result:** All angle motor settings were successfully migrated. No missing settings.

#### Drive Motor Configuration

| Setting | 2024 Code | 2026 Code | Status |
|---------|-----------|-----------|--------|
| Factory reset | `driveMotor.restoreFactoryDefaults()` | `ResetMode.kResetSafeParameters` | ✅ Equivalent |
| CAN bus optimization | `CANSparkUtil.setCANSparkBusUsage(driveMotor, kAll)` | `CANSparkUtil.setSparkBusUsage(sparkFlexConfig, kAll)` | ⚠️ **Wrong signals** — see §9.1 |
| Current limit | `driveMotor.setSmartCurrentLimit(50)` | `sparkFlexConfig.smartCurrentLimit(40)` | ⚠️ Value changed (50→40) |
| Inversion | `driveMotor.setInverted(false)` | `sparkFlexConfig.inverted(false)` | ✅ Equivalent |
| Idle mode | `driveMotor.setIdleMode(kBrake)` | `sparkFlexConfig.idleMode(kBrake)` | ✅ Equivalent |
| Velocity conversion | `driveEncoder.setVelocityConversionFactor(...)` | `sparkFlexConfig.encoder.velocityConversionFactor(...)` | ✅ Values differ (gear ratio change) |
| Position conversion | `driveEncoder.setPositionConversionFactor(...)` | `sparkFlexConfig.encoder.positionConversionFactor(...)` | ✅ Values differ (gear ratio change) |
| PID P | `driveController.setP(0.1)` | `sparkFlexConfig.closedLoop.p(0.1)` | ✅ Equivalent |
| PID I | `driveController.setI(0.0)` | `sparkFlexConfig.closedLoop.i(0.0)` | ✅ Equivalent |
| PID D | `driveController.setD(0.0)` | `sparkFlexConfig.closedLoop.d(0.0)` | ✅ Equivalent |
| PID FF | `driveController.setFF(0.0)` | *(not set)* | ✅ No impact — was 0.0, default is 0.0 |
| Voltage compensation | `driveMotor.enableVoltageCompensation(12.0)` | `sparkFlexConfig.voltageCompensation(12.0)` | ✅ Equivalent |
| Burn to flash | `driveMotor.burnFlash()` | `PersistMode.kPersistParameters` | ✅ Equivalent |
| Reset encoder | `driveEncoder.setPosition(0.0)` | `driveEncoder.setPosition(0.0)` | ✅ Equivalent |

**Result:** All drive motor settings were successfully migrated. The only value change is the current limit (50→40A, covered in §1.6).

#### MISSING from both years (never applied):
- `openLoopRamp` (0.25) — defined in Constants but never called via `setOpenLoopRampRate()` or equivalent config in either year
- `closedLoopRamp` (0.0) — defined in Constants but never applied in either year

### 2.2 optimize() Method — Custom Implementation Replaced

**2024 (line 107–108):**
```java
desiredState = OnboardModuleState.optimize(desiredState, getState().angle);
```
Uses `OnboardModuleState.optimize()` which handles angle scoping via `placeInAppropriate0To360Scope()` and then performs the 90° flip optimization.

**2026 (line 244–267):**
```java
private SwerveModuleState optimize(SwerveModuleState desiredState, Rotation2d currentAngle){
    double difference = desiredState.angle.getDegrees() - currentAngle.getDegrees();
    double turnAmount = Math.IEEEremainder(difference, 360);
    double speed = desiredState.speedMetersPerSecond;
    if (turnAmount > 90){ turnAmount -= 180; speed *= -1; }
    if (turnAmount < -90){ turnAmount += 180; speed *= -1; }
    double direction = currentAngle.getDegrees() + turnAmount;
    return new SwerveModuleState(speed, Rotation2d.fromDegrees(direction));
}
```

**Analysis:** The 2026 `optimize()` is functionally equivalent to `OnboardModuleState.smolOptimize()` from 2024 (which used `Math.IEEEremainder` and the same 90° threshold logic). The 2024 codebase had TWO optimize implementations — `optimize()` (used) and `smolOptimize()` (not used). The 2026 version essentially implements `smolOptimize()`.

Both correctly:
1. Normalize the angle difference to ±180°
2. Flip direction if |turn| > 90°
3. Produce the new target angle relative to the current angle (continuous, not wrapped to 0–360)

**Severity:** Not a bug — functionally equivalent.

> **📝 Note — Why Not Use WPILib's Built-in `SwerveModuleState.optimize()`?**
>
> WPILib provides its own [`optimize()` method](https://github.com/wpilibsuite/allwpilib/blob/main/wpimath/src/main/java/edu/wpi/first/math/kinematics/SwerveModuleState.java) on `SwerveModuleState`. However, **it cannot be used as a drop-in replacement** for the custom implementation without additional changes.
>
> The reason: the angle SparkMax's integrated encoder reads in **continuous degrees** (e.g., 540° after 1.5 rotations), and `angleController.setReference()` expects a target in that same continuous domain. The custom `optimize()` handles this correctly — it computes the target as `currentAngle + turnAmount`, keeping it in the encoder's domain. WPILib's version outputs a `Rotation2d` normalized to [-180°, 180°], which would cause the PID to see huge errors (e.g., encoder at 540°, target at 210° → PID tries to spin 330° the wrong way).
>
> The 2024 codebase had a comment explaining this: *"custom optimize function because built-in doesn't work for some reason"* (`OnboardModuleState.java`, line 16).
>
> To use WPILib's version, you would also need to **enable PID position wrapping** on the angle motor:
> ```java
> sparkMaxConfig.closedLoop.positionWrappingEnabled(true);
> sparkMaxConfig.closedLoop.positionWrappingMinInput(0);
> sparkMaxConfig.closedLoop.positionWrappingMaxInput(360);
> ```
> This tells the SparkMax PID that 0° and 360° are equivalent, so it always computes the shortest path regardless of encoder domain.
>
> WPILib also offers a bonus `cosineScale(currentAngle)` method that scales drive speed by `cos(angleError)` during turns, reducing sideways drift while the module rotates. Neither 2024 nor 2026 uses this.
>
> **Recommendation:** Keep the custom `optimize()` for now — it works correctly and changing it introduces risk during competition season. Switching to WPILib's `optimize()` + PID wrapping + `cosineScale()` would be a good **offseason cleanup** to reduce custom code and gain smoother driving.

### 2.3 Angle Offset Preferences System — NEW in 2026 — CRITICAL

**2024 (line 64):**
```java
angleOffset = moduleConstants.angleOffset;
```
The offset comes directly from the `Rotation2d` constant defined in `Constants.java`.

**2026 (line 78, 84–86):**
```java
this.angleOffsetPreferenceKey = "Swerve/Module" + moduleNumber + "/AngleOffsetDegrees";
double storedOffset = Preferences.getDouble(angleOffsetPreferenceKey, moduleConstants.angleOffset());
angleOffset = Rotation2d.fromDegrees(normalizeDegrees(storedOffset));
```

**What this does:** On startup, the 2026 code checks WPILib's Preferences store (persistent key-value storage on the roboRIO) for a saved angle offset. If a value exists, it **overrides the default from Constants.java**. If no value exists, the default is used.

**Why this is CRITICAL:**
1. If a previous calibration or test session wrote incorrect values to Preferences, they will silently override the correct defaults.
2. The `saveModuleOffsets()` and `saveCanCoderZero()` methods (accessible via controller buttons in RobotContainer) can write to Preferences.
3. If one module has a bad stored offset, **only that module** would be affected — explaining why only one module jitters.
4. The `normalizeDegrees()` function forces offsets into the 0–360° range. If an offset was originally intended to be negative (like 2024's Mod3 at −138°), it gets normalized to the equivalent positive angle (222°), which is mathematically correct but could mask issues if the original value was wrong.

**Real-world confirmation:** The team has reported needing to recalibrate angle offsets multiple times — aligning wheels with a metal rod, reading CANcoder values, and updating `Constants.java`. **This should never be necessary more than once** (the CANcoder is an absolute encoder; its reading for a given physical position is fixed). The fact that the offsets appear to "change" is strong evidence that the Preferences system is the problem:

- The team enters correct values into `Constants.java` and deploys
- On boot, `Preferences.getDouble()` finds a previously stored value and returns *that* instead of the new Constants default
- The robot uses the old/wrong offset, ignoring the updated Constants
- The team sees the alignment is still wrong, assumes the offset "changed," and recalibrates again
- This cycle repeats indefinitely because the stale Preference is never cleared

**Additionally:** The "Save Offsets" button (Start/Menu on the controller) writes offsets to Preferences. If this was ever pressed accidentally during driving or with wheels not perfectly aligned, the bad value would persist across every subsequent reboot and code deploy, silently overriding any Constants.java corrections.

**Severity:** 🔴 **CRITICAL** — Most likely cause of both the drift and single-module jittering issues.

**Recommendation:**
1. **Check Preferences on the roboRIO.** Connect to the robot, open SmartDashboard or Shuffleboard, and check for keys matching `Swerve/Module*/AngleOffsetDegrees`. If any exist, compare them to the defaults in Constants.java.
2. **Delete all stored Preferences** related to swerve offsets and reboot. This forces the code to use the defaults from Constants.
3. **Verify defaults are correct.** With Preferences cleared, point all wheels straight forward, and verify the CANcoder readings minus the default offsets result in approximately 0° for all modules.
4. **Consider removing the Preferences override entirely.** If the team prefers to manage offsets in `Constants.java` (which is simpler and more transparent), change the constructor to always use `moduleConstants.angleOffset()` directly — matching the 2024 behavior. The Preferences system adds complexity without clear benefit unless the team has a deliberate workflow for saving offsets to the roboRIO.

### 2.4 setAngle() Low-Speed Threshold

**2024 (line 135):**
```java
Rotation2d angle = (Math.abs(desiredState.speedMetersPerSecond) <= (Constants.SwerveConstants.maxSpeed * 0.01))
    ? lastAngle : desiredState.angle;
```
Threshold: 5 × 0.01 = **0.05 m/s**

**2026 (line 318):**
```java
Rotation2d angle = (Math.abs(desiredState.speedMetersPerSecond) <= (Constants.SwerveConstants.maxSpeed * 0.01))
    ? lastAngle : desiredState.angle;
```
Threshold: 3 × 0.01 = **0.03 m/s**

**Analysis:** Identical logic. The threshold is slightly lower in 2026 (0.03 vs 0.05 m/s) due to the different maxSpeed. Both are well below any meaningful speed and effectively prevent angle changes when the robot is at rest. The threshold is still present and working correctly — it is **not** the cause of jittering.

**Severity:** MINOR — negligible functional difference.

### 2.5 openLoopRamp and closedLoopRamp — Never Applied

**2024 Constants (line 237–238):**
```java
public static final double openLoopRamp = 0.25;
public static final double closedLoopRamp = 0.0;
```
**2024 SwerveModule:** Neither `setOpenLoopRampRate()` nor `setClosedLoopRampRate()` is called.

**2026 Constants (line 42–43):**
```java
public static final double openLoopRamp = 0.25;
public static final double closedLoopRamp = 0.0;
```
**2026 SwerveModule:** No ramp rate configured in the SparkFlexConfig.

**Analysis:** These constants exist in both years but are **never applied to the motor controllers**. The drive motors have no ramp rate (instant response). The SlewRateLimiter in TeleopSwerve provides acceleration smoothing instead.

**Severity:** MINOR — identical behavior in both years (no ramp applied).

### 2.6 setSpeed() — Closed Loop Slot Parameter

**2024 (line 122–127):**
```java
driveController.setReference(
    desiredState.speedMetersPerSecond,
    ControlType.kVelocity,
    0,  // PID slot 0
    feedforward.calculate(desiredState.speedMetersPerSecond));
```

**2026 (line 295–299):**
```java
driveController.setReference(
    desiredState.speedMetersPerSecond,
    ControlType.kVelocity,
    ClosedLoopSlot.kSlot0,
    feedforward.calculate(desiredState.speedMetersPerSecond));
```

**Analysis:** Pure API migration — `0` → `ClosedLoopSlot.kSlot0`. Functionally identical.

**Severity:** Not a bug.

### 2.7 getCanCoder() — API Migration

**2024 (line 157):**
```java
return Rotation2d.fromRotations(angleEncoder.getAbsolutePosition().getValue());
```

**2026 (line 200):**
```java
return Rotation2d.fromRotations(angleEncoder.getAbsolutePosition().getValue().in(Units.Rotations));
```

**Analysis:** In CTRE Phoenix6 2024, `getValue()` returned a `double` (rotations). In Phoenix6 2026, `getValue()` returns a `Measure<Angle>`, requiring `.in(Units.Rotations)` to extract the double. Functionally identical.

**Severity:** Not a bug.

### 2.8 resetToAbsolute() — Functionally Identical

**2024 (line 150–153):**
```java
private void resetToAbsolute() {
    double absolutePosition = getCanCoder().getDegrees() - angleOffset.getDegrees();
    integratedAngleEncoder.setPosition(absolutePosition);
}
```

**2026 (line 404–407):**
```java
private void resetToAbsolute() {
    double absolutePosition = getCanCoder().getDegrees() - angleOffset.getDegrees();
    integratedAngleEncoder.setPosition(absolutePosition);
}
```

**Analysis:** Identical logic. The only difference is how `angleOffset` is initialized (see §2.3 — Preferences system).

**Severity:** Logic is fine; risk comes from incorrect `angleOffset` values (§2.3).

---

## 3. SwerveSubsystem.java

**Files:** `2024-Season/src/main/java/frc/robot/subsystems/SwerveSubsystem.java` vs `2026-Season/src/main/java/frc/robot/Subsystems/SwerveSubsystem.java`

### 3.1 getYaw() — Different API, Same Result

**2024 (line 161–169):**
```java
public double getYawAsDouble(){
    double yaw = pigeon.getAngle();
    return (SwerveConstants.invertPigeon) ? 360 - yaw : yaw;
}
public Rotation2d getYaw() {
    return Rotation2d.fromDegrees(getYawAsDouble());
}
```
Uses `pigeon.getAngle()` (WPILib Gyro interface, CW-positive, cumulative).  
With `invertPigeon = true`: returns `360 - pigeon.getAngle()` = `360 + pigeon.getYaw()` (effectively CCW-positive).

**2026 (line 179–184):**
```java
public Rotation2d getYaw() {
    return (Constants.SwerveConstants.invertPigeon)
        ? Rotation2d.fromDegrees(360 - pigeon.getYaw().getValueAsDouble())
        : Rotation2d.fromDegrees(pigeon.getYaw().getValueAsDouble());
}
```
Uses `pigeon.getYaw().getValueAsDouble()` (Phoenix6 native, CCW-positive, cumulative).  
With `invertPigeon = false`: returns `pigeon.getYaw()` directly (CCW-positive).

**Analysis:** Both produce CCW-positive yaw values, which is the correct convention for WPILib field coordinates. The combination of `invertPigeon` change + API method change produces identical results. See §1.2 for detailed analysis.

**Severity:** Not a bug.

### 3.2 drive() and driveFromChassisSpeeds() — Functionally Identical

Both years implement the same flow:
1. Convert field-relative inputs to ChassisSpeeds
2. Convert ChassisSpeeds to SwerveModuleState[]
3. Desaturate wheel speeds
4. Set each module's desired state with `isOpenLoop = true` for teleop

The 2026 version adds NetworkTables publishing of desired states for debugging — no functional impact.

**Severity:** Not a bug.

### 3.3 periodic() — Vision/Odometry Differences

**2024:**
- Checks `isOdometryValid()` before updating odometry (skips update if any motor has errors)
- Uses MegaTag1 vision: `LimelightHelpers.getBotPoseEstimate_wpiBlue("limelight-a")`
- Complex fusion: trusts estimates when both cameras see 1 tag, or one camera sees ≥2 tags
- Calls `keepOdometryOnField()` to snap position back if it leaves field bounds

**2026:**
- **Always** updates odometry (no validity check)
- Uses MegaTag2 vision: `LimelightHelpers.getBotPoseEstimate_wpiBlue_MegaTag2("limelight-a")`
- Simpler rejection: rejects if gyro rotating > 720°/s or no tags visible
- Sets vision measurement standard deviations: `VecBuilder.fill(.7, .7, 99999)` (trusts translation, ignores rotation)
- Does NOT call `keepOdometryOnField()`

**Impact on driving straight:** These differences affect pose estimation but do NOT affect teleop driving. The `drive()` method does not use the robot's estimated pose — it only uses the gyro yaw for field-oriented conversion. Vision/odometry issues would only affect auto-align commands and autonomous.

**Severity (teleop):** MINOR — drift/jitter are not caused by vision differences.

**Severity (autonomous):** 🔴 **CRITICAL** — These three removed safeguards directly degrade the pose accuracy that PathPlanner depends on:
1. **`isOdometryValid()` removed:** In 2024, odometry updates were skipped when any motor reported encoder errors, preventing corrupted data from poisoning the pose estimate. In 2026, bad encoder data is always accepted. During a fast autonomous routine, even a single cycle of garbage encoder data can shift the estimated position by centimeters — and PathPlanner will try to correct for a "deviation" that didn't actually happen, causing the robot to veer off course.
2. **`keepOdometryOnField()` removed:** In 2024, if the pose estimate drifted outside the field boundaries (impossible in reality), it was clamped back. This acted as a safety net that prevented runaway pose drift from causing PathPlanner to generate nonsensical paths. Without it, a bad vision measurement or encoder glitch during auto could push the estimated position off-field, and PathPlanner would attempt to drive the robot "back" onto the field — potentially into a wall or other robots.
3. **Different vision fusion logic:** The 2024 code required either both cameras to see a tag simultaneously, or one camera to see ≥2 tags, before trusting vision. The 2026 code trusts any single tag from any single camera (with only gyro-velocity and tag-count-zero rejection). This is more permissive and could accept lower-quality pose estimates during auto, especially when the robot is moving fast and only catches a brief glimpse of one tag. However, MegaTag2 is generally considered more robust than MegaTag1 for single-tag scenarios, so this may be acceptable if the standard deviations (`.7, .7, 99999`) are tuned correctly.

### 3.4 Module Creation — Different Data Source

**2024 (line 63–69):**
```java
new SwerveModule(0, SwerveConstants.Mod0.constants),
new SwerveModule(1, SwerveConstants.Mod1.constants),
new SwerveModule(2, SwerveConstants.Mod2.constants),
new SwerveModule(3, SwerveConstants.Mod3.constants)
```

**2026 (line 69–73):**
```java
mSwerveMods = new SwerveModule[4];
for (int i = 0; i < 4; i++){
    ModuleData data = SwerveConstants.moduleData[i];
    mSwerveMods[i] = new SwerveModule(i, data);
}
```

**Analysis:** Functionally equivalent — just uses an array and loop instead of explicit construction. The module indices match the kinematics ordering in both years.

**Severity:** Not a bug.

---

## 4. TeleopSwerve.java

**Files:** `2024-Season/src/main/java/frc/robot/commands/TeleopSwerve.java` vs `2026-Season/src/main/java/frc/robot/Command/TeleopSwerve.java`

### 4.1 Simplified Command — Auto-Aim Modes Removed

**2024:** TeleopSwerve includes 2024-game-specific auto-aim modes (speaker and amp scoring) using `OnboardModuleState.closestAngle()` and a `ProfiledPIDController`. Takes 7 constructor parameters.

**2026:** TeleopSwerve is a simple manual swerve command with no auto-aim. Takes 6 constructor parameters (the `isAutoAlignSupplier` is accepted but never stored or used — see below).

**Impact:** The auto-aim was 2024-game-specific and doesn't affect basic driving. Not a bug.

### 4.2 Unused Constructor Parameter

**2026 (line 32–46):**
```java
public TeleopSwerve(SwerveSubsystem SwerveSubsystem,
    DoubleSupplier translationSupplier,
    DoubleSupplier strafeSupplier,
    DoubleSupplier rotationSupplier,
    BooleanSupplier robotCentricSupplier,
    BooleanSupplier isAutoAlignSupplier) {  // ← accepted but never stored
    ...
    this.m_robotCentricSupplier = robotCentricSupplier;
    // isAutoAlignSupplier is NOT stored as a field
}
```

The `isAutoAlignSupplier` parameter is declared but never assigned to a field or used. This is dead code but harmless — AutoAlign is a separate command.

**Severity:** MINOR — dead code, no functional impact.

### 4.3 Alliance Inversion Placement

**2024:** Inversion is applied before slew rate limiting:
```java
double xVal = invert * xLimiter.calculate(MathUtil.applyDeadband(...));
```

**2026:** Inversion is applied after slew rate limiting:
```java
double xVal = translationLimiter.calculate(MathUtil.applyDeadband(...));
// ... later in drive() call:
xVal * SwerveConstants.maxSpeed * invert
```

**Impact:** Functionally equivalent for smooth driving. The slew rate limiter operates on the deadbanded value regardless of sign. The inversion (×-1) just flips the direction after limiting. No behavioral difference.

**Severity:** MINOR.

### 4.4 SlewRateLimiter Values

Both years use `SlewRateLimiter(3.0)` for all three axes.

**Severity:** No difference.

---

## 5. RobotContainer.java

**Files:** `2024-Season/src/main/java/frc/robot/RobotContainer.java` vs `2026-Season/src/main/java/frc/robot/RobotContainer.java`

### 5.1 Joystick Input Scaling — 0.5 Multiplier Added — MODERATE

**2024 (line 97–99):**
```java
() -> -getSpeedMultiplier() * driveController.getRawAxis(translationAxis),
() -> -getSpeedMultiplier() * driveController.getRawAxis(strafeAxis),
() -> -driveController.getRawAxis(rotationAxis),
```

**2026 (line 116–120):**
```java
() -> -getSpeedMultiplier() * driveController.getRawAxis(translationAxis) * 0.5,
() -> -getSpeedMultiplier() * driveController.getRawAxis(strafeAxis) * 0.5,
() -> -driveController.getRawAxis(rotationAxis) * 0.5,
```

**Impact:** All joystick inputs are halved in 2026. Combined with `maxSpeed = 3` (vs 5), the effective max teleop speed is **1.5 m/s** (2026) vs **5 m/s** (2024). This is likely a safety precaution during development/testing. It does NOT cause drift or jitter.

Note: `Constants.motorSpeedMultiplier = 0.5` is defined in 2026 but is NOT used anywhere. The `* 0.5` is hardcoded directly in the lambda expressions.

**Severity (teleop):** MODERATE — very slow driving, likely intentional, but **verify this is desired** for competition. If this multiplier is only for testing, remove it before competitions.

**Severity (autonomous):** N/A — this multiplier is applied to joystick inputs only. PathPlanner drives the robot via `closedLoopDrive()` / `driveFromChassisSpeeds()`, which bypasses the joystick lambdas entirely. The 0.5 multiplier has zero effect on autonomous performance.

### 5.2 Controller Bindings

The following bindings are new in 2026 and absent from 2024:

| Binding | Button | Action |
|---------|--------|--------|
| Resync encoders | Back (View) | `m_drive.resyncModuleEncoders()` |
| Save module offsets | Start (Menu) | `m_drive.saveModuleOffsets()` |
| Auto-align left | Left Trigger | `new AutoAlign(m_drive, true)` |
| Auto-align right | Right Trigger | `new AutoAlign(m_drive, false)` |

**Impact:** The `saveModuleOffsets()` binding writes angle offsets to Preferences (§2.3). If accidentally pressed during testing with wheels not properly aligned, it would save incorrect offsets, causing module-specific drift/jitter issues.

**Severity:** MODERATE — these utilities interact with the Preferences system that could cause issues.

---

## 6. Robot.java

**Files:** `2024-Season/src/main/java/frc/robot/Robot.java` vs `2026-Season/src/main/java/frc/robot/Robot.java`

### 6.1 Initialization Pattern

**2024:** Uses `robotInit()` override to create RobotContainer.  
**2026:** Uses constructor `Robot()` to create RobotContainer.

This is a WPILib 2026 version change. Functionally identical.

### 6.2 Removed Periodic Callbacks

**2024:** Has `disabledPeriodic()`, `autonomousPeriodic()`, `teleopPeriodic()` that call RobotContainer methods (for LEDs).  
**2026:** These are empty stubs.

**Impact:** No drive-related code was in these callbacks. Not a bug.

**Severity:** MINOR.

---

## 7. OnboardModuleState.java (2024 Only)

**File:** `2024-Season/src/main/java/frc/lib/OnboardModuleState.java` — no 2026 equivalent

### 7.1 Where Did This Functionality Go?

The 2024 `OnboardModuleState` class provided:
1. `optimize()` — **(the version actually used in 2024)** — angle optimization with `placeInAppropriate0To360Scope()`, a verbose manual implementation that uses while-loops and boundary checks to place the target angle in a 360° window around the current angle
2. `smolOptimize()` — **(written but NOT used in 2024)** — a cleaner reimplementation using `Math.IEEEremainder()` that achieves the same result in far fewer lines
3. `closestAngle()` — find the closest equivalent angle (used by TeleopSwerve auto-aim)
4. `fixedMod()` — modulo that works correctly with negatives

**In 2026:**
- The local `optimize()` method in `SwerveModule.java` (line 244–267) is based on `smolOptimize()`, not the 2024 `optimize()` that was actually used. However, **this is not a problem** — see analysis below.
- `closestAngle()` is not needed (TeleopSwerve no longer has auto-aim).
- `fixedMod()` is not needed.

**The WPILib built-in `SwerveModuleState.optimize()` is NOT used.** The 2026 code uses its own custom implementation. (See §2.2 for a detailed explanation of why the WPILib version cannot be used as a drop-in replacement.)

**Analysis — Are `optimize()` and `smolOptimize()` functionally equivalent?**

Yes. The 2024 `placeInAppropriate0To360Scope()` is a verbose, manual implementation of what `Math.IEEEremainder()` does in a single call. Both compute "the angle closest to the current encoder reading that is rotationally equivalent to the target." Traced through the same inputs:

| Example: current = 540°, target = 45° | `optimize()` (2024 used) | `smolOptimize()` / 2026 `optimize()` |
|---|---|---|
| **Scoping step** | `placeInAppropriate0To360Scope(540, 45)` → 405° | `IEEEremainder(45−540, 360)` = −135, closestAngle = 405° |
| **Delta** | 405 − 540 = −135° | −135° |
| **After 90° flip** | target = 585°, speed reversed | target = 585°, speed reversed |
| **Result** | **Identical** | **Identical** |

After the scoping step, both methods apply the same 90° flip logic and output the target in the same continuous encoder domain (`current + adjusted_error`). The 2024 team likely wrote `smolOptimize` as a planned cleanup of their verbose `optimize` but never switched over. The 2026 students picked the cleaner version, which was the right call — no need to revert to the verbose 2024 `optimize()`.

**Severity:** Not a bug — functionally equivalent.

---

## 8. MeasureWheelDiameter.java and MoveToPose.java (2024 Only)

**Teleop severity:** MINOR — neither command runs during teleop.
**Auto severity:** 🟡 MODERATE — both have direct impact on autonomous accuracy.

### 8.1 MeasureWheelDiameter.java — Wheel Diameter Calibration Tool

This diagnostic command spins the robot in place, compares encoder rotations against the gyro angle, and calculates the **actual** wheel diameter (accounting for wear). The result is displayed on SmartDashboard for the team to update in `Constants.java`.

**Why this matters for auto:** The wheel diameter feeds directly into `driveConversionPositionFactor`, which converts encoder rotations to meters. This conversion is used by **all odometry** — every pose estimate depends on it.

| Parameter | 2024 | 2026 | Difference |
|-----------|------|------|------------|
| `wheelDiameter` | `0.0992` m (≈3.91") — **measured** | `Units.inchesToMeters(4.0)` = `0.1016` m (4.00") — **nominal** | **2.4%** |

The 2024 value (3.91") strongly suggests the team used this tool and found their wheels had worn down from the 4" nominal size. The 2026 code uses the factory nominal value.

**Impact:** A 2.4% error compounds over distance. Over a 5-meter auto path, this produces **~12 cm of odometry drift** — enough to miss a shooting alignment or depot pickup. Over a full auto routine with multiple legs, the cumulative error could exceed 30 cm.

**Recommendation:** Port `MeasureWheelDiameter.java` to 2026 and run it on the current robot. If the wheels are even slightly worn, update `wheelDiameter` in `Constants.java` with the measured value. This is a simple, high-value calibration step.

### 8.2 MoveToPose.java — Closed-Loop Point-to-Point Navigation

This command drives the robot to a target `Pose2d` using three `ProfiledPIDController`s (X, Y, rotation) with `AutoConstants` PID values and trapezoidal motion profiles. It is a **precision autonomous navigation primitive** — exactly the kind of command needed for the planned 2026 auto routine (drive into shooting band, drive to depot, drive back).

Its absence in 2026, combined with the missing `AutoConstants` and `closedLoopDrive()` method (see §13), means the 2026 codebase has **no ready-made way to autonomously drive to a specific field position**. This would need to be rebuilt (or PathPlanner configured) before autonomous routines are possible.

---

## 9. CANSparkUtil.java — CRITICAL BUG

**Files:** `2024-Season/src/main/java/frc/lib/CANSparkUtil.java` vs `2026-Season/src/main/java/frc/lib/CANSparkUtil.java`

### 9.1 Wrong Signal Names in 2026 — CAN Bus Frame Configuration Targets Analog Sensor Instead of Integrated Encoder

This is the most significant code bug found in this comparison.

**2024 (correct):**
```java
// Status0 = Applied output, faults
motor.setPeriodicFramePeriod(CANSparkLowLevel.PeriodicFrame.kStatus0, period);
// Status1 = Motor velocity, bus voltage, temperature
motor.setPeriodicFramePeriod(CANSparkLowLevel.PeriodicFrame.kStatus1, period);
// Status2 = Motor position
motor.setPeriodicFramePeriod(CANSparkLowLevel.PeriodicFrame.kStatus2, period);
// Status3 = Analog sensor data
motor.setPeriodicFramePeriod(CANSparkLowLevel.PeriodicFrame.kStatus3, period);
```

These correctly configure the **integrated encoder** data frame rates (Status1 for velocity, Status2 for position).

**2026 (WRONG signal names):**
```java
// Applied output period (maps to old Status0) — CORRECT
config.signals.appliedOutputPeriodMs(period);
// Analog velocity (maps to old analog sensor, NOT integrated encoder) — WRONG
config.signals.analogVelocityPeriodMs(period);
// Analog position (maps to old analog sensor, NOT integrated encoder) — WRONG
config.signals.analogPositionPeriodMs(period);
// Analog voltage (maps to old Status3 analog sensor) — expected for analog
config.signals.analogVoltagePeriodMs(period);
```

**The problem:** In REVLib 2026, the signals API uses specific method names for each type of sensor data:

| Signal Method | What It Controls | Corresponds to 2024 Frame |
|--------------|-----------------|--------------------------|
| `primaryEncoderPositionPeriodMs()` | **Integrated encoder position** | kStatus2 |
| `primaryEncoderVelocityPeriodMs()` | **Integrated encoder velocity** | kStatus1 (velocity part) |
| `analogPositionPeriodMs()` | External analog sensor position | kStatus3 (analog) |
| `analogVelocityPeriodMs()` | External analog sensor velocity | kStatus3 (analog) |
| `appliedOutputPeriodMs()` | Motor output/faults | kStatus0 |

The 2026 code uses `analogVelocityPeriodMs` and `analogPositionPeriodMs` (external analog sensor) instead of `primaryEncoderVelocityPeriodMs` and `primaryEncoderPositionPeriodMs` (integrated encoder). Since these motors don't have external analog sensors, **these configuration calls have no effect**.

**What this means:**
1. The CAN bus optimization is **completely non-functional** in 2026. All integrated encoder signals remain at their default rates (20ms for the integrated encoder, per REVLib defaults).
2. For the angle motor (configured as `kPositionOnly`), the intended optimization was to set velocity data to 1000ms and position data to 20ms. In 2026, both remain at 20ms — extra CAN bus traffic but functionally OK.
3. For the drive motor (configured as `kAll`), the intended configuration was position and velocity at 20ms. In 2026, they're at the defaults (also 20ms) — no effective difference.

**Impact on driving:** The direct impact is **increased CAN bus traffic** (because the optimization isn't working). In most cases, this won't cause issues. However, if the CAN bus is overloaded (many other devices, frequent status frames), it could cause:
- Delayed motor controller responses
- Stale encoder data
- One module being affected more than others if CAN arbitration delays affect it disproportionately

**Severity (teleop):** 🔴 **CRITICAL (code bug)** — the CAN bus optimization is completely non-functional. While this alone is unlikely to cause the driving-straight issue, it could contribute to jittering if CAN bus congestion is high. **Must be fixed regardless.**

**Severity (autonomous):** 🔴 **CRITICAL (amplified)** — During autonomous, the drive motors run in closed-loop mode (PID + feedforward), which requires accurate, timely encoder velocity data every 20ms cycle. The broken CAN optimization means all encoder signals are at default rates rather than the optimized rates. If CAN bus utilization is high (multiple motors + sensors all at default rates), encoder data could arrive late or be stale, causing the closed-loop PID to overshoot or oscillate. This is worse in auto than teleop because teleop uses open-loop (voltage percentage) control where stale velocity data doesn't affect motor output.

**Fix:**
```java
// Replace in 2026 CANSparkUtil.java:
config.signals.analogVelocityPeriodMs(...)  →  config.signals.primaryEncoderVelocityPeriodMs(...)
config.signals.analogPositionPeriodMs(...)  →  config.signals.primaryEncoderPositionPeriodMs(...)
config.signals.analogVoltagePeriodMs(...)   →  (remove, or keep for true analog sensors)
```

The corrected implementation should be:

```java
if (usage == Usage.kAll) {
    config.signals.primaryEncoderVelocityPeriodMs(20);
    config.signals.primaryEncoderPositionPeriodMs(20);
} else if (usage == Usage.kPositionOnly) {
    config.signals.primaryEncoderVelocityPeriodMs(1000);
    config.signals.primaryEncoderPositionPeriodMs(20);
} else if (usage == Usage.kVelocityOnly) {
    config.signals.primaryEncoderVelocityPeriodMs(20);
    config.signals.primaryEncoderPositionPeriodMs(1000);
} else if (usage == Usage.kMinimal) {
    config.signals.primaryEncoderVelocityPeriodMs(500);
    config.signals.primaryEncoderPositionPeriodMs(500);
}
```

---

## 10. SwerveModuleConstants.java (2024) vs ModuleData record (2026)

**Files:** `2024-Season/src/main/java/frc/lib/SwerveModuleConstants.java` vs `Constants.SwerveConstants.ModuleData` (inner record in 2026 Constants.java)

### 10.1 Field Comparison

| Field | 2024 SwerveModuleConstants | 2026 ModuleData | Type Change? |
|-------|---------------------------|-----------------|-------------|
| driveMotorID | `int driveMotorID` | `int driveMotorID` | No |
| angleMotorID | `int angleMotorID` | `int angleMotorID` | No |
| encoder ID | `int cancoderID` | `int encoderID` | Renamed only |
| angleOffset | `Rotation2d angleOffset` | `double angleOffset` | **YES — Rotation2d → double (degrees)** |
| location | *(not present)* | `Translation2d location` | **Added in 2026** |

**Severity:** MINOR — structural refactoring only. The rename (`cancoderID` → `encoderID`) and the addition of `location` have no behavioral impact. The `angleOffset` type change is analyzed in §10.2 below.

### 10.2 angleOffset Type Change

In 2024, the offset is a `Rotation2d` object. In 2026, it's a raw `double` (degrees). This is handled correctly in `SwerveModule.java`:

- 2024: `angleOffset = moduleConstants.angleOffset;` (already Rotation2d)
- 2026: `angleOffset = Rotation2d.fromDegrees(normalizeDegrees(storedOffset));` (converted from double)

The `normalizeDegrees()` function wraps to 0–360°. Since `Rotation2d.fromDegrees()` handles any input angle, this is functionally equivalent but forces positive offsets.

**Severity:** Not a bug — the type change is handled correctly at the point of use.

### 10.3 Location Field Added

The 2026 `ModuleData` includes a `Translation2d location` field, which stores the physical position of each module. This field is passed to each `ModuleData` but is **never actually used** inside `SwerveModule.java`. The kinematics still uses the positions from `swerveKinematics` (defined separately). This is dead data but harmless.

**Severity:** MINOR.

---

## 11. AutoAlign.java (2026 Only)

**File:** `2026-Season/src/main/java/frc/robot/Command/AutoAlign.java`

### 11.1 Could AutoAlign Interfere with Normal Teleop Driving?

**Analysis:**
- Uses `addRequirements(m_SwerveSubsystem)` → properly requires the swerve subsystem
- Bound to trigger axes in RobotContainer: only runs **while trigger is held**
- When AutoAlign starts, it interrupts TeleopSwerve (default command). When it ends, TeleopSwerve resumes.
- `end()` method calls `driveFromChassisSpeeds(new ChassisSpeeds(), true)` — stops the robot cleanly
- `isFinished()` returns `false` — driver must release trigger to stop

**Conclusion:** AutoAlign **cannot** interfere with normal teleop driving. It only activates when the trigger is explicitly held, and properly releases control when finished.

### 11.2 API Differences from TeleopSwerve

AutoAlign calls `driveFromChassisSpeeds(requestedSpeeds, false)` (closed-loop), while TeleopSwerve drives with open-loop via `drive()` → `driveFromChassisSpeeds(..., true)`. This is correct — autonomous/auto-align should use closed-loop for accuracy.

**Severity:** Not a bug.

---

## 12. LimelightHelpers Usage in SwerveSubsystem

**2024 SwerveSubsystem calls:**
- `LimelightHelpers.getBotPoseEstimate_wpiBlue("limelight-a")`
- `LimelightHelpers.getBotPoseEstimate_wpiBlue("limelight-b")`
- Accesses `.pose`, `.avgTagDist`, `.tagCount`, `.timestampSeconds` on `PoseEstimate`

**2026 SwerveSubsystem calls:**
- `LimelightHelpers.SetRobotOrientation(limelightName, yaw, 0, 0, 0, 0, 0)`
- `LimelightHelpers.getBotPoseEstimate_wpiBlue_MegaTag2(limelightName)`
- Accesses `.pose`, `.tagCount`, `.timestampSeconds` on `PoseEstimate`

**Analysis:** The 2026 code uses MegaTag2 (which requires setting robot orientation first) instead of MegaTag1. The `PoseEstimate` fields accessed are a subset of the 2024 usage (`.avgTagDist` no longer used). The LimelightHelpers v1.13 (2026) supports all these method signatures.

**Impact:** Vision/odometry only — does not affect basic teleop driving, drift, or jitter.

**Severity (teleop):** Not relevant to the reported issues.

**Severity (autonomous):** MODERATE — The switch from MegaTag1 to MegaTag2 changes how vision measurements are generated and trusted. MegaTag2 uses the robot's gyro heading as a prior, which generally improves single-tag accuracy but means the vision estimate is only as good as the gyro data. The 2026 code also no longer checks `avgTagDist` (rejecting tags > 5m away), which means distant, less-accurate tag sightings could be fused into the pose during auto. The standard deviations `(.7, .7, 99999)` tell the estimator to heavily distrust vision rotation (good — the Pigeon is more accurate for heading) but to moderately trust vision translation. These values should be validated during auto testing and may need tuning based on observed accuracy.

---

## 13. Autonomous Mode Assessment — 2024 Infrastructure vs 2026 Gaps

### 13.1 Why This Matters

The drift and jitter issues are frustrating in teleop, but a human driver can compensate. In autonomous mode, the robot relies entirely on odometry and path-following software to navigate. If the drivebase has a persistent directional error (drift) or a module that oscillates (jitter), every path segment accumulates error. The strategy team's planned auto routine — shoot 8 preloaded fuel, drive to the depot for more, return to the shooting band and shoot again — requires precise navigation to multiple field locations within 20 seconds. Even small per-segment errors compound into missed alignment with the hub, failed depot pickups, and wasted time.

The 2024 team achieved a top-tier autonomous routine (one of the best at that year's competition). That was possible because of a **three-layer compensation system** that actively fought against drift and odometry error in real-time. The 2026 codebase is currently missing nearly all of this infrastructure.

### 13.2 How 2024 Autonomous Navigation Worked

The 2024 code did NOT blindly trust PathPlanner. It used three layers of compensation working together every 20ms cycle:

#### Layer 1: Vision-Fused Pose Estimation (continuous during auto)

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

#### Layer 2: PathPlanner Configuration with Active Correction

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

#### Layer 3: Closed-Loop Velocity Control During Auto

PathPlanner drove the robot through `closedLoopDrive()`, which used PID + feedforward motor control:

```java
// 2024 SwerveSubsystem
public void closedLoopDrive(ChassisSpeeds speeds){
    driveFromChassisSpeeds(speeds, false);  // false = closed-loop
}
```

In closed-loop mode, each drive motor uses its onboard PID controller + feedforward to hit the exact commanded velocity. In teleop, the robot uses open-loop (voltage percentage) for driver feel. The closed-loop mode during auto ensures each wheel actually spins at the speed PathPlanner requests, not just "approximately" that speed.

#### The Complete Feedback Loop

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

### 13.3 What 2026 Has and What It's Missing

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

### 13.4 Impact of Current Drivebase Bugs on Autonomous

Even with the full PathPlanner infrastructure ported, the currently identified drivebase issues would severely degrade autonomous performance:

| Issue | Impact on Auto | Why Compensation Can't Fully Fix It |
|-------|---------------|-------------------------------------|
| **Bad angle offsets** (§2.3) | Robot physically drives at an angle to the intended direction | Vision corrects the pose *estimate*, but the correction command is also misdirected because the modules are pointing wrong. PathPlanner would see the error and try to correct, but each correction is itself slightly off. This creates jerky, oscillatory path following. |
| **Module jitter** (§2.3, §1.5) | Jittering module introduces vibration and inconsistent wheel contact | PathPlanner expects smooth module responses. A jittering module provides inconsistent wheel speed data, corrupting the odometry that feeds the pose estimator. Vision can partially compensate, but the robot will follow a wobbly path instead of a smooth one. |
| **CAN signal bug** (§9.1) | Possible stale encoder data during fast maneuvers | PathPlanner commands velocity changes every 20ms. If encoder data is delayed due to CAN congestion, the drive motor PID in closed-loop mode reacts to stale data, causing velocity oscillation. |

**For the planned autonomous routine:** The timing margin is tight — shoot 8 preloads, drive to depot, collect fuel, drive back, and shoot again, all in 20 seconds. Even if PathPlanner successfully compensates for drift, the compensation itself costs time — every correction is distance the robot travels that isn't along the optimal path. A clean drivebase that drives straight without correction saves ~0.2–0.5 seconds per path segment. Over the five segments of the planned routine (start → band, align + shoot, band → depot, depot → band, align + shoot), those savings add up to 1–2.5 seconds — often the difference between completing the depot cycle or running out of time after the first volley.

### 13.5 Recommended Path to a Competitive Autonomous

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

---

## Final Summary

### Part A: Teleop Issues

#### Prioritized Causes: Straight-Line Driving Issue

| Rank | Issue | Severity | Section | Explanation |
|------|-------|----------|---------|-------------|
| **1** | **Stale Preferences overriding angle offsets** | 🔴 CRITICAL | §2.3 | If one or more modules have incorrect stored offsets from a previous calibration, they would point in subtly wrong directions. Even 2–3° of error would cause the robot to pull to one side when driving straight. |
| **2** | **CANSparkUtil configuring wrong signal names** | 🔴 CRITICAL (code bug) | §9.1 | CAN bus optimization is non-functional. Increased CAN traffic could cause delayed encoder updates for some modules, leading to inconsistent module behavior. |
| **3** | **angleInvert changed to `true`** | ⚠️ Verify | §1.3 | If the 2026 robot uses Mk4i modules, `true` is correct. If any module is a Mk4, or if the physical setup differs, this would cause that module's steering to be backwards. |
| **4** | **Default angle offsets in Constants.java may be incorrect** | ⚠️ Verify | §1.5 | The default offsets (31.46°, 49.57°, 33.13°, 8.52°) must match the physical CANcoder positions when wheels are straight. These should be verified with physical testing. |
| **5** | **Pigeon gyro inversion / API change** | ⚠️ Verify | §1.2, §3.1 | The `invertPigeon` change is correct for the API change, but if the Pigeon is mounted differently than expected, field-oriented driving would be wrong. |
| **6** | **drive current limit reduced (50A → 40A)** | MODERATE | §1.6 | Asymmetric current limiting across modules is unlikely, but if one motor hits the limit while others don't, it could cause drift under heavy load. |
| **7** | **0.5 speed multiplier in RobotContainer** | MODERATE | §5.1 | Makes the robot very slow; doesn't cause drift but could mask other issues at low speed that become apparent at higher speeds. |

#### Prioritized Causes: Single-Module Jittering Issue

| Rank | Issue | Severity | Section | Explanation |
|------|-------|----------|---------|-------------|
| **1** | **Stale Preferences overriding angle offset for that module** | 🔴 CRITICAL | §2.3 | **Most likely cause.** If the `saveModuleOffsets()` button was pressed while wheels weren't perfectly straight, one module could have an incorrect stored offset. The PID would constantly fight to correct to the wrong position, causing oscillation/jitter. |
| **2** | **Incorrect default angle offset for that specific module** | 🔴 CRITICAL | §1.5 | Even without Preferences, if the default offset in `moduleData` is wrong for one module, it would jitter as the PID hunts for the wrong zero position. |
| **3** | **CANSparkUtil wrong signal names** | ⚠️ POSSIBLE | §9.1 | If CAN bus congestion causes stale position data for one module's angle encoder, its PID loop could oscillate. This is more likely to affect one module if CAN arbitration timing varies per motor ID. |
| **4** | **angleInvert wrong for that specific module** | ⚠️ Verify | §1.3 | If one physical module has a different internal gear orientation (e.g., it's a Mk4 while the others are Mk4i), the inversion would be wrong for only that module, causing its PID to fight itself. |
| **5** | **Hardware issue** | — | — | Loose encoder connector, damaged CANcoder, or mechanical issue with that one module. This cannot be detected via code comparison but should be checked. |

#### Why Only ONE Module Jitters — Most Likely Explanations

1. **Stored Preferences:** The `saveModuleOffsets()` / `saveCanCoderZero()` functions write offsets per-module. If a calibration was performed with one wheel not properly aligned, only that module's stored offset would be wrong.

2. **Incorrect default offset:** The `moduleData` array has one entry per module. If one entry has a wrong `angleOffset` value (and Preferences don't override it), only that module is affected.

3. **Hardware:** A loose CANcoder connector or intermittent CAN connection on one module would cause position data glitches that only affect that module.

#### Recommended Teleop Debugging Steps

1. **Check and clear WPILib Preferences:**
   - Connect to the roboRIO
   - Look for keys matching `Swerve/Module*/AngleOffsetDegrees`
   - Delete all swerve-related Preferences
   - Reboot the robot

2. **Verify angle offsets:**
   - With cleared Preferences and wheels pointed straight forward
   - Read each module's CANcoder value from SmartDashboard
   - Subtract the default offset from Constants
   - The result should be approximately 0° for all modules
   - If any module is significantly off, update its `angleOffset` in `moduleData`

3. **Fix CANSparkUtil signal names:**
   - Replace `analogVelocityPeriodMs` → `primaryEncoderVelocityPeriodMs`
   - Replace `analogPositionPeriodMs` → `primaryEncoderPositionPeriodMs`
   - Remove `analogVoltagePeriodMs` (or repurpose for actual analog sensors)

4. **Verify angleInvert:**
   - With robot on blocks, command each module to 90°
   - Verify all four rotate the correct direction
   - If one doesn't, check its physical module type (Mk4 vs Mk4i)

5. **Remove the 0.5 speed multiplier** (when ready for faster driving):
   - In RobotContainer, remove the `* 0.5` from the TeleopSwerve lambdas
   - Test at higher speeds to see if drift is more apparent

6. **Verify Pigeon mounting orientation:**
   - Zero the gyro, then physically rotate the robot 90° counterclockwise
   - The reported yaw should increase by ~90°
   - If it decreases, the Pigeon inversion is wrong

7. **Check CAN bus health:**
   - Monitor CAN utilization in the Driver Station
   - Look for CAN errors or timeouts
   - If utilization is high, the CANSparkUtil fix (step 3) becomes more urgent

---

### Part B: Autonomous Issues

#### Prioritized Causes: Autonomous Navigation Readiness

| Rank | Issue | Severity | Section | Explanation |
|------|-------|----------|---------|-------------|
| **1** | **PathPlanner not configured — no auto routines exist** | 🔴 CRITICAL | §13.3 | No `AutoBuilder` setup, no path-following PID constants, no `closedLoopDrive()` method. `getAutonomousCommand()` returns a print statement. The robot literally cannot run any autonomous routine. |
| **2** | **`isOdometryValid()` check removed** | 🔴 CRITICAL | §3.3 | In 2024, odometry updates were skipped when encoders reported errors. In 2026, bad encoder data is always accepted into the pose estimate. During fast auto maneuvers, a single corrupt reading can shift the position by centimeters, causing PathPlanner to "correct" for a deviation that never happened. |
| **3** | **`keepOdometryOnField()` removed** | 🔴 CRITICAL | §3.3 | In 2024, if the pose estimate drifted outside field boundaries (e.g., from a bad vision measurement), it was clamped back. Without this safety net, a runaway estimate could cause PathPlanner to generate paths that drive the robot into walls or across the field. |
| **4** | **CAN signal bug — amplified impact in auto** | 🔴 CRITICAL | §9.1 | Auto uses closed-loop motor control (PID + feedforward), which requires accurate encoder velocity data every 20ms. The broken CAN optimization means encoder signals may be stale, causing the drive motor PID to overshoot or oscillate. This is worse in auto than teleop, which uses open-loop (voltage) control. |
| **5** | **`maxSpeed` reduced to 3 m/s** | MODERATE | §1.8 | PathPlanner uses `maxSpeed` as the module speed cap. At 3 m/s (vs. 2024's 5 m/s), the robot is 40% slower during auto. Complex autonomous routines requiring field traversal may not complete within the 20-second autonomous period at this reduced speed. |
| **6** | **Vision fusion trust model changed (MegaTag1 → MegaTag2)** | MODERATE | §3.3, §12 | The 2026 code uses MegaTag2 with simpler rejection logic (no `avgTagDist` filter, no multi-camera triangulation requirement). This may accept lower-quality pose estimates during fast auto maneuvers. Standard deviations (`.7, .7, 99999`) need validation. |
| **7** | **Missing `closedLoopDrive()` and `getRobotRelativeSpeed()` methods** | 🔴 CRITICAL | §13.3 | These are required by PathPlanner's `AutoBuilder` to command the robot during auto. Without them, PathPlanner cannot be configured. |

#### Why This Matters for Competition

The 2024 team achieved one of the best autonomous routines at that year's competition. The 2026 strategy team's planned auto routine is similarly demanding — it requires the robot to:
- Drive into the shooting band and align with the hub
- Shoot 8 preloaded fuel
- Navigate to the depot and collect more fuel
- Drive back into the shooting band, re-align, and shoot again
- Complete all of this within the 20-second autonomous period

This was possible because the 2024 code had a continuous feedback loop: vision-corrected pose → PathPlanner deviation detection → on-the-fly replanning → closed-loop motor execution → repeat every 20ms. The robot was never more than one or two cycles behind on correcting for any drift.

Without this feedback loop, any autonomous routine will accumulate uncorrected errors. Even the teleop drivebase bugs (which a human driver can compensate for) become fatal in auto — a module that drifts 2° per second will be 40° off by the end of the 20-second autonomous period.

#### Recommended Autonomous Development Steps

*Prerequisites: Complete all teleop fixes (Part A) first. The robot must drive straight with no jitter before auto development begins.*

1. **Restore pose estimation safety nets and calibration:**
   - Re-add `isOdometryValid()` to skip odometry updates on encoder errors
   - Re-add `keepOdometryOnField()` to clamp pose to field boundaries
   - Fix CAN signal names in `CANSparkUtil.java` (shared with teleop fix)
   - **Measure actual wheel diameter** and update `wheelDiameter` in `Constants.java` — the 2024 team found theirs was 3.91" (not the 4.00" factory spec), a 2.4% error that causes ~12 cm odometry drift per 5 m of travel (see §8.1)

2. **Port PathPlanner infrastructure from 2024:**
   - Verify PathPlanner is in `vendordeps/`
   - Create `AutoConstants` with path-following PID constants (start with 2024 values: P=5.0 translation, P=5.0 rotation)
   - Add `closedLoopDrive()` and `getRobotRelativeSpeed()` to `SwerveSubsystem`
   - Add `configPathPlanner()` to `SwerveSubsystem` — use 2026 PathPlanner API (method names changed from 2024)
   - Add `followPathFromFile()` and `startAutoAt()` convenience methods

3. **Build auto routines incrementally:**
   - Phase 1 only: drive into shooting band, align with hub, shoot 8 preloaded fuel → validates path following and hub alignment
   - Add Phase 2: after shooting, drive to depot and collect fuel → validates multi-segment navigation
   - Add Phase 3: drive back to shooting band, re-align, shoot depot fuel → completes the full planned routine
   - Use AdvantageScope to compare desired vs. actual path in real-time

4. **Optimize for competition speed:**
   - Increase `maxSpeed` from 3 m/s toward 5 m/s once path following is accurate
   - Tune path constraints per segment
   - Validate vision standard deviations under match conditions
   - See §13.5 for the full phased roadmap

---

## 14. Recommendations for Using AI Tools

*For team leads and mentors — practical guidance on leveraging AI to accelerate the fixes and porting work identified in this report, without sacrificing understanding.*

### 14.1 The Problem: Time Pressure vs. Code Complexity

The autonomous infrastructure that needs to be ported from 2024 (PathPlanner configuration, closed-loop drive, odometry safety checks) spans multiple files and involves API migrations where the 2024 method names no longer exist in the 2026 libraries. Doing this work entirely by hand — reading WPILib/REVLib/PathPlanner changelogs, finding the renamed methods, rewriting code — is doable but time-consuming. With competition approaching, the team may not have enough sessions to complete all of this manually.

At the same time, blindly asking AI to "write me an autonomous system" and deploying the output without review ("vibe coding") is dangerous on a physical robot. Incorrect motor configurations can damage hardware, and untested path-following code can send the robot into walls.

### 14.2 The Recommended Approach: AI as a Migration Accelerator

The sweet spot for this team's situation is using AI as a **code migration assistant** — somewhere between "better Google" and "vibe coding." The key principle: **the team should always understand what the code does before deploying it, but AI can dramatically speed up the process of getting from a 2024 reference to a working 2026 draft.**

Here's how this works in practice:

#### Level 1: AI as Explainer (Low Risk, Immediate Value)

Use AI to understand the 2024 code that needs to be ported. This is the "better Google" end of the spectrum, but far more effective than Google for understanding specific code.

**Examples tied to this report:**
- *"Explain what the `configPathPlanner()` method in our 2024 `SwerveSubsystem.java` does. What is each parameter for?"*
- *"What does `isOdometryValid()` check for? Why would encoder data ever be invalid?"*
- *"What's the difference between open-loop and closed-loop driving in our `driveFromChassisSpeeds()` method?"*

**Why this is safe:** No code is generated. The team builds understanding that makes them better at writing and reviewing code. This is the single highest-value use of AI for a student team.

#### Level 2: AI as API Translator (Medium Risk, High Value)

Give AI the 2024 code and ask it to produce the 2026 equivalent, **with the specific library versions identified.** This is where the biggest time savings are.

**Examples tied to this report:**
- *"Here is our 2024 `CANSparkUtil.java` that uses `CANSparkBase.setPeriodicFramePeriod()`. Rewrite it for REVLib 2025.1 using `SparkBaseConfig.signals`. Make sure to use `primaryEncoderPositionPeriodMs` and `primaryEncoderVelocityPeriodMs` for the integrated encoder, NOT the analog sensor methods."*
- *"Here is our 2024 `configPathPlanner()` method that uses `AutoBuilder.configureHolonomic()`. The 2026 PathPlanner API renamed this method. Rewrite it for PathPlannerLib 2026.x."*
- *"Here is our 2024 `closedLoopDrive()` method. Port it to work with the 2026 `SwerveSubsystem` class. Here is the current 2026 `SwerveSubsystem.java` for context: [paste file]."*

**Critical rule:** Always provide the 2024 code as context. AI is much more accurate when translating known-working code than when generating from scratch. The 2024 codebase is a massive advantage — use it.

**Review checklist before deploying AI-translated code:**
1. Does every method call exist in the 2026 library? (Check imports — if the IDE shows red, something's wrong.)
2. Do the parameter types and order match? (Especially for PID constructors — P, I, D order varies between libraries.)
3. Are motor/sensor IDs correct? (AI doesn't know your CAN bus wiring.)
4. Does the logic match the 2024 version? (Read the AI output side-by-side with the 2024 original.)

#### Level 3: AI as Code Reviewer (Medium Risk, High Value)

After writing or porting code, ask AI to review it — especially for the types of migration bugs found in this report.

**Examples tied to this report:**
- *"Here is our 2026 `CANSparkUtil.java`. Compare it to the 2024 version. Are we configuring the correct CAN signals for integrated encoders?"*
- *"Here is our 2026 `SwerveModule.java` constructor. Does every motor configuration setting from the 2024 imperative API have an equivalent in the 2026 declarative API? List any settings that are missing."*
- *"Review our 2026 `SwerveSubsystem.periodic()` method. Compare it to the 2024 version. Are there any safety checks or odometry guards that were removed?"*

**Why this is valuable:** The CANSparkUtil signal-name bug (§9.1) — the single most insidious bug in this report — would likely have been caught by an AI code review comparing the 2024 and 2026 versions side-by-side. These are exactly the kinds of subtle API mapping errors that humans miss and AI catches.

#### Level 4: AI for Debugging and Diagnostics (Medium Risk, Situational)

When testing on the physical robot, use AI to help diagnose unexpected behavior.

**Examples:**
- *"Our swerve module 2 jitters back and forth by about 3 degrees when the robot is stationary. The other three modules are fine. Here is the SwerveModule code and the Constants for module 2. What could cause only one module to behave differently?"*
- *"Our robot drifts to the left when we command it to drive straight forward. Here are our module angle offsets and kinematics configuration. What should we check?"*
- *"PathPlanner shows the robot deviating from the path by 30cm on turns. Here are our path-following PID constants and the AdvantageScope log. What should we tune?"*

### 14.3 What NOT to Do

| ❌ Don't | ✅ Do Instead |
|----------|--------------|
| "Write me a complete swerve drive subsystem" | "Port this specific method from 2024 to the 2026 API" |
| Accept AI code without reading it | Read AI output side-by-side with the 2024 original |
| Deploy AI-generated code directly to competition | Test every change on the physical robot before competition |
| Ask AI to pick PID constants | Use the 2024 values as a starting point and tune on the robot |
| Trust AI to know your CAN IDs or motor wiring | Always verify hardware-specific values against the physical robot |
| Use AI-generated code you can't explain to a teammate | If you can't explain it, you don't understand it — ask AI to explain it first |

### 14.4 Suggested Workflow for Porting Auto Infrastructure

For the specific task of porting the autonomous system from 2024 to 2026, here's a practical workflow that balances speed with understanding:

1. **Understand first (Level 1):** Have each team member working on auto read the relevant 2024 code and ask AI to explain any parts they don't understand. Target: every team member can explain the three-layer correction system (vision-fused odometry → PathPlanner → closed-loop motors) in their own words.

2. **Draft the port (Level 2):** Give AI the 2024 file and the current 2026 file. Ask it to produce the 2026 equivalent of each missing method, one method at a time. Don't ask for the entire file at once — work method by method so each piece can be reviewed and understood.

3. **Aggressive code review (Level 3) — the critical step:** Don't just skim the AI output. Integrate it into the project, then conduct a **genuinely adversarial code review** where team members challenge each other to explain the code. This is the step that turns AI-generated code into *the team's* code. Specific practices:

   - **The "explain every line" rule:** The person integrating the code must be able to explain what each line does *and why it's there* to a teammate. If they can't explain it, they don't understand it — and they should ask AI to explain that specific part before proceeding.
   - **Add comments that prove understanding:** After review, the team should add inline comments in their own words explaining the logic. Not redundant comments like `// set PID` — substantive comments like `// Skip this odometry update if any encoder reports a position jump > 1m in a single cycle, which indicates a sensor glitch rather than real movement`. If you can't write a meaningful comment, you don't understand the code well enough.
   - **Cross-reference against 2024:** For every method ported, a reviewer should have the 2024 original open side-by-side and verify that the behavior is preserved. Use the §14.2 checklist: do all method calls exist? Are parameter types correct? Are hardware-specific values right?
   - **The "CANSparkUtil test":** As a gut check, ask: *"Could the type of bug found in CANSparkUtil (§9.1) — where a method name looks plausible but targets the wrong hardware signal — be hiding in this code?"* This is the exact class of bug that slips past casual review but gets caught by deliberate scrutiny.

   > **Why this approach works better than "type it yourself":** Transcribing code by hand under time pressure introduces typos that waste hours to debug and teach nothing about robotics. In contrast, explaining code to a teammate and writing substantive comments tests understanding at a deeper level — you can type code on autopilot, but you can't explain code you don't understand. With competition approaching, the team's scarce practice time should be spent on understanding and testing, not on retyping.

4. **Test incrementally:** Deploy to the robot after each method is added. Don't port everything and test at the end — one method at a time, verify it compiles, verify it doesn't break teleop, then move to the next.

### 14.5 A Note on AI Limitations for FRC

AI tools have specific weaknesses in the FRC context that the team should be aware of:

- **Library version knowledge may be stale.** REVLib 2025/2026 and PathPlannerLib 2026 are relatively new. AI may suggest deprecated method names or pre-2026 APIs. Always verify that suggested method names actually exist by checking imports and IDE autocomplete.
- **AI doesn't know your hardware.** It can't know which CAN ID is wired to which motor, which direction your Pigeon is mounted, or whether your modules are Mk4 or Mk4i. Never trust AI for hardware-specific values.
- **AI is best when given context.** The more of your actual code you provide as context, the better the output. "Port this method" with the actual code attached is far more useful than "write me a PathPlanner config."
- **AI excels at exactly the type of bugs in this report.** API migration errors (wrong signal names, missing configuration, renamed methods) are pattern-matching tasks where AI is often better than humans. Use it for code review after every migration change.
