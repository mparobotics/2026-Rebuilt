# Dashboard Interaction Design: Elastic GUI Compatibility

> Extracted from [diagnostic-testing-framework-design.md](diagnostic-testing-framework-design.md) to keep that document focused on the core framework design.

## Problem Statement

The diagnostic test framework requires users to **select**, **execute**, and **cancel** tests through a dashboard GUI. The current implementation uses two SmartDashboard mechanisms:

1. **`SendableChooser`** (dropdown) — for test selection
2. **`SmartDashboard.putBoolean()`** (boolean value) — for the Start/Cancel trigger

This works in the **WPILib Sim GUI**, where booleans render as an interactive dropdown (`false`/`true`) — switching the value to `true` acts like a button click. However, in the **Elastic dashboard**, raw boolean NetworkTables entries render as **read-only status indicators** (red circle when false, green when true) with no mechanism for the user to change the value. The `SendableChooser` dropdown works correctly in both dashboards.

The result is that tests **cannot be started or cancelled** from the Elastic GUI, only from the Sim GUI.

## Root Cause

Elastic and Sim GUI handle NetworkTables value types differently:

| NT Value Type | Sim GUI Rendering | Elastic Rendering |
|---|---|---|
| Raw Boolean | Interactive dropdown (false/true) | Read-only status indicator (red/green) |
| `SendableChooser` | Interactive dropdown | Interactive dropdown ✓ |
| `Command` (Sendable) | Interactive button | Interactive button ✓ |

The key insight is that Elastic **does** support interactive widgets — but only for recognized `Sendable` types (like `SendableChooser` and `Command`), not for raw boolean values.

## How WPILib Command Buttons Work

