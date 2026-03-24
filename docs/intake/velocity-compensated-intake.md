# Proposal: Velocity-Compensated Intake Roller Speed

## The Problem

When our robot drives toward a game piece, the intake rollers sometimes push it away instead of picking it up. This gets worse the faster we drive.

Here's why: if the robot approaches a piece at 2 m/s but the rollers only spin at a surface speed of 2 m/s, there's zero net "grabbing" force. The rollers need to spin **faster** than the robot is moving to reliably pull pieces in.

## What Other Teams Do

A widely referenced guideline (from Team 254, documented on [FRCDesign.org](https://www.frcdesign.org/learning-course/stage2/2C/intake-golden-rules/)) is:

> Intake roller surface speed should be at least **2x** the robot's ground speed.

Team 1678 [designed their Rapid React intake](https://www.frcdesign.org/mechanism-examples/intake/linkage/1678_2022_intake/) at 2.5x. The idea is simple: if you might drive at 3 m/s while intaking, your rollers should spin at 6+ m/s surface speed.

## Where We Stand Today

With our current settings, the numbers look like this:

- `INTAKE_SPEED = 0.50` (max 50% motor output)
- Auto uses `INTAKE_POWER = -0.5`, so actual motor output = **25%**
- At 25% output, our roller surface speed is roughly **2.0 m/s**
- Our auto drives at **2.0 m/s**

That's a **1:1 ratio** -- barely matching robot speed, well below the recommended 2x. This likely explains why intake is unreliable while moving.

## A Possible Approach

One option would be to make the intake aware of how fast the robot is driving and automatically set roller speed to maintain the 2x ratio. The operator model would be simplified to:

- **Stick forward** (past deadband) = intake on. Speed is automatic.
- **Stick backward** (past deadband) = eject at a fixed reverse speed.
- **Stick centered** = intake off.

The operator doesn't need to modulate roller speed -- the "right" speed is determined by the robot's velocity, not feel.

### The Core Math

When the intake is on, the roller output is:

```java
velocityBasedOutput = (robotForwardMps * INTAKE_SPEED_MULTIPLIER) / ROLLER_MAX_SURFACE_SPEED;
output = MathUtil.clamp(velocityBasedOutput, INTAKE_MIN_SPEED, 1.0);
```

Where:
- `robotForwardMps` comes from `SwerveSubsystem.getChassisSpeeds().vxMetersPerSecond`
- `INTAKE_SPEED_MULTIPLIER` is the target multiplier (e.g. 2.0, the minimum recommended by other teams) -- tunable
- `ROLLER_MAX_SURFACE_SPEED` is how fast our rollers spin at 100% output (see below)
- `INTAKE_MIN_SPEED` is the floor -- the slowest the rollers will ever spin when the intake is on (e.g. 0.30). This ensures the rollers still grab pieces when the robot is stationary or moving slowly, since the velocity formula would give near-zero at low speeds.

The `Math.max` handles the transition naturally: at low robot speeds `INTAKE_MIN_SPEED` applies; once the robot is fast enough that the 2x formula exceeds the minimum, velocity compensation takes over.

### Constants

**Tuning constants** (adjust based on testing):
- `INTAKE_MIN_SPEED` -- roller output floor when intake is on (e.g. 0.30)
- `INTAKE_SPEED_MULTIPLIER` -- target roller-to-ground-speed ratio (e.g. 2.0)
- `INTAKE_EJECT_SPEED` -- fixed reverse speed for ejecting

**Physical parameters** (tweak if the hardware changes):

```java
public static final double ROLLER_MOTOR_MAX_RPM = 5400;   // measure this (see below)
public static final int    ROLLER_GEAR_MOTOR_TEETH = 18;  // gear on motor shaft
public static final int    ROLLER_GEAR_ROLLER_TEETH = 24; // gear on roller shaft
public static final double ROLLER_BASE_DIAMETER = Units.inchesToMeters(1.25);
public static final double ROLLER_SLEEVE_THICKNESS = Units.inchesToMeters(0.125);
```

**Computed from the above** (not hardcoded):

```java
// Gear ratio: how many roller turns per motor turn (18:24 = 0.75)
public static final double ROLLER_GEAR_RATIO =
    (double) ROLLER_GEAR_MOTOR_TEETH / ROLLER_GEAR_ROLLER_TEETH;

// Roller outer diameter including the silicone sleeve on each side
public static final double ROLLER_OUTER_DIAMETER =
    ROLLER_BASE_DIAMETER + 2 * ROLLER_SLEEVE_THICKNESS;

// How far the roller surface travels in one full rotation
public static final double ROLLER_CIRCUMFERENCE =
    Math.PI * ROLLER_OUTER_DIAMETER;

// Roller RPM = motor RPM scaled by gear ratio
public static final double ROLLER_MAX_RPM =
    ROLLER_MOTOR_MAX_RPM * ROLLER_GEAR_RATIO;

// Surface speed (m/s) = roller RPM * circumference, converted from per-minute to per-second
public static final double ROLLER_MAX_SURFACE_SPEED =
    ROLLER_MAX_RPM * ROLLER_CIRCUMFERENCE / 60.0;
```

Each physical parameter is its own constant so we can verify or adjust them independently. For example, if the silicone sleeve turns out to be thinner than 1/8", just change that one value and everything recalculates.

### Measuring ROLLER_MOTOR_MAX_RPM

The `5400` above is a placeholder. To get the real value:

1. Deploy code, open SmartDashboard
2. Lower the intake arm
3. Hold the intake stick at full power (the rollers don't touch the ground, so just run them)
4. Read the motor RPM from SmartDashboard (the SparkMax encoder reports this)
5. Update the constant with the observed value

This gives a real-world number that accounts for friction, belt/gear losses, etc.

## What Would Change in the Code

Three files would be touched. Auto routines would **not** need any changes.

### Constants.java

Add the physical parameter and tuning constants shown above.

### IntakeSubsystem.java

**Constructor** -- accept a `DoubleSupplier` so the subsystem can read the robot's forward speed each cycle:

```java
private final java.util.function.DoubleSupplier forwardSpeedSupplier;
private double requestedIntakePower = 0.0;

public IntakeSubsystem(java.util.function.DoubleSupplier forwardSpeedSupplier) {
    this.forwardSpeedSupplier = forwardSpeedSupplier;
    // ... rest of existing constructor unchanged ...
}
```

**`setIntakePower()`** -- instead of setting the motor directly, just store what the operator requested. The actual motor command happens in `periodic()`:

```java
public void setIntakePower(double power) {
    // store the request power to be used by periodic()
    requestedIntakePower = Math.max(-1.0, Math.min(1.0, power));
    intakeOn = Math.abs(requestedIntakePower) > 0.0;
}
```

**`periodic()`** -- at the end, after the existing arm PID logic, add roller motor control. This is where the compensation actually happens:

```java
// Roller velocity compensation
double output = 0.0;

if (requestedIntakePower < 0) {
    // Intaking: apply velocity compensation
    double forwardMps = Math.max(0, forwardSpeedSupplier.getAsDouble());

    // Output calculated from the robot's current velocity
    double velocityBasedOutput =
        (forwardMps * IntakeConstants.INTAKE_SPEED_MULTIPLIER) / IntakeConstants.ROLLER_MAX_SURFACE_SPEED;

    // Clamp between INTAKE_MIN_SPEED (so we grab pieces even when stationary)
    // and 1.0 (motor can't exceed 100%). Negate because intake direction is negative.
    output = -MathUtil.clamp(velocityBasedOutput, IntakeConstants.INTAKE_MIN_SPEED, 1.0);

} else if (requestedIntakePower > 0) {
    // Ejecting: fixed reverse speed
    output = IntakeConstants.INTAKE_EJECT_SPEED;
}

intakeMotor.set(output);
```

The key idea: `periodic()` runs every 20ms, so the motor output continuously tracks the robot's speed even though `setIntakePower()` might only be called once (as in auto's `runOnce`).

### RobotContainer.java

One-line change to wire the swerve speed into the intake:

```java
// Before:
private final IntakeSubsystem m_intake = new IntakeSubsystem();

// After:
private final IntakeSubsystem m_intake = new IntakeSubsystem(
    () -> m_drive.getChassisSpeeds().vxMetersPerSecond);
```

This works because `m_drive` is declared before `m_intake`, and the lambda is only called later at runtime.

## What This Would Look Like With Real Numbers

Assuming measured max surface speed ~8.1 m/s and `INTAKE_MIN_SPEED = 0.30`:

Motor output is the result of `max(INTAKE_MIN_SPEED, (robotSpeed * INTAKE_SPEED_MULTIPLIER) / ROLLER_MAX_SURFACE_SPEED)`. Roller-to-robot ratio is roller surface speed / robot speed (the 2x target).

| Scenario | Robot Speed | Motor Output | Roller Surface Speed | Roller:Robot Ratio | Notes |
|---|---|---|---|---|---|
| Stationary | 0 m/s | 30% | 2.4 m/s | n/a | Min speed applies |
| Slow drive | 1 m/s | 30% | 2.4 m/s | 2.4x | Min speed still higher than 2x formula |
| Auto | 2 m/s | 49% | 4.0 m/s | 2.0x | 2x formula takes over |
| Fast teleop | 3 m/s | 74% | 6.0 m/s | 2.0x | |
| Full speed | 5 m/s | 100% | 8.1 m/s | 1.62x | Mechanical limit |

## Also Worth Noting

There's a bug in `toggleIntake()` where the boolean assignments are flipped -- `intakeOn` gets set to `false` when turning on and `true` when turning off. Worth fixing regardless of whether we do velocity compensation.

## Open Questions for the Team

- Does the 2x ratio feel right as a starting point, or should we try something different?
- What should `INTAKE_MIN_SPEED` be? 0.30? Higher?
- Are there concerns about current draw from running the rollers harder at high speed?
- Does simplifying the stick to on/off/eject make sense, or do we want to keep variable speed control?
