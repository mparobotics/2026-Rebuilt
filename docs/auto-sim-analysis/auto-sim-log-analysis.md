# Autonomous Simulation Trace Log Analysis

## 1. Cycle-by-Cycle Narrative

### Pre-Autonomous: DISABLED Phase (TRACE-006 through TRACE-6938)

The robot starts in DISABLED mode. ~6,938 trace calls occur but are suppressed
by `shouldLog()` (only phase changes are logged during DISABLED). The shared
counter increments silently, which is why AUTONOMOUS starts at TRACE-6939.

---

### AUTONOMOUS Cycle 0 — `autonomousInit()` (TRACE-6939 → TRACE-6960)

This is the critical cycle where the pose reset happens and the bug is planted.

**TRACE-6939–6940**: Phase changes to AUTONOMOUS. `Robot.autonomousInit()` enters.

**TRACE-6941–6948**: The EightLemonAuto command is constructed:
- `getAutonomousCommand()` selects EightLemonAuto
- The EightLemonAuto constructor calls `addCommands(...)` which creates:
  1. `drive.startAutoAt(3.5355, 7.13, 91.82)` → a `runOnce(...)` (InstantCommand)
  2. `drive.autoDrive("8FuelPath")` → a `FollowPathCommand`
- The path file loads successfully, and a FollowPathCommand is returned

**TRACE-6949–6950**: `autonomousInit()` receives the command and calls
`CommandScheduler.getInstance().schedule(...)`.

**KEY FINDING — TRACE-6951**: The `startAutoAt` lambda **executes immediately**
during `schedule()`. This is because WPILib's `CommandScheduler.schedule()` calls
`command.initialize()` synchronously. For a `SequentialCommandGroup`,
`initialize()` calls the first sub-command's `initialize()`. Since `startAutoAt`
returns `runOnce(...)` — which places the action in `initialize()`, not
`execute()` — the lambda fires immediately during the `schedule()` call,
**inside `autonomousInit()`**, before any `robotPeriodic()` has run.

> **Note on `runOnce` semantics:** WPILib's `SubsystemBase.runOnce(Runnable)`
> creates an `InstantCommand` where `initialize()` runs the lambda, `execute()`
> is a no-op, and `isFinished()` always returns `true`. The action is placed in
> `initialize()` — not `execute()` — by design, so the command completes in a
> single scheduler cycle. This is the standard WPILib pattern for one-shot
> actions in a `SequentialCommandGroup` and is correct here; the bug is not in
> the command structure but in what `hardResetPose` does when the lambda runs.

**TRACE-6952–6958**: The lambda executes `hardResetPose()`:

| Step | What happens | Value |
|------|-------------|-------|
| **6953** | pigeonYawBefore | **0.0°** |
| **6953** | odoPoseBefore | (0.00, 0.00, 0.00°) |
| **6954** | `pigeon.setYaw(271.82)` called | — |
| **6955** | pigeonYawAfter | **0.0°** ⚠️ `setYaw` did NOT take effect |
| **6956** | `odometry.resetPosition(gyroAngle=271.82°, ...)` called | gyroAngle = `newPose.getRotation()` = 271.82° |
| **6957** | odoPoseAfter | (13.00, 0.94, **271.82°**) ✓ Correct |
| **6958** | pendingSimPoseReset set | (13.00, 0.94, 271.82°) |

The critical observation: **`pigeon.setYaw(271.82)` has NO immediate effect**
(TRACE-6955 shows yaw still 0.0°). But `odometry.resetPosition()` was called
with `gyroAngle=271.82°` (the desired heading, not the actual pigeon reading).
The odometry is now internally calibrated to the assumption that "the gyro is
currently reading 271.82°" — but it isn't. It reads 0°.

**TRACE-6959–6960**: Lambda exits, `autonomousInit()` exits. No `periodic()` or
`simulationPeriodic()` has run yet.

---

### AUTONOMOUS Cycle 1 — First `robotPeriodic()` (TRACE-6961 → TRACE-6979)

**TRACE-6961**: `robotPeriodic()` enters. This is the first periodic cycle after
autonomous starts.

**TRACE-6962**: `SwerveSubsystem.periodic()` runs.
odoPose before update = **(13.00, 0.94, 271.82°)** — still correct from
`hardResetPose`.

**TRACE-6963**: `odometry.update()` is called with:
- `yaw = Rotation2d(0.00°)` ← **THE BUG MANIFESTS HERE**. The pigeon yaw is
  still 0° because `pigeon.setYaw()` hasn't taken effect.
- `positions = [0, 0, 0, 0]` — no wheel movement

**TRACE-6964**: `odometry.update()` returns
`odoPose = (13.00, 0.94, **0.00°**)` ← **ROTATION DESTROYED**.

Here's why: `odometry.resetPosition(gyroAngle=271.82°, ..., pose=271.82°)`
stored internally that "at reset time, gyro was 271.82°". The gyro offset is
271.82° − 271.82° = 0°. Now `update(yaw=0°)` computes:
heading = 271.82° + (0° − 271.82°) = **0°**. The estimator faithfully applied
the delta: the gyro "changed" by −271.82°, so the heading changed by −271.82°.

**TRACE-6965–6967**: `periodic()` continues — `getPose()` returns
(13.00, 0.94, **0.00°**) twice (for `field.setRobotPose` and `robotPose.set`),
then exits.

**TRACE-6968**: `getPose()` returns (13.00, 0.94, **0.00°**) — this is
**PathPlanner calling `getPose()` during `FollowPathCommand.initialize()`**
(happens during `CommandScheduler.run()`, after `periodic()` exits).

**TRACE-6969**: `getChassisSpeeds()` returns (0, 0, 0) — PathPlanner reading
current speeds during initialization.

**TRACE-6970**: `robotPeriodic()` exits.

**TRACE-6971–6979**: `simulationPeriodic()` runs AFTER `robotPeriodic()`:
- SimulationManager consumes the pending pose reset → sets `simPose` to
  (13.00, 0.94, 271.82°)
- Calls `pigeonSimState.setRawYaw(-88.18°)` (271.82° normalized to [-180, 180])
- This rawYaw won't be visible to `getYaw()` until a future cycle due to CTRE
  sim state latency

**CRITICAL**: `simulationPeriodic()` runs **too late**. By the time it updates
the pigeon sim state, `periodic()` has already read the stale 0° yaw and
corrupted the odometry, and PathPlanner has already initialized with the wrong
heading.

---

### AUTONOMOUS Cycle 2 — PathPlanner Executes and Finishes (TRACE-6980 → TRACE-6998)

**TRACE-6981**: `periodic()` — odoPose before update =
(13.00, 0.94, **0.00°**) (corrupted last cycle).

**TRACE-6982**: `odometry.update(yaw=0.00°, ...)` — pigeon STILL reads 0°.
CTRE sim state change from cycle 1 hasn't propagated yet.

**TRACE-6983**: odoPose after update = (13.00, 0.94, **0.00°**) — unchanged.

**TRACE-6987**: `getPose()` → (13.00, 0.94, **0.00°**) — **PathPlanner sees
heading 0°** during its `execute()`.

**TRACE-6988**: `getChassisSpeeds()` → (0, 0, 0) — robot is stationary.

**TRACE-6989**: `driveFromChassisSpeeds(vx=7.136, vy=7.647, omega=-10.064,
openLoop=false)` — **PathPlanner commands enormous speeds**. With a 271.82°
heading error, PathPlanner's PID controllers generate massive corrective outputs.
The speeds are saturated at the robot's physical limits.

**TRACE-6990**: `driveFromChassisSpeeds(vx=0, vy=0, omega=0, openLoop=false)` —
**PathPlanner commands zero immediately after**. This is
`FollowPathCommand.end()` being called. The command finished
(`isFinished()` returned true) after a single execute cycle. The
`openLoop=false` confirms it's still PathPlanner (not TeleopSwerve).

**TRACE-6991**: `robotPeriodic()` exits. PathPlanner is done.
FollowPathCommand ran for exactly **1 execute cycle**.

**TRACE-6992–6998**: SimulationManager runs, sets pigeon to -88.18° again.
Still no visible effect on getYaw().

---

### AUTONOMOUS Cycle 3 — TeleopSwerve Takes Over (TRACE-6999 → TRACE-7014)

**TRACE-7001**: `odometry.update(yaw=183.64°, ...)` — **NOW the pigeon
responds**, but with the wrong value! The pigeon reads 183.64° because of the
doubling bug:
- `pigeon.setYaw(271.82)` created offset = +271.82°
- `pigeonSimState.setRawYaw(-88.18)` set rawYaw = −88.18°
- Reported yaw = −88.18° + 271.82° = **183.64°** (should be 271.82° ≡ −88.18°)

**TRACE-7002**: odoPose = (13.00, 0.94, **−176.36°**) — the sudden 183.64° jump
from the stale 0° baseline.

**TRACE-7006**: `driveFromChassisSpeeds(vx=0, vy=0, omega=0, openLoop=true)` —
`openLoop=true` confirms **TeleopSwerve is now the active command**. PathPlanner
is gone. The robot sits still.

---

### AUTONOMOUS Cycle 4 — Steady State (TRACE-7015 → TRACE-7030)

