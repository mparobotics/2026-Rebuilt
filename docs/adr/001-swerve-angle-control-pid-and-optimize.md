---
status: proposed
date: 2026-01-08
decision-makers: {TBD - team to fill}
consulted: {TBD - team to fill}
informed: {TBD - team to fill}
---

# Swerve Module Angle Control: WPILib PIDController with Continuous Input and Standard Optimize Method

## Context and Problem Statement

Our swerve drive system controls the angle (steering) of each module using a PID controller. The angle control system operates at two different levels:

1. **Setpoint Selection (`optimize` method)**: Runs every 20ms on the roboRIO, determines the optimal target angle and wheel direction to minimize rotation distance. This method can flip the wheel 180° and reverse drive speed if it reduces the rotation needed (e.g., rotating 120° becomes rotating 60° in the opposite direction).

2. **PID Control Loop**: Runs at 1kHz (every 1ms) on the motor controller, calculates motor output based on the difference between current angle and target angle.

**The Problem**: The `optimize` method and continuous input are often thought to serve the same purpose, but they operate at different levels and serve complementary roles:

- **`optimize`** selects the **target setpoint** (what angle to aim for)
- **Continuous input** affects how the **PID controller calculates error** (how it interprets the difference between current and target)

**Why Both Are Needed**: Even when `optimize` correctly selects an efficient target, the PID controller can still calculate incorrect errors if it doesn't understand that angles wrap around. This happens because:

1. The `optimize` method runs every 20ms and sets a new target angle
2. The PID controller runs independently at 1kHz (20 times between each `optimize` update)
3. During those 20 PID cycles, the controller calculates error using raw angle values
4. Without continuous input, the PID treats angles as linear values, leading to incorrect error calculations near wrap-around boundaries

**Concrete Example**:

Consider a module that needs to rotate from 350° to 10°:

- **Without `optimize`**: If the system treated angles linearly, it might calculate a rotation of 340° (inefficient)
- **With `optimize`**: The system correctly identifies that rotating 20° is the shortest path (350° → 360° → 10°), so it sets the target to 10° and the rotation distance is only 20°

However, **even with `optimize` setting the correct target angle**, if the PID controller lacks continuous input:

- Current angle: 350° (from encoder)
- Target angle: 10° (from `optimize` - correctly chosen to minimize rotation)
- **PID error calculation without continuous input**: Error = 10° - 350° = **-340°** ❌
  - The PID controller treats angles as linear values and calculates a large negative error
  - This causes excessive motor output, overshoot, oscillation, or inefficient rotation
  - The PID controller doesn't understand that 350° and 10° are actually close together

- **PID error calculation with continuous input**: Error = **20°** ✅
  - The PID controller recognizes that 350° and 10° are only 20° apart (not 340°)
  - Motor output is appropriate for the actual rotation distance
  - The controller correctly interprets the circular nature of angles

**Note**: The `optimize` method ensures the rotation distance is ≤90° (by potentially flipping the wheel 180°), but it does not constrain the absolute angle values. The optimized target angle can be any value (e.g., 10°, 350°, -170°, etc.) depending on the current angle and desired direction. What matters is that the rotation needed to reach that target is minimized.

**Additional Scenario - Oscillation Near Wrap-Around Boundary**: This scenario demonstrates how small overshoots or encoder noise near the 0°/360° boundary can cause dramatic control instability without continuous input.

Consider a module trying to reach 0° (or 360°):

**Scenario Setup**:
- Target angle: 0° (set by `optimize`)
- Module is very close to target, rotating slowly to settle

**What happens without continuous input**:

1. **Cycle 1** (encoder reads 359.5°):
   - Current: 359.5°
   - Target: 0°
   - PID error = 0° - 359.5° = **-359.5°** ❌
   - PID controller sees this as a huge negative error and commands strong motor output to "correct" it
   - Module overshoots past 0°

2. **Cycle 2** (encoder reads 0.5°):
   - Current: 0.5°
   - Target: 0°
   - PID error = 0° - 0.5° = **-0.5°** ✅
   - PID controller correctly sees a small error and commands small correction
   - Module rotates back toward 0°

3. **Cycle 3** (encoder reads 359.8° due to slight overshoot or encoder noise):
   - Current: 359.8°
   - Target: 0°
   - PID error = 0° - 359.8° = **-359.8°** ❌
   - PID controller again sees a huge error and commands excessive output
   - This creates a **feedback loop of oscillation**

**The Problem**: The PID controller cannot distinguish between:
- Being 0.2° away from target (should command tiny correction)
- Being 359.8° away from target (should command tiny correction in opposite direction)

Without continuous input, it treats these as completely different situations, causing wild swings in motor output even when the module is very close to the target.

**With continuous input**: The PID controller recognizes that 359.8° and 0° are only 0.2° apart, calculates appropriate error = 0.2°, and commands smooth, stable correction.

**Real-World Impact**: This oscillation can cause:
- Excessive wear on swerve modules
- Poor tracking accuracy
- Difficulty tuning PID parameters (oscillations mask the true system response)
- Unpredictable robot behavior during autonomous or precision maneuvers

