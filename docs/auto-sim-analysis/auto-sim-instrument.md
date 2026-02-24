# Instrumentation Task: Autonomous Simulation Runtime Analysis

## Context

Read `docs/auto-sim-analysis.md` first — it contains the full history of this
investigation, including the symptom, what we've tried, what worked, what didn't,
and what we still don't know.

**TL;DR**: When autonomous mode starts in simulation, PathPlanner's `FollowPathCommand`
runs for exactly ONE cycle, commands huge speeds, then finishes. We've been doing
static analysis (reading code, forming hypotheses, applying fixes) for five iterations
and the core issue is unchanged. We need to switch to **runtime analysis** — instrument
the code to see exactly what happens, in what order, with what values.

## Goal

Add comprehensive instrumentation logging to trace the **exact runtime execution flow**
from the moment autonomous mode starts until PathPlanner finishes (which currently
takes ~1 cycle). The output should let us:

1. **Determine the exact execution order** — which methods are called in what sequence,
   within which WPILib lifecycle callback
2. **See the values at every decision point** — what does PathPlanner see when it
   decides to finish? What does odometry report? What does the pigeon report?
3. **Identify the boundary between our code and 3rd-party code** — when execution
   enters a library method we can't instrument (PathPlanner, WPILib, CTRE), log the
   inputs going in and the outputs/state coming out

## Instrumentation Rules

### Easy Removal

All instrumentation code MUST be trivial to find and remove after the analysis is
complete. Follow these rules strictly:

1. **Every instrumentation line** (imports, field declarations, method calls) MUST be
   on its own line(s) and bracketed by comments:
   ```java
   // --- TRACE INSTRUMENTATION ---
   TraceLogger.log("SwerveSubsystem.periodic", "ENTER");
   // --- END TRACE ---
   ```

2. **The `TraceLogger` utility class** should be in its own file
   (`src/main/java/frc/robot/util/TraceLogger.java`) so it can be deleted in one step.

3. **Do NOT modify existing lines of code.** Add trace lines *between* existing lines.
   Never combine a trace call with a functional code change on the same line.

4. After the analysis, a single `grep -r "TRACE INSTRUMENTATION" --include="*.java"`
   will find every instrumented location, and the blocks between `--- TRACE
   INSTRUMENTATION ---` and `--- END TRACE ---` can be deleted to restore the code
   exactly to its pre-instrumentation state.

5. **Remove the existing temporary diagnostics** (prefixed `[SWERVE-DEBUG]`,
   `[SIM-DEBUG]`, `[DRIVE-DEBUG]`, and the plain `println` in `consumeSimPoseReset`)
   as part of adding the new instrumentation. These are NOT bracketed with removal
   markers and have been cluttering the code. Replace them with properly bracketed
   trace lines. This cleanup is part of the instrumentation task.

### Logging Format

All instrumentation lines MUST use this format:
```
[TRACE-nnn] LOCATION | message
```

Where:
- `nnn` is a monotonically increasing sequence number (use a shared static counter)
- `LOCATION` is the class and method name (e.g., `SwerveSubsystem.periodic`)
- `message` contains the relevant data

Example:
```
[TRACE-001] Robot.autonomousInit | ENTER
[TRACE-002] RobotContainer.getAutonomousCommand | creating EightLemonAuto
[TRACE-003] Robot.autonomousInit | scheduling command: EightLemonAuto
[TRACE-004] Robot.robotPeriodic | ENTER — calling CommandScheduler.run()
[TRACE-005] SwerveSubsystem.periodic | ENTER — getYaw()=0.00° odoPose before update=(0,0,0°)
[TRACE-006] SwerveSubsystem.periodic | after odometry.update() — odoPose=(0,0,0°)
[TRACE-007] SwerveSubsystem.periodic | EXIT
...
```

### Shared Sequence Counter

Create a simple utility class for the shared counter:

```java
// src/main/java/frc/robot/util/TraceLogger.java
public class TraceLogger {
    private static int seq = 0;
    public static void log(String location, String message) {
        System.out.printf("[TRACE-%03d] %s | %s%n", seq++, location, message);
    }
    public static void reset() { seq = 0; }
}
```

### 3rd-Party Method Boundaries

