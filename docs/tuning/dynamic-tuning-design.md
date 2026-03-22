# FRC Tuning Library Design (Draft)

## Overview

This document describes a lightweight, extensible tuning system for FRC robots.
The system is designed to be:

- Explicit (no hidden magic)
- Composable
- Extensible (supports future UI and tooling)
- Library-friendly (usable by other teams)

---

## Package Naming

This library is intended to be published as an open source contribution to the FRC
community. To that end, it uses the package namespace:

```
org.moundsparkacademy.frc.tuning
```

This follows the reverse-domain naming convention used by prominent FRC libraries:

| Library | Package |
|---|---|
| WPILib | `edu.wpi.first.*` |
| AdvantageKit (6328) | `org.littletonrobotics.junction.*` |
| PhotonVision | `org.photonvision.*` |
| CTRE Phoenix 6 | `com.ctre.phoenix6.*` |
| REV Robotics | `com.revrobotics.*` |

The organization-based pattern (`org.moundsparkacademy.frc`) provides a clear namespace
for this and any future libraries published by the team.

---

## Core Concepts

### TunableProvider

A `TunableProvider` is any class that exposes tuners. It declares the subsystem name
and returns the tuners that belong to it.

```java
public interface TunableProvider {
    String getSubsystemName();
    List<Tuner> getTuners();
}
```

- Subsystems implement this to expose their tunable parameters
- `getSubsystemName()` returns the owning subsystem (e.g., `"Intake"`)
- Each provider declares its subsystem name once, eliminating duplication across tuners
- `getTuners()` is called exactly once per provider by the `TuningManager` at construction
  time — the result is stored in a local variable and reused for both validation and binding
- `RobotContainer` does not implement this interface — it aggregates providers

---

### Tuner

A `Tuner` represents a logical grouping of tunable parameters within a subsystem.

```java
public record Tuner(String name, List<TuningParameter> parameters) {
    public Tuner {
        Objects.requireNonNull(name, "name must not be null");
        parameters = List.copyOf(parameters);
    }
}
```

- `name()` returns the tuner name within its subsystem (e.g., `"Arm"`)
- The subsystem name is provided by the owning `TunableProvider`, not by the `Tuner`
- Together, the provider's subsystem name and the tuner name form a natural namespace
  for NetworkTables paths (e.g., `Intake/Arm/kP`)
- The compact constructor validates inputs and defensively copies the parameter list

---

### TuningParameter

A `TuningParameter` represents a single tunable value with optional bounds.

#### Design Goals:
- Encapsulate getter/setter behavior
- Avoid stringly-typed maps
- Support optional min/max bounds for safety
- Support future metadata (units, etc.)

#### Suggested Implementation

```java
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class TuningParameter {

    private final String name;
    private final double min;
    private final double max;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    public TuningParameter(String name, DoubleSupplier getter, DoubleConsumer setter) {
        this(name, getter, setter, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public TuningParameter(String name, DoubleSupplier getter, DoubleConsumer setter,
                           double min, double max) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.getter = Objects.requireNonNull(getter, "getter must not be null");
        this.setter = Objects.requireNonNull(setter, "setter must not be null");
        if (min > max) {
            throw new IllegalArgumentException(
                "min (" + min + ") must not be greater than max (" + max + ")");
        }
        this.min = min;
        this.max = max;
    }

    public String getName() {
        return name;
    }

    public double getValue() {
        return getter.getAsDouble();
    }

    public void setValue(double value) {
        setter.accept(MathUtil.clamp(value, min, max));
    }
}
```

#### Bounds Handling

Bounds are optional and default to `Double.NEGATIVE_INFINITY` / `Double.POSITIVE_INFINITY`,
which makes `MathUtil.clamp()` a no-op when bounds are not specified. This means
`setValue()` always clamps — the behavior is consistent regardless of whether bounds
are declared. The 5-arg constructor validates that `min <= max` and throws
`IllegalArgumentException` if violated, catching misconfiguration at construction
time rather than producing silent, unpredictable clamping behavior.

The two constructors cover the common cases:
- **3-arg** (name, getter, setter) — No bounds. Used when any value is safe.
- **5-arg** (name, getter, setter, min, max) — Both bounds. Used for parameters
  like PID gains where out-of-range values could be dangerous.