Pigeon reads 183.64° (unchanged — no robot motion), odoPose is −176.36°.
TeleopSwerve continues commanding zero. The robot is stuck.

---

## 2. Root Cause Identification

There are **two interacting bugs**, with the first being the primary cause of the
instant termination:

### Primary Bug: `hardResetPose` passes the wrong `gyroAngle` to `odometry.resetPosition()`

In `SwerveSubsystem.java` line 140:

```java
Rotation2d gyroAngle = updatePigeon ? newPose.getRotation() : getYaw();
```

When `updatePigeon=true`, the code passes `newPose.getRotation()` (the
**desired** heading, 271.82°) instead of `getYaw()` (the **actual** pigeon
reading, 0°) as the `gyroAngle` parameter.

`SwerveDrivePoseEstimator.resetPosition(gyroAngle, positions, pose)` records the
gyroAngle as the baseline for future delta calculations. It computes internally:

```
gyroOffset = pose.rotation − gyroAngle
```

When gyroAngle equals the pose rotation, offset = 0°.

Then `update(currentYaw, ...)` computes:

```
heading = pose.rotation + (currentYaw − storedGyroAngle)
```

With storedGyroAngle=271.82° and currentYaw=0°:

```
heading = 271.82° + (0° − 271.82°) = 0°
```

**The odometry interprets the 271.82° difference between the stored baseline and
the actual pigeon as the robot having rotated −271.82° since the reset**, and
"helpfully" adjusts the heading to 0°.

#### Chain of Causation

1. `pigeon.setYaw(271.82)` does not take immediate effect in CTRE simulation →
   `getYaw()` returns 0°
2. `odometry.resetPosition(gyroAngle=271.82°, ...)` stores 271.82° as baseline,
   even though the actual gyro reads 0°
3. First `periodic()` calls `odometry.update(yaw=0°)` → heading snaps from
   271.82° to **0°**
4. PathPlanner's `FollowPathCommand` initializes with `getPose()` returning
   heading **0°** instead of **271.82°**
5. With 271.82° heading error, PathPlanner computes a trajectory that is either
   instant or has a vanishingly short duration
6. FollowPathCommand finishes after 1 execute cycle; TeleopSwerve takes over

### Secondary Bug: Pigeon yaw doubling in simulation

Even if the primary bug were fixed, the simulation-specific yaw doubling would
cause problems during path following:

- `pigeon.setYaw(271.82)` creates an internal offset of +271.82°
- `SimulationManager` calls `pigeonSimState.setRawYaw(-88.18°)`
- Reported yaw = −88.18° + 271.82° = **183.64°**, instead of the correct
  −88.18° (≡ 271.82°)

This would corrupt the heading by ~88° once the sim state propagates (cycle 3+),
causing path-following errors.

---

## 3. Execution Order Diagram

