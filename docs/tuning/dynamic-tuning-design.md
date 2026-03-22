# FRC Dynamic PID Tuning Architecture

## Overview

This document describes an architecture for **dynamic tuning of control parameters (e.g., PID)** using NetworkTables (Elastic/Shuffleboard), while maintaining:

- Clean separation from production robot code
- Deterministic updates within the control loop
- Subsystem ownership of control logic
- Safe operation (restricted to test mode)

---

## Design Goals

1. **Non-invasive to production code**
   - Subsystems should not depend on NetworkTables or UI tools
   - No tuning logic embedded in control loops

2. **Explicit ownership**
   - Subsystems define what is tunable and how updates are applied

3. **Deterministic updates**
   - All parameter changes occur during the robot control loop (e.g., `testPeriodic`)

4. **Extensibility**
   - Easy to add new tunable components

5. **Safety**
   - Tuning enabled only in test mode (or via explicit toggle)

---

## Core Concepts

### 1. `Tuner` Interface

A `Tuner` represents a single logical unit of tuning (e.g., "Drive PID", "Arm Controller").

```java
public interface Tuner {
    String getName();

    Map<String, Double> getValues();

    void setValues(Map<String, Double> values);
}
```

#### Responsibilities:
- Expose current parameter values
- Accept updated values
- Apply updates safely (clamping, resetting, etc.)

---

### 2. Subsystem Integration

Each subsystem defines one or more `Tuner` instances.

Example:

```java
public class DriveSubsystem {

    private PIDController pid;

    public Tuner getDrivePIDTuner() {
        return new Tuner() {
            @Override
            public String getName() {
                return "Drive PID";
            }

            @Override
            public Map<String, Double> getValues() {
                return Map.of(
                    "kP", pid.getP(),
                    "kI", pid.getI(),
                    "kD", pid.getD()
                );
            }

            @Override
            public void setValues(Map<String, Double> values) {
                double p = values.get("kP");
                double i = values.get("kI");
                double d = values.get("kD");

                // Optional: validation / clamping
                pid.setPID(p, i, d);
            }
        };
    }
}
```

#### Notes:
- Subsystems retain full control over how parameters are applied
- No direct exposure of internal controllers
- No dependency on NetworkTables

---

### 3. `RobotContainer` Aggregation

Expose all tuners from subsystems:

```java
public List<Tuner> getTuners() {
    return List.of(
        driveSubsystem.getDrivePIDTuner(),
        armSubsystem.getArmTuner()
    );
}
```

---

### 4. `TuningManager`

Central class responsible for:
- Creating NetworkTables entries
- Synchronizing values
- Applying updates during the control loop

#### Responsibilities:

1. Discover tuners
2. Create NT entries for each parameter
3. Read updated values
4. Apply updates via `Tuner.setValues()`

---

### 5. NetworkTables Structure

Suggested hierarchy:

```
/Tuning/
    Drive PID/
        kP
        kI
        kD
    Arm Controller/
        kP
        kD
```

---

## TuningManager Example

```java
public class TuningManager {

    private final List<Tuner> tuners;
    private final Map<String, Map<String, NetworkTableEntry>> entries = new HashMap<>();

    public TuningManager(List<Tuner> tuners) {
        this.tuners = tuners;
        initializeEntries();
    }

    private void initializeEntries() {
        for (Tuner tuner : tuners) {
            Map<String, Double> values = tuner.getValues();
            Map<String, NetworkTableEntry> tunerEntries = new HashMap<>();

            for (String key : values.keySet()) {
                NetworkTableEntry entry = NetworkTableInstance.getDefault()
                    .getTable("Tuning")
                    .getSubTable(tuner.getName())
                    .getEntry(key);

                entry.setDouble(values.get(key));
                tunerEntries.put(key, entry);
            }

            entries.put(tuner.getName(), tunerEntries);
        }
    }

    public void update() {
        for (Tuner tuner : tuners) {
            Map<String, NetworkTableEntry> tunerEntries = entries.get(tuner.getName());
            Map<String, Double> newValues = new HashMap<>();

            for (Map.Entry<String, NetworkTableEntry> entry : tunerEntries.entrySet()) {
                newValues.put(entry.getKey(), entry.getValue().getDouble(0.0));
            }

            tuner.setValues(newValues);
        }
    }
}
```

---

## Robot Integration

### In `Robot.testInit()`

```java
tuners = robotContainer.getTuners();
tuningManager = new TuningManager(tuners);
```

### In `Robot.testPeriodic()`

```java
tuningManager.update();
```

---

## Optional Enhancements

### 1. Change Detection
Only apply updates if values have changed:

```java
if (!newValues.equals(previousValues)) {
    tuner.setValues(newValues);
}
```

---

### 2. Validation / Clamping
Handled inside `Tuner.setValues()`:

```java
double p = MathUtil.clamp(values.get("kP"), 0.0, 10.0);
```

---

### 3. Integral Reset (for PID)
If `kI` changes:

```java
pid.setI(newI);
pid.reset();
```

---

### 4. Enable/Disable Toggle

Add a global NT flag:

```
/Tuning/enabled
```

```java
if (!enabled) return;
```

---

## Key Design Principles

- **Subsystems own behavior**
- **Tuning is an external control layer**
- **No asynchronous updates**
- **All changes occur inside the control loop**
- **Avoid hidden side effects**

---

## Anti-Patterns to Avoid

- Updating controllers from NT listeners
- Recreating PID controllers at runtime
- Letting subsystems depend on NetworkTables
- Applying updates outside the robot loop
- Using string-based lookup systems for tuners

---

## Summary

This architecture provides:

- Safe, real-time parameter tuning
- Clean separation of concerns
- Scalable structure for multiple subsystems
- Minimal impact on production robot code

It is well-suited for iterative tuning during development and testing while maintaining competition reliability.