One-sided bounds are expressed by leaving one side at infinity:
- **Lower bound only:** `new TuningParameter("kP", getter, setter, 0, Double.POSITIVE_INFINITY)`
- **Upper bound only:** `new TuningParameter("maxSpeed", getter, setter, Double.NEGATIVE_INFINITY, 4.0)`

Bounds checking is the **subsystem's responsibility** — the subsystem has domain
knowledge about what values are safe for each parameter. The `TuningParameter`
enforces the bounds the subsystem declares, but the library never imposes its own.

---

## Hierarchical Composition

`RobotContainer` aggregates `TunableProvider` instances from all subsystems. Each
subsystem implements `TunableProvider` and may return one or more tuners depending
on how many tunable components it contains.

`RobotContainer` itself does not implement `TunableProvider` — it simply collects
the providers and passes them to the `TuningManager`.

### Example

- `RobotContainer` (aggregates providers)
  - `DriveSubsystem` implements `TunableProvider` — returns tuners for drive control
  - `IntakeSubsystem` implements `TunableProvider` — returns tuners for Arm (PID) and Rollers (speed)

---

## Implementation Pattern

### Subsystem Example (IntakeSubsystem)

A single subsystem can return multiple tuners — one per tunable component.
This example is based on the `IntakeSubsystem`. The **Arm** tuner reflects the
current implementation, which uses a PID controller and feedforward. The **Rollers**
tuner envisions an improved design where roller speed adapts to robot velocity
using a linear offset model:

```
rollerSpeed = baseSpeed + (speedScale * robotSpeed)
```

- `BaseSpeed` — roller speed when the robot is stationary
- `SpeedScale` — how much roller speed increases per unit of robot velocity

This ensures the rollers always spin faster than the ball's ground speed for
reliable collection, regardless of how fast the robot is driving. (The current
`IntakeSubsystem` uses a fixed speed constant instead.)

```java
public class IntakeSubsystem extends SubsystemBase implements TunableProvider {

    private final PIDController intakeArmController = new PIDController(
        IntakeConstants.INTAKE_ARM_kP,
        IntakeConstants.INTAKE_ARM_kI,
        IntakeConstants.INTAKE_ARM_kD);

    private final ArmFeedforward intakeArmFeedforward = new ArmFeedforward(
        IntakeConstants.INTAKE_ARM_kS,
        IntakeConstants.INTAKE_ARM_kG,
        IntakeConstants.INTAKE_ARM_kV,
        IntakeConstants.INTAKE_ARM_kA);

    private double rollerBaseSpeed = IntakeConstants.ROLLER_BASE_SPEED;
    private double rollerSpeedScale = IntakeConstants.ROLLER_SPEED_SCALE;

    // ... constructor, periodic, and other methods omitted ...

    @Override
    public String getSubsystemName() { return "Intake"; }

    @Override
    public List<Tuner> getTuners() {
        return List.of(
            new Tuner("Arm", List.of(
                new TuningParameter("kP",
                    intakeArmController::getP,
                    intakeArmController::setP,
                    0, IntakeConstants.INTAKE_ARM_kP_MAX),
                new TuningParameter("kI",
                    intakeArmController::getI,
                    value -> {
                        intakeArmController.setI(value);
                        intakeArmController.reset();
                    },
                    0, IntakeConstants.INTAKE_ARM_kI_MAX),
                new TuningParameter("kD",
                    intakeArmController::getD,
                    intakeArmController::setD,
                    0, IntakeConstants.INTAKE_ARM_kD_MAX),
                new TuningParameter("kS",
                    intakeArmFeedforward::getKs,
                    intakeArmFeedforward::setKs),
                new TuningParameter("kG",
                    intakeArmFeedforward::getKg,
                    intakeArmFeedforward::setKg),
                new TuningParameter("kV",
                    intakeArmFeedforward::getKv,
                    intakeArmFeedforward::setKv,
                    0, Double.POSITIVE_INFINITY),
                new TuningParameter("kA",
                    intakeArmFeedforward::getKa,
                    intakeArmFeedforward::setKa,
                    0, Double.POSITIVE_INFINITY)
            )),
            new Tuner("Rollers", List.of(
                new TuningParameter("BaseSpeed",
                    () -> rollerBaseSpeed,
                    value -> rollerBaseSpeed = value),
                new TuningParameter("SpeedScale",
                    () -> rollerSpeedScale,
                    value -> rollerSpeedScale = value)
            ))
        );
    }
}
```

