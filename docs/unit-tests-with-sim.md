# Unit Testing with WPILib HAL Simulation

## Overview

WPILib's HAL (Hardware Abstraction Layer) provides a simulation backend that replaces physical hardware with in-memory state.  When robot code runs on a desktop JVM instead of a roboRIO, every call to a HAL function (reading an encoder, commanding a motor, checking the DriverStation) goes through this simulation layer.

This same mechanism works inside JUnit tests.  By calling `HAL.initialize(500, 0)` before the test code runs, the simulation backend is activated, and all WPILib hardware objects operate against simulated state.  No GUI, no network socket, and no physical hardware are involved — the tests run headless and deterministically.

### What this enables

- **Subsystem logic tests** — verify that a subsystem responds correctly to sensor inputs you control.
- **Command lifecycle tests** — exercise `initialize()` / `execute()` / `isFinished()` / `end()` without a running `TimedRobot` loop.
- **DriverStation state tests** — programmatically set enabled/disabled/autonomous/teleop mode and verify that robot code reacts accordingly.
- **CI/CD integration** — all of the above runs in a headless environment on any OS (Linux, macOS, Windows) via `./gradlew build`.

### What this does NOT cover

- Full closed-loop simulation with physics models (e.g. `DCMotorSim`, `SwerveDriveSimulation`).  Those run in the `simulationPeriodic()` loop of a running robot program, not in JUnit tests.
- Vendor-specific hardware simulation (CTRE Phoenix, REV).  Vendor sim classes exist but are separate from the WPILib HAL sim discussed here.

---

## Prerequisites — `build.gradle` Configuration

The project's `build.gradle` already contains everything needed to run HAL-sim-based tests.  The relevant lines are:

```gradle
// Desktop support must be enabled
def includeDesktopSupport = true

dependencies {
    // ...desktop native libraries for simulation...
    nativeDebug wpi.java.deps.wpilibJniDebug(wpi.platforms.desktop)
    nativeDebug wpi.java.vendor.jniDebug(wpi.platforms.desktop)

    nativeRelease wpi.java.deps.wpilibJniRelease(wpi.platforms.desktop)
    nativeRelease wpi.java.vendor.jniRelease(wpi.platforms.desktop)

    // JUnit 5
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
    systemProperty 'junit.jupiter.extensions.autodetection.enabled', 'true'
}

// THIS IS THE CRITICAL LINE — it adds the desktop native libraries
// (including the HAL simulation JNI) to the test classpath so that
// HAL.initialize() works inside JUnit tests.
wpi.java.configureTestTasks(test)
```

**Key point:** `wpi.java.configureTestTasks(test)` copies the platform-specific native libraries (`.so` / `.dylib` / `.dll`) onto the JUnit test classpath.  Without this line, `HAL.initialize()` would throw an `UnsatisfiedLinkError` because the JNI bindings wouldn't be available.

No additional Gradle configuration is required.

---

## Writing Tests — Patterns and Examples

### 1. Minimal HAL initialization

The simplest approach — used when you need WPILib math or path-planning classes that internally touch the HAL, but you aren't simulating specific hardware.

```java
package frc.robot.example;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.hal.HAL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MinimalHALTest {

    @BeforeAll
    static void initHAL() {
        // Initializes the HAL simulation backend.
        // 500 = timeout in ms (standard value), 0 = default mode.
        HAL.initialize(500, 0);
    }

    @Test
    void testSomethingThatTouchesHAL() {
        // WPILib classes that internally call HAL functions will work here.
        // Example: PathPlannerPath, Rotation2d, kinematics classes, etc.
        assertTrue(true);
    }
}
```

This is the pattern used by the existing `TrajectoryGenerationTest` in this project:

```java
@BeforeAll
static void initHAL() {
    // HAL must be initialized for PathPlannerPath internals to work.
    HAL.initialize(500, 0);
}
```