Within a single robot loop iteration (one call to `TimedRobot`'s main loop), the
actual execution order is:

```
╔══════════════════════════════════════════════════════════════════════╗
║                    AUTONOMOUS CYCLE 1                                ║
║           (first robotPeriodic after autonomousInit)                 ║
╠══════════════════════════════════════════════════════════════════════╣
║                                                                      ║
║  1. robotPeriodic() ENTERS                                           ║
║     │                                                                ║
║     └─▶ 2. CommandScheduler.run()                                    ║
║          │                                                           ║
║          ├─▶ 3. SwerveSubsystem.periodic()                           ║
║          │     │                                                     ║
║          │     ├─▶ getYaw() → 0.00° ← STALE! pigeon.setYaw           ║
║          │     │                       hasn't taken effect           ║
║          │     │                                                     ║
║          │     ├─▶ odometry.update(yaw=0°, positions=[0,0,0,0])      ║
║          │     │   └── odoPose: 271.82° → 0.00° ✗ CORRUPTED          ║
║          │     │                                                     ║
║          │     └── EXIT                                              ║
║          │                                                           ║
║          └─▶ 4. SequentialCommandGroup.execute()                     ║
║                │                                                     ║
║                ├── InstantCommand.execute() (no-op, already ran)     ║
║                ├── InstantCommand.isFinished() → true                ║
║                ├── Advance to FollowPathCommand                      ║
║                │                                                     ║
║                └─▶ FollowPathCommand.initialize()                    ║
║                     ├── getPose() → (13.00, 0.94, 0.00°) ✗           ║
║                     └── getChassisSpeeds() → (0, 0, 0)               ║
║                     (Trajectory computed with WRONG heading)         ║
║                                                                      ║
║  5. robotPeriodic() EXITS                                            ║
║                                                                      ║
║  6. simulationPeriodic()                                             ║
║     │                                                                ║
║     └─▶ SimulationManager.simulationPeriodic()                       ║
║          ├── consumes pendingSimPoseReset                            ║
║          │   └── simPose = (13.00, 0.94, 271.82°)                    ║
║          ├── pigeonSimState.setRawYaw(-88.18°)                       ║
║          │   └── ⚠ TOO LATE! periodic() already read 0°              ║
║          └── EXIT                                                    ║
║                                                                      ║
╠══════════════════════════════════════════════════════════════════════╣
║                    AUTONOMOUS CYCLE 2                                ║
╠══════════════════════════════════════════════════════════════════════╣
║                                                                      ║
║  1. robotPeriodic() ENTERS                                           ║
║     └─▶ CommandScheduler.run()                                       ║
║          ├─▶ periodic() — yaw STILL 0° (sim state lag)               ║
║          │   └── odoPose remains (13.00, 0.94, 0°)                   ║
║          │                                                           ║
║          └─▶ FollowPathCommand.execute()                             ║
║               ├── getPose() → (13.00, 0.94, 0°) ✗                    ║
║               ├── getChassisSpeeds() → (0, 0, 0)                     ║
║               ├── driveFromChassisSpeeds(7.1, 7.6, -10.1) ← !!       ║
║               ├── isFinished() → TRUE (trajectory complete)          ║
║               └── end() → driveFromChassisSpeeds(0, 0, 0)            ║
║                                                                      ║
║  2. simulationPeriodic() — setRawYaw(-88.18) again                   ║
║                                                                      ║
╠══════════════════════════════════════════════════════════════════════╣
║                    AUTONOMOUS CYCLE 3+                               ║
╠══════════════════════════════════════════════════════════════════════╣
║                                                                      ║
║  TeleopSwerve (default command) now active.                          ║
║  Pigeon yaw finally changes to 183.64° (doubled).                    ║
║  Robot sits still. PathPlanner is gone.                              ║
║                                                                      ║
╚══════════════════════════════════════════════════════════════════════╝
```

**The core timing problem**: `periodic()` runs BEFORE `CommandScheduler`
executes the PathPlanner command, and `simulationPeriodic()` runs AFTER both. So
the pigeon sim state written by `SimulationManager` is never visible until at
least 1 cycle later (plus CTRE's own sim state propagation delay, adding
potentially 1 more cycle).

---

## 4. Proposed Fix

### Fix A (Production Code): Always use actual gyro reading in `odometry.resetPosition()`

In `SwerveSubsystem.hardResetPose` (line 140):

**Before:**
```java
Rotation2d gyroAngle = updatePigeon ? newPose.getRotation() : getYaw();
```

**After:**
```java
Rotation2d gyroAngle = getYaw();
```

**Why this works**: Instead of telling the estimator "the gyro currently reads
271.82°" (a lie — it reads 0°), we tell it the truth: "the gyro reads 0° and the
robot is at 271.82°." The estimator computes:

```
offset = 271.82° − 0° = 271.82°
```

When the next `update(yaw=0°)` arrives, it correctly calculates:

```
heading = 0° + 271.82° = 271.82° ✓
```

**Why this is safe on real hardware**: On a real Pigeon2, `setYaw()` may or may
not take immediate effect. If it does, `getYaw()` returns 271.82° → offset = 0°
→ same result as current code. If it doesn't, `getYaw()` returns the old value →
offset compensates → correct result. The fix is **more correct** than the current
code in all cases.

**No simulation-specific branching**: This is a pure logic fix that removes an
incorrect assumption (that `setYaw` takes immediate effect). It improves
production code correctness.

### Fix B (SimulationManager): Eliminate pigeon yaw doubling

In `SimulationManager.simulationPeriodic()`, when consuming a pose reset, also
**zero out the pigeon offset** by writing the raw yaw that produces the correct
reported yaw. Currently `pigeon.setYaw()` creates an offset, and then
`setRawYaw()` sets a raw value — the two add up (doubling).

The cleanest approach: when SimulationManager sets the pigeon sim state after a
pose reset, it should also clear the offset created by `pigeon.setYaw()`. Add
this after consuming the reset:

```java
if (reset != null) {
    this.simPose = reset;
    // Clear the offset created by hardResetPose's pigeon.setYaw() call.
    // In CTRE simulation, setYaw() creates an internal offset and setRawYaw()
    // sets the raw value; reported yaw = rawYaw + offset. To prevent doubling,
    // we set rawYaw to 0 first (so setYaw's offset targets 0), then overwrite
    // rawYaw with the correct heading.
    pigeonSimState.setRawYaw(0);
    swerveSubsystem.getPigeon().setYaw(0);  // clears the offset to 0
    pigeonSimState.setRawYaw(simPose.getRotation().getDegrees());  // real value
}
```

**Alternative simpler approach**: Since Fix A makes odometry robust against stale
gyro readings, the doubling is less catastrophic — the offset will compensate.
However, the reported heading will still be wrong (183.64° instead of 271.82°),
which would cause PathPlanner to compute incorrect corrections during path
following. So Fix B is recommended for correct ongoing simulation.

### Fix Priority

**Fix A alone solves the instant-termination bug.** PathPlanner will initialize
with the correct heading and compute a valid trajectory. Even with the pigeon
doubling (Fix B not applied), the odometry offset would partially compensate
during the first couple cycles while the pigeon value is still stale (0°), giving
PathPlanner correct poses initially.

Fix B is needed for correct long-term path following in simulation.

Both fixes respect the design constraint: **Fix A improves production code
without any simulation branching; Fix B modifies only `SimulationManager`.**

---

## Answers to Specific Analysis Questions

### Q1: What is the exact sequence of method calls from `autonomousInit` through the first few `robotPeriodic` cycles?

See the cycle-by-cycle narrative above. The key sequence is:

1. `autonomousInit()` → `getAutonomousCommand()` → `EightLemonAuto()` constructor
   → `startAutoAt()` + `autoDrive()` → `schedule()` → lambda fires →
   `hardResetPose()` → odometry set to 271.82°
2. First `robotPeriodic()` → `periodic()` → `odometry.update(yaw=0°)` → heading
   snaps to 0° → PathPlanner `initialize()` sees 0°
3. Second `robotPeriodic()` → `periodic()` → still 0° → PathPlanner `execute()`
   → commands 7+ m/s → `isFinished()` → true → `end()`
4. Third `robotPeriodic()` → TeleopSwerve active → robot stationary

### Q2: When does `startAutoAt`'s lambda actually execute?

**During `autonomousInit()`**, specifically inside `CommandScheduler.schedule()`.
It fires BEFORE any `robotPeriodic()` call. The `schedule()` call triggers
`SequentialCommandGroup.initialize()` → `InstantCommand.initialize()` →
lambda runs. This was confirmed by the lambda executing at TRACE-6951, between
the schedule call (6950) and `autonomousInit EXIT` (6960).

### Q3: What does `pigeon.getYaw()` return at each point?

| When | pigeon.getYaw() | Why |
|------|-----------------|-----|
| Before `pigeon.setYaw(271.82)` | 0.0° | Initial value |
| Immediately after `pigeon.setYaw(271.82)` | **0.0°** | setYaw is deferred in CTRE sim |
| Cycle 1 `periodic()` | **0.0°** | Still deferred |
| Cycle 2 `periodic()` | **0.0°** | Still deferred (1-cycle CTRE lag) |
| Cycle 3 `periodic()` | **183.64°** | Finally visible, but DOUBLED |

`pigeon.setYaw()` does NOT take immediate effect. There is a minimum 2-cycle
delay before the pigeon reports any change. When it does report, the value is
wrong due to offset doubling (183.64° instead of 271.82°).

### Q4: What does `odometry.getEstimatedPosition()` return before and after each `update()` and `resetPosition()` call?

| Event | odoPose |
|-------|---------|
| Before `resetPosition()` | (0.00, 0.00, 0.00°) |
| After `resetPosition(gyro=271.82°, pose=271.82°)` | (13.00, 0.94, **271.82°**) ✓ |
| Cycle 1: before `update()` | (13.00, 0.94, **271.82°**) ✓ |
| Cycle 1: after `update(yaw=0°)` | (13.00, 0.94, **0.00°**) ✗ |
| Cycle 2: after `update(yaw=0°)` | (13.00, 0.94, **0.00°**) ✗ |
| Cycle 3: after `update(yaw=183.64°)` | (13.00, 0.94, **−176.36°**) ✗ |

### Q5: What values does PathPlanner see when it calls `getPose()` and `getChassisSpeeds()`?

- **Cycle 1** (initialize): `getPose()` = (13.00, 0.94, **0.00°**),
  `getChassisSpeeds()` = (0, 0, 0)
- **Cycle 2** (execute): `getPose()` = (13.00, 0.94, **0.00°**),
  `getChassisSpeeds()` = (0, 0, 0)

PathPlanner never sees the correct 271.82° heading.

### Q6: What speeds does PathPlanner command via `driveFromChassisSpeeds()`?

- **Cycle 2**: vx=7.136, vy=7.647, omega=−10.064 (openLoop=false) — one
  enormous burst
- **Cycle 2**: vx=0, vy=0, omega=0 (openLoop=false) — `end()` zeros

PathPlanner commands non-zero speeds for exactly **1 execute cycle** before
finishing. From cycle 3 onward, `openLoop=true` indicates TeleopSwerve.

### Q7: When does `SimulationManager.simulationPeriodic()` run relative to everything else?

It runs **AFTER** `robotPeriodic()` (which contains `CommandScheduler.run()`).
The WPILib `TimedRobot` lifecycle order is:

```
robotPeriodic() → autonomousPeriodic() → simulationPeriodic()
```

This means pigeon sim state written by SimulationManager is not visible until
the NEXT cycle's `periodic()` — and even then, CTRE's sim state may add an
additional cycle of latency.

### Q8: Where exactly do 3rd-party library calls happen, and what are their inputs/outputs?

| 3rd-party call | Location | Inputs | Output |
|---------------|----------|--------|--------|
| `pigeon.setYaw(271.82)` | hardResetPose, cycle 0 | 271.82° | No immediate effect (yaw stays 0°) |
| `odometry.resetPosition(...)` | hardResetPose, cycle 0 | gyro=271.82°, pose=271.82° | odoPose = (13, 0.94, 271.82°) |
| `odometry.update(...)` | periodic, cycle 1 | yaw=0°, pos=[0,0,0,0] | odoPose = (13, 0.94, **0°**) |
| `odometry.update(...)` | periodic, cycle 2 | yaw=0°, pos=[0,0,0,0] | odoPose = (13, 0.94, 0°) |
| `pigeonSimState.setRawYaw(...)` | simPeriodic, cycle 1 | -88.18° | Written to sim state |
| `odometry.update(...)` | periodic, cycle 3 | yaw=183.64°, pos=[varied] | odoPose = (13, 0.94, −176.36°) |

---

## Key Mystery Resolved

From `auto-sim-analysis.md`, Section "What We Don't Know":

> **Why is `odoPose` rotation 0° right after `hardResetPose` set it to 271.82°?**

**Answer**: Because `hardResetPose` passes `newPose.getRotation()` (271.82°) as
the `gyroAngle` to `odometry.resetPosition()`, but the pigeon's actual yaw is
0°. On the very next `periodic()` call, `odometry.update(yaw=0°)` computes a
−271.82° heading delta and snaps the rotation to 0°.

> **Why does `FollowPathCommand` finish after exactly 1 execute cycle?**

**Answer**: PathPlanner initializes with heading 0° instead of 271.82°. It
executes once, commands enormous speeds (7+ m/s, 10+ rad/s), then `isFinished()`
returns true. The robot **does not actually move** — the translation remains
(13.00, 0.94) throughout. The path is NOT completed; PathPlanner terminates
prematurely.

We cannot determine the exact internal mechanism without instrumenting
PathPlanner itself. The most likely explanation is that
`path.generateTrajectory()`, called during `initialize()` with the wildly wrong
starting heading (0° vs 271.82°), produces a **degenerate trajectory with zero
or near-zero total time**. PathPlanner's `isFinished()` is typically
`timer.hasElapsed(trajectory.getTotalTimeSeconds())` — if the total time is ≈ 0,
this returns true on the first check. The enormous speed command in the single
`execute()` cycle is PathPlanner's computed output for that degenerate
trajectory, but it is immediately overwritten by `end()` zeroing the motors
before `SimulationManager` can integrate any motion.

---

## 5. Follow-up Analysis: `sim-output2.log` (with `TracedCommand` instrumentation)

### 5.1 What the New Log Confirmed

After adding `TracedCommand` wrappers around both `startAutoAt` and
`autoDrive[8FuelPath]`, the second simulation run (`sim-output2.log`) provided
definitive lifecycle evidence:

1. **`startAutoAt` lifecycle** — works as expected:
   - `initialize()` fires during `autonomousInit()` (cycle 0), executing the
     `hardResetPose` lambda
   - `execute()` is a no-op (cycle 1)
   - `isFinished()` = true (cycle 1)
   - `end(interrupted=false)` (cycle 1)

2. **`autoDrive[8FuelPath]` lifecycle** — confirmed: **exactly ONE `execute()`
   cycle, then `isFinished()=true`**:
   - `initialize()` fires in cycle 1 (same `CommandScheduler.run()` where
     `startAutoAt` finishes)
   - `execute()` fires in cycle 2, commanding vx=-7.417, vy=7.375, omega=-2.292
   - `isFinished()` = **true** in cycle 2
   - `end(interrupted=false)` in cycle 2, zeroing speeds to (0, 0, 0)

3. **Robot final position**: The robot **stays at the path START** (13.00, 0.94).
   It does NOT move. The enormous speed command persists for a single 20ms
   cycle, but `end()` zeros the motors before `SimulationManager.simulationPeriodic()`
   can integrate any motion. The `desiredVx`/`desiredVy`/`desiredOmega` seen by
   SimulationManager are all 0.000.

### 5.2 What Was Ruled Out

#### Alliance flip mismatch — RULED OUT

PathPlanner 2026.1.2's `FlippingUtil` defaults to `FieldSymmetry.kRotational`
(confirmed by reading the library source from the Gradle cache JAR). The
kRotational flip is:
- Position: `(fieldSizeX − x, fieldSizeY − y)`
- Rotation: `rotation − 180°` (equivalent to `rotation + 180°`)

This is **identical** to the team's `FieldConstants.flipForAlliance()`:
- Position: `(FIELD_LENGTH − x, FIELD_WIDTH − y)`
- Rotation: `rotation + 180°`

There is no double-flip or coordinate mismatch between where `startAutoAt`
places the robot and where PathPlanner expects the path to start.

#### Heading corruption at PathPlanner initialization — NON-DETERMINISTIC

**Important**: The pigeon `setYaw()` latency is **non-deterministic** between
simulation runs. In `sim-output.log`, the pigeon yaw was still 0° in cycles 1
and 2, corrupting the heading. In `sim-output2.log`, the pigeon yaw offset
**takes effect by cycle 1**. This means
`odometry.update(yaw=271.82°)` sees the same yaw that `resetPosition()` stored,
so the heading is **preserved as -88.18°** (≡ 271.82°):

```
[TRACE-7160] odometry.update(yaw=Rotation2d(Deg: 271.82), ...) → odoPose=(..., Deg: -88.18)
```

PathPlanner initializes in cycle 1 with `getPose()` returning
**(13.00, 0.94, -88.18°)** — the **correct** heading. Velocity is (0, 0, 0).
The flipped path's `idealStartingState` is also rotation=-88.18°, velocity=0.
**Both match**, so PathPlanner uses its pre-computed ideal trajectory.

**Yet PathPlanner STILL finishes after 1 execute cycle.** This means the
heading corruption identified in the initial analysis (Section 2) is NOT the
only cause of the instant termination. Something else is wrong.

#### Pigeon yaw doubling — confirmed but happens AFTER auto finishes

The yaw corruption (271.82° → 183.64°) first appears in cycle 3:
```
[TRACE-7210] odometry.update(yaw=Rotation2d(Deg: 183.64), ...) → odoPose=(..., Deg: -176.36)
```
By this point, `autoDrive` has already finished (cycle 2). The doubling is a
real bug that would affect longer-running paths, but it is not responsible for
the immediate termination observed here.

### 5.3 PathPlanner `isFinished()` — Source Code Analysis

Reading `FollowPathCommand` source from the PathPlanner 2026.1.2 JAR:

```java
// FollowPathCommand.java (3rd-party — com.pathplanner.lib.commands)
@Override
public boolean isFinished() {
    double totalTime = trajectory.getTotalTimeSeconds();
    return timer.hasElapsed(totalTime) || !Double.isFinite(totalTime);
}
```

Two conditions cause termination:
1. **`timer.hasElapsed(totalTime)`** — the internal timer (started in
   `initialize()`) has exceeded the trajectory's total duration
2. **`!Double.isFinite(totalTime)`** — the trajectory's total time is `NaN` or
   `Infinity`

Both `trajectory` and `timer` are **private fields** of `FollowPathCommand`
(a 3rd-party class). We cannot access them from our `TracedCommand` wrapper.

To determine which condition triggers, we added elapsed-time tracking to
`TracedCommand`: it records `Timer.getFPGATimestamp()` at `initialize()` and
logs the elapsed time at each `isFinished()` call. If `isFinished()=true` with
elapsed time ≈ 0.02s, then either `totalTime ≤ 0.02` or `totalTime` is
non-finite.

### 5.4 Reverse-Engineering the Target State

PathPlanner's `FollowPathCommand.execute()` works as follows:

```java
double currentTime = timer.get();                           // elapsed since initialize
var targetState = trajectory.sample(currentTime);           // "where should robot be at t?"
ChassisSpeeds targetSpeeds = controller.calculateRobotRelativeSpeeds(currentPose, targetState);
output.accept(targetSpeeds, targetState.feedforwards);      // → driveFromChassisSpeeds
```

`trajectory.sample(t)` returns the trajectory state at time `t`. If
`t >= totalTime`, it returns the **end state** (final destination). The
controller then computes PID feedback = `kP × (target − current)` with kP=5.0.

From the trace output `driveFromChassisSpeeds(vx=-7.417, vy=7.375, ...)`, we
can reverse-engineer the target position by converting robot-relative speeds
back to field-relative, then dividing by kP:

| | Robot (current) | Implied target | PID feedback (kP=5.0) |
|---|---|---|---|
| X | 13.00 m | ~14.43 m | 5.0 × 1.43 ≈ 7.15 |
| Y | 0.94 m | ~2.47 m | 5.0 × 1.53 ≈ 7.65 |

The implied target **(14.43, 2.47)** matches the **end point** of the flipped
8FuelPath. The path file `8FuelPath.path` defines:
- Blue start: (3.5355, 7.1300) → flipped red: **(13.00, 0.94)**
- Blue end: (2.1083, 5.6006) → flipped red: **(14.43, 2.47)**

This confirms `trajectory.sample()` returned the end state, meaning the timer
had already exceeded the trajectory's total time after just ~20ms.

> **Note on `trajectory.sample()` timing**: The `timer.get()` value of ~0.02s
> represents actual elapsed FPGA time between `initialize()` (cycle 1) and
> `execute()` (cycle 2). This is real wall-clock time, not a hardcoded value.
> (The project's `SimulationManager` similarly uses actual elapsed time via
> `Timer.getFPGATimestamp()` rather than assuming a fixed 0.02s per cycle.)

### 5.5 Path File vs. Trajectory — What Can and Cannot Be Read From `8FuelPath.path`

The path file (`src/main/deploy/pathplanner/paths/8FuelPath.path`) contains:

| Data | Available? | Value |
|------|-----------|-------|
| Start position (anchor) | ✅ Yes | (3.5355, 7.1300) |
| End position (anchor) | ✅ Yes | (2.1083, 5.6006) |
| Bézier control points | ✅ Yes | nextControl/prevControl for each waypoint |
| Constraints (max vel/accel) | ✅ Yes | maxVelocity=3.0 m/s, maxAcceleration=3.0 m/s² |
| Ideal starting state | ✅ Yes | velocity=0, rotation=91.82° |
| Goal end state | ✅ Yes | velocity=0, rotation=65.82° |
| **Trajectory total time** | ❌ **No** | Computed at runtime by PathPlanner |
| **Trajectory states** | ❌ **No** | Computed at runtime by PathPlanner |

The trajectory (time-parameterized sequence of poses, velocities, and
accelerations) is **generated at runtime** by PathPlanner using the path
geometry + constraints + `RobotConfig`. The method
`PathPlannerPath.generateTrajectory(startingSpeeds, startingRotation, config)`
is public and can be called directly to inspect the generated trajectory.

### 5.6 Trajectory Trace Instrumentation Added

To answer the remaining mystery, we added trace output in `autoDrive()` that
calls `PathPlannerPath.generateTrajectory()` with the same inputs PathPlanner
would use (flipped path, ideal starting state, `ROBOT_CONFIG`). This logs:
- `totalTime` and whether it is finite
- Number of trajectory states
- Sampled positions at 0%, 25%, 50%, 75%, and 100% of the trajectory

We also added elapsed-time tracking to `TracedCommand.isFinished()` to log how
much real time has passed since `initialize()` when `isFinished()` returns true.

**These traces will appear in the next simulation run.** The trajectory trace
in `autoDrive()` logs the actual `totalTime` value and `Double.isFinite(totalTime)`
at path construction time — so we will see the exact value (e.g. `totalTime=NaN
isFinite=false` or `totalTime=0.003s isFinite=true`) immediately in the log,
without needing to wait for the command to run.

### 5.7 Remaining Open Question (answered in Section 6)

**Why does the pre-computed ideal trajectory appear to have near-zero total
time?** The 8FuelPath covers ~2.1 meters (computed from the two anchor points)
with maxVelocity=3.0 m/s and maxAcceleration=3.0 m/s². A normal trajectory
should take approximately 1.7 seconds. The trajectory generation uses the
project's `AutoConstants.ROBOT_CONFIG` (mass=52kg, MOI=6.8, NEO Vortex motors,
6.75:1 gearing, 0.0508m wheel radius). These values appear reasonable.

