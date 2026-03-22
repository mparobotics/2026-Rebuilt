# FRC Tuning Library

`org.moundsparkacademy.frc.tuning`

A lightweight library for dynamically tuning robot control parameters (PID gains,
feedforward constants, motor speeds, etc.) through any NetworkTables-compatible
dashboard during test mode.

## Quick Start

### 1. Implement `TunableProvider` on your subsystem

```java
import org.moundsparkacademy.frc.tuning.TunableProvider;
import org.moundsparkacademy.frc.tuning.Tuner;
import org.moundsparkacademy.frc.tuning.TuningParameter;

public class ArmSubsystem extends SubsystemBase implements TunableProvider {

    private final PIDController pidController = new PIDController(1.0, 0.0, 0.0);
    private double motorSpeed = 0.5;

    @Override
    public String getSubsystemName() { return "Arm"; }

    @Override
    public List<Tuner> getTuners() {
        return List.of(
            new Tuner("PID", List.of(
                new TuningParameter("kP",
                    pidController::getP, pidController::setP,
                    0, 20.0),
                new TuningParameter("kI",
                    pidController::getI,
                    value -> { pidController.setI(value); pidController.reset(); },
                    0, 10.0),
                new TuningParameter("kD",
                    pidController::getD, pidController::setD,
                    0, 5.0)
            )),
            new Tuner("Motor", List.of(
                new TuningParameter("Speed",
                    () -> motorSpeed,
                    value -> motorSpeed = value,
                    0, 1.0)
            ))
        );
    }

    // ... rest of subsystem ...
}
```

### 2. Expose providers from `RobotContainer`

```java
public class RobotContainer {

    private final ArmSubsystem arm = new ArmSubsystem();

    public List<TunableProvider> getTunableSubsystems() {
        return List.of(arm);
    }
}
```

### 3. Wire `TuningManager` into `Robot.java`

```java
import org.moundsparkacademy.frc.tuning.TuningManager;

public class Robot extends TimedRobot {

    private RobotContainer robotContainer;
    private TuningManager tuningManager;

    @Override
    public void robotInit() {
        robotContainer = new RobotContainer();
    }

    @Override
    public void testInit() {
        if (tuningManager != null) {
            tuningManager.close();
        }
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

### 4. Tune from the dashboard

1. Connect to the robot and open any NetworkTables dashboard (Shuffleboard, Elastic, Glass)
2. Switch to **Test mode** in the Driver Station
3. Parameters appear under `Tuning/<Subsystem>/<Tuner>/<Parameter>`
   (e.g., `Tuning/Arm/PID/kP`)
4. Edit values on the dashboard — they are applied to the subsystem on the next cycle
5. Exit test mode when done — tuned values remain active until the robot program restarts

## Classes

| Class | Role |
|---|---|
| `TunableProvider` | Interface — subsystems implement this to declare their tunable parameters |
| `Tuner` | Record — a named group of related parameters within a subsystem |
| `TuningParameter` | Class — a single tunable value with getter, setter, and optional bounds |
| `TuningManager` | Class — orchestrates NetworkTables sync; created in test mode only |
| `TuningValidation` | Utility — centralizes name validation rules (null, blank, `/`) |

## TuningParameter Patterns

**Method references (simplest case):**
```java
new TuningParameter("kP", controller::getP, controller::setP, 0, 20.0)
```

**Lambda with side effects (e.g., resetting integral accumulator):**
```java
new TuningParameter("kI",
    controller::getI,
    value -> { controller.setI(value); controller.reset(); },
    0, 10.0)
```

**Field-backed parameter (e.g., motor speed):**
```java
new TuningParameter("Speed", () -> speed, value -> speed = value, 0, 1.0)
```

**No bounds (any value is safe):**
```java
new TuningParameter("kG", feedforward::getKg, feedforward::setKg)
```

**One-sided bound (lower only):**
```java
new TuningParameter("kV", feedforward::getKv, feedforward::setKv, 0, Double.POSITIVE_INFINITY)
```

## Bounds

Bounds are optional. When provided, `TuningParameter.setValue()` clamps the value
before passing it to the setter. The dashboard is updated with the actual applied
value, so the operator sees the effect of clamping immediately.

- 3-arg constructor: no bounds (any value accepted)
- 5-arg constructor: both min and max specified
- One-sided bounds: use `Double.NEGATIVE_INFINITY` or `Double.POSITIVE_INFINITY`

## Lifecycle

The tuning system is active **only in test mode**:

- `testInit()` — creates a new `TuningManager`, which publishes all parameters to
  NetworkTables with their current values
- `testPeriodic()` — syncs dashboard values to subsystem parameters each cycle
- `testExit()` — calls `close()`, which unpublishes all NetworkTables entries

Tuned parameter values persist in the subsystems after exiting test mode. To revert
to the original values, restart the robot program.

## Validation

Names are validated at construction time. The following cause `IllegalArgumentException`:

- Null or blank names (subsystem, tuner, or parameter)
- Names containing `/` (would create malformed NetworkTables paths)
- Duplicate subsystem names across providers
- Duplicate tuner names within a subsystem
- Duplicate parameter names within a tuner
- A provider returning an empty tuner list