The `TuningParameter` getter/setter lambdas bind directly to the subsystem's
internal state. Four patterns appear in this example:

- **Method references with bounds** — PID gains like `kP` and `kD` use simple method
  references (`::getP`, `::setP`) with the 5-arg constructor to declare safe bounds.
  `TuningParameter.setValue()` clamps the value before calling the setter, so the
  subsystem doesn't need to handle clamping in a wrapper lambda. (The bounds constants
  like `INTAKE_ARM_kP_MAX` are envisioned additions to `IntakeConstants` — they do not
  exist in the current codebase.)
- **Setter with side effects and bounds** — The `kI` setter needs a lambda because
  it calls `reset()` to clear the integral accumulator after setting the gain. The
  lambda receives already-clamped values from `setValue()`, so it only handles the
  side effect — not the bounds logic.
- **Method references without bounds** — Feedforward gains like `kS` and `kG` use
  simple method references (`::getKs`, `::setKs`) with the 3-arg constructor. Any
  value is acceptable for these parameters.
- **One-sided bounds** — Feedforward gains `kV` and `kA` use a lower bound of `0`
  with `Double.POSITIVE_INFINITY` as the upper bound. WPILib's `ArmFeedforward`
  throws `IllegalArgumentException` for negative kV or kA, so the lower bound
  prevents invalid values while leaving the upper end unconstrained.

The subsystem controls what is exposed and how values are applied. The tuning system
never reaches into subsystem internals.

---

### RobotContainer Example

`RobotContainer` does not implement `TunableProvider`. Instead, it exposes a list
of all tunable subsystems for the `TuningManager` to consume.

```java
public class RobotContainer {

    private final DriveSubsystem drive;
    private final IntakeSubsystem intake;

    public RobotContainer() {
        this.drive = new DriveSubsystem();
        this.intake = new IntakeSubsystem();
    }

    public List<TunableProvider> getTunableSubsystems() {
        return List.of(drive, intake);
    }
}
```

---

### TuningManager

The `TuningManager` is the orchestration class that coordinates the tuning process.
It acts as the bridge between the `TunableProvider` hierarchy and NetworkTables.

#### Responsibilities

1. **Validate structure** — Check for duplicate names at each level of the hierarchy
2. **Discover parameters** — Collect all `Tuner` instances from the providers
3. **Publish to NetworkTables** — Write current parameter values so they appear on the dashboard
4. **Read from NetworkTables** — Read updated values as edited by the operator on the dashboard
5. **Apply changes** — Call the `TuningParameter` setters to update control parameters
6. **Clean up** — Unpublish all NetworkTables entries when tuning ends

Because the `TuningManager` publishes to NetworkTables, any NetworkTables-compatible
dashboard (Shuffleboard, Elastic, Glass) can display and edit tuning parameters in
real time. No additional UI integration is required.

#### Lifecycle

The `TuningManager` is created and initialized entirely within `Robot.testInit()`.
It is **not** created during `robotInit()` — this ensures the tuning system has zero
presence during autonomous and teleop modes, preventing any accidental leakage into
competition code.

The `periodic()` method is called from `Robot.testPeriodic()`. Because `testPeriodic()`
runs on the main robot loop, all parameter reads and writes are synchronized with the
control loop. There are no concurrent access concerns — parameters are never updated
outside this loop.

**Dashboard values take precedence.** The sync is one-directional: when the dashboard
value differs from the subsystem's current value, the dashboard value is applied to the
subsystem. If something else modifies a parameter while in test mode (e.g., a command),
`periodic()` will overwrite that change with the dashboard value on the next cycle.
This is intentional — during tuning, the dashboard is the single source of truth for
parameter values.

When test mode is exited, `Robot.testExit()` calls `close()` on the `TuningManager`
and sets the reference to `null`. This unpublishes all NetworkTables entries and
ensures the tuning system is fully torn down and does not persist into subsequent
auto or teleop modes.

#### Suggested Implementation