**This question is definitively answered in Section 6 below.**

---

## 6. Definitive Root Cause: `ROBOT_CONFIG` Parameter Mismatch

### 6.1 Background: DC Motor Physics

This section explains the physics concepts needed to understand the bug.

#### Key Units

| Unit | Name | Measures |
|------|------|----------|
| **Nm** | Newton-meters | Torque (rotational force) |
| **A** | Amps | Electrical current |
| **V** | Volts | Electrical potential |
| **Ω** | Ohms | Electrical resistance |
| **rad/s** | Radians per second | Rotational speed |
| **m/s** | Meters per second | Linear speed |

**Ohm's Law**: `V = I × R` (volts = current × resistance), or equivalently
`I = V / R`.

**12V** is the nominal voltage produced by the robot's battery.

#### Motor Terminology

| Term | Definition |
|------|-----------|
| **Stall torque** | The **maximum** torque a motor can produce. It occurs when the shaft is held still **by a load** (0 RPM) — the motor is pushing as hard as it can but something is preventing it from spinning. Think of pedaling a bike uphill: you push hardest on the pedals when going nearly zero speed. As you pedal faster, you can push with less force. "Stall" means "the shaft wants to spin but can't because the load is too heavy." For our geared NEO Vortex: **24.3 Nm**. |
| **Stall current** | The current the motor draws when stalled (shaft held still by a load). This is the **maximum** current the motor ever draws — because there is no back-EMF to resist it (see below). For our NEO Vortex: **211 A**. |
| **Free speed** | The maximum speed the motor reaches when spinning with NO load (nothing attached to the shaft). The motor is spinning as fast as it can but producing zero useful torque. For the raw NEO Vortex motor, REV Robotics publishes a free speed of ~6784 RPM (≈710 rad/s). After our 6.75:1 gear reduction: **710 / 6.75 = 105.2 rad/s** at the output shaft. Converting to robot speed: **105.2 × 0.0508 m (wheel radius) = 5.35 m/s**. |
| **Free current** | The current drawn at free speed — just enough to overcome internal friction. This is the **minimum** current. Published by REV Robotics for the NEO Vortex: **3.6 A**. |