### 2. Testing with hardware simulation objects

When you want to test code that reads sensors or drives motors, you use the `*Sim` classes from `edu.wpi.first.wpilibj.simulation`.  These give you setter methods to inject simulated sensor values and getter methods to observe what the robot code commanded.

```java
package frc.robot.example;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.hal.HAL;
import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.simulation.EncoderSim;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EncoderSimTest {

    private Encoder encoder;
    private EncoderSim encoderSim;

    @BeforeAll
    static void initHAL() {
        HAL.initialize(500, 0);
    }

    @BeforeEach
    void setup() {
        encoder = new Encoder(0, 1);          // Create real Encoder object
        encoderSim = new EncoderSim(encoder);  // Wrap it with the sim companion
    }

    @AfterEach
    void teardown() {
        encoder.close();  // Release HAL port allocations
    }

    @Test
    void testEncoderReadsInjectedDistance() {
        encoderSim.setDistance(2.5);  // Inject a simulated distance reading

        assertEquals(2.5, encoder.getDistance(), 0.001,
            "Encoder.getDistance() should return the value injected via EncoderSim");
    }

    @Test
    void testEncoderReadsInjectedRate() {
        encoderSim.setRate(1.2);

        assertEquals(1.2, encoder.getRate(), 0.001,
            "Encoder.getRate() should return the value injected via EncoderSim");
    }
}
```

### 3. Testing with DriverStation state

`DriverStationSim` lets you programmatically set the robot's mode and enabled state.  This is useful for testing code paths that depend on `DriverStation.isEnabled()`, `DriverStation.isAutonomous()`, etc.

```java
package frc.robot.example;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.hal.HAL;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.simulation.DriverStationSim;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DriverStationSimTest {

    @BeforeAll
    static void initHAL() {
        HAL.initialize(500, 0);
    }

    @Test
    void testCanSetRobotToAutonomousEnabled() {
        DriverStationSim.setAutonomous(true);
        DriverStationSim.setEnabled(true);
        DriverStationSim.notifyNewData();  // Flush changes to the DriverStation

        assertTrue(DriverStation.isAutonomousEnabled(),
            "DriverStation should report autonomous-enabled after DriverStationSim sets it");
    }

    @Test
    void testCanSetRobotToDisabled() {
        DriverStationSim.setEnabled(false);
        DriverStationSim.notifyNewData();

        assertFalse(DriverStation.isEnabled(),
            "DriverStation should report disabled");
    }
}
```

**Important:** Call `DriverStationSim.notifyNewData()` after changing DriverStation state.  This flushes the simulated values so that `DriverStation` queries see the updated state.

### 4. Testing WPILib Commands with the CommandScheduler

When testing `Command`-based logic, you need to manage the `CommandScheduler` lifecycle.  The scheduler is a singleton with global state that persists across tests unless explicitly reset.

```java
package frc.robot.example;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.hal.HAL;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CommandSchedulerTest {

    @BeforeAll
    static void initHAL() {
        HAL.initialize(500, 0);
    }

    @BeforeEach
    void setup() {
        // Reset the CommandScheduler to a clean state before each test.
        // This removes all registered subsystems, default commands,
        // and scheduled commands from previous tests.
        CommandScheduler.getInstance().cancelAll();
        CommandScheduler.getInstance().unregisterAllSubsystems();
    }

    @AfterEach
    void teardown() {
        CommandScheduler.getInstance().cancelAll();
        CommandScheduler.getInstance().unregisterAllSubsystems();
    }

    @Test
    void testInstantCommandRunsOnce() {
        int[] counter = {0};
        Command cmd = new InstantCommand(() -> counter[0]++);

        cmd.schedule();
        CommandScheduler.getInstance().run();  // Execute one scheduler cycle

        assertEquals(1, counter[0], "InstantCommand should have executed once");
        assertTrue(cmd.isFinished(), "InstantCommand should be finished after one cycle");
    }
}
```

