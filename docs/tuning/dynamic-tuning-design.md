# FRC Tuning Library Design (Draft)

## Overview

This document describes a lightweight, extensible tuning system for FRC robots.
The system is designed to be:

- Explicit (no hidden magic)
- Composable (supports subsystem hierarchies)
- Extensible (supports future UI and tooling)
- Library-friendly (usable by other teams)

---

## Package Naming

This library is intended to be published as an open source contribution to the FRC
community. To that end, it uses the package namespace:

```
org.moundsparkacademy.frc.tuning
```

The `org.moundsparkacademy.frc` namespace provides a clear home for this and any
future libraries published by the team.

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

A `TunableProvider` is any class that exposes tuners.

```java
public interface TunableProvider {
    List<Tuner> getTuners();
}
```

- Subsystems implement this to expose their tunable parameters
- Higher-level components (like RobotContainer) aggregate tuners
- Encourages compositional design

---

### Tuner

A `Tuner` represents a logical grouping of tunable parameters.

```java id="uk3o1m"
public interface Tuner {
    String getSubsystemName();
    String getName();
    List<TuningParameter> getParameters();
}
```

- Represents a logical grouping of tunable parameters within a subsystem
- `getSubsystemName()` returns the owning subsystem (e.g., `"Intake"`)
- `getName()` returns the tuner name within that subsystem (e.g., `"Arm"`)
- Together these form a natural namespace for NetworkTables paths (e.g., `Intake/Arm/kP`)

---

### TuningParameter

A `TuningParameter` represents a single tunable value.

#### Design Goals:
- Encapsulate getter/setter behavior
- Avoid stringly-typed maps
- Support future metadata (min/max, units, etc.)

#### Suggested Implementation (Stateless)

```java id="p5n6xv"
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class TuningParameter {

    private final String name;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    public TuningParameter(String name, DoubleSupplier getter, DoubleConsumer setter) {
        this.name = name;
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() {
        return name;
    }

    public double getValue() {
        return getter.getAsDouble();
    }

    public void setValue(double value) {
        setter.accept(value);
    }
}
```

---

## Hierarchical Composition

`RobotContainer` aggregates tuners from all subsystems. Each subsystem may return
one or more tuners depending on how many tunable components it contains.

### Example

- `RobotContainer` (top-level `TunableProvider`)
  - `DriveSubsystem` — returns tuners for drive control
  - `IntakeSubsystem` — returns tuners for Arm (PID) and Rollers (speed)

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

```java id="brxrn9"
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
    public List<Tuner> getTuners() {
        return List.of(
            createArmTuner(),
            createRollersTuner()
        );
    }

    private Tuner createArmTuner() {
        return new Tuner() {
            @Override
            public String getSubsystemName() { return "Intake"; }

            @Override
            public String getName() { return "Arm"; }

            @Override
            public List<TuningParameter> getParameters() {
                return List.of(
                    new TuningParameter("kP",
                        intakeArmController::getP,
                        intakeArmController::setP),
                    new TuningParameter("kI",
                        intakeArmController::getI,
                        value -> {
                            intakeArmController.setI(value);
                            intakeArmController.reset();
                        }),
                    new TuningParameter("kD",
                        intakeArmController::getD,
                        intakeArmController::setD),
                    new TuningParameter("kS",
                        intakeArmFeedforward::getKs,
                        intakeArmFeedforward::setKs),
                    new TuningParameter("kG",
                        intakeArmFeedforward::getKg,
                        intakeArmFeedforward::setKg),
                    new TuningParameter("kV",
                        intakeArmFeedforward::getKv,
                        intakeArmFeedforward::setKv),
                    new TuningParameter("kA",
                        intakeArmFeedforward::getKa,
                        intakeArmFeedforward::setKa)
                );
            }
        };
    }

    private Tuner createRollersTuner() {
        return new Tuner() {
            @Override
            public String getSubsystemName() { return "Intake"; }

            @Override
            public String getName() { return "Rollers"; }

            @Override
            public List<TuningParameter> getParameters() {
                return List.of(
                    new TuningParameter("BaseSpeed",
                        () -> rollerBaseSpeed,
                        value -> rollerBaseSpeed = value),
                    new TuningParameter("SpeedScale",
                        () -> rollerSpeedScale,
                        value -> rollerSpeedScale = value)
                );
            }
        };
    }
}
```

Note how the `TuningParameter` getter/setter lambdas bind directly to the subsystem's
internal state:

- **PID gains** use method references on `PIDController` (`::getP`, `::setP`).
  The `kI` setter also calls `reset()` to clear the integral accumulator, preventing
  a stale accumulator from causing an output spike when the gain changes.