> **Where do these numbers come from?** The raw motor specs (stall torque,
> stall current, free speed, free current) are published by the motor
> manufacturer (REV Robotics for the NEO Vortex). The geared values are
> calculated by applying our 6.75:1 gear ratio. The robot speed is calculated
> from the geared motor speed × wheel radius. The motor resistance is derived
> from Ohm's Law: `R = 12V / 211A = 0.057 Ω` (it is not typically published
> directly by the manufacturer).

#### Back-EMF: Why Faster Motors Draw Less Current

**EMF** stands for **Electromotive Force** (not electromagnetic field). It is
measured in volts.

When a motor spins, its spinning magnets act as a **generator**, producing a
voltage that **opposes** the applied battery voltage. This opposing voltage is
called **back-EMF**. The faster the motor spins, the more back-EMF it
generates.

**The current flowing through the motor** depends on the difference between
the battery voltage and the back-EMF:

```
                    Battery Voltage − Back-EMF
Motor Current = ─────────────────────────────────
                        Motor Resistance
```

Or in symbols: `I = (V − back_EMF) / R`

- **At stall** (0 speed): back-EMF = 0V → current = 12V / 0.057Ω = **211A** (maximum)
- **At free speed** (105.2 rad/s): back-EMF = 12V − 3.6A × 0.057Ω = **11.795V**
  → current = (12V − 11.795V) / 0.057Ω = **3.6A** (minimum)
- **At any speed in between**: back-EMF is proportional to speed, so current
  is somewhere between 3.6A and 211A

**This is counter-intuitive**: slower speeds draw MORE current, not less.
A stalled motor is basically a short circuit across the battery.

#### Kv and Kt: The Two Motor Constants

**Kv (velocity constant)** relates motor speed to back-EMF voltage:

```
back-EMF = motor_speed / Kv
```

Measured in **rad/s per volt**: "how many rad/s does the motor spin per volt
of back-EMF?" We can derive Kv from the free-speed conditions. At free speed,
we know the current (3.6A, published by REV), the voltage (12V, battery), and
the geared speed (710 rad/s published by REV ÷ 6.75 gear ratio = 105.2 rad/s):

```
At free speed: back-EMF = batteryVoltage − freeCurrent × resistance
                        = 12V − 3.6A × 0.057Ω = 11.795V

Since back-EMF = motor_speed / Kv:
  Kv = motor_speed / back-EMF
     = 105.2 / 11.795
     = 8.92 rad/s/V
```

Substituting back into the current formula:

```
                     12V − (motor_speed / 8.92)
Motor Current = ──────────────────────────────────
                           0.057 Ω
```

**Kt (torque constant)** relates current to torque:

```
torque = Kt × current
```

Measured in **Nm per amp**: "how much torque does the motor produce per amp
of current?" Derived from stall conditions (maximum torque at maximum current):

```
Kt = stallTorque / stallCurrent = 24.3 Nm / 211 A = 0.1152 Nm/A
```

#### `getCurrent()` and `getTorque()` — Real WPILib Methods

These are actual methods on the `DCMotor` class in WPILib. They implement
the physics formulas described above:

| Method | What it computes | Formula |
|--------|-----------------|---------|
| `getCurrent(speed, voltage)` | "At `speed` rad/s with `voltage` applied, how many amps?" | `I = (V − speed/Kv) / R` |
| `getTorque(current)` | "At `current` amps, how much torque?" | `torque = Kt × current` |

#### Speed vs. Current Table for Our Motor

| Motor speed | Robot speed | Current at 12V | Torque |
|---|---|---|---|
| 0 rad/s (**stalled**) | 0 m/s | **211 A** | 24.3 Nm |
| 59.1 rad/s | **3.0 m/s** (our maxSpeed) | **94.6 A** | 10.9 Nm |
| 86.7 rad/s | 4.41 m/s | **40 A** (our current limit) | 4.6 Nm |
| 105.2 rad/s (**free**) | 5.35 m/s | **3.6 A** | 0.4 Nm |

### 6.2 What is `ModuleConfig`?

`ModuleConfig` is PathPlanner's representation of one swerve drive module —
the physical properties of the wheel + motor + gearbox combination. Our code
creates it in `Constants.java`:

```java
public static final ModuleConfig MODULE_CONFIG = new ModuleConfig(
    SwerveConstants.wheelDiameter / 2,            // wheelRadius = 0.0508 m (2 inches)
    SwerveConstants.maxSpeed,                      // maxDriveVelocityMPS = 3.0 m/s
    1.2,                                           // wheelCOF (coefficient of friction)
    DCMotor.getNeoVortex(1).withReduction(6.75),   // drive motor with gearbox
    SwerveConstants.driveContinuousCurrentLimit,    // driveCurrentLimit = 40 A
    1);                                            // 1 motor per module
```

PathPlanner uses `ModuleConfig` to answer physics questions like "how much
force can this module exert on the carpet?" during trajectory generation.

### 6.3 What is `torqueLoss`?

**`torqueLoss`** represents the torque consumed by friction and inefficiency —
the torque the motor must produce just to keep the wheels spinning, with
nothing left over for acceleration or deceleration.

PathPlanner computes it inside the `ModuleConfig` constructor:

```java
// How fast do the wheels spin at maxDriveVelocityMPS?
maxDriveVelocityRadPerSec = maxDriveVelocityMPS / wheelRadiusMeters;
//                        = 3.0 / 0.0508 = 59.05 rad/s

// How much current does the motor draw at that speed, at 12V?
maxSpeedCurrentDraw = driveMotor.getCurrent(59.05, 12.0);
//                  = 94.63 A

// Clamp to the current limit:
clampedCurrent = Math.min(94.63, 40.0);
//             = 40.0 A                   ← THIS IS THE PROBLEM

// torqueLoss = the torque at the clamped current:
torqueLoss = driveMotor.getTorque(40.0);
//         = 0.1152 × 40 = 4.607 Nm
```

### 6.4 What is a Trajectory "State"?

A PathPlanner **trajectory** is a time-ordered list of **states**. Each state
is a snapshot answering: "At time T seconds, where should the robot be and how
fast should it be moving?"

| State field | Meaning |
|---|---|
| `timeSeconds` | When this state occurs (seconds from start) |
| `pose` | Where the robot should be (x, y, heading) |
| `linearVelocity` | How fast the robot should be moving (m/s) |
| `heading` | Direction of travel |
| `fieldSpeeds` | Velocity broken into vx, vy, omega components |

For example, a healthy 8FuelPath trajectory has 12 states:

```
state[ 0] t=0.000s  pose=(3.54, 7.13)  vel=0.000 m/s  ← start (stopped)
state[ 1] t=0.369s  pose=(3.41, 6.98)  vel=1.081 m/s  ← accelerating
...
state[ 5] t=0.826s  pose=(2.88, 6.38)  vel=2.380 m/s  ← peak speed
...
state[11] t=1.690s  pose=(2.11, 5.60)  vel=0.000 m/s  ← end (stopped)
```

PathPlanner generates these states by simulating the robot's motor physics:
"Given the motor's torque capability, friction, and the robot's mass, how
quickly can the robot accelerate from rest, cruise, and decelerate to a stop?"

### 6.5 The Bug: Zero Available Torque

During trajectory generation, PathPlanner runs a **forward acceleration pass**
over the states. For each state, it computes how fast the module can be going,
using this logic from `PathPlannerTrajectory.forwardAccelPass()`:

```java
// What current does the motor draw at the previous state's speed?
double currentDraw = Math.min(
    driveMotor.getCurrent(lastVelRadPerSec, 12.0),   // physics current
    driveCurrentLimit);                                // our 40A limit

// How much torque is available for acceleration?
double availableTorque = driveMotor.getTorque(currentDraw) - torqueLoss;
```

Starting from state[0] (robot at rest, velocity = 0):

```
Step 1: lastVel = 0 m/s (robot is stopped)

Step 2: lastVelRadPerSec = 0 / 0.0508 = 0 rad/s

Step 3: getCurrent(0, 12.0) = 211 A
        ↑ At 0 rad/s the motor draws stall current (I = 12V / 0.057Ω = 211A)

Step 4: min(211, 40) = 40 A
        ↑ Clamped to our driveCurrentLimit (40A)

Step 5: getTorque(40) = Kt × 40 = 0.1152 × 40 = 4.607 Nm
        ↑ The motor's torque output at 40A

Step 6: availableTorque = 4.607 − 4.607 = 0.000 Nm  ← ZERO!
                          ─────   ─────
                            │       └── torqueLoss (computed in Section 6.3:
                            │           the clamped current at maxSpeed was
                            │           ALSO 40A → same torque: 4.607 Nm)
                            │
                            └── torque at stall, clamped to 40A (Step 5)
```

**Summary**: The stall current (211A) is clamped to our 40A limit. The
max-speed current (94.6A) was ALSO clamped to the same 40A limit when
`torqueLoss` was computed (Section 6.3). Same clamped current → same torque →
**zero available torque → zero acceleration → velocity stays at 0 forever.**

With zero velocity at every state, the time between states becomes
**infinite** — the robot can never reach the next position. PathPlanner
computes `time = distance / velocity = distance / 0 = Infinity`. Since
`Infinity` is not a usable number, PathPlanner's code skips the time
assignment, leaving `timeSeconds` at its default value of **0.0** for every
state. The result: `totalTime = 0.0 seconds`, and PathPlanner's `isFinished()`
check (`timer.hasElapsed(0.0)`) returns `true` immediately.

### 6.6 Why This is Even Worse Than "Can't Reach 3 m/s"

You asked: *"Doesn't PathPlanner recognize that it can NOT go 3 m/s at 40A?"*

It's actually **worse** than that. PathPlanner doesn't just fail to reach
3 m/s — it concludes the robot **cannot accelerate at all, from any speed**.

Here's why: at EVERY speed from 0 to 3 m/s, the motor draws MORE than 40A at
12V (see the table in Section 6.1). So at every speed, the current is clamped
to 40A, producing the same torque (4.607 Nm) — which equals `torqueLoss`.
Available torque is zero at every single speed.

PathPlanner doesn't explicitly "realize" this or print a warning. It just
computes the physics: zero available torque → zero acceleration → all
velocities remain at zero → the trajectory has no duration. The result is a
trajectory where the robot is at 12 distinct positions but with 0 velocity
and 0 time at each one — a physically impossible "teleportation" that
`FollowPathCommand` finishes instantly.

### 6.7 The Real Meaning of `maxDriveVelocityMPS`

PathPlanner's `ModuleConfig` documentation says:

> **maxDriveVelocityMPS**: "The max speed that the drive motor can reach
> while actually driving the robot at full output."

This is a **physical parameter** — the maximum speed the motor/gearbox/wheel
combination can physically achieve. It is NOT a software speed limit.

Our motor's physical maximum speed (at the wheel) is **5.35 m/s** — calculated
from the manufacturer's published free speed (710 rad/s) through our 6.75:1
gearbox (→ 105.2 rad/s) times our wheel radius (0.0508 m). We set
`maxDriveVelocityMPS` to **3.0 m/s**, which is our desired software speed
limit, not the physical maximum.

The software speed limit belongs in the **path file's constraints**
(`maxVelocity: 3.0` in `8FuelPath.path`), which PathPlanner applies separately
during trajectory generation to cap the actual planned speed.

### 6.8 How We Found This: JUnit Tests

This root cause was discovered by writing JUnit tests that call
`PathPlannerPath.generateTrajectory()` directly, bypassing the full simulation.
The test file is `src/test/java/frc/robot/auto/TrajectoryGenerationTest.java`.

**Key test results:**

1. **`testRobotConfigValues`** — PASSED. Dumped all config values and confirmed
   `torqueLoss (4.607) < stallTorque (24.300)`. The config LOOKS sane at first
   glance, because the overall motor CAN produce more torque than `torqueLoss`.
   But the current limit clamps the operating point to exactly `torqueLoss`.

2. **`test8FuelPath_pathPointsAreDistinct`** — PASSED. The path has 12 points
   spanning 2.09 meters. The path geometry is fine.

3. **`testDriveTestPath_noFlip`** — FAILED. Even the simplest straight-line
   path produces `totalTime=0.0` and zero velocities at every state. This
   proved the bug is in the config, not in path geometry or flipping.

4. **`test8FuelPath_noFlip`**, **`_flipped`**, **`_mirrored`**,
   **`_mirroredThenFlipped`** — ALL FAILED. Every variant produces
   `totalTime=0.0`. This proved the bug is independent of path transformation.

5. **`testRootCause_currentLimitCausesZeroAcceleration`** — The definitive
   test. It:
   - Reproduced PathPlanner's `torqueLoss` calculation step by step
   - Proved `clampedCurrent == clampedStallCurrent == 40A` → `availableTorque = 0`
   - **Fix A** (set maxSpeed to 5.35): `totalTime = 1.69s` ✅
   - **Fix B** (set currentLimit to 120A): `totalTime = 1.72s` ✅

### 6.9 The Fix

#### Fix (in `Constants.java`): Set `maxDriveVelocityMPS` to the motor's physical maximum

**Before:**
```java
public static final ModuleConfig MODULE_CONFIG = new ModuleConfig(
    SwerveConstants.wheelDiameter / 2,
    SwerveConstants.maxSpeed,                      // 3.0 m/s ← SOFTWARE limit, not physical
    1.2,
    DCMotor.getNeoVortex(1).withReduction(SwerveConstants.driveGearRatio),
    SwerveConstants.driveContinuousCurrentLimit,
    1);
```

**After:**
```java
// maxDriveVelocityMPS must be the PHYSICAL max speed of the motor+gearbox+wheel,
// NOT the software speed limit. The software limit comes from the path file's
// maxVelocity constraint. Using the motor's theoretical free speed:
double physicalMaxSpeed = DCMotor.getNeoVortex(1)
    .withReduction(SwerveConstants.driveGearRatio)
    .freeSpeedRadPerSec * (SwerveConstants.wheelDiameter / 2);  // = 5.35 m/s

public static final ModuleConfig MODULE_CONFIG = new ModuleConfig(
    SwerveConstants.wheelDiameter / 2,
    physicalMaxSpeed,                              // 5.35 m/s ← PHYSICAL max
    1.2,
    DCMotor.getNeoVortex(1).withReduction(SwerveConstants.driveGearRatio),
    SwerveConstants.driveContinuousCurrentLimit,
    1);
```

**Why this works:**
- At 5.35 m/s (free speed), the motor draws only 3.6A → `torqueLoss = getTorque(3.6) = 0.41 Nm`
- At stall (0 m/s), current is clamped to 40A → `getTorque(40) = 4.607 Nm`
- `availableTorque = 4.607 − 0.41 = 4.20 Nm` → robot accelerates normally
- Trajectory generates correctly: **totalTime = 1.69 seconds**, 12 states
  with velocities ramping up to 2.38 m/s and back down to 0

**No other files need to change.** The path files' `maxVelocity: 3.0`
constraint still limits the robot to 3 m/s during path following.

### 6.10 Relationship to Earlier Findings

The `ROBOT_CONFIG` parameter mismatch is the **primary root cause** of the
instant PathPlanner termination. It affects ALL paths, ALL transformations
(flip, mirror, no-op), and ALL starting conditions.

The bugs identified in Sections 2–4 (pigeon yaw latency, odometry heading
corruption, pigeon doubling) are **real but secondary**:

| Bug | Still real? | Impact with Config fixed |
|-----|-----------|--------------------------|
| `hardResetPose` passes wrong `gyroAngle` | Yes | Would cause 1-2 cycles of wrong heading, but PathPlanner would recover since the trajectory is now 1.7s long |
| Pigeon yaw doubling in SimulationManager | Yes | Would cause ongoing heading error during path following in simulation |
| `pigeon.setYaw()` latency in CTRE sim | Yes | Would cause temporary heading glitch, compensated by Fix A from Section 4 |

**All three bugs should still be fixed**, but they would not cause instant
termination once the config is corrected.

---

## 7. Final Summary

### The Bug

PathPlanner's `FollowPathCommand` finished after exactly one `execute()` cycle,
commanding enormous speeds (~7 m/s, ~10 rad/s) for a single 20ms frame before
zeroing the motors. The robot never moved.

### Root Cause

In `Constants.java`, `ModuleConfig.maxDriveVelocityMPS` was set to **3.0 m/s**
(a software speed limit) instead of the motor's physical maximum of **5.35 m/s**.
At 3.0 m/s, the NEO Vortex motor draws 94.6A — well above our 40A current
limit. PathPlanner's trajectory generator clamped both the "max-speed current"
and the "stall current" to the same 40A, producing identical torque values. The
result: `availableTorque = 0 Nm` → zero acceleration → zero velocity at every
state → `totalTime = 0.0s` → `isFinished()` returns true immediately.

