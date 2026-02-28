# Swerve Drive Fault Isolation Methodology

## Philosophy

The key principle is **isolation**: systematically eliminate variables until you've narrowed the problem to a single root cause. You have two symptoms — treat them as potentially related but investigate independently. At each step, you're asking: *"Does this test eliminate a category of causes?"*

The methodology has three phases:
1. **Observe & Instrument** — gather data before changing anything
2. **Isolate** — eliminate categories of causes using targeted tests
3. **Confirm** — verify the root cause by fixing it and demonstrating the symptom disappears

### One Rule Above All

> **Change ONE variable at a time, test, and record the result before changing the next variable.**

If you change PID values AND angle offsets AND swap a motor at the same time and the problem goes away, you have no idea which change fixed it — and you've learned nothing.

---

## Problem Statements

1. **Primary Issue**: The robot fails to drive straight — it drifts/pulls to one side when attempting to drive straight forward
2. **Secondary Issue**: One specific angle motor (only one of the four modules) jitters back and forth slightly when the drive base is not being commanded to move (robot at rest)

## Possible Root Cause Categories

| Category | Likelihood | Examples |
|---|---|---|
| Our code (especially the 2024→2026 REVLib migration) | **HIGH** | Dropped motor config settings, wrong angleOffset, bad conversion factors, PID values, missing/incorrect inversion flags |
| Electronics (wiring, misconfiguration, hardware failure) | MODERATE | Loose CAN bus connection, bad motor controller, encoder failure, wiring swap |
| Mechanical | MODERATE | Uneven wheel wear, binding module, bent frame, weight distribution |
| Vendor code (REVLib, Phoenix6) | LOW | API behavior change between versions, default value change |
| WPILib code | LOW | Change in `SwerveModuleState.optimize()`, kinematics math |

---

## Phase 1: Observe & Instrument (Do This First, Change Nothing)

Before touching any code or hardware, collect baseline data. The codebase already publishes useful telemetry to SmartDashboard and NetworkTables — use it.

### Test 1.1: Record Baseline Telemetry (At Rest)

**Goal**: Characterize the jittering module's behavior compared to the other three.

**Procedure**:
1. Power on the robot, connect to SmartDashboard/AdvantageScope
2. **Do NOT touch the joysticks** — let the robot sit idle with the robot **enabled** in teleop mode
3. Record for 30+ seconds and capture for ALL four modules:
   - `Mod X Cancoder` (absolute encoder angle)
   - `Mod X Integrated` (integrated encoder angle)
   - `Mod X Velocity` (drive velocity — should be ~0)
4. Identify which module is jittering. Note its module number (0–3).

**What you're looking for**:

| Observation | Suggests |
|---|---|
| Integrated encoder oscillates but Cancoder is stable | The PID is hunting — likely a **code/tuning** issue |
| Both encoders oscillate together | The wheel is **physically moving** — could be mechanical or electrical |
| Cancoder and Integrated disagree significantly | Bad `angleOffset` calibration or failed `resetToAbsolute()` |
| Drive velocity is non-zero at rest | Something is commanding movement — check TeleopSwerve deadband or default command |

**Record these values in the Data Recording Template at the bottom of this document.**

### Test 1.2: Record Baseline Telemetry (Driving Straight)

**Goal**: Characterize the drift direction and magnitude.

**Procedure**:
1. Place the robot on a flat, open surface (competition carpet if possible)
2. Zero the gyro
3. Push the forward-only joystick axis gently (~30% power) and hold straight for 3–5 seconds
4. Record:
   - Pigeon Yaw (did the heading change? Which direction?)
   - All four module angles (are all four pointing the same direction?)
   - All four module velocities (are they all the same speed?)
   - Desired Swerve States vs actual Swerve States (are they matching?)

**What you're looking for**:

| Observation | Suggests |
|---|---|
| One module angle is offset from the others | Bad `angleOffset`, bad `resetToAbsolute()`, or bad CANcoder |
| One module velocity differs significantly from others | Mechanical (wheel friction), electrical (wiring), or motor issue |
| All modules match but robot still drifts | Gyro issue (field-oriented mode using bad heading), or mechanical (weight distribution, floor friction) |
| Desired states ≠ Actual states for one module | That module's control loop isn't tracking — PID tuning, encoder, or motor issue |

### Test 1.3: Record Startup Calibration Values