When execution calls a method from a 3rd-party library (PathPlanner, WPILib,
CTRE Phoenix), we CANNOT instrument the library's internal code. Instead, log
**before and after** the call with inputs and outputs:

```java
// BEFORE calling 3rd-party method
TraceLogger.log("SwerveSubsystem.periodic",
    "CALLING odometry.update() — gyroAngle=" + getYaw() + " positions=" + Arrays.toString(getPositions()));
odometry.update(getYaw(), getPositions());
// AFTER calling 3rd-party method
TraceLogger.log("SwerveSubsystem.periodic",
    "RETURNED from odometry.update() — odoPose=" + odometry.getEstimatedPosition());
```

Mark these clearly so the reader knows the gap represents opaque 3rd-party execution:
```
[TRACE-042] SwerveSubsystem.periodic | >>> ENTERING 3RD-PARTY: odometry.update(gyro=271.82°, positions=[...])
[TRACE-043] SwerveSubsystem.periodic | <<< RETURNED FROM 3RD-PARTY: odometry.update() → odoPose=(13.00, 0.94, 0.00°)
```

### What to Instrument

Instrument **every method** in the execution path from `autonomousInit()` through
the first few cycles of autonomous. Specifically:

#### 1. `Robot.java`
- `autonomousInit()` — ENTER/EXIT, what command is created and scheduled
- `robotPeriodic()` — ENTER/EXIT (before and after `CommandScheduler.run()`)
- `simulationPeriodic()` — ENTER/EXIT (before and after `simManager.simulationPeriodic()`)

#### 2. `RobotContainer.java`
- `getAutonomousCommand()` — what auto mode is selected, what command is returned

#### 3. `SwerveSubsystem.java`
- `periodic()` — ENTER/EXIT, pigeon yaw before/after `odometry.update()`, odoPose
  before/after
- `hardResetPose()` — all parameters, pigeon yaw before/after `pigeon.setYaw()`,
  odoPose before/after `odometry.resetPosition()`
- `startAutoAt()` — the lambda execution (when does it actually run?)
- `autoDrive()` — path loading, what `AutoBuilder.followPath()` returns
- `driveFromChassisSpeeds()` — speeds and openLoop flag
- `resetOdometry()` — pose parameter (PathPlanner may call this)
- `getPose()` — what it returns (PathPlanner calls this frequently)
- `getChassisSpeeds()` — what it returns (PathPlanner calls this)
- `getYaw()` — raw pigeon value and returned value
- `configurePathPlanner()` — confirm this runs during construction

#### 4. `SimulationManager.java`
- `simulationPeriodic()` — ENTER/EXIT, consumed reset, dt, desired speeds,
  simPose before/after integration, pigeon value set, encoder values set

#### 5. `EightLemonAuto.java` / `DriveTestAuto.java`
- Constructor — log when `addCommands` is called (this runs during `autonomousInit`
  when the command is constructed)

### Cycle and Phase Tracking

The `TraceLogger` should track the current **phase** and **cycle number** to help
segment the output during analysis. Phases correspond to WPILib lifecycle states:

```java
public class TraceLogger {
    private static int seq = 0;
    private static String currentPhase = "INIT";
    private static int cycleInPhase = 0;

    public static void log(String location, String message) {
        System.out.printf("[TRACE-%03d] [%s cycle=%d] %s | %s%n",
            seq++, currentPhase, cycleInPhase, location, message);
    }

    public static void setPhase(String phase) {
        currentPhase = phase;
        cycleInPhase = 0;
        log("TraceLogger", "=== PHASE CHANGE: " + phase + " ===");
    }

    public static void incrementCycle() { cycleInPhase++; }
    public static int getCycleInPhase() { return cycleInPhase; }
    public static String getPhase() { return currentPhase; }
    public static void reset() { seq = 0; currentPhase = "INIT"; cycleInPhase = 0; }
}
```

Call `TraceLogger.setPhase("AUTONOMOUS")` in `Robot.autonomousInit()` (before
scheduling the command), and `TraceLogger.incrementCycle()` at the top of
`Robot.robotPeriodic()`.

Example output with phases:
```
[TRACE-047] [AUTONOMOUS cycle=0] Robot.robotPeriodic | ENTER
[TRACE-048] [AUTONOMOUS cycle=0] SwerveSubsystem.periodic | ENTER
...
[TRACE-071] [AUTONOMOUS cycle=0] Robot.simulationPeriodic | EXIT
[TRACE-072] [AUTONOMOUS cycle=1] Robot.robotPeriodic | ENTER
```