### The Fix

Changed `ModuleConfig`'s `maxDriveVelocityMPS` from `SwerveConstants.maxSpeed`
(3.0 m/s, the teleop software limit) to the motor's theoretical free speed at
the wheel (~5.35 m/s, computed from manufacturer specs). This is a one-line
change in `Constants.java`. The teleop speed limit (`maxSpeed = 3`) is
unchanged, and path files still enforce their own `maxVelocity` constraints.

### How We Found It

1. **Trace logging** (`sim-output.log`, `sim-output2.log`) revealed the
   one-cycle termination and showed that even with correct heading,
   PathPlanner still finished immediately
2. **Trajectory tracing** (`sim-output3.log`) showed `totalTime = 0.0s` for
   all generated trajectories, ruling out heading/flipping issues
3. **JUnit tests** (`TrajectoryGenerationTest.java`) called
   `PathPlannerPath.generateTrajectory()` directly, proving that ALL paths
   produced `totalTime = 0.0` with the original config, and that changing
   `maxDriveVelocityMPS` to the physical free speed fixed it

### Verification

The fix was verified in three ways:

1. **JUnit tests** — all 8 tests pass, including one that proves the old
   config produces `totalTime = 0.0` and the new config produces
   `totalTime = 1.69s` with proper acceleration/deceleration profiles
2. **Simulation run** (`sim-output4-fixed.log`) — `autoDrive` now runs across
   multiple cycles with smoothly increasing velocities (0.015 → 0.032 →
   0.099 m/s over the first 3 execute cycles), `isFinished() = false` at
   each check, and the trajectory has `totalTime = 1.78s` (13 states)
3. **Visual confirmation** — the robot moves along the path in the simulator

---

## 8. Remaining Issues to Fix

The following bugs were identified during this investigation. They are **not**
responsible for the instant termination (now fixed), but they will cause
problems during longer autonomous paths in simulation.

> **Branch context**: The `MODULE_CONFIG` fix (Section 6.9) and
> `TrajectoryGenerationTest` unit tests were applied to the
> `jmm-sim-test-support` branch. All other changes below — including the
> `hardResetPose` infrastructure, SimulationManager pose sync, field
> dimensions, EightLemonAuto coordinates, and trace instrumentation — exist
> **only** on the `jmm-auto-mode-debug` branch and have NOT been merged to
> `jmm-sim-test-support`.

### 8.1 `startAutoAt` passes wrong `gyroAngle` to `odometry.resetPosition()`

**Affects**: Both branches (different code, same underlying bug)

**Bug**: When resetting the robot's pose, the code passes
`newPose.getRotation()` (the **desired** heading) as `gyroAngle` to
`odometry.resetPosition()` instead of `getYaw()` (the **actual** pigeon
reading). Because `pigeon.setYaw()` does not take immediate effect (especially
in simulation), the stored gyro baseline is wrong. On the next `periodic()`,
`odometry.update()` computes a large heading delta and corrupts the rotation.

On `jmm-sim-test-support`, this code is in `startAutoAt()` directly:
```java
pigeon.setYaw(startPose2d.getRotation().getDegrees());
odometry.resetPosition(startPose2d.getRotation(), getPositions(), startPose2d);
//                      ^^^^^^^^^^^^^^^^^^^^^^^^^ should be getYaw()
```

On `jmm-auto-mode-debug`, the same bug exists inside `hardResetPose()`:
```java
Rotation2d gyroAngle = updatePigeon ? newPose.getRotation() : getYaw();
//                                    ^^^^^^^^^^^^^^^^^^^^^ should be getYaw()
```

**Impact**: 1-2 cycles of incorrect heading at the start of autonomous. With
the config fix applied, PathPlanner's trajectory is long enough (~1.7s) to
recover, but the initial heading error causes unnecessary corrective maneuvers.

**Fix**: Always use `getYaw()` as the gyroAngle:
```java
Rotation2d gyroAngle = getYaw();  // actual pigeon reading, not desired heading
```
This is correct on both real hardware and in simulation.

### 8.2 SimulationManager does not sync pose after `startAutoAt`

**Affects**: `jmm-sim-test-support` branch

**Bug**: On `jmm-sim-test-support`, `SimulationManager` has no mechanism to
detect when `startAutoAt` (or `zeroGyro`) resets the robot's pose. Its internal
`simPose` stays at `(0, 0, 0°)` after `startAutoAt` places the robot at, say,
`(13.0, 0.94, 271.82°)`. On the next `simulationPeriodic()` call,
`pigeonSimState.setRawYaw(simPose.rotation)` overwrites the pigeon with 0°
(from the stale `simPose`), undoing the `pigeon.setYaw()` call in `startAutoAt`.

On `jmm-auto-mode-debug`, this was partially addressed by adding:
- `pendingSimPoseReset` field on `SwerveSubsystem`
- `consumeSimPoseReset()` method
- `SimulationManager` consuming the reset each cycle

However, the pigeon yaw **doubling** bug (8.3) still exists on that branch.

**Fix**: Port the `consumeSimPoseReset` infrastructure from
`jmm-auto-mode-debug` to `jmm-sim-test-support`, then also fix the doubling
(8.3).

### 8.3 Pigeon yaw doubling in SimulationManager

**Affects**: `jmm-auto-mode-debug` branch (on `jmm-sim-test-support`, the sim
doesn't sync pose at all — see 8.2 — so doubling doesn't occur, but a
different and worse bug occurs instead)

**Bug**: `pigeon.setYaw(X)` creates an internal offset of +X, and then
`pigeonSimState.setRawYaw(X)` sets a raw value. The reported yaw = rawYaw +
offset = X + X = **2X**. For example, a 180° heading becomes 360° (≡ 0°).

**Impact**: Ongoing heading error during path following in simulation. The
odometry heading will be wrong by the original setYaw value for the entire
autonomous period.

**Fix**: When `SimulationManager` consumes a pose reset, clear the pigeon
offset before setting the raw yaw:
```java
pigeonSimState.setRawYaw(0);
swerveSubsystem.getPigeon().setYaw(0);    // clears the offset
pigeonSimState.setRawYaw(desiredYawDeg);  // set the actual value
```

### 8.4 `pigeon.setYaw()` latency in CTRE simulation

**Affects**: Both branches

**Characteristic**: `pigeon.setYaw()` does not take immediate effect in CTRE
simulation. The yaw value remains stale for 1-2 cycles before the new value
appears. This is a CTRE library behavior, not our bug.

**Impact**: Temporary heading glitch during the first 1-2 cycles after a pose
reset. Fix 8.1 makes the system robust against this latency, so no additional
code change is needed specifically for this issue.

### 8.5 `ROBOT_CONFIG` module order may not match `SwerveDriveKinematics`

**Affects**: `jmm-sim-test-support` branch

**Bug**: On `jmm-sim-test-support`, `ROBOT_CONFIG` is constructed with module
order `FL, FR, BL, BR`. On `jmm-auto-mode-debug`, this was changed to
`FL, FR, BR, BL` with a comment: "Module order must match
SwerveDriveKinematics: FL, FR, BR, BL." If the orders don't match, PathPlanner
will associate module physics with the wrong physical wheel positions.

**Fix**: Verify the `swerveKinematics` construction order in `Constants.java`
and ensure `ROBOT_CONFIG` uses the same order.

### 8.6 Field dimensions do not match 2026 official values

**Affects**: `jmm-sim-test-support` branch

**Bug**: `FieldConstants.FIELD_LENGTH` = 17.548 m and `FIELD_WIDTH` = 8.052 m.
On `jmm-auto-mode-debug`, these were updated to the 2026 official dimensions:
`FIELD_LENGTH` = 16.54 m and `FIELD_WIDTH` = 8.07 m with the comment: "Must
match PathPlanner's FlippingUtil defaults so alliance flipping is consistent."

**Impact**: Alliance flipping (`flipForAlliance`) computes mirrored positions
using `(FIELD_LENGTH − x, FIELD_WIDTH − y)`. If these constants don't match
PathPlanner's internal field size, the flipped path start won't match the
flipped `startAutoAt` position, causing a position mismatch at the start of
autonomous.

**Fix**: Update `FIELD_LENGTH` and `FIELD_WIDTH` to match the 2026 official
field dimensions and PathPlanner's `FlippingUtil` defaults.

### 8.7 `EightLemonAuto` coordinates and heading don't match path file

**Affects**: `jmm-sim-test-support` branch

**Bug**: `EightLemonAuto` calls `startAutoAt(3.53, 7.13, -130.45)` but the
`8FuelPath.path` file defines:
- First anchor: `(3.5355, 7.1300)`
- `idealStartingState.rotation`: `91.82°`

On `jmm-auto-mode-debug`, this was corrected to
`startAutoAt(3.5355, 7.1300, 91.82)`.

**Impact**: The robot starts at a slightly wrong position and a completely
wrong heading (-130.45° vs 91.82°), causing PathPlanner to compute corrective
maneuvers at the start of the path.