**Goal**: Verify that `resetToAbsolute()` is correctly calibrating each module at startup.

**Procedure**:
1. Physically point all four wheels straight forward (use a straight-edge against the frame)
2. Power cycle the robot
3. Immediately after boot, before enabling, record for each module:
   - `Mod X Cancoder` value
   - `Mod X Integrated` value
   - The `angleOffset` from Constants.java (or Preferences if overridden)
4. Verify the math: `Integrated` should equal `Cancoder − angleOffset`

**What you're looking for**:

| Observation | Suggests |
|---|---|
| Math checks out for all modules | `resetToAbsolute()` is working correctly |
| One module has wrong Integrated value | CAN bus timing issue — CANcoder may not have sent data before `resetToAbsolute()` ran |
| Values change between power cycles | CANcoder update frequency too low (currently 1 Hz) — module may read stale/zero data |

---

## Phase 2: Isolate (Systematic Fault Tree)

Use the telemetry data from Phase 1 to decide which branch of the fault tree to follow. The tree below is structured as a series of **binary isolation tests** — each test eliminates one category.

### Fault Tree: Robot Drifts When Driving Straight

```
Robot Drifts When Driving Straight
├── A. Are all 4 module ANGLES correct when commanding "straight"?
│   ├── NO → Go to Branch A (Angle Problem)
│   └── YES ↓
├── B. Are all 4 module SPEEDS equal when commanding "straight"?
│   ├── NO → Go to Branch B (Speed Problem)
│   └── YES ↓
├── C. Does it drift in BOTH robot-centric AND field-oriented modes?
│   ├── Only field-oriented → Go to Branch C (Gyro/Heading Problem)
│   └── Both → Go to Branch D (Mechanical/Physical Problem)
```

### Fault Tree: One Module Jitters at Rest

```
One Module Jitters at Rest
├── E. Does it jitter with the robot code DISABLED (just powered on)?
│   ├── YES → Electrical/mechanical issue (not code)
│   └── NO ↓
├── F. Does it jitter if you set angleKP to 0 (disable angle PID)?
│   ├── NO → PID is causing it → Go to Branch F (PID/Tuning)
│   └── YES → Something else is commanding movement → Go to Branch G
├── H. Does the SAME physical module jitter if you SWAP module numbers?
│   ├── Jitter follows the PHYSICAL module → Hardware issue with that module
│   └── Jitter follows the MODULE NUMBER → Software/config issue for that slot
```

---

### Branch A: One or More Module Angles Are Wrong

#### Test A1: Verify Angle Offsets

**Procedure**:
1. Physically align all four wheels to point **perfectly straight forward** (use a straight-edge)
2. Read the CANcoder values for each module from SmartDashboard
3. Compare to the `angleOffset` values in `Constants.java`:

| Module | CAN IDs (drive, angle, encoder) | angleOffset (Constants.java) | CANcoder Reading (wheels straight) | Match? |
|---|---|---|---|---|
| 0 - Front Left | 6, 5, 7 | 31.46° | ___ | ___ |
| 1 - Front Right | 9, 8, 10 | 49.57° | ___ | ___ |
| 2 - Back Right | 12, 11, 13 | 33.13° | ___ | ___ |
| 3 - Back Left | 15, 14, 16 | 8.52° | ___ | ___ |

4. **The CANcoder reading when wheels are straight should equal the `angleOffset`**. If they don't match, you've found your problem.

**CRITICAL CHECK — Preferences Override**: The code reads `angleOffset` from `Preferences` first (stored on the roboRIO, survives code deploys). If someone previously ran `saveModuleOffsets()` or manually set a Preference, it will **silently override** the values in `Constants.java`:

```java
// From SwerveModule.java constructor:
double storedOffset =
    Preferences.getDouble(angleOffsetPreferenceKey, moduleConstants.angleOffset());
angleOffset = Rotation2d.fromDegrees(normalizeDegrees(storedOffset));
```

To check: Open the Preferences viewer in SmartDashboard/Shuffleboard and look for keys like `Swerve/Module0/AngleOffsetDegrees` through `Swerve/Module3/AngleOffsetDegrees`. If they exist, those values are being used instead of Constants.java.

**Fix**: Either delete the Preferences entries to use Constants.java defaults, or use `saveModuleOffsets()` with all wheels physically pointed straight to write correct values.

#### Test A2: Verify `resetToAbsolute()` Succeeds at Startup