- **Feedforward gains** use method references on `ArmFeedforward` (`::getKs`, `::setKs`)
- **Roller parameters** use lambdas that read/write mutable fields (`rollerBaseSpeed`, `rollerSpeedScale`)

The subsystem controls what is exposed and how values are applied. The tuning system
never reaches into subsystem internals.

---

### RobotContainer Example

```java id="ye5nhw"
public class RobotContainer implements TunableProvider {

    private final DriveSubsystem drive;
    private final IntakeSubsystem intake;

    public RobotContainer() {
        this.drive = new DriveSubsystem();
        this.intake = new IntakeSubsystem();
    }

    @Override
    public List<Tuner> getTuners() {
        return Stream.of(
            drive.getTuners(),
            intake.getTuners()
        )
        .flatMap(List::stream)
        .toList();
    }
}
```

---

### TuningManager

The `TuningManager` is the orchestration class that coordinates the tuning process.
It acts as the bridge between the `TunableProvider` hierarchy and NetworkTables.

#### Responsibilities

1. **Discover parameters** — Collect all `Tuner` instances from the `TunableProvider` hierarchy
2. **Publish to NetworkTables** — Write current parameter values so they appear on the dashboard
3. **Read from NetworkTables** — Read updated values as edited by the operator on the dashboard
4. **Apply changes** — Call the `TuningParameter` setters to update control parameters

#### Lifecycle

The `TuningManager` is created and initialized entirely within `Robot.testInit()`.
It is **not** created during `robotInit()` — this ensures the tuning system has zero
presence during autonomous and teleop modes, preventing any accidental leakage into
competition code.

The `periodic()` method is called from `Robot.testPeriodic()`. Because `testPeriodic()`
runs on the main robot loop, all parameter reads and writes are synchronized with the
control loop. There are no concurrent access concerns — parameters are never updated
outside this loop.

When test mode is exited, `Robot.testExit()` calls `close()` on the `TuningManager`
and sets the reference to `null`. This ensures the tuning system is fully torn down
and does not persist into subsequent auto or teleop modes.

#### Suggested Interface

```java
public class TuningManager {

    private final List<TunerBinding> bindings = new ArrayList<>();

    public TuningManager(TunableProvider provider) {
        NetworkTable rootTable = NetworkTableInstance.getDefault().getTable("Tuning");

        for (Tuner tuner : provider.getTuners()) {
            NetworkTable tunerTable = rootTable
                .getSubTable(tuner.getSubsystemName())
                .getSubTable(tuner.getName());

            for (TuningParameter param : tuner.getParameters()) {
                NetworkTableEntry entry = tunerTable.getEntry(param.getName());
                entry.setDouble(param.getValue());
                bindings.add(new TunerBinding(param, entry));
            }
        }
    }

    public void periodic() {
        for (TunerBinding binding : bindings) {
            double currentValue = binding.param.getValue();
            double dashboardValue = binding.entry.getDouble(currentValue);

            if (dashboardValue != currentValue) {
                binding.param.setValue(dashboardValue);
            }

            binding.entry.setDouble(binding.param.getValue());
        }
    }

    public void close() {
        bindings.clear();
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
- **Change guard** — `periodic()` only calls `param.setValue()` when the dashboard value
  differs from the current subsystem value.

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
        tuningManager = new TuningManager(robotContainer);
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

### 2. Composition Over Inheritance

- Subsystems can contain other subsystems
- Tuning hierarchy mirrors system architecture

---

### 3. Separation of Concerns

- Subsystems define tuners
- RobotContainer aggregates tuners
- Tuning system does not depend on subsystem internals

---

### 4. Extensibility

Future enhancements may include:

- Metadata on parameters (min, max, units)
- UI integration (Shuffleboard / Elastic)
- Persistence of tuning values
- Real-time updates via NetworkTables

---

## Future Extensions (Optional)

### Parameter Metadata

```java id="svqv7p"
public class TuningParameter {
    private final String name;
    private final double min;
    private final double max;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
}
```

---

### Stream-Based API (Advanced)

```java id="5qu82v"
Stream<Tuner> getTuners();
```

---

### UI Integration

- Group tuners by subsystem
- Automatically build hierarchical UI trees
- Sync values via NetworkTables

---

## Summary

- `TunableProvider` defines *capability* — who has tunable parameters
- `Tuner` defines *grouping* — a named set of parameters within a subsystem
- `TuningParameter` defines *individual values* — a single getter/setter pair
- `TuningManager` defines *orchestration* — syncs parameters with NetworkTables on the control loop

This structure provides a clean, scalable, and extensible foundation for robot tuning systems in FRC.