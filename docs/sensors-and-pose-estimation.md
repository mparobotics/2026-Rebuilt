# Sensors, Robot Pose, and Pose Estimation

## Overview

The robot needs to know **where it is on the field** at all times. This field position
is called the robot's **pose** — an (x, y, θ) value representing its position and the
direction it is facing.

No single sensor can reliably provide the pose. Instead, the robot **estimates** its pose
by combining readings from multiple sensors, each of which measures a different aspect
of the robot's state.

## Sensors

| Sensor | What it measures | Output |
|--------|-----------------|--------|
| **Pigeon2 gyro** | Which direction the robot is facing (yaw) | Heading in degrees |
| **Wheel encoders** | How far each swerve module wheel has traveled | Distance in meters per wheel |
| **Limelight cameras** | Position of AprilTags in the camera's view | Full pose estimate (x, y, θ) |

## The Pose Estimator

WPILib's `SwerveDrivePoseEstimator` fuses sensor readings into a single pose estimate.
Each robot loop (every 20 ms), it:

1. Reads the pigeon yaw
2. Reads the four wheel encoder positions
3. Computes how the pose changed since the last cycle
4. Optionally incorporates vision measurements from Limelight cameras

```
Pigeon Yaw ──────┐
                  ├──► SwerveDrivePoseEstimator ──► Pose2d (x, y, θ)
Wheel Encoders ──┘
                        ▲
Limelight Cameras ──────┘ (optional soft corrections)
```

## How Sensor Readings Are Used: Absolute vs. Delta

The estimator treats the pigeon and encoders **differently**. Understanding this
distinction is important for getting hard resets and simulation right.

### Pigeon Yaw — Absolute Value

The estimator reads the pigeon's yaw as an **absolute heading**. It records the yaw
at reset time as a baseline, and on each subsequent cycle computes:

```
rotation change = current pigeon yaw − baseline pigeon yaw
```

The pigeon holds a persistent value that the estimator reads directly. If anything
externally changes the pigeon's value (e.g., simulation overwriting it), the estimator
interprets the change as real robot rotation.

**Key implication:** If the pigeon's value is overwritten to something unexpected, the
estimator sees a large sudden rotation and the pose gets corrupted.

### Wheel Encoders — Delta (Relative Change)

The estimator reads each wheel encoder's **position** (total distance traveled), records
it at reset time as a baseline, and on each subsequent cycle computes:

```
distance traveled = current encoder position − baseline encoder position
```

The absolute encoder value doesn't matter — only how much it has **changed** since the
baseline was recorded. If the encoders read [100, 200, 150, 175] at reset time, those
become the baseline. A reading of [100.05, 200.03, 150.04, 175.02] on the next cycle
means each wheel moved a small amount — regardless of the absolute numbers.

**Key implication:** As long as encoder values change incrementally (by small deltas each
cycle), the estimator tracks correctly. The starting absolute value is irrelevant.

### Vision — Soft Correction

Limelight cameras provide an independent full-pose estimate by detecting AprilTags.
These are added as **soft corrections** via `addVisionMeasurement()` — the estimator
blends them in using a Kalman filter rather than overwriting the pose. This means vision
data nudges the estimate gently rather than causing sudden jumps.

## Hard Resets

A **hard reset** occurs when we have an authoritative "oracle" pose (e.g., a known
starting position for autonomous) and want to force the estimator to adopt it.

We call `resetPosition(gyroAngle, modulePositions, newPose)`, which:

1. Records the current pigeon yaw as the new baseline for rotation deltas
2. Records the current encoder positions as the new baseline for distance deltas
3. Sets the internal pose estimate to the new pose

After a reset, the estimator computes all future changes relative to these new baselines.

### Why the Pigeon Must Be Updated During a Hard Reset

Because the estimator uses the pigeon's value as an absolute reference, the pigeon must
be set to match the new pose's rotation **before or at the same time** as the reset.
Otherwise:

- Reset records baseline gyro = (whatever the pigeon currently reads)
- If the pigeon reads something different on the next cycle (e.g., simulation overwrites
  it), the estimator computes a large false rotation delta
- The pose estimate gets corrupted immediately

### Why Encoders Don't Need Updating During a Hard Reset

Because the estimator uses encoder **deltas**, and `resetPosition()` records the current
encoder values as the new baseline, the absolute encoder values are irrelevant. Whatever
the encoders happen to read at reset time becomes the zero point for future distance
calculations.

## Simulation Implications

In simulation, the `SimulationManager` updates simulated sensors each cycle:

| Sensor | How simulation updates it | Safe across resets? |
|--------|--------------------------|-------------------|
| **Pigeon** | Overwrites with absolute value from internal sim pose | ❌ No — can corrupt baseline |
| **Encoders** | Adds incremental delta to current value | ✅ Yes — preserves baseline |

This is why the simulation must be notified of hard resets: the `SimulationManager`'s
internal pose must be synced to the new pose so that the absolute pigeon value it writes
each cycle is consistent with what the estimator expects.
