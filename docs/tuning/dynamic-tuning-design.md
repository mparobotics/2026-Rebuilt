# FRC Tuning Library Design (Draft)

## Overview

This document describes a lightweight, extensible tuning system for FRC robots.
The system is designed to be:

- Explicit (no hidden magic)
- Composable (supports subsystem hierarchies)
- Extensible (supports future UI and tooling)
- Library-friendly (usable by other teams)

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
    String getName();
    List<TuningParameter> getParameters();
}
```

- Represents a subsystem or a logical grouping within a subsystem
- Provides a human-readable name for UI grouping

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

Subsystems can be composed of smaller subsystems.

### Example

- `RobotContainer` (top-level)
  - `IntakeSubsystem`
    - `IntakeArmSubsystem`
    - `IntakeRollersSubsystem`

---

## Implementation Pattern

### Subsystem Example

```java id="brxrn9"
public class IntakeSubsystem implements TunableProvider {

    private final IntakeArmSubsystem arm;
    private final IntakeRollersSubsystem rollers;

    public IntakeSubsystem() {
        this.arm = new IntakeArmSubsystem();
        this.rollers = new IntakeRollersSubsystem();
    }

    @Override
    public List<Tuner> getTuners() {
        return List.of(
            arm.getTuner(),
            rollers.getTuner()
        );
    }
}
```

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

- `TunableProvider` defines *capability*
- `Tuner` defines *grouping*
- `TuningParameter` defines *individual values*

This structure provides a clean, scalable, and extensible foundation for robot tuning systems in FRC.