**Related Issues**:
- Encoder drift (clarification): Not using continuous input does **not** physically “drift” the encoder. However, it can create **apparent drift** in logs and behavior near wrap boundaries, because tiny real errors get interpreted as huge angular errors.
  - **True drift (sensor/zeroing problem)**: The most common root cause is the **relative (integrated) encoder** slowly losing its relationship to the **absolute encoder** over time (startup offset error, mechanical slip, missed counts, reboot, etc.). This is independent of continuous input.
  - **Apparent drift (control interpretation problem)**: Near 0°/360° (or ±180°), a small measurement/overshoot (e.g., 359.8° vs 0.2°) can look like a ~360° error to a non-continuous PID. The controller then commands a strong correction in the wrong “long way” direction, which makes the module move in a way that looks like the encoder “drifted,” even though the sensor did not jump.
  - **Why this matters to debugging**: Without continuous input, plots of "error" can suddenly spike by ~360° at the wrap boundary. That can be mistaken for encoder drift when it is actually an **angle wrapping** / **error calculation** artifact.

- **PID tuning difficulties**: Tuning P and D parameters becomes extremely difficult when the controller misinterprets error magnitudes near wrap-around boundaries. Here's why:

  **Problem 1: Non-linear error signal**
  - When testing at angles like 10° or 170°, the PID sees normal errors (e.g., ±5°)
  - When testing near 0°/360° or ±180°, the PID suddenly sees errors of ±350°+
  - The system appears to have **wildly different behavior** depending on which angle range you're testing
  - You can't tell if oscillations are from:
    - P gain being too high (real tuning issue)
    - Wrap-around error miscalculation (control system bug)
    - Both (most common)

  **Problem 2: Masked system response**
  - Example: You increase P gain and see oscillation
  - Is the oscillation because P is too high? Or because the module crossed a wrap boundary?
  - Without continuous input, you can't distinguish between these causes
  - This leads to **over-tuning** (reducing P too much) or **under-tuning** (accepting poor performance)

  **Problem 3: Inconsistent tuning results**
  - Tuning at 45° might suggest P = 0.5 works well
  - Testing at 350° might suggest P = 0.1 is needed (due to wrap-around errors)
  - You end up with **compromised tuning** that works poorly everywhere, or **angle-specific tuning** that's fragile

  **Problem 4: Derivative term becomes unreliable**
  - D term responds to the rate of change of error
  - Near wrap boundaries, error can jump from +2° to -358° (360° change in one cycle)
  - D term sees this as massive acceleration and commands huge corrections
  - This makes D tuning nearly impossible - you can't tell if D is helping or hurting

  **Real-world impact**: Teams often report that PID tuning "works sometimes" or "is very sensitive" - these symptoms are classic signs of wrap-around issues interfering with the tuning process.

- **Oscillation**: Modules may oscillate near ±180° boundaries because the PID controller doesn't recognize that -179° and 179° are close together. This oscillation is distinct from tuning-related oscillation - it's a **control system bug** that appears regardless of PID gain values.

**Decision Scope**: This ADR addresses two related decisions:
1. **Recommendation #15**: Use WPILib's `PIDController` with `enableContinuousInput()` instead of REV's built-in position controller for angle control
2. **Recommendation #16**: Use WPILib's `SwerveModuleState.optimize()` instead of our custom `optimize()` method

Both decisions work together to ensure proper angle handling at both the setpoint selection level (`optimize`) and the PID control level (continuous input).

<!-- This is an optional element. Feel free to remove. -->
## Decision Drivers

* Need for proper angle wrapping behavior in PID error calculation
* Desire to use standard WPILib methods instead of custom implementations
* Difficulty tuning PID parameters with encoder drift and oscillation issues
* Need for consistency with WPILib trajectory-following commands
* Reduction of technical debt and maintenance burden

## Considered Options

* {TBD - team to fill}
* {TBD - team to fill}
* {TBD - team to fill}
* … <!-- numbers of options can vary -->

## Decision Outcome

Chosen option: "{TBD - team to fill}", because {TBD - team to fill}.

<!-- This is an optional element. Feel free to remove. -->
### Consequences

* Good, because {TBD - team to fill}
* Bad, because {TBD - team to fill}
* … <!-- numbers of consequences can vary -->

<!-- This is an optional element. Feel free to remove. -->
### Confirmation

{TBD - team to fill}

<!-- This is an optional element. Feel free to remove. -->
## Pros and Cons of the Options

### {title of option 1}

<!-- This is an optional element. Feel free to remove. -->
{example | description | pointer to more information | …}

* Good, because {argument a}
* Good, because {argument b}
<!-- use "neutral" if the given argument weights neither for good nor bad -->
* Neutral, because {argument c}
* Bad, because {argument d}
* … <!-- numbers of pros and cons can vary -->

### {title of other option}

{example | description | pointer to more information | …}

* Good, because {argument a}
* Good, because {argument b}
* Neutral, because {argument c}
* Bad, because {argument d}
* …

<!-- This is an optional element. Feel free to remove. -->
## More Information

See:
- [Recommendation #15: Use enableContinuousInput for Swerve Module Angle Control](../code-improvements.md#15-use-enablecontinuousinput-for-swerve-module-angle-control-performance-improvement)
- [Recommendation #16: Use WPILib's SwerveModuleState.optimize() Instead of Custom Implementation](../code-improvements.md#16-use-wpilibs-swervemodulestateoptimize-instead-of-custom-implementation-code-standardization)

{You might want to provide additional evidence/confidence for the decision outcome here and/or document the team agreement on the decision and/or define when/how this decision the decision should be realized and if/when it should be re-visited. Links to other decisions and resources might appear here as well.}