When a `Command` is published via `SmartDashboard.putData("key", command)`, WPILib's `Command.initSendable()` publishes a NetworkTables subtable with:
- **`.type`** = `"Command"` (tells the dashboard which widget to render)
- **`.name`** property (read-only string — the command's name)
- **`running`** property (read-write boolean): getter returns `isScheduled()`, setter calls `schedule()` when set to `true` and `cancel()` when set to `false`

Dashboards that recognize the `"Command"` Sendable type (Shuffleboard, Elastic) render this as an **interactive toggle button**: click to schedule the command, click again to cancel it. This is the standard WPILib pattern for testing commands from dashboards.

## Current Architecture Issues

Beyond the Elastic compatibility problem, the current design has an additional concern:

**New instance creation on every execution**: The current `DiagnosticTestManager.startSelectedTest()` calls `selectedTest.createTest(robotContainer)` to create a fresh `Command` instance each time a test is run. While this works, it's unnecessary — the test commands already fully reset their state in `initialize()` and read fresh parameters from SmartDashboard each run. Persistent instances that are reused across multiple runs would be cleaner.

## Options Evaluated

### Option 1: Custom SendableButton Class

Create a class implementing `Sendable` that mimics the Command Sendable protocol (`SmartDashboardType = "Command"`, `running` boolean property) but instead of scheduling itself, calls back to the manager to start/cancel tests.

| Pros | Cons |
|------|------|
| Minimal change to existing architecture | Fragile: mimics undocumented internal protocol |
| Decouples button from Command lifecycle | Maintenance risk if WPILib/Elastic changes protocol |
| | Doesn't address instance-per-execution concern |
| | Reinvents what Command already provides |

**Verdict**: Unnecessarily complex. If we're going to use the Command Sendable protocol anyway, we should just use an actual Command.

### Option 2: Publish Selected Test Command Directly as a Button

When the user selects a test from the chooser, publish that test's `Command` instance via `SmartDashboard.putData("DiagnosticTests/StartTest", selectedTestCommand)`. Elastic renders it as a clickable button.

| Pros | Cons |
|------|------|
| Native WPILib pattern, well-supported | Must re-call `putData()` with different Sendable when selection changes |
| Elastic has built-in Command widget | Re-publishing may cause `SendableBuilder` rebinding issues |
| Tests are directly the button | Manager loses some control over lifecycle tracking |
| Simple conceptual model | |

**Verdict**: Elegant in theory, but re-publishing different Sendables to the same NetworkTables key is not a well-tested pattern in WPILib and could cause subtle bugs with stale property bindings.

### Option 3: Proxy Command Pattern ⭐ (Recommended)

Create a **single persistent** `TestRunnerCommand` published **once** via `SmartDashboard.putData()`. Elastic renders it as a button. When clicked:
- `initialize()` → reads the chooser, gets the selected persistent test instance, schedules it via `CommandScheduler`
- `execute()` → monitors the inner test, updates status display
- `isFinished()` → returns `true` when the inner test completes
- `end(interrupted)` → if interrupted (button clicked again), cancels the inner test

Combined with **persistent test instances** created once and reused across runs.

| Pros | Cons |
|------|------|
| Published once — no Sendable rebinding issues | Proxy + actual test = two commands scheduled simultaneously |
| Native Command button in Elastic | Slightly more indirection |
| Eliminates create-new-instance-each-time pattern | |
| Preserves select → configure parameters → run workflow | |
| Manager still handles status display | |
| Works in Sim GUI, Elastic, and Shuffleboard | |

**How it works in practice:**

1. User selects "Swerve Angle Drift Test" from the chooser dropdown (works in Elastic ✓)
2. Manager detects selection change, calls `initializeParameters()` on the persistent test instance
3. User adjusts parameters on the dashboard
4. User clicks the "Start Test" button (Command widget in Elastic ✓)
5. `TestRunnerCommand.initialize()` fires → reads chooser → schedules the persistent `SwerveAngleDriftTestCommand` → button label changes to **"Cancel Test"**
6. Both the proxy and the actual test are scheduled (no subsystem conflict — the proxy has no subsystem requirements)
7. Button shows "Cancel Test" label in running state in Elastic
8. Test completes naturally → proxy detects it → `isFinished()` returns `true` → button label changes back to **"Start Test"** → button resets
9. **OR** user clicks "Cancel Test" button → proxy is cancelled → `end(true)` cancels the inner test → button label changes back to **"Start Test"**

**Why two commands can coexist:** The `TestRunnerCommand` does not call `addRequirements()` for any subsystem. The actual test commands require their respective subsystems (e.g., `SwerveSubsystem`). Since they don't share subsystem requirements, the `CommandScheduler` runs them independently without conflict.

### Option 4: Replace Boolean with a SendableChooser for Action

Replace the boolean trigger with a `SendableChooser<String>` offering "Idle" / "Start" / "Cancel" options.

| Pros | Cons |
|------|------|
| Minimal code change | Very clunky UX (selecting "Start" from a dropdown) |
| SendableChooser dropdown works in Elastic | Semantically wrong — an action selector, not a value selector |
| | Need to detect and reset after selection |
| | Error-prone (what if user selects "Start" twice?) |

**Verdict**: A hack. Works technically but provides poor UX.

### Option 5: Publish ALL Tests as Individual Command Buttons

Publish every test as a separate Command button on the dashboard: `SmartDashboard.putData("Test: Swerve Drift", testA)`, `SmartDashboard.putData("Test: LED State", testB)`, etc.

| Pros | Cons |
|------|------|
| Simplest mental model | Loses the select → configure → run workflow |
| Each test is its own button | Dashboard clutter with many buttons |
| No chooser needed | Hard to show per-test parameters (which section belongs to which?) |
| | No centralized status tracking |
| | Adding tests = more dashboard clutter |

**Verdict**: Works for a small number of tests but doesn't scale and loses the framework's centralized management benefits.

## Recommended Approach: Option 3 (Proxy Command + Persistent Instances)

### Why This Option

1. **Solves the Elastic issue** using native WPILib Command button rendering
2. **Eliminates instance-per-execution** — tests are created once and reused
3. **Preserves the existing workflow** (select → configure parameters → run)
4. **Publishes one Command once** to SmartDashboard — no rebinding concerns
5. **Works across all dashboards** (Sim GUI, Elastic, Shuffleboard)

### Persistent Instances Are Already Supported

The existing test commands already support reuse without modification:
- **State reset in `initialize()`**: All tests fully reset their state machine, counters, and results arrays in `initialize()` (e.g., `currentCycle = 0`, `currentState = MOVING_TO_TARGET`, `testResults = new TestCycleResult[...]`)
- **Parameters read in `initialize()`**: All tests read fresh values from SmartDashboard each run via `TestDashboard.getParam*()` calls
- **Subsystem requirements set once**: `addRequirements()` is called in the constructor and persists across runs

### Required Changes

| Component | Change |
|-----------|--------|
| **New: `TestRunnerCommand`** | Simple proxy Command in `frc.lib.test`. Published once as a button. Delegates scheduling to the selected test. Has no subsystem requirements. |
| **`DiagnosticTestManager`** | Create all test instances once in constructor (stored in `Map<DiagnosticTestRegistry, Command>`). Replace `putBoolean(KEY_START_CANCEL_TEST)` with `putData()` for the proxy command. Remove boolean polling from `periodic()`. Keep all status monitoring and display logic. |
| **`DiagnosticTestRegistry`** | No changes needed — factory method still used, but instances are cached by the manager. |
| **Individual test commands** | No changes needed. |

### Updated SmartDashboard Layout

```
DiagnosticTests/
  ├── TestSelector/          (SendableChooser - dropdown, unchanged)
  ├── StartTest/               (Command button - replaces Boolean Start-Cancel)
  ├── CurrentTest/           (String - name of running test or "None")
  ├── CurrentTest Description/ (String - description of the selected test)
  ├── CurrentTest Status/    (String - Idle, Running, Complete, Cancelled, Error)
  └── Message/               (String - status messages and error information)
```

### TestRunnerCommand Sketch

```java
/**
 * Proxy command that bridges the Elastic dashboard button to test execution.
 *
 * Published once via SmartDashboard.putData(), this command appears as a
 * clickable button in Elastic. When clicked, it schedules the currently
 * selected test from the chooser. When clicked again (or the test completes),
 * the button resets.
 *
 * This command has no subsystem requirements, so it can run concurrently
 * with the actual test command without scheduling conflicts.
 */
public class TestRunnerCommand extends Command {
    private static final String LABEL_START = "Start Test";
    private static final String LABEL_CANCEL = "Cancel Test";

    private final Supplier<Command> selectedTestSupplier;
    private Command activeTest;

    @Override
    public void initialize() {
        activeTest = selectedTestSupplier.get();
        if (activeTest != null) {
            CommandScheduler.getInstance().schedule(activeTest);
            setName(LABEL_CANCEL); // Button label → "Cancel Test"
        }
    }

    @Override
    public boolean isFinished() {
        return activeTest == null
            || !CommandScheduler.getInstance().isScheduled(activeTest);
    }

    @Override
    public void end(boolean interrupted) {
        if (interrupted && activeTest != null) {
            activeTest.cancel();
        }
        activeTest = null;
        setName(LABEL_START); // Button label → "Start Test"
    }
}
```

### Dynamic Button Label

The `TestRunnerCommand` changes its button label between **"Start Test"** and **"Cancel Test"** by calling `setName()` in `initialize()` and `end()`. This works because WPILib's `Command.initSendable()` registers the `.name` property with a getter:

```java
builder.addStringProperty(".name", this::getName, null);
```

The `SendableBuilder` periodically polls `getName()` and publishes the current value to NetworkTables. When `setName("Cancel Test")` is called, the next poll picks up the change and pushes it to the dashboard. This provides clear feedback to the operator about what action the button will perform.

**Verification note:** While the mechanism is sound (the `.name` value in NetworkTables *will* update), whether Elastic specifically re-renders the button label text in real-time is an Elastic implementation detail that should be confirmed during end-to-end testing. If Elastic caches the label on widget creation, the `CurrentTest Status` string ("Running" / "Idle") still provides the operator with state information. The `running` boolean state (which controls button toggle appearance) is unaffected — that always updates correctly.

### Sim GUI Compatibility

This approach maintains full Sim GUI compatibility:
- The `SendableChooser` still appears as a dropdown in Sim GUI
- The `Command` button appears as an interactive widget in Sim GUI (Commands are rendered as toggleable entries)
- The dynamic button label (`setName()`) is reflected in SimGUI's command widget display
- All status strings continue to update normally

## Implementation Design: Option 3 Details

This section provides the detailed design needed to implement Option 3 (Proxy Command + Persistent Instances). It covers concrete class designs, specific changes to existing code, interaction flows, and edge case handling.

### TestRunnerCommand — Detailed Design

**Location:** `frc.lib.test.TestRunnerCommand` (new file)

The `TestRunnerCommand` is a thin proxy `Command` that bridges the Elastic dashboard button to test execution. It is published **once** via `SmartDashboard.putData()` during `DiagnosticTestManager` construction, and Elastic renders it as a native clickable toggle button.

**Key design properties:**
- **No subsystem requirements** — never calls `addRequirements()`, so it coexists with any test command without scheduling conflicts
- **Delegates to a supplier** — receives a `Supplier<Command>` from the manager that returns the currently selected persistent test instance
- **Stateless between runs** — holds a reference to the inner test only while active; clears it in `end()`
- **Dynamic button label** — calls `setName("Cancel Test")` in `initialize()` and `setName("Start Test")` in `end()`, so the dashboard button label reflects the current action. This works because WPILib's `Command.initSendable()` registers `.name` with a getter (`this::getName`) that the `SendableBuilder` polls periodically — name changes propagate to NetworkTables automatically.

**Full class design:**

```java
package frc.lib.test;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import java.util.function.Supplier;

/**
 * Proxy command that bridges the Elastic dashboard button to test execution.
 *
 * Published once via SmartDashboard.putData(), this command appears as a
 * clickable button in Elastic (and SimGUI). When clicked, it schedules
 * the currently selected test. When clicked again (or the test completes),
 * the button resets.
 *
 * This command has no subsystem requirements, so it runs concurrently
 * with the actual test command without scheduling conflicts.
 */
public class TestRunnerCommand extends Command {

    private final Supplier<Command> selectedTestSupplier;
    private Command runningTest;

    /**
     * Creates a new TestRunnerCommand.
     *
     * @param selectedTestSupplier Supplier that returns the persistent test
     *     instance to run. Returns null if no test is selected or available.
     *     Called once per button click (in initialize()).
     */
    private static final String LABEL_START = "Start Test";
    private static final String LABEL_CANCEL = "Cancel Test";

    public TestRunnerCommand(Supplier<Command> selectedTestSupplier) {
        this.selectedTestSupplier = selectedTestSupplier;
        setName(LABEL_START); // Initial button label shown in Elastic
    }

    @Override
    public void initialize() {
        runningTest = selectedTestSupplier.get();
        if (runningTest != null) {
            CommandScheduler.getInstance().schedule(runningTest);
            setName(LABEL_CANCEL); // Button label changes to "Cancel Test"
        }
    }

    @Override
    public void execute() {
        // Nothing — just waiting for the inner test to finish
    }

    @Override
    public boolean isFinished() {
        // Finished when: no test was selected, or inner test is done
        return runningTest == null
            || !CommandScheduler.getInstance().isScheduled(runningTest);
    }

    @Override
    public void end(boolean interrupted) {
        if (interrupted && runningTest != null
                && CommandScheduler.getInstance().isScheduled(runningTest)) {
            runningTest.cancel();
        }
        runningTest = null;
        setName(LABEL_START); // Button label reverts to "Start Test"
    }
}
```

**Lifecycle summary:**

| Event | What Happens |
|-------|-------------|
| User clicks "Start Test" button | Elastic sets `running=true` → CommandScheduler schedules the proxy → `initialize()` fires → supplier returns selected test → proxy schedules it → `setName("Cancel Test")` → button label updates |
| Test runs | Both proxy and inner test are scheduled concurrently. Proxy's `execute()` is a no-op. Proxy's `isFinished()` polls whether inner test is still scheduled. Button shows "Cancel Test". |
| Test completes naturally | Inner test's `isFinished()` returns true → scheduler ends it → proxy detects it's gone → proxy's `isFinished()` returns true → proxy ends → `setName("Start Test")` → button label and state reset |
| User clicks "Cancel Test" button | Elastic sets `running=false` → scheduler cancels the proxy → proxy's `end(true)` fires → proxy cancels inner test → inner test's `end(true)` fires → `setName("Start Test")` → button label resets |
| No test selected | Supplier returns null → `isFinished()` returns true immediately → proxy ends → `setName("Start Test")` → button resets |

### Persistent Test Instance Management

The `DiagnosticTestManager` creates all test instances **once** in its constructor and stores them in a map. When the user clicks "Start Test", the supplier returns the existing instance — no new instance is created.

**Instance cache:**
```java
private final Map<DiagnosticTestRegistry, Command> testInstances = new HashMap<>();

// In constructor:
for (DiagnosticTestRegistry entry : DiagnosticTestRegistry.values()) {
    try {
        Command test = entry.createTest(robotContainer);
        testInstances.put(entry, test);
    } catch (Exception e) {
        System.err.println("Failed to create test: " + entry.getDisplayName()
            + ": " + e.getMessage());
        // Test won't be available, but other tests still work
    }
}
```

**Why reuse works:** All existing test commands already support reuse without modification:
- **State reset in `initialize()`**: Tests fully reset state machines, counters, and result arrays (e.g., `currentCycle = 0`, `currentState = MOVING_TO_TARGET`, `testResults = new TestCycleResult[...]`)
- **Parameters read in `initialize()`**: Tests read fresh values from SmartDashboard via `TestDashboard.getParam*()` each run
- **Subsystem requirements set once**: `addRequirements()` is called in the constructor and persists across runs
- **No constructor-only state**: All mutable test state is reset in `initialize()`, not in the constructor

**Parameter initialization change:** When the user selects a test from the chooser, the manager calls `initializeParameters()` directly on the persistent instance — no throwaway instance needed:
```java
Command testCommand = testInstances.get(selectedTest);
if (testCommand instanceof DiagnosticTest) {
    DiagnosticTest diagnosticTest = (DiagnosticTest) testCommand;
    diagnosticTest.initializeParameters();
    SmartDashboard.putString(KEY_DESCRIPTION, diagnosticTest.getTestDescription());
}
```

**Instance lifetime:** Instances exist for the duration of a single test mode session (`testInit()` → `testExit()`). When `testExit()` is called, the manager is garbage collected along with all instances. Fresh instances are created on the next `testInit()`.

### DiagnosticTestManager — Required Changes

**Summary of changes:**

| Category | Action | Details |
|----------|--------|---------|
| **Add** | `testInstances` field | `Map<DiagnosticTestRegistry, Command>` — persistent instance cache |
| **Add** | `runTestCommand` field | `TestRunnerCommand` — proxy published as dashboard button |
| **Add** | `getSelectedTestInstance()` method | Supplier method for the proxy — returns selected persistent instance, sets `activeTest` |
| **Rename** | `KEY_START_CANCEL_TEST` | → `KEY_START_TEST` (`"DiagnosticTests/StartTest"`) |
| **Remove** | `handleStartCancelButton()` | Button handling moved to proxy |
| **Remove** | `startSelectedTest()` | Test scheduling moved to proxy |
| **Remove** | `cancelActiveTest()` | Test cancellation moved to proxy |
| **Modify** | Constructor | Create instance cache and proxy command |
| **Modify** | `initializeDashboard()` | Replace `putBoolean` with `putData` for proxy command |
| **Modify** | `periodic()` | Remove boolean polling block |
| **Modify** | `updateTestSelection()` | Use persistent instances for parameter init (no throwaway instance) |
| **Modify** | `cleanup()` | Cancel proxy, remove boolean cleanup |

**No changes needed** to `updateTestStatus()` — it already monitors `activeTest` and detects scheduled/unscheduled/finished transitions. The existing logic correctly identifies COMPLETE (not scheduled + isFinished) vs CANCELLED (not scheduled + not finished).

**`getSelectedTestInstance()` — new supplier method:**

This method is called by the proxy's `initialize()` when the user clicks the button. It replaces the functionality of the current `startSelectedTest()`:

```java
/**
 * Supplier method for TestRunnerCommand. Returns the currently selected
 * persistent test instance, or null if no valid test is available.
 *
 * Also updates manager state (activeTest, currentStatus, display) to
 * begin tracking the test — equivalent to the old startSelectedTest().
 */
private Command getSelectedTestInstance() {
    DiagnosticTestRegistry selected = testChooser.getSelected();
    if (selected == null) {
        SmartDashboard.putString(KEY_MESSAGE,
            "No test selected. Select a test from the dropdown.");
        return null;
    }

    Command test = testInstances.get(selected);
    if (test == null) {
        SmartDashboard.putString(KEY_MESSAGE,
            "Test not available: " + selected.getDisplayName());
        return null;
    }

    // Update manager tracking state (replaces startSelectedTest() logic)
    activeTest = test;
    suppressedSelectionWarning = null;
    currentStatus = TestStatus.RUNNING;
    SmartDashboard.putString(KEY_CURRENT_TEST, selected.getDisplayName());
    SmartDashboard.putString(KEY_TEST_STATUS, currentStatus.toString());
    SmartDashboard.putString(KEY_MESSAGE,
        "Test running: " + selected.getDisplayName());
    System.out.println("Started test: " + selected.getDisplayName());

    return test;
}
```

**Why `activeTest` is set inside the supplier:**
The supplier runs inside `TestRunnerCommand.initialize()`, which is called by the CommandScheduler during `robotPeriodic()`. By the time `testPeriodic()` calls `manager.periodic()`, `activeTest` is already set and the inner test is scheduled. The existing `updateTestStatus()` logic then monitors the test normally — no race condition or one-cycle gap.

### Interaction Flows

**Flow 1: Start a test**
```
1. User selects "Swerve Angle Drift Test" from chooser dropdown
2. manager.periodic() → updateTestSelection() detects selection change
3. Manager calls initializeParameters() on persistent instance
4. Parameters appear in SmartDashboard; user adjusts if desired
5. User clicks "Start Test" button in Elastic
6. Elastic sets running=true → CommandScheduler schedules proxy
7. proxy.initialize() → calls getSelectedTestInstance()
8. Supplier returns persistent test, sets activeTest + status=RUNNING
9. Proxy schedules the inner test via CommandScheduler
10. Proxy calls setName("Cancel Test") → button label updates in Elastic
11. Both proxy and test running concurrently
12. manager.periodic() → updateTestStatus() confirms RUNNING status
```

**Flow 2: Test completes naturally**
```
1. Test's isFinished() returns true → CommandScheduler ends it → test.end(false)
2. Proxy's isFinished() detects inner test gone → returns true
3. CommandScheduler ends the proxy → proxy.end(false)
   → clears runningTest → setName("Start Test")
4. Button label reverts to "Start Test" and button resets in Elastic
5. manager.periodic() → updateTestStatus()
   → activeTest not scheduled + isFinished=true → status=COMPLETE
   → Message: "Test completed: ..."
```

**Flow 3: User cancels a running test**
```
1. User clicks "Cancel Test" button while test is running
2. Elastic sets running=false → CommandScheduler cancels the proxy
3. proxy.end(true) → cancels inner test → test.end(true) → setName("Start Test")
4. Button label reverts to "Start Test" and button resets in Elastic
5. manager.periodic() → updateTestStatus()
   → activeTest not scheduled + not finished → status=CANCELLED
   → Message: "Test cancelled unexpectedly: ..."
```

Note: In Flow 3, the manager's `updateTestStatus()` sees the test was cancelled "unexpectedly" (not via our old button). This is accurate — from the manager's perspective, the cancellation came from outside (the proxy). The status message could say "Test cancelled: ..." instead. This is a minor wording difference that can be adjusted if desired.

**Flow 4: Selection change during a running test**
```
1. User changes chooser while a test is running
2. manager.periodic() → updateTestSelection()
   → Detects selection change + isTestRunning()=true
   → Prints warning, keeps "Current Test" showing running test name
   → Does NOT initialize parameters for new selection
   → lastSelectedTest NOT updated (preserves change detection)
3. Test completes (or is cancelled) → status updates normally
4. Next manager.periodic() → updateTestSelection()
   → Detects selectedTest != lastSelectedTest (still different)
   → isTestRunning()=false now
   → Initializes parameters for the newly selected test
```

### Edge Cases and Error Handling

| Edge Case | Behavior |
|-----------|----------|
| **Button clicked, no test selected** | Supplier returns null → proxy `isFinished()` returns true immediately → button resets → message: "No test selected" |
| **Button clicked, test instance failed to create** | Instance not in map → supplier returns null → same as above → message: "Test not available" |
| **Rapid double-click** | First click schedules proxy → second click cancels proxy → proxy cancels inner test. Test may have run for only one 20ms cycle. Acceptable behavior — same as cancelling. |
| **`testExit()` while test is running** | `cleanup()` cancels proxy and `activeTest` → both end cleanly |
| **Test throws exception during execution** | CommandScheduler catches it and ends the test. `updateTestStatus()` detects the test is no longer scheduled and sets appropriate status. |
| **Same test re-run** | Supplier returns same persistent instance → CommandScheduler schedules it again (allowed after previous run ended) → test's `initialize()` resets all state → runs fresh |
| **All test instances fail to create** | `testInstances` map is empty → any button click → supplier returns null → button resets with error message. Chooser still shows test names. |

## Implementation Steps

Ordered steps to implement Option 3. Each step results in a compilable, testable state.

**Step 1: Create `TestRunnerCommand`**
- New file: `src/main/java/frc/lib/test/TestRunnerCommand.java`
- Implements the proxy command as designed above
- No changes to existing files in this step

**Step 2: Modify `DiagnosticTestManager`**
- Add `testInstances` map field and populate in constructor
- Add `runTestCommand` field, create in constructor with `this::getSelectedTestInstance`
- Add `getSelectedTestInstance()` method (new supplier)
- `initializeDashboard()`: Replace `putBoolean(KEY_START_CANCEL_TEST, false)` with `putData(KEY_START_TEST, runTestCommand)`
- `periodic()`: Remove the boolean polling block (lines that read `KEY_START_CANCEL_TEST` and call `handleStartCancelButton()`)
- `updateTestSelection()`: Replace throwaway-instance try/catch block with direct call to `testInstances.get(selectedTest)` and null check
- `cleanup()`: Cancel `runTestCommand`, remove `putBoolean` cleanup
- Rename constant: `KEY_START_CANCEL_TEST` → `KEY_START_TEST = DASHBOARD_PREFIX + "StartTest"`
- Delete methods: `handleStartCancelButton()`, `startSelectedTest()`, `cancelActiveTest()`

**Step 3: Verify no external references to the boolean key**
- The `KEY_START_CANCEL_TEST` boolean is only referenced inside `DiagnosticTestManager` — no changes needed to test commands, `DiagnosticTestRegistry`, `Robot.java`, or `TestDashboard`

**Step 4: Test end-to-end**
- Verify in SimGUI: chooser dropdown works, "Start Test" Command button appears, test starts/stops correctly
- Verify button label changes to "Cancel Test" while a test is running, and reverts to "Start Test" after completion or cancellation
- Verify selection change triggers parameter initialization
- Verify cancel (click button during running test)
- Verify test completion updates status to "Complete"
- Verify re-running the same test works (persistent instance reuse)
- If Elastic is available: verify the button label updates dynamically in the Elastic Command widget