**Lifecycle pattern summary:**

| Annotation    | Action                                        |
|---------------|-----------------------------------------------|
| `@BeforeAll`  | `HAL.initialize(500, 0)` — once per test class |
| `@BeforeEach` | Reset `CommandScheduler`, create hardware objects |
| `@AfterEach`  | Cancel commands, unregister subsystems, `close()` hardware objects |

### 5. Available simulation classes

The `edu.wpi.first.wpilibj.simulation` package includes simulation companions for most WPILib hardware classes:

| Sim Class             | Corresponding Hardware        | Key Methods                              |
|-----------------------|-------------------------------|------------------------------------------|
| `EncoderSim`          | `Encoder`                     | `setDistance()`, `setRate()`, `setCount()` |
| `AnalogInputSim`      | `AnalogInput`                 | `setVoltage()`                           |
| `DigitalInputSim`     | `DigitalInput`                | `setValue()`                             |
| `DIOSim`              | `DigitalOutput`               | `setValue()`                             |
| `PWMSim`              | `PWMMotorController` subclasses | `getSpeed()`, `getPosition()`          |
| `DriverStationSim`    | `DriverStation`               | `setEnabled()`, `setAutonomous()`, `notifyNewData()` |
| `RoboRioSim`          | `RobotController`             | `setVInVoltage()` (battery voltage)      |
| `AnalogGyroSim`       | `AnalogGyro`                  | `setAngle()`, `setRate()`                |
| `BatterySim`          | (utility)                     | `calculateDefaultBatteryLoadedVoltage()` |

**Note on vendor hardware:** CTRE and REV provide their own simulation support.  For CTRE Phoenix 6, see `TalonFXSimState`.  For REV, see `SparkMaxSim` / `SparkFlexSim`.  These are separate from the WPILib `*Sim` classes.

---

## Running Tests

### Locally

```bash
# Run all tests (this is what 'build' does — compile + test)
./gradlew build

# Run only tests (skip compilation if already built)
./gradlew test

# Run a single test class
./gradlew test --tests "frc.robot.auto.TrajectoryGenerationTest"

# Run with verbose output (shows individual test pass/fail)
./gradlew test --info

# Re-run tests even if nothing changed
./gradlew test --rerun
```

Test reports are generated at `build/reports/tests/test/index.html`.

### Platform compatibility

Tests using HAL simulation run on all three desktop platforms:

| Platform       | Native Library | Status |
|----------------|---------------|--------|
| Windows x86_64 | `.dll`        | Supported |
| macOS x86_64   | `.dylib`      | Supported |
| macOS arm64    | `.dylib`      | Supported |
| Linux x86_64   | `.so`         | Supported |

The `configureTestTasks` Gradle helper detects the current platform and loads the correct native library automatically.

---

## GitHub Actions CI/CD

### Current project configuration

The project's existing CI workflow (`.github/workflows/ci.yml`) already runs `./gradlew build`, which includes the `test` task:

```yaml
- name: Build with Gradle
  run: ./gradlew build
```

This means **HAL-sim-based JUnit tests already run in CI** with no additional configuration needed.  The workflow runs on `ubuntu-latest`, which provides a Linux x86_64 environment where the HAL native libraries load normally.

### Why it works headless

- The `halsim_gui` extension (Sim GUI) is **not loaded** during JUnit tests.  GUI extensions are only loaded by `simulateJava` / `simulateNative` tasks.
- The `halsim_ds_socket` extension (DriverStation socket) is also **not loaded**.  These extensions are configured in the `wpi.sim.*` block of `build.gradle`, which only applies to simulation tasks, not the `test` task.
- JUnit tests use only the base HAL simulation backend — a lightweight, in-process, headless simulation of the hardware abstraction layer.  No display server, no network listeners, no GUI toolkit.

### Workflow recommendations

