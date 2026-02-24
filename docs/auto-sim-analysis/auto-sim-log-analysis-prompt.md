# Task: Analyze Autonomous Simulation Trace Log

## Context

We're debugging a FRC robot simulation where PathPlanner's `FollowPathCommand`
finishes after exactly one execute cycle, commanding enormous speeds (7+ m/s),
then stopping. We've already found and fixed 4 bugs (field dimensions, rotation
mismatch, module order) but the core symptom persists. We instrumented the code
with comprehensive trace logging to capture the exact runtime execution flow.
The trace output is now in `sim-output.log`.

## Files to Read (in this order)

1. **`docs/auto-sim-analysis.md`** — Full investigation history: symptom
   description, key files and their roles, how the autonomous command is
   structured, how PathPlanner is configured, how `hardResetPose`/`periodic`/
   `SimulationManager` work, all bugs found and fixed so far, what we know and
   don't know, and the latest console output from before instrumentation.

2. **`docs/auto-sim-instrument.md`** — The instrumentation specification: what
   was instrumented, the logging format, trace rules, what questions the trace
   should answer.

3. **`sim-output.log`** — The actual trace output captured from running the
   simulator and switching to Autonomous mode. This is the primary artifact to
   analyze.

4. **Source files** (read as needed during analysis):
   - `src/main/java/frc/robot/util/TraceLogger.java` — The trace logging utility
   - `src/main/java/frc/robot/Robot.java` — Entry point, lifecycle callbacks
   - `src/main/java/frc/robot/RobotContainer.java` — Command creation
   - `src/main/java/frc/robot/Subsystems/SwerveSubsystem.java` — Swerve drivetrain (most heavily instrumented)
   - `src/main/java/frc/robot/sim/SimulationManager.java` — Simulation physics
   - `src/main/java/frc/robot/Auto/EightLemonAuto.java` — The auto command being run
   - `src/main/java/frc/robot/Constants.java` — Configuration constants

## Trace Format

Every trace line follows this format:
```
[TRACE-nnn] [PHASE cycle=N] LOCATION | message
```
- `nnn` = global sequence number (monotonically increasing)
- `PHASE` = `INIT`, `DISABLED`, or `AUTONOMOUS`
- `cycle=N` = cycle count within the current phase
- `LOCATION` = class.method being traced
- 3rd-party library call boundaries are marked with `>>>` (entering) and `<<<` (returning)

The trace only emits verbose output during the first 5 autonomous cycles
(cycles 0–4). During DISABLED phase, only phase-change transitions are logged.

## What to Analyze

Read `auto-sim-analysis.md` and `auto-sim-instrument.md` first to understand
the full context and the questions we're trying to answer. Then analyze
`sim-output.log` to build a complete picture of what happens at runtime.

### Specific Questions to Answer

These come directly from the instrumentation spec (Section "Expected Output"):

1. **What is the exact sequence of method calls** from `autonomousInit` through
   the first few `robotPeriodic` cycles?

2. **When does `startAutoAt`'s lambda actually execute?** Before or after
   `periodic()`? (This determines whether odometry sees the reset pose before
   PathPlanner's first execute.)

3. **What does `pigeon.getYaw()` return at each point?** Does `pigeon.setYaw()`
   take effect immediately or is it deferred? (Look at the pigeon yaw value
   logged before and after `pigeon.setYaw()` in `hardResetPose`, and the yaw
   value passed to `odometry.update()` in `periodic()`.)

4. **What does `odometry.getEstimatedPosition()` return** before and after each
   `update()` and `resetPosition()` call?

5. **What values does PathPlanner see** when it calls `getPose()` and
   `getChassisSpeeds()`? (Look for `SwerveSubsystem.getPose` and
   `SwerveSubsystem.getChassisSpeeds` trace lines that appear BETWEEN
   `periodic` EXIT and `robotPeriodic` EXIT — those are PathPlanner calling
   them during command execution.)

6. **What speeds does PathPlanner command** via `driveFromChassisSpeeds()`?
   How many cycles does it command non-zero speeds before finishing?

7. **When does `SimulationManager.simulationPeriodic()` run** relative to
   everything else? (It runs AFTER `robotPeriodic`, so the pigeon sim state
   it writes is not visible until the NEXT cycle's `periodic()`.)

8. **Where exactly do 3rd-party library calls happen**, and what are their
   inputs/outputs?

### Key Mystery From Previous Analysis

From `auto-sim-analysis.md`, Section "What We Don't Know":

- **Why is `odoPose` rotation 0° right after `hardResetPose` set it to 271.82°?**
  The `hardResetPose` calls `odometry.resetPosition()` with the correct pose,
  and the trace confirms odometry reports the correct pose immediately after.
  But then `periodic()` calls `odometry.update()` and the rotation resets to 0°.
  WHY? Look at what yaw value `periodic()` passes to `odometry.update()` — if
  the pigeon hasn't been updated yet (because `SimulationManager` hasn't run),
  the gyro yaw will be stale/wrong, and the odometry estimator will "correct"
  the pose based on the stale gyro reading.

- **Why does `FollowPathCommand` finish after exactly 1 execute cycle?**
  Look at what `getPose()` returns when PathPlanner calls it. If the pose
  heading is 0° instead of 271.82°, PathPlanner may compute a trajectory
  that's essentially instant or invalid, causing immediate termination.

## Deliverables

After analyzing the trace, provide:

1. **A cycle-by-cycle narrative** — Walk through the trace output and explain
   what happens at each step, especially the first 3 autonomous cycles.

2. **Root cause identification** — Based on the trace evidence, identify why
   PathPlanner finishes after one cycle. Be specific about which values are
   wrong, why they're wrong, and the chain of causation.

3. **Execution order diagram** — Show the actual method call order within a
   single `robotPeriodic` cycle (e.g., `periodic()` → CommandScheduler executes
   commands → `simulationPeriodic()`), noting where the timing gap causes
   problems.

4. **Proposed fix** — Based on the root cause, propose a specific fix. Remember
   the design constraint: **no simulation-specific branching in production code**
   (`SwerveSubsystem`, `Robot`, etc.). Simulation-specific fixes belong in
   `SimulationManager`.

## Important Notes

- The trace sequence numbers start at 6939 for AUTONOMOUS because ~6938
  trace calls happened during the DISABLED phase (the `shouldLog()` method
  suppressed their output but the counter still incremented).
- 3rd-party output (WPILib warnings, CTRE Phoenix messages) is interleaved
  with trace lines — these may contain clues.
- The `openLoop=true` flag in `driveFromChassisSpeeds` indicates TeleopSwerve
  (the default command) has taken over — PathPlanner uses `openLoop=false`.
- Do NOT modify any code or instrumentation. This task is analysis only.