The CANcoder is configured to update at only 1 Hz. There is a 1-second `Timer.delay()` before `resetToAbsolute()` runs. If the CANcoder hasn't sent its first reading within that window, the integrated encoder will be calibrated to a stale or zero value.

**Procedure**:
1. Power cycle the robot
2. Immediately check `Mod X Integrated` vs `Mod X Cancoder` for each module
3. Power cycle again and re-check — are the values consistent?

If values are inconsistent between power cycles, the 1-second delay may not be enough. Try temporarily increasing the CANcoder update frequency (e.g., to 10 Hz) or increasing the delay to 2 seconds.

#### Test A3: Verify Module Location / Kinematics Consistency

Your kinematics setup and ModuleData locations use **swapped variable names** for X and Y:

```java
// swerveKinematics uses (halfTrackWidth, halfWheelBase):
new Translation2d(halfTrackWidth, halfWheelBase),   // Front left

// But FRONT_LEFT (used in ModuleData) uses (halfWheelBase, halfTrackWidth):
public static final Translation2d FRONT_LEFT = new Translation2d(halfWheelBase, halfTrackWidth);
```

In WPILib's coordinate system: `Translation2d(x, y)` where +X = forward, +Y = left. The first argument should be the **forward/backward** distance (halfWheelBase) and the second should be the **left/right** distance (halfTrackWidth).

Since `halfTrackWidth` and `halfWheelBase` are **both the same value** (27/2 inches), this doesn't cause a numerical bug today. But the `swerveKinematics` definition has them **backwards** compared to the `ModuleData` locations — and the kinematics is what actually controls the wheel calculations. Verify which is correct for your physical robot.

#### Test A4: Verify Module Ordering

Confirm that the module order in `swerveKinematics` matches the module order in `moduleData`:

| Index | swerveKinematics position | moduleData label | Physical location |
|---|---|---|---|
| 0 | (+X, +Y) = Front Left | Front Left | ✓ or ✗? |
| 1 | (+X, -Y) = Front Right | Front Right | ✓ or ✗? |
| 2 | (-X, -Y) = Back Right | Back Right | ✓ or ✗? |
| 3 | (-X, +Y) = Back Left | Back Left | ✓ or ✗? |

If the CAN IDs in `moduleData` don't match the physical module at the kinematics position, the robot will drive incorrectly.

---

### Branch B: One or More Module Speeds Are Wrong

#### Test B1: Free-Spin Speed Test

**Procedure**:
1. Lift the robot so all wheels are off the ground
2. Command straight forward at ~50% speed
3. Visually observe: Are all four wheels spinning at the same speed and same direction?
4. Check telemetry: Are all four velocity readings similar?

| Observation | Suggests |
|---|---|
| One wheel spins backwards | `driveInvert` is wrong for that module, or motor phase wiring is swapped |
| One wheel spins noticeably slower | Mechanical drag, motor issue, or different gear ratio on that module |
| Speeds match on the bench but not on the ground | Mechanical issue (wheel diameter difference, tire wear, weight distribution) |

#### Test B2: Open Loop vs Closed Loop

Your `drive()` method currently always uses **open loop** (percent output):

```java
// In SwerveSubsystem.drive():
driveFromChassisSpeeds(desiredSpeeds, true);  // true = open loop
```

In open loop mode, motor variations, friction differences, and battery voltage sag cause speed differences between modules — this is a **known source of drift** for swerve drives.

**Procedure**:
1. Temporarily change `true` to `false` in the `drive()` method to use closed-loop velocity control
2. Drive straight and observe — does drift improve?

| Result | Conclusion |
|---|---|
| Drift significantly improves | The drift was caused by motor/friction variation. Open loop can't compensate. Consider using closed-loop for competition. |
| Drift doesn't change | The root cause is elsewhere (angle error, kinematics, gyro, mechanical) |

**Note**: Closed-loop control requires properly tuned `driveKP/KI/KD` and feedforward values (`driveKS`, `driveKV`, `driveKA`). The current values are marked `//to calculate` and `//to tune`, so they may need characterization first.

---

### Branch C: Gyro / Heading Problem (Field-Oriented Only)

#### Test C1: Robot-Centric vs Field-Oriented

This is a **quick binary test** — do it early.

**Procedure**:
1. Switch to robot-centric mode (hold the robot-centric button)
2. Drive straight forward
3. **Does it still drift?**

