# Simulation Approach Comparison

This document compares the two simulation implementations present in our codebase and places them in context alongside approaches used by other FRC teams.

---

## Table of Contents

1. [Overview of Our Two Approaches](#overview-of-our-two-approaches)
2. [Detailed Comparison](#detailed-comparison)
3. [External FRC Team Approaches](#external-frc-team-approaches)
4. [Summary Comparison Matrix](#summary-comparison-matrix)
5. [Analysis](#analysis)
6. [Appendix A — Feature Portability and Calculation Review](#appendix-a--feature-portability-and-calculation-review)
7. [Appendix B — SimulationManager Data Flow and Code Path Analysis](#appendix-b--simulationmanager-data-flow-and-code-path-analysis)

---

## Overview of Our Two Approaches

### `SimulationManager` — `jmm-sim-test-support` branch

**File**: `src/main/java/frc/robot/sim/SimulationManager.java` (154 lines)

`SimulationManager` is a standalone class in its own `sim` package.  It is instantiated in `Robot.simulationInit()` and called from `Robot.simulationPeriodic()`.  It operates externally to the subsystem — it reads desired module states from `SwerveSubsystem`, computes simulated robot motion via kinematic integration, then writes simulated values back into vendor hardware simulation APIs (Pigeon2 `SimState`, CANcoder `SimState`, REV `RelativeEncoder.setPosition()`).

**How it works:**

1. Reads desired swerve module states from `SwerveSubsystem.getDesiredStates()`, which in turn reads per-module `desiredState` fields stored by `SwerveModule.setDesiredState()`.
2. Converts module states to `ChassisSpeeds` via the swerve kinematics.
3. Integrates chassis speeds over `dt` using `Pose2d.exp(Twist2d)` to update an internal `simPose`.
4. Writes `simPose` heading into `Pigeon2SimState.setRawYaw()`.
5. Writes per-module drive distance and angle into each module's `RelativeEncoder.setPosition()` and `CANcoderSimState.setRawPosition()`.
6. Relies on the normal `SwerveSubsystem.periodic()` (which calls `odometry.update(getYaw(), getPositions())`) to read back these simulated sensor values and update odometry and Field2d as usual.

**Key design decisions:**

- Simulation logic is fully separated from production subsystem code.  `SwerveSubsystem` has no `if (isSimulation())` conditionals in its `periodic()` method — it always calls `odometry.update()` using whatever sensor values are present.
- The `SimulationManager` writes to the same sensor simulation APIs that vendors provide, so the subsystem's production code path runs identically in sim and on hardware.
- `SwerveModule` stores a `desiredState` field (written by `setDesiredState()`, read by `getDesiredState()`) specifically to give the simulation access to individual module targets.  This is the only production-code change required for simulation support.
- Additional accessor methods were added to `SwerveSubsystem` (`getDesiredStates()`, `getPigeon()`, `getModules()`, `getKinematics()`) for simulation and diagnostic test access.  These are grouped in a clearly marked "Simulation and Test Support Methods" section.

### `RobotSimulation` — `NonoAuto` branch

**File**: `src/main/java/frc/robot/RobotSimulation.java` (68 lines)

`RobotSimulation` is a top-level class that lives alongside `Robot.java` and `RobotContainer.java`.  It is instantiated in the `Robot` constructor (not lazily in `simulationInit()`) and delegates to `SwerveSubsystem` for the actual physics work.

**How it works:**

1. `simulationInit()` configures `DriverStationSim` state (attached, disabled, mode flags) and calls `drive.simulationReset()`.
2. `simulationPeriodic()` computes `dt`, then calls `drive.simulationUpdate(dtSeconds)`.
3. `SwerveSubsystem.simulationUpdate()` does the physics:
   - Reads `lastCommandedSpeeds` (stored by `driveFromChassisSpeeds()`) — or zero if disabled.
   - Integrates yaw via `simYawDegrees += Math.toDegrees(speeds.omegaRadiansPerSecond * dt)`.
   - Converts chassis speeds to module states, accumulates per-wheel position in `simWheelPositionsMeters[]` arrays.
   - Calls `pigeon.setYaw()` and `odometry.update()` directly with the computed positions.
4. Also simulates battery voltage via `BatterySim` based on estimated current draw from drive and rotation fractions.

**Key design decisions:**

- Simulation physics lives inside `SwerveSubsystem` itself.  The subsystem has dedicated simulation state fields (`simYawDegrees`, `simWheelPositionsMeters[]`, `simWheelAngles[]`, `lastCommandedSpeeds`).
- `SwerveSubsystem.periodic()` wraps its real odometry and vision update in `if (!RobotBase.isSimulation())` — in sim mode, `periodic()` only updates `Field2d`, while `simulationUpdate()` separately calls `odometry.update()` with simulated positions.
- The `RobotSimulation` class handles DriverStation sim configuration and battery voltage simulation, adding a layer of environmental realism.
- No vendor-specific SimState APIs are used.  Instead, the subsystem maintains its own parallel arrays of simulated positions and passes them directly to the odometry estimator.

---

## Detailed Comparison

### Architecture and Separation of Concerns

| Aspect | `SimulationManager` | `RobotSimulation` |
|--------|---------------------|-------------------|
| Where physics lives | Dedicated `sim/` package class | Inside `SwerveSubsystem` |
| Subsystem awareness of sim | None — no `isSimulation()` checks in `periodic()` | Yes — `periodic()` has `if (!RobotBase.isSimulation())` guard |
| Sensor simulation method | Writes to vendor SimState APIs (Pigeon2, CANcoder, REV encoders) | Maintains parallel state arrays, calls `odometry.update()` directly |
| Production code modifications | Adds `desiredState` field to `SwerveModule`, adds accessor methods to `SwerveSubsystem` | Adds `lastCommandedSpeeds`, `simYawDegrees`, `simWheelPositionsMeters[]`, `simWheelAngles[]` to `SwerveSubsystem`; adds `simulationReset()`, `simulationUpdate()`, `getLastCommandedSpeeds()` methods |
| Robot.java integration | Created in `simulationInit()`, called in `simulationPeriodic()` | Created in `Robot` constructor, init/periodic delegated |

**`SimulationManager` pros:**
- Production code path (`SwerveSubsystem.periodic()`) is identical in sim and on hardware.  There is no branching in the subsystem based on runtime environment.
- Writing to vendor SimState APIs means the full sensor-to-odometry pipeline is exercised in simulation, including any conversion factors, offsets, or configuration applied by the subsystem when reading sensors.
- The simulation can be removed entirely (delete the `sim/` package, remove two lines from `Robot.java`) without touching any subsystem code.

**`SimulationManager` cons:**
- Depends on vendor SimState APIs working correctly in desktop simulation.  REV `RelativeEncoder.setPosition()` behavior in sim is not officially documented and may not faithfully model real encoder behavior.
- Adds accessor methods (`getDesiredStates()`, `getPigeon()`, `getModules()`) to `SwerveSubsystem` that exist only for simulation and test support.  These widen the subsystem's public API surface.
- The `desiredState` field in `SwerveModule` is production code that exists solely for simulation access.

**`RobotSimulation` pros:**
- Does not depend on any vendor SimState APIs.  The simulation directly computes positions and passes them to the odometry estimator, avoiding any uncertainty about vendor sim fidelity.
- Battery voltage simulation (via `BatterySim`) adds environmental realism.
- DriverStation sim configuration (`setDsAttached`, `setEnabled`, etc.) provides a more complete sim environment setup.
- Fewer public accessor methods added to the subsystem.

**`RobotSimulation` cons:**
- `SwerveSubsystem.periodic()` has different code paths for sim vs. real.  In simulation, the real odometry update and vision fusion are skipped.  This means the sim does not exercise the same `periodic()` logic that runs on hardware.
- Simulation state fields (`simYawDegrees`, `simWheelPositionsMeters[]`, etc.) live inside `SwerveSubsystem`, adding member variables that are unused on hardware.
- The `lastCommandedSpeeds` field that simulation depends on is only set by `driveFromChassisSpeeds()`.  If any code path sets module states without going through `driveFromChassisSpeeds()` (e.g., individual module control during testing), those commands will not appear in simulation.

### Physics Model

Both approaches use the same fundamental simulation model: **kinematic integration of chassis speeds** (no force-based or motor-model physics).  Neither models motor dynamics, wheel slip, friction, inertia, or current draw affecting motor behavior.

| Aspect | `SimulationManager` | `RobotSimulation` |
|--------|---------------------|-------------------|
| Pose integration | `Pose2d.exp(Twist2d)` | Linear `yaw +=` and per-wheel position accumulation |
| Per-module simulation | Yes — individual module states drive encoders | Yes — module states derived from chassis speeds |
| Source of commanded motion | `SwerveModule.getDesiredState()` per module | `lastCommandedSpeeds` (chassis-level) |
| Disabled behavior | Modules report zero desired state (no special handling needed) | Explicitly checks `DriverStation.isDisabled()` and substitutes zero speeds |
| dt clamping | `if (dt <= 0 \|\| dt > 1.0) dt = 0.02` | `MathUtil.clamp(dt, 0.0, 0.05)` |

**Notable difference:** `SimulationManager` reads desired states at the per-module level, while `RobotSimulation` reads at the chassis-speeds level.  This matters for scenarios like diagnostic test commands that control individual modules — `SimulationManager` would simulate those correctly, while `RobotSimulation` would not (since `lastCommandedSpeeds` is only set by the normal drive path).

### Code Footprint

| Metric | `SimulationManager` | `RobotSimulation` |
|--------|---------------------|-------------------|
| Simulation class lines | 154 | 68 |
| SwerveSubsystem sim-related additions | ~65 lines (accessor methods section) | ~45 lines (fields + `simulationReset()` + `simulationUpdate()`) |
| SwerveModule changes | +1 field, +1 method (`getDesiredState()`) | None |
| Total sim-related code | ~220 lines | ~113 lines |
| `periodic()` changes | None | Added `if (!RobotBase.isSimulation())` guard |

---

## External FRC Team Approaches

### FRC 6328 — Mechanical Advantage: AdvantageKit IO Layer Pattern

**Repository**: [Mechanical-Advantage/RobotCode2025Public](https://github.com/Mechanical-Advantage/RobotCode2025Public)

**Key files:**
- `ModuleIO.java` — interface defining all module operations and a structured `ModuleIOInputs` class
- `ModuleIOComp.java` — real hardware implementation using CTRE TalonFX motors
- `ModuleIOSim.java` (126 lines) — simulation implementation using WPILib `DCMotorSim`
- `Drive.java` — subsystem that accepts `ModuleIO` implementations via constructor injection

**Architecture:**

Team 6328 uses the **IO layer pattern** from their AdvantageKit framework.  Every hardware interaction is defined through a Java interface (e.g., `ModuleIO`).  The subsystem (`Drive`) only interacts with these interfaces, never with concrete hardware classes.  At robot startup, the appropriate implementation is injected based on the robot type:

```
case COMPBOT -> new Drive(new GyroIOPigeon2(), new ModuleIOComp(...), ...);
case SIMBOT  -> new Drive(new GyroIO() {},      new ModuleIOSim(),    ...);
```

The `ModuleIOSim` class uses WPILib's `DCMotorSim` to model individual motor physics (voltage in → angular velocity/position out), including motor models, gear ratios, and moments of inertia.  The simulated motors respond to the same PID and feedforward commands that the real hardware would receive.

The subsystem's `periodic()` method and all command logic are identical regardless of whether real or simulated IO is plugged in.  There are zero `isSimulation()` checks in subsystem or command code.

**Pros:**
- Complete decoupling between robot logic and hardware.  The subsystem and all commands are tested against the exact same code path in sim and on hardware.
- Motor-level physics (via `DCMotorSim`) provide more realistic behavior than kinematic-only integration — PID tuning in sim has some correlation to real-world tuning.
- Log replay: AdvantageKit can replay logged inputs through the same code to reproduce and debug issues offline.
- Each subsystem (drive, elevator, climber, etc.) has its own `IOSim` class, enabling simulation of the full robot.

**Cons:**
- Requires significant upfront architectural investment.  Every hardware interaction must be abstracted through an interface, which approximately doubles the number of files per subsystem (interface + real impl + sim impl + inputs class).
- The `DCMotorSim` physics model is still an approximation — it does not model wheel-floor friction, tire slip, or robot-environment collisions.
- Adopting AdvantageKit is a team-wide decision that affects the entire codebase structure.  It is not something that can be added incrementally to a single subsystem.
- Steeper learning curve for team members unfamiliar with interface-based design and dependency injection.

**Reference**: [AdvantageKit Documentation](https://docs.advantagekit.org/)

### FRC 254 — The Cheesy Poofs: IO Layer + MapleSim Physics Engine

**Repository**: [Team254/FRC-2025-Public](https://github.com/Team254/FRC-2025-Public)

**Key files:**
- `DriveIO.java` — interface for drive subsystem operations
- `DriveIOHardware.java` — real hardware implementation extending CTRE `SwerveDrivetrain`
- `DriveIOSim.java` (117 lines) — simulation implementation that extends `DriveIOHardware` and integrates MapleSim
- `MapleSimSwerveDrivetrain.java` (275 lines) — adapter connecting MapleSim physics to CTRE device SimState APIs
- `SimulatedRobotState.java` (907 lines) — comprehensive simulation state manager handling game piece tracking, intake simulation, scoring simulation, and mechanism state

**Architecture:**

Team 254 uses an IO interface pattern similar to 6328, but with a different simulation backend.  Their `DriveIOSim` class extends `DriveIOHardware` (which wraps CTRE's `SwerveDrivetrain`) and replaces the CTRE built-in sim with a MapleSim-powered physics engine.

MapleSim (from the [maple-sim library](https://github.com/Shenzhen-Robotics-Alliance/maple-sim)) uses the [dyn4j](https://github.com/dyn4j/dyn4j) 2D rigid-body dynamics engine to simulate forces, collisions, and friction.  The simulated robot has mass, bumper dimensions, wheel coefficients of friction, and interacts with field elements and game pieces as rigid bodies in a physics world.

Their `MapleSimSwerveDrivetrain` class bridges MapleSim's physics output back into CTRE's vendor SimState APIs (`TalonFXSimState`, `CANcoderSimState`, `Pigeon2SimState`), so the real `SwerveDrivetrain` code processes simulated sensor data through the same pipeline it uses on hardware.

The `SimulatedRobotState` class goes further by simulating game-piece intake, indexing, scoring at reef branches, and climber mechanics — creating a nearly complete game simulation.

Team 254 supports both MapleSim and CTRE's built-in sim, toggled by a `Constants.useMapleSim` flag.

**Pros:**
- Force-based physics with collision detection enables testing autonomous paths that interact with field elements, game pieces, and field boundaries.
- Writing simulated values back through vendor SimState APIs means the full CTRE `SwerveDrivetrain` code path (including odometry, status signals, etc.) is exercised in sim.
- Game-piece simulation enables end-to-end autonomous testing including intake and scoring.
- MapleSim is available as a vendor dependency (no need to build from source).

**Cons:**
- Substantial complexity.  The simulation layer for Team 254 totals over 1,300 lines across `DriveIOSim`, `MapleSimSwerveDrivetrain`, and `SimulatedRobotState` — and that does not include the MapleSim library itself.
- Requires the IO interface pattern as a prerequisite (same architectural investment as 6328's approach).
- MapleSim is a third-party dependency maintained by a single FRC team (Shenzhen Robotics Alliance / FRC 5516).  Its long-term maintenance and WPILib compatibility are not guaranteed.
- The 2D rigid-body physics, while more realistic than kinematic integration, still cannot model 3D effects (tipping, weight transfer during acceleration, etc.).
- Game-piece simulation requires season-specific implementation work (though MapleSim provides some season-specific modules).

**Reference**: [MapleSim Documentation](https://shenzhen-robotics-alliance.github.io/maple-sim/)

### MapleSim — Shenzhen Robotics Alliance (FRC 5516)

**Repository**: [Shenzhen-Robotics-Alliance/maple-sim](https://github.com/Shenzhen-Robotics-Alliance/maple-sim)

MapleSim is an open-source FRC simulation library that integrates the dyn4j 2D rigid-body dynamics engine into the WPILib simulation framework.  It is used by Team 254 (above) and provides template projects for use with AdvantageKit.

**Key capabilities:**
- `SwerveDriveSimulation` — simulates swerve drivetrain physics including motor propulsion forces, wheel friction, and centripetal forces during turning
- `SwerveModuleSimulation` — per-module simulation with motor models, gear ratios, and wheel friction coefficients
- `GyroSimulation` — simulates IMU readings with configurable drift
- `SimulatedArena` — manages the physics world, including field walls, game pieces, and robot-to-robot collisions
- Season-specific modules for game-piece simulation (Reefscape 2025 coral/algae, Crescendo 2024 notes, Rebuilt 2026 fuel)
- `IntakeSimulation` — simulates game-piece acquisition with proximity detection

**Architecture notes:**
- MapleSim is designed to be used alongside an IO-layer pattern.  It provides the physics engine; the user's code provides the IO implementations that bridge simulated motor/sensor outputs to their subsystem code.
- It runs on a configurable tick rate (default 5ms) independent of the robot loop, providing higher-fidelity physics integration.
- It outputs motor positions and velocities that can be fed into vendor SimState APIs or used directly.

**Pros:**
- Most realistic physics model available for FRC simulation — models force, friction, collisions, and game-piece interactions.
- Actively maintained with season-specific support.
- Available as a vendor dependency with published JavaDocs and documentation.
- Template projects lower the barrier to adoption.

**Cons:**
- Third-party dependency with uncertain long-term support.
- Designed to work with IO-layer architectures — adoption into a codebase without hardware abstraction layers requires additional adapter code.
- Adds significant build complexity and dependency overhead.
- The 2D physics engine, while more realistic, is still an approximation and may not match real-world behavior closely enough to trust for PID tuning or precise trajectory validation.

### WPILib Built-In Simulation

**Reference**: [WPILib Simulation Documentation](https://docs.wpilib.org/en/stable/docs/software/wpilib-tools/robot-simulation/physics-sim.html)

WPILib provides a set of built-in simulation classes as part of the standard library:

- `DCMotorSim` — simulates a DC motor with gearing and moment of inertia
- `FlywheelSim`, `ElevatorSim`, `SingleJointedArmSim` — mechanism-specific simulations
- `DifferentialDrivetrainSim` — simulates a differential drivetrain (no official swerve equivalent)
- Vendor SimState APIs (`Pigeon2SimState`, `CANcoderSimState`, `TalonFXSimState`) — allow setting simulated sensor values that are read by vendor library code

WPILib does not provide a built-in swerve drive simulation class.  Teams simulating swerve drives must implement their own kinematic or dynamic model.  Both of our approaches and Team 6328's approach do this; Team 254 uses MapleSim for the physics layer.

The `simulationInit()` and `simulationPeriodic()` hooks in `Robot.java` provide the standard entry points for simulation code.  The `DriverStationSim` class allows configuring the simulated driver station state.

**Pros:**
- Part of the standard library — no additional dependencies.
- Well-documented and widely used by the FRC community.
- `DCMotorSim` provides motor-level physics for individual mechanisms.

**Cons:**
- No built-in swerve drive simulation — teams must build their own.
- Mechanism sims are independent — there is no built-in way to simulate interactions between mechanisms or with the field environment.
- Vendor SimState API support varies by vendor and is not always well-documented for desktop simulation.

---

## Summary Comparison Matrix

| Criterion | `SimulationManager` (ours) | `RobotSimulation` (ours) | 6328 IO Layer | 254 IO + MapleSim |
|-----------|---------------------------|-------------------------|---------------|-------------------|
| **Separation from production code** | High — separate package, no `isSimulation()` in subsystem | Low — sim fields and methods inside subsystem, `isSimulation()` in `periodic()` | Complete — interface boundary | Complete — interface boundary |
| **Sensor pipeline exercised** | Yes — writes to vendor SimState, subsystem reads back normally | No — bypasses sensor reading, calls `odometry.update()` directly | Yes — sim IO produces same input structure as real IO | Yes — writes to vendor SimState APIs |
| **Physics model** | Kinematic (velocity × time) | Kinematic (velocity × time) | Motor-level (`DCMotorSim`) | Force-based (dyn4j rigid body) |
| **Per-module control in sim** | Yes | No (chassis-speeds only) | Yes | Yes |
| **Battery simulation** | No | Yes | No (handled separately) | Yes (via MapleSim) |
| **Game-piece simulation** | No | No | No | Yes |
| **Collision detection** | No | No | No | Yes |
| **Dependency on vendor sim APIs** | Yes (Pigeon2, CANcoder, REV encoder) | No | No (uses WPILib `DCMotorSim`) | Yes (Pigeon2, TalonFX, CANcoder) |
| **External dependencies** | None | None | AdvantageKit | AdvantageKit + MapleSim |
| **Code complexity (total sim lines)** | ~220 | ~113 | ~126 (drive module sim only) | ~1,300+ (drive + game sim) |
| **Architectural prerequisite** | None — works with existing codebase | None — works with existing codebase | IO interface pattern across all subsystems | IO interface pattern across all subsystems |
| **Removability** | Delete package + 2 lines in Robot.java | Delete class + remove fields/methods from SwerveSubsystem | Replace IO implementations | Replace IO implementations |

---

## Analysis

### What both of our approaches get right

Both `SimulationManager` and `RobotSimulation` achieve the core goal of making the robot move on a Field2d in desktop simulation.  Both use kinematic integration (the simplest possible physics model), which is appropriate for the team's current needs: driver practice and basic autonomous path validation.

### Where they differ meaningfully

The most significant architectural difference is how each approach interacts with production code:

**`SimulationManager`** treats the subsystem as a black box.  It writes to simulated sensors and lets the subsystem's normal `periodic()` code read those sensors and update odometry.  This means:
- The sim exercises the same `periodic()` code path as hardware.
- Bugs in the odometry update logic (e.g., wrong conversion factor, incorrect gyro offset handling) would manifest in sim, potentially catching them earlier.
- However, it depends on vendor SimState APIs working correctly in desktop simulation, which is a partially documented area.

**`RobotSimulation`** treats the subsystem as a collaborator.  It calls `simulationUpdate()`, which directly computes simulated positions and feeds them to the odometry estimator, bypassing the normal sensor reading path.  This means:
- The sim is simpler and has fewer external dependencies.
- It does not exercise the sensor-reading code path, so issues in that path would not be caught in sim.
- It requires the subsystem to have a runtime check (`if (!RobotBase.isSimulation())`) in `periodic()`, creating two distinct code paths.

### How our approaches compare to external teams

Both of our approaches are **simpler and less architecturally invasive** than what Teams 6328 and 254 use.  This is a tradeoff, not a deficiency — the IO-layer pattern requires restructuring the entire codebase around hardware abstraction interfaces.  That investment pays off when a team has the experience and bandwidth to maintain it, but it imposes significant upfront and ongoing complexity costs.

Teams 6328 and 254 have dedicated software mentors with professional software engineering backgrounds, multi-year codebases built around these patterns, and enough team members to maintain the abstraction layers.  These are relevant context factors when evaluating whether their approaches would be appropriate for our team.

Both of our approaches use **kinematic-only physics**, which is the same fundamental model as the simplest implementations from external teams.  The force-based physics from MapleSim provides more realism but is primarily useful for autonomous path testing with field interactions and game-piece simulation — capabilities we do not currently need.

### Practical considerations

**For the team's current situation**, either approach is functional for the stated goals of driver practice and basic autonomous validation.  The choice between them involves these tradeoffs:

- If exercising the real sensor-reading code path in simulation is valued (catching odometry bugs in sim), `SimulationManager` provides that.
- If minimal code complexity and no vendor sim API dependencies are valued, `RobotSimulation` provides that.
- If per-module simulation capability is needed (e.g., for diagnostic tests that control individual modules), `SimulationManager` provides that.
- If battery voltage and DriverStation state simulation are valued, `RobotSimulation` provides those.

Neither approach prevents a future migration to an IO-layer pattern if the team decides to pursue that direction.

---

## Appendix A — Feature Portability and Calculation Review

This appendix evaluates features present in `RobotSimulation` that are absent from `SimulationManager`, assesses whether each is worth integrating, and reviews calculation differences between the two approaches.

### Feature Evaluation

#### 1. Battery Voltage Simulation (`BatterySim`)

**What `RobotSimulation` does:**

`RobotSimulation.simulationPeriodic()` estimates current draw based on the commanded drive and rotation fractions, then uses WPILib's `BatterySim` to compute a loaded battery voltage:

```java
double driveFraction = Math.hypot(speeds.vxMetersPerSecond, speeds.vyMetersPerSecond) / SwerveConstants.maxSpeed;
double rotateFraction = Math.abs(speeds.omegaRadiansPerSecond) / SwerveConstants.maxAngularVelocity;
double estimatedCurrentAmps = 8.0 + 80.0 * driveFraction + 40.0 * rotateFraction;
RoboRioSim.setVInVoltage(BatterySim.calculateDefaultBatteryLoadedVoltage(estimatedCurrentAmps));
```

This models a nominal 12V battery with internal resistance.  At idle the simulated draw is 8A; at full translational speed it reaches ~88A; at full translation + rotation it reaches ~128A.  `BatterySim.calculateDefaultBatteryLoadedVoltage()` applies `V_loaded = V_nominal - I * R_internal` (default R_internal = 0.02Ω), producing a voltage drop of ~1.6–2.6V under full load.

**Is it worth integrating into `SimulationManager`?**

Low priority.  The current estimate is rough — it uses speed fractions as a proxy for current draw rather than actual motor models or torque calculations.  The resulting voltage drop affects the value returned by `RobboRioSim.getVInVoltage()`, which matters only if:

- Subsystem code reads `RobotController.getBatteryVoltage()` and adjusts behavior (e.g., voltage compensation on motor controllers).
- Dashboard or logging tools display battery voltage during simulation.
- Other subsystems (LEDs, pneumatics compressor) use battery voltage to make decisions.

Currently, none of our subsystem code reads battery voltage to modify behavior.  Motor controllers configured with voltage compensation read voltage from their own internal firmware, not from `RobotController.getBatteryVoltage()`, so the simulated RoboRIO voltage does not affect motor output in sim.

**If integrated**, it would be straightforward — approximately 5 lines added to `SimulationManager.simulationPeriodic()` after computing `desiredChassisSpeeds`.  It does not require any changes to `SwerveSubsystem` or `SwerveModule`.  The main benefit would be a more realistic voltage readout in the Sim GUI and AdvantageScope logs.

**Assessment:** Not harmful to add, but provides no functional benefit with the current codebase.  Could be added later if subsystem code begins using battery voltage.

#### 2. DriverStation State Simulation

**What `RobotSimulation` does:**

`RobotSimulation.simulationInit()` programmatically sets DriverStation state:

```java
DriverStationSim.setDsAttached(true);
DriverStationSim.setEnabled(false);
DriverStationSim.setAutonomous(false);
DriverStationSim.setTest(false);
DriverStationSim.notifyNewData();
```

This establishes a known initial state: driver station connected, robot disabled, not in autonomous or test mode.

**Is it worth integrating into `SimulationManager`?**

This programmatic setup is redundant with functionality provided by the existing simulation infrastructure.  Two mechanisms already control DriverStation state without any code in the simulation framework:

1. **Sim GUI (`halsim_gui`)** — enabled by `wpi.sim.addGui()` in `build.gradle`.  The Sim GUI displays a "Robot State" widget with clickable buttons for Disabled / Autonomous / Teleoperated / Test, and shows DS connection status.  Its default startup state is DS connected + robot disabled — identical to what the `RobotSimulation` code sets programmatically.

2. **Real FRC Driver Station via `halsim_ds_socket`** — enabled by `wpi.sim.addDriverstation()` in `build.gradle`.  When the real Driver Station application connects to the simulation (Windows only), it takes over control of all DriverStation state: enabled/disabled, mode selection, joystick data, match time, and alliance info.  Any values set programmatically by `DriverStationSim` are overridden by the real DS on connection.

In both cases, the DriverStation state is managed externally — by the Sim GUI interactively, or by the real DS application via the socket protocol.  The programmatic `DriverStationSim` calls in `RobotSimulation` set the same defaults that these mechanisms already establish.

The programmatic setup would provide independent value if the simulation were run headless (no Sim GUI, no real DS) — for example, in a CI/CD pipeline.  That does not apply to the current workflow.

Note: `SimulationManager` on the current branch already handles joystick warning suppression in `Robot.simulationInit()` (via the `sim.silenceJoystick` system property), which addresses the most common sim startup annoyance.

**Assessment:** Not integrating.  The functionality is already provided by the Sim GUI and the `halsim_ds_socket` extension, both of which are configured in `build.gradle`.

#### 3. Simulation Reset

**What `RobotSimulation` does:**

`RobotSimulation.simulationInit()` calls `drive.simulationReset()`, which resets all simulation state to a known origin:

```java
public void simulationReset() {
    simYawDegrees = getYaw().getDegrees();
    for (int i = 0; i < 4; i++) {
        simWheelPositionsMeters[i] = 0.0;
        simWheelAngles[i] = new Rotation2d();
    }
    pigeon.setYaw(simYawDegrees);
    odometry.resetPosition(Rotation2d.fromDegrees(simYawDegrees), positions, new Pose2d());
}
```

**Is it worth integrating into `SimulationManager`?**

Potentially useful as a future enhancement.  `SimulationManager` currently initializes `simPose` to `new Pose2d()` (origin) in its field declaration, and encoder positions start at whatever the REV sim state defaults to.  This works for the initial startup.

However, `SimulationManager` does not expose a way to reset the simulation mid-run (e.g., after testing one autonomous path and wanting to start another from a clean state without restarting the sim).  A `simulationReset()` method on `SimulationManager` could:
- Reset `simPose` to origin (or a specified pose).
- Zero out drive encoder positions via `driveEncoder.setPosition(0)`.
- Reset the Pigeon2 yaw via `pigeonSimState.setRawYaw(0)`.
- Optionally reset the odometry estimator via `swerveSubsystem.getOdometry().resetPosition(...)`.

This would be compatible with the existing architecture — it would write to the same vendor SimState APIs, and the subsystem's `periodic()` would pick up the reset values naturally.

**Assessment:** Worth considering as a future addition.  The `SimulationManager` design can accommodate a reset method without architectural changes.  The implementation would be ~15 lines.

#### 4. Explicit Disabled-State Handling

**What `RobotSimulation` does:**

Inside `SwerveSubsystem.simulationUpdate()`:

```java
ChassisSpeeds speeds = DriverStation.isDisabled() ? new ChassisSpeeds() : lastCommandedSpeeds;
```

This explicitly substitutes zero speeds when the robot is disabled, regardless of what `lastCommandedSpeeds` contains.

**What `SimulationManager` does:**

No explicit disabled check.  It reads `swerveSubsystem.getDesiredStates()`, which reads each module's `desiredState` field.  When the robot is disabled, the command scheduler does not run commands, so no new desired states are set.  Modules retain their last `desiredState` from before disable.

**Is it worth integrating into `SimulationManager`?**

This is a minor robustness concern.  When the robot transitions from enabled to disabled, the modules' `desiredState` fields retain the last commanded values.  Since no new commands run while disabled, `SimulationManager` would continue integrating those stale velocities, causing the simulated robot to drift.

In practice, this is partially mitigated by the fact that the swerve default command (which typically commands zero speeds when the joystick is centered) runs while enabled and would have set near-zero desired states before disable.  But if the robot is disabled while actively driving, a brief drift would occur until the next `simulationPeriodic()` reads the stale non-zero desired states.

A simple fix would be to add a `DriverStation.isDisabled()` check in `SimulationManager.simulationPeriodic()`:

```java
if (DriverStation.isDisabled()) {
    desiredChassisSpeeds = new ChassisSpeeds();
    desiredStates = null; // skip module encoder updates
}
```

**Assessment:** Worth integrating.  It is a one-line guard that prevents a real (if minor) simulation artifact.  It does not affect the architecture.

---

### Calculation Differences

#### 1. Time Delta (`dt`) Clamping

| | `SimulationManager` | `RobotSimulation` |
|---|---|---|
| **Code** | `if (dt <= 0 \|\| dt > 1.0) dt = 0.02` | `MathUtil.clamp(now - lastTimestampSeconds, 0.0, 0.05)` |
| **Behavior when dt = 0** | Substitutes 0.02s (simulates motion that did not happen) | Uses 0.0 (no motion — correct) |
| **Behavior when dt = 0.2s** (e.g., GC pause) | Uses 0.2s (large pose jump) | Clamps to 0.05s (limits jump, loses 0.15s of motion) |
| **Behavior when dt = -0.001s** (clock jitter) | Substitutes 0.02s | Clamps to 0.0 |
| **Upper bound** | 1.0s | 0.05s |

**Analysis:**

The `RobotSimulation` approach is more defensive.  The 50ms upper clamp prevents large pose jumps during GC pauses or debugger breakpoints.  `SimulationManager`'s 1.0s upper bound is permissive enough that a 500ms pause (not uncommon during debugging) would cause a visible position jump.

However, the `RobotSimulation` approach of clamping dt to 0 when it is zero or negative is more correct than substituting 0.02s.  A dt of zero should produce zero motion, not an artificial 20ms of movement.

**Recommendation:** Adopt the `MathUtil.clamp()` pattern with a tighter upper bound.  A reasonable upper bound is 50ms (2.5× the nominal loop period).  This prevents large pose jumps while still producing smooth motion during normal operation:

```java
double dt = MathUtil.clamp(currentTime - lastTime, 0.0, 0.05);
```

#### 2. Pose Integration Method

| | `SimulationManager` | `RobotSimulation` |
|---|---|---|
| **Code** | `simPose = simPose.exp(new Twist2d(vx*dt, vy*dt, omega*dt))` | `simYawDegrees += Math.toDegrees(omega * dt)` + per-wheel linear accumulation |
| **Mathematical model** | Exponential map (SE(2) Lie group) | Euler integration |

**Analysis:**

`Pose2d.exp(Twist2d)` computes the exact integral of constant-velocity motion over the time step, correctly modeling the arc the robot follows when simultaneously translating and rotating.  The Euler integration in `RobotSimulation` accumulates yaw separately from position, which is less accurate when the robot is turning and translating simultaneously.

At 20ms time steps, the numerical difference between exponential and Euler integration is small (the error is proportional to `dt²`).  For a robot rotating at 1 rad/s while translating at 3 m/s, the per-step position error is on the order of 0.0006 meters — negligible for driver practice.

However, the exponential map is the standard approach recommended by WPILib (it is what `SwerveDrivePoseEstimator` uses internally), it has no additional computational cost, and it is already implemented in `SimulationManager`.

**Recommendation:** No change needed.  `SimulationManager` already uses the more correct method.

#### 3. Wheel Speed Desaturation

| | `SimulationManager` | `RobotSimulation` |
|---|---|---|
| **Code** | Reads `getDesiredState()` directly (already desaturated by `setDesiredState()` call chain) | `SwerveDriveKinematics.desaturateWheelSpeeds(states, SwerveConstants.maxSpeed)` after converting back from chassis speeds |

**Analysis:**

In `SimulationManager`, the desired states have already passed through the normal swerve command pipeline: `driveFromChassisSpeeds()` → `SwerveDriveKinematics.toSwerveModuleStates()` → `desaturateWheelSpeeds()` → `setDesiredState()`.  So the states `SimulationManager` reads are already desaturated.

`RobotSimulation` applies desaturation again after converting `lastCommandedSpeeds` back to module states.  This is necessary in its design because it starts from chassis speeds (which may round-trip through kinematics differently), but it is a redundant operation.

In `SimulationManager`, direct module commands from diagnostic tests bypass `driveFromChassisSpeeds()` and may not be desaturated.  However, diagnostic tests typically command individual modules at controlled speeds, so desaturation is not expected to apply.

**Recommendation:** No change needed.  `SimulationManager` reads already-desaturated states.

#### 4. Gyro Update Method

| | `SimulationManager` | `RobotSimulation` |
|---|---|---|
| **Code** | `pigeonSimState.setRawYaw(simPose.getRotation().getDegrees())` | `pigeon.setYaw(simYawDegrees)` |
| **API used** | Vendor `Pigeon2SimState.setRawYaw()` (sim API) | `Pigeon2.setYaw()` (production API) |
| **Source of truth** | Derived from integrated `simPose` heading | Accumulated in `simYawDegrees` via `+= Math.toDegrees(omega * dt)` |

**Analysis:**

`SimulationManager` uses the CTRE `Pigeon2SimState` API, which is the designated simulation path — it sets the simulated raw sensor value without affecting configuration state.  `RobotSimulation` uses `Pigeon2.setYaw()`, which is the production API for zeroing/setting the gyro heading.  Using the production API in sim works, but it conflates "setting the simulated sensor reading" with "commanding the gyro to recalibrate to a heading," which are conceptually different operations.

`SimulationManager` derives the yaw from `simPose.getRotation()`, which is computed via the exponential map.  `RobotSimulation` accumulates yaw separately via Euler integration (`+=`).  This means `SimulationManager`'s yaw is consistent with its x/y pose, while `RobotSimulation`'s yaw could accumulate a small drift relative to the pose that its odometry computes (though in practice the difference is negligible).

**Recommendation:** No change needed.  `SimulationManager` already uses the more appropriate API.

---

### Summary of Recommendations

| Feature / Calculation | Worth Integrating? | Effort | Priority |
|---|---|---|---|
| **Disabled-state guard** | Yes | ~3 lines | Medium — prevents simulation drift when disabled |
| **Tighter dt clamping** | Yes | ~1 line change | Medium — prevents large pose jumps during debugging |
| **Battery voltage simulation** | Not currently | ~5 lines | Low — no subsystem code reads battery voltage |
| **DriverStation state setup** | No | ~5 lines | None — Sim GUI provides the same functionality |
| **Simulation reset method** | Future consideration | ~15 lines | Low — useful for multi-run testing without restart |
| **Pose integration method** | Already better in `SimulationManager` | N/A | N/A |
| **Gyro update API** | Already better in `SimulationManager` | N/A | N/A |
| **Wheel desaturation** | Not needed | N/A | N/A |

The two changes recommended for near-term integration (disabled-state guard and tighter dt clamping) are both small, isolated, and do not affect the `SimulationManager` architecture or its relationship with the subsystem.

---

## Appendix B — `SimulationManager` Data Flow and Code Path Analysis

This appendix explains how the `SimulationManager` approach achieves its goal of reusing production code paths in simulation.  It provides side-by-side comparisons of the data flow and code execution in real robot mode vs. simulation mode, showing that the production code is identical in both modes.

### The Core Idea

On the real robot, physical motors turn physical wheels, physical encoders measure how far the wheels have moved, and a physical gyro measures which direction the robot is facing.  The subsystem reads these sensors every 20 ms and feeds the readings into the pose estimator to determine where the robot is on the field.

In simulation, there are no physical motors, wheels, encoders, or gyro.  The `SimulationManager` fills the gap by computing what the sensor readings *would be* if the robot were moving as commanded, and writing those computed values into the vendor simulation APIs.  When the subsystem's `periodic()` method runs — the exact same code as on the real robot — it reads those simulated sensor values and updates the pose estimator as usual.

The result: the subsystem does not know or care whether it is running on a real robot or in simulation.  Its `periodic()` method, its odometry, and its Field2d visualization all work identically in both modes.

### Data Flow Comparison

The following diagrams show the complete data flow for a single 20 ms loop cycle in each mode.  **Bold** text marks the steps that differ between real and simulation mode.  All other steps are identical code.

#### Real Robot Mode

```
Driver Input (joystick)
    │
    ▼
TeleopSwerve.execute()
    │  applies deadband, slew rate limiting, scales by max speed
    ▼
SwerveSubsystem.drive(x, y, rot, fieldOriented)
    │  converts to ChassisSpeeds (field-relative or robot-relative)
    ▼
SwerveSubsystem.driveFromChassisSpeeds(speeds, openLoop)
    │  kinematics.toSwerveModuleStates() → desaturateWheelSpeeds()
    ▼
SwerveModule.setDesiredState(state, openLoop)          ← ×4 modules
    │  optimize() → stores desiredState → setAngle() → setSpeed()
    ▼
Motor controllers execute PID commands
    │  ▪ angle motor rotates wheel to target angle
    │  ▪ drive motor spins wheel at target speed
    ▼
Physical wheels move → physical sensors update
    │  ▪ drive encoder position increases as wheel rolls
    │  ▪ angle encoder position reflects current wheel angle
    │  ▪ Pigeon2 gyro yaw reflects current robot heading
    ▼
SwerveSubsystem.periodic()
    │  getYaw()       → pigeon.getYaw()           → reads physical gyro
    │  getPositions() → driveEncoder.getPosition() → reads physical encoder
    │                 → angleEncoder.getPosition()  → reads physical encoder
    │  odometry.update(yaw, positions)             → fuses into pose estimate
    │  field.setRobotPose(getPose())               → updates Field2d
    │  robotPose.set(getPose())                    → publishes pose for AdvantageScope
    ▼
Dashboard / AdvantageScope shows robot position on field
```

#### Simulation Mode

```
Driver Input (joystick — real or simulated)
    │
    ▼
TeleopSwerve.execute()                                  ← SAME CODE
    │  applies deadband, slew rate limiting, scales by max speed
    ▼
SwerveSubsystem.drive(x, y, rot, fieldOriented)         ← SAME CODE
    │  converts to ChassisSpeeds (field-relative or robot-relative)
    ▼
SwerveSubsystem.driveFromChassisSpeeds(speeds, openLoop) ← SAME CODE
    │  kinematics.toSwerveModuleStates() → desaturateWheelSpeeds()
    ▼
SwerveModule.setDesiredState(state, openLoop)          ← ×4, SAME CODE
    │  optimize() → stores desiredState → setAngle() → setSpeed()
    ▼
Motor controllers NO-OP (no physical hardware)
    │  ▪ PID commands are issued but have no effect
    │  ▪ No physical wheels move
    │  ▪ desiredState field retains the commanded state
    ▼
 ╔══════════════════════════════════════════════════════════╗
 ║  SimulationManager.simulationPeriodic()  — SIM ONLY      ║
 ║                                                          ║
 ║  1. Read desired states from modules                     ║
 ║     desiredStates = swerveSubsystem.getDesiredStates()   ║
 ║                                                          ║
 ║  2. Compute what the robot would do                      ║
 ║     chassisSpeeds = kinematics.toChassisSpeeds(states)   ║
 ║     simPose = simPose.exp(Twist2d(vx*dt, vy*dt, ω*dt))   ║
 ║                                                          ║
 ║  3. Write simulated sensor values                        ║
 ║     pigeonSimState.setRawYaw(simPose heading)            ║
 ║     driveEncoder.setPosition(position + speed*dt)        ║
 ║     angleEncoder.setPosition(desired angle)              ║
 ║     cancoderSimState.setRawPosition(desired angle)       ║
 ╚══════════════════════════════════════════════════════════╝
    │
    ▼
SwerveSubsystem.periodic()                              ← SAME CODE
    │  getYaw()       → pigeon.getYaw()           → reads SIMULATED gyro
    │  getPositions() → driveEncoder.getPosition() → reads SIMULATED encoder
    │                 → angleEncoder.getPosition()  → reads SIMULATED encoder
    │  odometry.update(yaw, positions)             → fuses into pose estimate
    │  field.setRobotPose(getPose())               → updates Field2d
    │  robotPose.set(getPose())                    → publishes pose for AdvantageScope
    ▼
Dashboard / AdvantageScope shows robot position on field  ← SAME CODE
```

The only difference is the boxed section: `SimulationManager` runs between the motor commands and the sensor reads, filling in the sensor values that physical hardware would have produced.  Everything above the box (command processing) and everything below the box (odometry, Field2d) is identical production code.

### Code Path Comparison

The following table shows the actual methods called during a single loop cycle.  The "Real Robot" and "Simulation" columns indicate what each method call does in each mode.  Methods where the code itself is identical are marked with **=**.

| Step | Method | Real Robot | Simulation |
|------|--------|-----------|------------|
| 1 | `TeleopSwerve.execute()` | Reads joystick, computes speeds | **=** Same code |
| 2 | `SwerveSubsystem.drive()` | Converts to `ChassisSpeeds` | **=** Same code |
| 3 | `driveFromChassisSpeeds()` | Kinematics → module states | **=** Same code |
| 4 | `SwerveModule.setDesiredState()` | Optimizes, stores state, commands motors | **=** Same code (motors no-op) |
| 4a | `setAngle()` | `angleController.setReference()` → motor turns | **=** Same code (no-op in sim) |
| 4b | `setSpeed()` | `driveController.setReference()` → motor spins | **=** Same code (no-op in sim) |
| 5 | **`SimulationManager.simulationPeriodic()`** | *Does not run* | Computes motion, writes to sim sensors |
| 6 | `SwerveSubsystem.periodic()` | Reads physical sensors | **=** Same code (reads simulated sensors) |
| 6a | `pigeon.getYaw()` | Returns physical gyro heading | **=** Same code (vendor lib returns sim value) |
| 6b | `driveEncoder.getPosition()` | Returns physical encoder distance | **=** Same code (vendor lib returns sim value) |
| 6c | `integratedAngleEncoder.getPosition()` | Returns physical encoder angle | **=** Same code (vendor lib returns sim value) |
| 7 | `odometry.update(yaw, positions)` | Fuses physical sensor readings | **=** Same code (fuses simulated readings) |
| 8 | `field.setRobotPose(getPose())` | Displays physical pose on Field2d | **=** Same code (displays simulated pose) |
| 9 | `robotPose.set(getPose())` | Publishes pose to NetworkTables for AdvantageScope | **=** Same code (publishes simulated pose) |

Steps 1–4 and 6–9 execute the same Java methods with the same code in both modes.  Step 5 is the only addition — it runs exclusively in simulation and only writes to sensor simulation APIs.

### How Vendor Libraries Enable This

The key to this design is that vendor libraries (CTRE Phoenix 6 for Pigeon2 and CANcoder, REV for SparkMax/SparkFlex encoders) internally handle the real-vs-simulation routing:

```
Production code calls:         pigeon.getYaw()
                                    │
                       ┌────────────┴────────────┐
                       ▼                         ▼
              Real robot mode              Simulation mode
           Read hardware via CAN       Return value from SimState
           (physical sensor)           (set by SimulationManager)
```

The production code — `pigeon.getYaw()`, `driveEncoder.getPosition()`, `integratedAngleEncoder.getPosition()` — never checks `RobotBase.isSimulation()`.  The vendor library does that internally.  This means:

- `SwerveSubsystem.periodic()` contains **zero** simulation-specific conditionals
- `SwerveModule.setDesiredState()` contains **zero** simulation-specific conditionals
- `SwerveModule.getState()` and `getPosition()` contain **zero** simulation-specific conditionals

The `SimulationManager` writes to the "back door" of these vendor objects (the SimState APIs), and the production code reads from the "front door" (the normal getter methods).  The vendor library connects the two internally.

### What This Means for Bug Detection

Because the production `periodic()` code path runs identically in simulation, certain categories of bugs would manifest in simulation the same way they do on the real robot:

| Bug Category | Detected in Sim? | Why |
|-------------|------------------|-----|
| Wrong encoder conversion factor | ✅ Yes | `periodic()` reads the same encoder object with the same conversion factor |
| Gyro sign inversion (e.g., `invertPigeon` configured wrong) | ✅ Yes | `getYaw()` applies the same inversion logic to simulated yaw |
| Odometry reset not updating gyro baseline | ✅ Yes | `resetOdometry()` calls the same `resetPosition()` with the same gyro value |
| Wrong kinematics (module positions) | ✅ Yes | Same `SwerveDriveKinematics` instance used in both modes |
| Module optimization bug (e.g., angle accumulation) | ✅ Yes | Same `optimize()` method runs in both modes |
| Motor PID tuning issues | ❌ No | Motors no-op in sim; desired speed is assumed to be achieved instantly |
| Wheel slip / friction effects | ❌ No | No force-based physics model |
| Mechanical issues (loose belt, broken encoder) | ❌ No | Simulation assumes perfect hardware |

The first five rows are the primary benefit of the `SimulationManager` approach: the full sensor-to-odometry pipeline is exercised in simulation using the same code path, so bugs in that pipeline are caught.

### Production Code Modifications Required

The `SimulationManager` approach requires a small set of additions to production code.  These are accessor methods only — they do not change any existing behavior.

**`SwerveModule` additions:**

| Addition | Purpose | Lines |
|----------|---------|-------|
| `desiredState` field | Stores the optimized state from `setDesiredState()` for simulation to read | 1 |
| `getDesiredState()` | Returns the stored desired state | 3 |
| `getCanCoderDevice()` | Exposes CANcoder hardware object for SimState access | 3 |
| `getDriveEncoder()` | Exposes drive encoder for `setPosition()` in sim | 3 |
| `getAngleEncoder()` | Exposes angle encoder for `setPosition()` in sim | 3 |

**`SwerveSubsystem` additions:**

| Addition | Purpose | Lines |
|----------|---------|-------|
| `getDesiredStates()` | Collects desired states from all four modules | 6 |
| `getPigeon()` | Exposes Pigeon2 for SimState access | 3 |
| `getModules()` | Exposes module array (defensive copy) | 3 |
| `getKinematics()` | Exposes kinematics for chassis speed calculation | 3 |
| `getOdometry()` | Exposes pose estimator for reset support | 3 |

**Unchanged production methods** (these run identically in both modes):

- `SwerveSubsystem.periodic()` — no `isSimulation()` check
- `SwerveSubsystem.drive()`
- `SwerveSubsystem.driveFromChassisSpeeds()`
- `SwerveSubsystem.getYaw()`
- `SwerveSubsystem.getPositions()`
- `SwerveSubsystem.resetOdometry()`
- `SwerveModule.setDesiredState()`
- `SwerveModule.getState()`
- `SwerveModule.getPosition()`
- All command classes (`TeleopSwerve`, `AutoAlign`, autonomous commands)

### Removability

The simulation support can be completely removed without affecting production code behavior:

1. Delete `src/main/java/frc/robot/sim/SimulationManager.java`
2. Remove two lines from `Robot.java` (`simManager` field declaration and `simulationInit()`/`simulationPeriodic()` bodies)
3. Optionally remove the accessor methods from `SwerveModule` and `SwerveSubsystem` (they are unused by production code, but leaving them causes no harm)

No production code behavior changes because the accessor methods are never called by production code — they are only called by `SimulationManager`.