```java
public class TuningManager implements AutoCloseable {

    private static final double VALUE_CHANGE_TOLERANCE = 1e-9;

    private final List<TunerBinding> bindings = new ArrayList<>();
    private boolean closed = false;

    public TuningManager(List<TunableProvider> providers) {
        NetworkTable rootTable = NetworkTableInstance.getDefault().getTable("Tuning");

        // track seen subsystems for duplicate detection
        Set<String> seenSubsystems = new HashSet<>();

        for (TunableProvider provider : providers) {
            String subsystemName = provider.getSubsystemName();
            List<Tuner> tuners = provider.getTuners();

            if (!seenSubsystems.add(subsystemName)) {
                throw new IllegalArgumentException(
                    "Duplicate subsystem name: " + subsystemName);
            }

            if (tuners.isEmpty()) {
                continue;
            }

            NetworkTable subsystemTable = rootTable.getSubTable(subsystemName);

            // track seen tuners for duplicate detection
            Set<String> seenTuners = new HashSet<>();

            for (Tuner tuner : tuners) {
                if (!seenTuners.add(tuner.name())) {
                    throw new IllegalArgumentException(
                        "Duplicate tuner name: " + tuner.name()
                        + " in subsystem " + subsystemName);
                }

                NetworkTable tunerTable = subsystemTable.getSubTable(tuner.name());

                // track seen parameters for duplicate detection
                Set<String> seenParams = new HashSet<>();

                for (TuningParameter param : tuner.parameters()) {
                    if (!seenParams.add(param.getName())) {
                        throw new IllegalArgumentException(
                            "Duplicate parameter name: " + param.getName()
                            + " in tuner " + subsystemName + "/" + tuner.name());
                    }

                    // initialize NetworkTable entry with current parameter value
                    NetworkTableEntry entry = tunerTable.getEntry(param.getName());
                    double value = param.getValue();
                    entry.setDouble(value);
                    bindings.add(new TunerBinding(param, entry));
                    log("Registered: %s = %.4f", entry.getName(), value);
                }
            }
        }

        log("Initialized with %d tunable parameters", bindings.size());
    }

    public void periodic() {
        if (closed) {
            log("periodic() called after close() — ignoring");
            return;
        }

        for (TunerBinding binding : bindings) {
            double currentValue = binding.param.getValue();
            double dashboardValue = binding.entry.getDouble(currentValue);

            if (!MathUtil.isNear(currentValue, dashboardValue, VALUE_CHANGE_TOLERANCE)) {
                binding.param.setValue(dashboardValue);
                double appliedValue = binding.param.getValue();
                // Write back the applied value to the NetworkTable in case it is
                // different from original value due to clamping on bounds
                binding.entry.setDouble(appliedValue);
                log("%s changed: %.4f -> %.4f",
                    binding.entry.getName(), currentValue, appliedValue);
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        for (TunerBinding binding : bindings) {
            binding.entry.unpublish();
        }
        bindings.clear();

        log("Closed — all tuning entries unpublished");
    }

    private void log(String fmt, Object... args) {
        System.out.printf("[TuningManager] " + fmt + "%n", args);
    }

    private record TunerBinding(TuningParameter param, NetworkTableEntry entry) {}
}
```

Key design decisions:

- **Fully initialized at construction** — The constructor creates all NetworkTables entries
  and populates them with current subsystem values. A new `TuningManager` is created each
  time test mode is entered, so the dashboard sees all parameters immediately.
- **Table hierarchy** — Uses `getTable("Tuning")` with `getSubTable()` to build a proper
  hierarchy (e.g., `Tuning/Intake/Arm/kP`) rather than flat keys in the root namespace.
- **Cached entry references** — `NetworkTableEntry` objects are resolved once at construction
  and reused in `periodic()`, avoiding string lookups every cycle.
- **Dashboard precedence** — `periodic()` implements a one-way sync: the dashboard value
  always wins when it differs from the subsystem's current value. This makes the dashboard
  the single source of truth during tuning sessions.
- **Tolerance-based change guard** — `periodic()` uses `MathUtil.isNear()` to compare the
  dashboard value with the current subsystem value, avoiding floating-point equality pitfalls.
  `param.setValue()` is only called when the values differ beyond `VALUE_CHANGE_TOLERANCE`.
  The write-back to NetworkTables (`entry.setDouble`) only occurs when a change is detected,
  avoiding redundant writes on every cycle. After `setValue()`, the write-back re-reads the
  value via `getValue()` so the dashboard reflects the actual applied value, which may differ
  from the requested value due to bounds clamping.