| Result | Conclusion |
|---|---|
| **Still drifts** | The gyro is NOT the problem. Go to Branches A, B, or D. |
| **Drift disappears** | The gyro heading is wrong, causing field-oriented math to rotate the chassis speed vector. Go to Test C2. |

#### Test C2: Gyro Drift Check

**Procedure**:
1. Place the robot still on a flat surface, enable and zero the gyro
2. Watch `Pigeon Yaw` on SmartDashboard for 60 seconds without touching the robot
3. Note any drift in the yaw reading

| Result | Conclusion |
|---|---|
| Yaw stays stable (< 0.5° drift in 60s) | Pigeon is fine — heading problem is likely from initialization (`zeroGyro()` alliance logic) |
| Yaw drifts noticeably | Pigeon may be faulty, poorly mounted (vibrations), or needs recalibration |

#### Test C3: Verify Gyro Zero/Alliance Logic

Check the `zeroGyro()` method — it sets yaw to 180° for Red alliance:

```java
public void zeroGyro() {
    if (FieldConstants.isRedAlliance()){
      pigeon.setYaw(180);
    } else {
      pigeon.setYaw(0);
    }
}
```

If the alliance isn't set correctly (e.g., in practice mode with no FMS), the gyro could initialize to the wrong heading. Verify what `DriverStation.getAlliance()` returns during your testing.

---

### Branch D: Mechanical / Physical

#### Test D1: Wheel Inspection

Inspect all four modules for:

| Check | Module 0 (FL) | Module 1 (FR) | Module 2 (BR) | Module 3 (BL) |
|---|---|---|---|---|
| Same tire type? | | | | |
| Even tire wear? | | | | |
| Same wheel diameter (calipers)? | | | | |
| Wheel makes ground contact? | | | | |
| Spins freely by hand (no binding)? | | | | |
| Module rotates freely (no binding)? | | | | |
| All bolts tight? | | | | |

#### Test D2: Physical Module Swap Test

This is the **definitive test** to separate hardware from software for module-specific issues.

**Procedure**:
1. Pick the jittering module and one healthy module
2. Physically swap their positions on the robot
3. Update the CAN IDs in `Constants.java` `moduleData` to match the new physical positions
4. Deploy and test

| Result | Conclusion |
|---|---|
| Jitter follows the **physical module** to its new position | **Hardware issue** with that module (motor, encoder, wiring, mechanical) |
| Jitter stays at the **original position** (now with a different physical module) | **Software/config issue** for that module slot (angleOffset, CAN ID, Preferences entry) |

---

### Branch E: Jitter with Robot Disabled

#### Test E1: Disabled Jitter Check

**Procedure**:
1. Power on the robot but do NOT enable
2. Watch the suspected jittering module — does it jitter?

| Result | Conclusion |
|---|---|
| Jitters while disabled | NOT a code issue. The motor controller is doing something on its own — check for electrical noise, bad wiring, or a faulty motor controller. |
| Only jitters when enabled | Code is causing the jitter. Continue to Branches F and G. |

---

### Branch F: PID / Tuning Causing Jitter

#### Test F1: Disable Angle PID

**Procedure**:
1. Temporarily set `angleKP = 0.0` in Constants.java (disables angle position control)
2. Deploy and enable — does the module still jitter?

| Result | Conclusion |
|---|---|
| Jitter stops | The PID is causing oscillation. Either `angleKP` is too high for that module, or the angle setpoint is constantly changing. |
| Jitter continues | Something else is commanding the motor. Check for competing commands or direct motor `.set()` calls. |

**Note**: With `angleKP = 0`, the modules won't hold their angle — only use this as a diagnostic test, not during driving.

#### Test F2: Log Angle Error for the Jittering Module

Add temporary logging to see what the PID is doing:

```java
// Temporary debug logging in SwerveModule.setAngle():
private void setAngle(SwerveModuleState desiredState){
    Rotation2d angle = (Math.abs(desiredState.speedMetersPerSecond) <= (Constants.SwerveConstants.maxSpeed * 0.01))
        ? lastAngle : desiredState.angle;
    
    // TEMPORARY DEBUG: Log angle error for this module
    double error = angle.getDegrees() - getAngle().getDegrees();
    SmartDashboard.putNumber("Mod " + moduleNumber + " Angle Error", error);
    SmartDashboard.putNumber("Mod " + moduleNumber + " Angle Target", angle.getDegrees());
    
    angleController.setReference(angle.getDegrees(), ControlType.kPosition);
    lastAngle = angle;
}
```