If you want to add a dedicated test-results step to the CI workflow, you can extend it:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Build with Gradle
        run: ./gradlew build

      # Optional: publish JUnit XML results as a check annotation
      - name: Publish test results
        uses: EnricoMi/publish-unit-test-result-action@v2
        if: always()
        with:
          files: build/test-results/test/**/*.xml

      - name: Upload build artifacts
        uses: actions/upload-artifact@v4
        if: success()
        with:
          name: build-artifacts
          path: |
            build/libs/*.jar
            build/reports/**
          retention-days: 7
```

The `publish-unit-test-result-action` step parses the JUnit XML output and adds pass/fail annotations directly to the pull request.

---

## Troubleshooting

### `UnsatisfiedLinkError` when running tests

**Cause:** The HAL native libraries are not on the test classpath.

**Fix:** Verify that `build.gradle` contains:
```gradle
wpi.java.configureTestTasks(test)
```
This line must appear **after** the `test { ... }` block.

### `HAL not initialized` errors

**Cause:** `HAL.initialize(500, 0)` was not called before the test created WPILib hardware objects.

**Fix:** Add a `@BeforeAll` method:
```java
@BeforeAll
static void initHAL() {
    HAL.initialize(500, 0);
}
```

### Port allocation conflicts between tests

**Cause:** Two tests allocate the same HAL port (e.g., DIO channel 0) without releasing it.

**Fix:** Call `.close()` on hardware objects in `@AfterEach`:
```java
@AfterEach
void teardown() {
    encoder.close();
    motor.close();
}
```

### `CommandScheduler` state leaking between tests

**Cause:** The `CommandScheduler` is a singleton.  Registered subsystems, default commands, and scheduled commands persist across tests within the same JVM.

**Fix:** Reset the scheduler in `@BeforeEach` and/or `@AfterEach`:
```java
@BeforeEach
void setup() {
    CommandScheduler.getInstance().cancelAll();
    CommandScheduler.getInstance().unregisterAllSubsystems();
}
```

### Tests pass locally but fail in CI

Common causes:
- **Platform mismatch:** The CI environment is Linux x86_64.  If your local machine is macOS arm64, the native libraries are different.  Both should work, but vendor libraries (CTRE, REV) may have platform-specific gaps.
- **Non-deterministic timing:** Avoid `Thread.sleep()` or real-time waits in tests.  Use `CommandScheduler.getInstance().run()` to advance the scheduler deterministically.
- **File path assumptions:** CI clones to a different directory.  Use classpath resources instead of absolute paths.

---

## Summary

| Topic                | Key Detail                                                        |
|----------------------|-------------------------------------------------------------------|
| Initialization       | `HAL.initialize(500, 0)` in `@BeforeAll`                          |
| Gradle setup         | `wpi.java.configureTestTasks(test)` — already present in project  |
| Running tests        | `./gradlew build` or `./gradlew test`                             |
| CI/CD                | Works out of the box — `./gradlew build` in GitHub Actions         |
| GUI required?        | No — tests are headless                                           |
| Platform support     | Windows, macOS (Intel + Apple Silicon), Linux                     |
| Cleanup pattern      | `.close()` hardware in `@AfterEach`, reset `CommandScheduler`     |

---

## References

- [WPILib Simulation Documentation](https://docs.wpilib.org/en/stable/docs/software/wpilib-tools/robot-simulation/index.html)
- [WPILib Unit Testing Documentation](https://docs.wpilib.org/en/stable/docs/software/wpilib-tools/robot-simulation/unit-testing.html)
- [`edu.wpi.first.wpilibj.simulation` API Javadoc](https://github.wpilib.org/allwpilib/docs/release/java/edu/wpi/first/wpilibj/simulation/package-summary.html)
- [`DriverStationSim` API Javadoc](https://github.wpilib.org/allwpilib/docs/release/java/edu/wpi/first/wpilibj/simulation/DriverStationSim.html)