**Fix**: Update `EightLemonAuto` coordinates and heading to match the path
file's first anchor and `idealStartingState.rotation`.

### 8.8 Centralized pose reset infrastructure not on `jmm-sim-test-support`

**Affects**: `jmm-sim-test-support` branch (architecture improvement)

On `jmm-auto-mode-debug`, all pose resets (`startAutoAt`, `zeroGyro`,
`resetOdometry`) were refactored to flow through a centralized
`hardResetPose()` method that handles pigeon, odometry, and sim notification
in one place. This prevents future bugs where one code path forgets to notify
the simulation.

On `jmm-sim-test-support`, each reset path (`startAutoAt`, `zeroGyro`,
`resetOdometry`) independently calls `pigeon.setYaw()` and/or
`odometry.resetPosition()` with no sim notification.

**Fix**: Port `hardResetPose()`, `hardResetHeading()`, and
`consumeSimPoseReset()` from `jmm-auto-mode-debug` to `jmm-sim-test-support`,
then fix the gyroAngle bug (8.1) in the centralized method.

### 8.9 Remove trace instrumentation (`jmm-auto-mode-debug` only)

**Affects**: `jmm-auto-mode-debug` branch only (not on `jmm-sim-test-support`)

**Files**: `SwerveSubsystem.java`, `RobotContainer.java`, `Robot.java`,
`SimulationManager.java`, `DriveTestAuto.java`, `EightLemonAuto.java`,
`TracedCommand.java`, `TraceLogger.java`

**Task**: The trace logging and `TracedCommand` wrappers were added for
debugging. They should be removed (or gated behind a debug flag) before
competition. All instrumented sections are delimited with
`// --- TRACE INSTRUMENTATION ---` and `// --- END TRACE ---` comments.

### 8.10 Restore `getAutonomousCommand()` to use selected auto mode (`jmm-auto-mode-debug` only)

**Affects**: `jmm-auto-mode-debug` branch only (not on `jmm-sim-test-support`)

**File**: `RobotContainer.java`

**Task**: The `getAutonomousCommand()` method was temporarily modified to always
return `DriveTestAuto` for testing. Restore it to use the `autoModeChooser`
selection:
```java
// Remove this line:
selected = AutoConstants.AutoMode.DriveTestAuto; // FOR TESTING
```

### Priority

| Issue | Branch(es) | Priority | Difficulty |
|-------|-----------|----------|-----------|
| 8.7 Fix EightLemonAuto coordinates/heading | sim-test-support | **High** (wrong start pose) | Trivial |
| 8.6 Fix field dimensions | sim-test-support | **High** (wrong alliance flip) | Trivial |
| 8.5 Fix ROBOT_CONFIG module order | sim-test-support | **High** (verify & fix) | Trivial |
| 8.1 Fix gyroAngle in pose reset | Both | **High** (affects auto accuracy) | Easy |
| 8.8 Port centralized pose reset | sim-test-support | **High** (prerequisite for 8.2/8.3 fixes) | Medium |
| 8.2 SimulationManager pose sync | sim-test-support | **Medium** (sim-only) | Easy (after 8.8) |
| 8.3 Fix pigeon yaw doubling | debug (after 8.8 on sim-test) | **Medium** (sim-only) | Easy |
| 8.10 Restore auto mode selection | debug only | **Medium** (debug cleanup) | Trivial |
| 8.9 Remove trace instrumentation | debug only | **Medium** (debug cleanup) | Easy but tedious |
| 8.4 CTRE pigeon latency | Both | **Low** (mitigated by 8.1) | N/A (library behavior) |

## 9. Odometry Reset Diagnostic Test Results

### 9.1 Test Overview

Two tests were created to investigate issues 8.1 and 8.2:

| Test | Type | Location | Branch(es) | Purpose |
|------|------|----------|------------|---------|
| `OdometryResetTest.java` | JUnit | `src/test/java/frc/robot/sim/` | `jmm-auto-mode-debug` only | Code-as-documentation: demonstrates the math bug using pure WPILib `SwerveDrivePoseEstimator` with no hardware dependencies. Does **not** test actual robot code. |
| `OdometryResetTestCommand.java` | DiagnosticTest (simulator) | `src/main/java/frc/robot/test/` | Both branches | Runs in the full simulator with real pigeon latency, SimulationManager, and SwerveSubsystem. Exercises the actual buggy code path. |

The JUnit test was moved to the debug branch because it serves only as
documentation — it cannot detect regressions in robot code since it doesn't
call any robot code. The `OdometryResetTestCommand` is on both branches as a
genuine diagnostic tool.

### 9.2 OdometryResetTestCommand Design

The test runs multiple trials (default 10), each with two phases:

**Phase 1 — Reset & Observe** (10 cycles):
- Resets the robot pose to `(5.0, 4.0, 90°)` using the same buggy code as
  `startAutoAt()`: `pigeon.setYaw(desired)` + `odometry.resetPosition(desired, ...)`
- Observes whether odometry heading is corrupted on subsequent cycles
- Measures pigeon latency (how many cycles before pigeon catches up)

**Phase 2 — Closed-Loop L-Path Drive**:
- Resets to a *different* heading (`0°`) to trigger genuine pigeon latency
  (pigeon must transition from 90° to 0°)
- Drives a 1m forward leg + 1m right-turn leg using proportional control
- Records lateral deviation from ideal path and final position error

### 9.3 Results (10-Trial Run)

From `docs/logs/odo-reset-test.log` (run on `jmm-sim-test-support` branch):

```
Trial | Corrupted | MaxHdgErr | PigeonLat | MaxLatDev | FinalPosErr | FinalHdgErr
------+-----------+-----------+-----------+-----------+-------------+------------
   1  |    1/10   |   90.00°  |    YES    |  0.0473m  |    0.0464m   |     0.90°
   2  |    0/10   |    0.00°  |    YES    |  0.0470m  |    0.0473m   |     0.92°
   3  |    0/10   |    0.00°  |    YES    |  0.0469m  |    0.0467m   |     0.90°
   4  |    0/10   |    0.00°  |    YES    |  0.0468m  |    0.0469m   |     0.91°
   5  |    0/10   |    0.00°  |    YES    |  0.0474m  |    0.0466m   |     0.90°
   6  |    0/10   |    0.00°  |    YES    |  0.0472m  |    0.0467m   |     0.91°
   7  |    0/10   |    0.00°  |    YES    |  0.0472m  |    0.0468m   |     0.91°
   8  |    0/10   |    0.00°  |    YES    |  0.0474m  |    0.0467m   |     0.91°
   9  |    0/10   |    0.00°  |    YES    |  0.0471m  |    0.0471m   |     0.92°
  10  |    0/10   |    0.00°  |    YES    |  0.0474m  |    0.0468m   |     0.91°
```

**Aggregate statistics:**
- Trials with heading corruption: **1/10** (10%) — first trial only
- Pigeon latency present: **10/10** (100%) — `pigeon.setYaw()` never instant
- Average max lateral deviation: **0.0472m** (~4.7cm)
- Worst final position error: **0.0473m** (~4.7cm)
- Worst final heading error: **0.92°**

### 9.4 Key Findings

1. **The heading corruption bug (8.1) is real but appears only on the first
   trial.** After Trial 1, the pigeon has already settled at 90° from the
   Phase 2→Phase 1 transition (−89° → 90°). The CTRE sim processes the
   `setYaw()` within the inter-trial gap, so Trials 2–10 show zero corruption.
   Trial 1 is unique because the pigeon starts at 0° (never been set before).

2. **Pigeon latency is 100% consistent.** Every Phase 2 reset shows
   `pigeonYaw after=90°` when the target was `0°`, confirming that
   `pigeon.setYaw()` does not take effect within the same cycle in CTRE
   simulation.

3. **Despite pigeon latency, the SimulationManager corrects it within 1 cycle.**
   By the first drive sample (`L1 C 0`), the pigeon and odometry heading are
   already at the correct value (0°). This means `SimulationManager.simulationPeriodic()`
   overwrites the pigeon with the correct heading from `simPose` before the
   drive loop reads it.

4. **The ~0.047m lateral deviation is controller tracking error, not bug-related.**
   It appears consistently across all 10 trials (including those with zero
   heading corruption) and is caused by the proportional-only controller's
   overshoot during the 90° turn in Leg 2. The deviation is identical whether
   or not heading corruption occurred.

5. **Practical impact in simulation: negligible.** The bug causes a 1-cycle
   heading glitch that is immediately corrected. Longer drive distances would
   not reveal additional problems, as the pigeon catches up within one cycle
   and the closed-loop controller handles the remainder.

### 9.5 Conclusion

The `odometry.resetPosition()` bug (8.1) is **mathematically real** but has
**negligible practical impact in the CTRE simulator** because:
- `SimulationManager` overwrites the pigeon each cycle, masking the latency
- The bug manifests as a single corrupted cycle that self-corrects

On **real hardware**, the impact may differ — pigeon latency could be shorter
(no sim frame delay) or longer (CAN bus congestion). The fix (use `getYaw()`
instead of desired heading) is still recommended as it eliminates the bug
regardless of latency behavior.