**What you're looking for**:

| Observation | Suggests |
|---|---|
| Error oscillates rapidly around zero (e.g., +0.5° / -0.5°) | Classic PID oscillation — `angleKP` is too aggressive, or there's mechanical backlash |
| Error is consistently non-zero (e.g., always +3°) | The module can't reach its target — possible mechanical binding, wrong conversion factor, or encoder issue |
| Target angle itself is changing rapidly | Something upstream is sending rapidly changing commands — check joystick deadband, check if the low-speed threshold is working |

#### Test F3: Verify the Low-Speed Angle Lock

The `setAngle()` method has a threshold that should prevent angle changes when the robot is nearly stopped:

```java
Rotation2d angle = (Math.abs(desiredState.speedMetersPerSecond) <= (Constants.SwerveConstants.maxSpeed * 0.01))
    ? lastAngle : desiredState.angle;
```

This means: if speed ≤ 1% of max (≤ 0.03 m/s), hold the last angle instead of accepting new angle commands. This should prevent jitter at rest.

**Verify**: Add logging to confirm this threshold is actually triggering when the robot is at rest. If `desiredState.speedMetersPerSecond` is slightly above the threshold (even 0.04 m/s due to joystick noise), the lock won't engage.

---

### Branch G: Something Commands Movement at Rest

#### Test G1: Verify Joystick Deadband

The deadband is set to 0.1 (10%):

```java
public static final double inputDeadband = .1;
```

**Procedure**:
1. Add temporary logging in `TeleopSwerve.execute()`:

```java
SmartDashboard.putNumber("Raw X", m_translationSupplier.getAsDouble());
SmartDashboard.putNumber("Raw Y", m_strafeSupplier.getAsDouble());
SmartDashboard.putNumber("Raw Rot", m_rotationSupplier.getAsDouble());
SmartDashboard.putNumber("Filtered X", xVal);
SmartDashboard.putNumber("Filtered Y", yVal);
SmartDashboard.putNumber("Filtered Rot", rotationVal);
```

2. Release the joystick completely and observe:
   - Are the raw values exactly 0? (Most joysticks have slight offset)
   - Are the filtered values 0 after deadband? (They should be)

| Observation | Suggests |
|---|---|
| Raw values are non-zero but filtered values are 0 | Deadband is working correctly — not the cause |
| Filtered values are non-zero at rest | Joystick offset exceeds deadband — increase deadband or recalibrate joystick |
| All values are 0 but module still jitters | The jitter source is not TeleopSwerve — check for competing commands |

#### Test G2: Check for Competing Commands

Verify that no other command is accidentally controlling the swerve subsystem:

1. In `RobotContainer.java`, check what is set as the default command for `SwerveSubsystem`
2. Check if `AutoAlign` or any other command could be scheduled during teleop
3. Look for any button bindings that trigger swerve-related commands without requiring the swerve subsystem (which would bypass the command scheduler's conflict resolution)

---

## Phase 3: Confirm

Once you've identified a suspected root cause:

1. **Document the hypothesis**: Write it down clearly (e.g., "We believe Module 2 jitters because its `angleOffset` stored in Preferences is incorrect")
2. **Predict the outcome**: Before making the fix, write down what you expect to see (e.g., "After correcting the offset, Module 2's angle error should stay within ±0.5° at rest")
3. **Make ONE change** to fix it
4. **Re-run Tests 1.1 and 1.2** to verify the symptom is gone
5. **Revert the change** and verify the symptom **returns** — this confirms causation, not just correlation
6. **Re-apply the fix** permanently

---

## Automated Diagnostic Test Framework

The `jmm-test-support` branch includes an automated diagnostic test framework that can run many of the tests in this methodology **programmatically** — eliminating joystick variability and producing repeatable, quantitative results.

### How to Use the Framework

1. **Switch to Test mode** on the Driver Station (not Teleop or Auto)
2. Open SmartDashboard — a **Test Selector** dropdown appears under `DiagnosticTests/`
3. Select a test, configure its parameters, and press **Start**
4. Results appear in the console output and on SmartDashboard under `DiagnosticTests/[TestName]/Results/`

### Available Automated Tests

| Test | Framework Command | Replaces Manual Test | What It Does |
|---|---|---|---|
| **Swerve Alignment Test** | `SwerveAlignmentTestCommand` | **A1** (Verify Angle Offsets) | Commands all 4 modules to the same angle(s) and measures how accurately each module reaches the target. Tests at 0°, 90°, 180°, 270° to detect angle-dependent errors. Reports per-module error, cross-module spread, and integrated-vs-absolute encoder comparison. |
| **Swerve Straight Line Test** | `SwerveStraightLineTestCommand` | **1.2, B1, B2** (Driving Straight, Speed Test, Open/Closed Loop) | Bypasses the joystick and feeds exact ChassisSpeeds through `driveFromChassisSpeeds()`. Records per-module angles and velocities at regular intervals, plus gyro heading drift. Supports toggling between open-loop and closed-loop control. |
| **Swerve Angle Drift Test** | `SwerveAngleDriftTestCommand` | **F2** (Log Angle Error) | Cycles a single module between a target angle and zero N times, comparing relative (integrated) to absolute (CANcoder) encoder at each stop. Quantifies encoder drift over many cycles. |
| **LED State Test** | `LedStateTestCommand` | *(N/A — LED diagnostics)* | Tests CandleSubsystem LED states independently. |

### When to Use Automated Tests vs Manual Tests

| Use Automated Tests When... | Use Manual Tests When... |
|---|---|
| You need **repeatable**, **quantitative** data | You need to observe **physical behavior** (binding, noise, vibration) |
| You want to **eliminate joystick variability** | You need to test **joystick-specific** behavior (deadband, input scaling) |
| You want to **compare before/after** a code change | You need to check **disabled behavior** (Test E1) |
| You want to **test specific modules** in isolation | You need to **physically swap modules** (Test D2) |
| You want to test **open-loop vs closed-loop** with identical inputs | You need to inspect **mechanical components** (Test D1) |

### Recommended Automated Testing Workflow

Run these tests in order when debugging drift/jitter:

1. **Swerve Alignment Test** (multi-angle mode) — 2 minutes
   - If any module shows error > 2°: bad angleOffset or failed calibration → fix before proceeding
   - If cross-module spread > 5° at any angle: modules disagree → calibration issue
2. **Swerve Angle Drift Test** on the jittering module — 1 minute
   - If total drift > 5° over 10 cycles: encoder drift issue
   - If drift is minimal: jitter is likely PID tuning, not encoder drift
3. **Swerve Straight Line Test** (open-loop) — 5 seconds + settle time
   - Check module angle errors (should all be ≈ 0°)
   - Check velocity spread between modules
   - Check gyro heading drift
4. **Swerve Straight Line Test** (closed-loop) — 5 seconds + settle time
   - Compare velocity spread to open-loop result
   - If velocity spread drops significantly: motor variation (use closed-loop for competition)

---

## Recommended Testing Order (Priority Queue)

Based on the symptoms described, the code review, and available automated tests, here is the recommended order of investigation — starting with the highest-probability, lowest-effort tests:

| Priority | Test | Time | Why This First |
|---|---|---|---|
| **1** | **C1: Robot-centric vs field-oriented** | 30 sec | Instantly eliminates or implicates the gyro. Almost zero effort. |
| **2** | **E1: Disabled jitter check** | 30 sec | Instantly tells you if the jitter is code or electrical. |
| **3** | 🤖 **Swerve Alignment Test** (automated) | 2 min | Replaces manual A1. Checks all 4 modules at 4 angles, reports per-module errors. Most common cause of drift. Also checks Preferences override. |
| **4** | **1.3: Startup calibration values** | 5 min | Verifies `resetToAbsolute()` succeeds. With 1 Hz CANcoder updates, there's a race condition at startup. |
| **5** | 🤖 **Swerve Angle Drift Test** (automated, on jittering module) | 1 min | Replaces manual F2. Quantifies encoder drift and directly diagnoses jitter root cause. |
| **6** | 🤖 **Swerve Straight Line Test — Open Loop** (automated) | 10 sec | Replaces manual B1 + 1.2. Repeatable straight-line test with exact inputs. Reports per-module angles, velocities, and gyro drift. |
| **7** | 🤖 **Swerve Straight Line Test — Closed Loop** (automated) | 10 sec | Replaces manual B2. Compare to open-loop result. If drift disappears, cause is motor variation. |
| **8** | **G1: Verify joystick deadband** | 10 min | Rules out joystick noise as a jitter source. (Must be manual — involves joystick hardware.) |
| **9** | **A3/A4: Verify kinematics & module ordering** | 15 min | Cross-check that CAN IDs, physical positions, and kinematics array are all consistent. |
| **10** | **D2: Physical module swap** | 30 min | Definitive hardware vs software test. Higher effort, so do it after ruling out easier causes. |

---

## Data Recording Template

Use this template for EVERY test. Fill it out before and after each test. Keep all completed templates together as a log.

```
═══════════════════════════════════════════════════════
Test ID:        [e.g., A1]
Test Name:      [e.g., Verify Angle Offsets]
Date/Time:      _______________
Tester(s):      _______________

HYPOTHESIS:
"If _________ then we expect to see _________"

SETUP:
- Robot state: [disabled / enabled-teleop / enabled-auto]
- Wheels: [on ground / elevated]
- Code changes: [none / describe changes]

RAW DATA:
Module 0 (FL): _______________
Module 1 (FR): _______________
Module 2 (BR): _______________
Module 3 (BL): _______________
Pigeon Yaw:    _______________
Other:         _______________

RESULT:
[Describe what you observed]

CONCLUSION:
- Eliminates: _______________
- Implicates: _______________

NEXT STEP:
[Which test to run next based on this result]
═══════════════════════════════════════════════════════
```

---

## Code-Specific Areas of Suspicion

Based on reviewing the 2026 codebase, these specific code areas warrant scrutiny during the investigation. These are not conclusions — they are starting points guided by the code review.

### 1. Preferences Override for Angle Offsets
**File**: `SwerveModule.java`, constructor  
**Risk**: A previously-saved bad offset in Preferences silently overrides `Constants.java`.  
**Action**: Check Preferences for `Swerve/ModuleX/AngleOffsetDegrees` keys.

### 2. CANcoder Update Frequency vs Startup Timing
**File**: `SwerveModule.java`, constructor  
**Risk**: CANcoder is set to 1 Hz updates. `resetToAbsolute()` runs after a 1-second delay. If the first CANcoder reading hasn't arrived, calibration uses stale data.  
**Action**: Test 1.3 will reveal this. Consider increasing update frequency to 10+ Hz during startup, then reducing after calibration.

### 3. Open-Loop Drive Control
**File**: `SwerveSubsystem.java`, `drive()` method  
**Risk**: Open-loop control (percent output) cannot compensate for motor-to-motor variation, friction differences, or battery sag. This is a known source of drift.  
**Action**: Test B2 will reveal this. Consider switching to closed-loop for competition driving.

### 4. Kinematics Variable Naming Inconsistency
**File**: `Constants.java`, `swerveKinematics` vs `FRONT_LEFT` etc.  
**Risk**: `swerveKinematics` uses `(halfTrackWidth, halfWheelBase)` while `ModuleData` locations use `(halfWheelBase, halfTrackWidth)` — X and Y are swapped. Currently harmless because both values are equal (27/2 inches), but indicates confusion about the coordinate system.  
**Action**: Verify which is correct and make consistent. Would become a bug if the robot weren't square.

### 5. Custom `optimize()` Method
**File**: `SwerveModule.java`  
**Risk**: The custom `optimize()` method replaced the 2024 codebase's `OnboardModuleState.optimize()`. Subtle differences in angle wrapping or optimization logic could cause modules to occasionally choose the wrong rotation direction.  
**Action**: Compare behavior of the custom `optimize()` against WPILib's built-in `SwerveModuleState.optimize()` for edge cases (angles near ±180°, angles near ±90°).

### 6. Angle PID Tuning
**File**: `Constants.java`  
**Risk**: `angleKP = 0.01` is quite low. If it's too low for the mechanical load, the module may not reach its target angle accurately, causing drift. If it's borderline for one module but OK for others (due to friction differences), it could explain why only one module jitters.  
**Action**: Test F2 (log angle error) will reveal this.

### 7. Missing Open-Loop Ramp Rate
**File**: `SwerveModule.java`, `configDriveMotor()`  
**Risk**: `Constants.java` defines `openLoopRamp = 0.25` and `closedLoopRamp = 0.0`, but neither appears to be applied in the `SparkFlexConfig` for the drive motor. If the 2024 code applied ramp rates and 2026 doesn't, motor response characteristics changed.  
**Action**: Check the 2024 `SwerveModule.java` to see if ramp rates were applied there.