### Cycle Limiting

**We only care about autonomous mode.** The robot starts in disabled mode and may
run several cycles before the user switches to autonomous. Comprehensive logging
should be **limited to the AUTONOMOUS phase** — specifically the first 5 cycles
after `autonomousInit()` fires. During other phases (DISABLED, TELEOP), either
don't log at all or log only phase transitions.

Use the phase and cycle tracking to manage this:
- During `AUTONOMOUS` phase, cycles 0–4: log everything
- During `AUTONOMOUS` phase, cycle 5+: stop logging (or one summary line per cycle)
- During other phases: log only `setPhase()` transitions

Err on the side of **over-instrumenting within those 5 autonomous cycles.** Since
the issue manifests in the very first cycle, even 5 cycles is generous. The bounded
window means even verbose logging produces a manageable amount of output.

### Important: `getPose()` and `getChassisSpeeds()` are called by PathPlanner

PathPlanner's `FollowPathCommand` calls `getPose()` and `getChassisSpeeds()` (via
the suppliers registered in `configurePathPlanner`). These calls happen INSIDE
PathPlanner's `execute()` method. By instrumenting `getPose()` and
`getChassisSpeeds()`, we can see what values PathPlanner is working with, even
though we can't instrument PathPlanner itself.

Similarly, PathPlanner drives the robot by calling the drive consumer lambda
`(speeds, feedforwards) -> driveFromChassisSpeeds(speeds, false)`. By instrumenting
`driveFromChassisSpeeds`, we see PathPlanner's output.

And PathPlanner may call `resetOdometry()` (the pose resetter registered in
`configurePathPlanner`). Instrumenting that tells us if PathPlanner is resetting
the pose.

### Important: Replace Existing Diagnostics

The code already contains temporary diagnostic logging (see "Existing Temporary
Diagnostic Logging" section in `auto-sim-analysis.md`). **Replace** all existing
`[SWERVE-DEBUG]`, `[SIM-DEBUG]`, `[DRIVE-DEBUG]` logging with the new unified
`[TRACE-nnn]` format. Don't have two logging systems running simultaneously.

## Expected Output

The console output (which will be redirected to a file) should tell a complete story.
Reading it top-to-bottom should answer:

1. What is the exact sequence of method calls from `autonomousInit` through the
   first few `robotPeriodic` cycles?
2. When does `startAutoAt`'s lambda actually execute? Before or after `periodic()`?
3. What does `pigeon.getYaw()` return at each point? Does `pigeon.setYaw()` take
   effect immediately or is it deferred?
4. What does `odometry.getEstimatedPosition()` return before and after each
   `update()` and `resetPosition()` call?
5. What values does PathPlanner see when it calls `getPose()` and `getChassisSpeeds()`?
6. What speeds does PathPlanner command via `driveFromChassisSpeeds()`?
7. When does `SimulationManager.simulationPeriodic()` run relative to everything else?
8. Where exactly do 3rd-party library calls happen, and what are their inputs/outputs?

## Console Output Notes

- The console output will be redirected to a file for analysis
- 3rd-party libraries (WPILib, PathPlanner, CTRE Phoenix) may also print to the
  console — their output will be interspersed with our `[TRACE-nnn]` lines
- Our trace lines are easily filterable by the `[TRACE-` prefix
- The sequence numbers allow reconstructing the exact order even if output from
  multiple sources is interleaved

## After Instrumentation

After adding the instrumentation:
1. Run `./gradlew simulateJava` and redirect output to a file
2. Switch to Autonomous mode in the Sim GUI
3. Capture the output
4. Analyze the trace to build a complete picture of the execution flow
5. Identify the root cause of the issue
6. Create a flow chart or sequence diagram of what actually happens at runtime

## Design Principle

**Do NOT add simulation-specific branching (`RobotBase.isReal()`, `isSimulation()`)
to production code.** If simulation-specific behavior is needed, it should live
entirely in `SimulationManager`. The production code (`SwerveSubsystem`,
`Robot`, `RobotContainer`, auto commands) should be identical whether running on
the real robot or in simulation.