- **Change logging** — Parameter changes are logged to `System.out` with the full
  parameter path and old/new values (e.g., `[TuningManager] /Tuning/Intake/Arm/kP
  changed: 0.5000 -> 0.7000`). The path comes from `NetworkTableEntry.getName()`,
  which returns the full NetworkTables path for the entry. Initialization logs each
  registered parameter and a summary count. Shutdown logs when entries are unpublished.
  This provides a complete audit trail of a tuning session without requiring additional
  tooling.
- **Clean shutdown** — `close()` calls `unpublish()` on every `NetworkTableEntry` to remove
  the tuning entries from NetworkTables when test mode exits, then clears the binding list.
- **Single-pass construction** — The constructor iterates each provider exactly once, calling
  `getSubsystemName()` and `getTuners()` once per provider and storing the results in local
  variables. Validation and NetworkTables binding happen in the same pass, eliminating
  redundant calls to provider methods.
- **Inline validation** — Duplicate names are checked at each level of the hierarchy (subsystem,
  tuner, parameter) as the constructor iterates, throwing `IllegalArgumentException` on
  duplicates. This fails fast at construction time rather than silently producing overlapping
  NetworkTables paths. The same parameter name may appear in different tuners (e.g.,
  `Intake/Arm/kP` and `Drive/Heading/kP`) — this is intentional, since the full
  NetworkTables path is unique.
- **Empty provider skipping** — Providers that return an empty tuner list are skipped entirely,
  preventing empty subsystem tables from appearing in NetworkTables.
- **AutoCloseable** — Implements `AutoCloseable` to support try-with-resources if desired,
  though the typical usage pattern is explicit `close()` in `testExit()`. The `close()` method
  is idempotent — calling it multiple times is safe and has no effect after the first call.

#### Usage in Robot.java

```java
public class Robot extends TimedRobot {

    private RobotContainer robotContainer;
    private TuningManager tuningManager;

    @Override
    public void robotInit() {
        robotContainer = new RobotContainer();
    }

    @Override
    public void testInit() {
        tuningManager = new TuningManager(robotContainer.getTunableSubsystems());
    }

    @Override
    public void testPeriodic() {
        tuningManager.periodic();
    }

    @Override
    public void testExit() {
        tuningManager.close();
        tuningManager = null;
    }
}
```

---

## Key Design Principles

### 1. Explicit Over Automatic

- No reflection or hidden discovery
- All tuners are explicitly defined and aggregated

---

### 2. Flat by Default, Hierarchical if Needed

- `RobotContainer` aggregates `TunableProvider` instances in a flat list
- Each subsystem owns its tuners and subsystem name
- No deep nesting is required — FRC robots typically have a single level of subsystems
- If a subsystem internally holds child `TunableProvider` references, it can aggregate
  their tuners into its own `getTuners()` method to support hierarchical composition

---

### 3. Separation of Concerns

- Subsystems define tuners and control bounds checking
- RobotContainer aggregates providers
- TuningManager orchestrates NetworkTables sync and validates structure
- Tuning system does not depend on subsystem internals

---

### 4. Future Enhancements

#### Parameter Metadata

Add optional metadata to `TuningParameter` such as units. This would allow
dashboards to render labels with appropriate units (e.g., "rad/s", "volts").
Min/max bounds are already supported by the current design.

#### Non-Double Parameter Types

The initial implementation supports only `double` parameters, which covers PID gains,
feedforward constants, and speed values. Support for additional types (boolean toggles,
integer values, enum-backed modes) can be added when concrete use cases arise that
require them.

#### Persistence

Support saving tuned values so they survive redeploys. Options include:
- WPILib Preferences (backed by NetworkTables persistent storage)
- JSON export/import (load a file at robot startup)

For now, developers read the final tuned values from the dashboard and hard-code
them into constants.

---

## Summary

- `TunableProvider` defines *capability* — who has tunable parameters, including the subsystem name
- `Tuner` defines *grouping* — a named set of parameters within a subsystem
- `TuningParameter` defines *individual values* — a getter/setter pair with optional bounds
- `TuningManager` defines *orchestration* — validates structure, syncs parameters with NetworkTables, and cleans up on close

This structure provides a clean, scalable, and extensible foundation for robot tuning systems in FRC.
