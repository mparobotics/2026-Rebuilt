# RobotState + NetworkTables Hybrid Architecture

## Overview

This document describes a hybrid approach that combines:
- **RobotState class** (like KnightKrawlers) - centralized state management with clean API
- **NetworkTables integration** - automatic external visibility and decoupling
- **Singleton pattern** - single instance, accessible from anywhere
- **Type-safe getters/setters** - compile-time safety, no string key typos

## Why This Approach is Excellent

### Benefits

1. **Type Safety** ✅
   - Getter/setter methods provide compile-time type checking
   - No string key typos (unlike raw NetworkTables)
   - IDE autocomplete works perfectly

2. **Centralized Key Management** ✅
   - All NetworkTables keys defined in one place
   - Easy to refactor (change key once, affects all code)
   - Self-documenting (keys are constants)

3. **Clean API** ✅
   - Simple getter/setter interface
   - Hides NetworkTables complexity
   - Subsystems don't need to know about NetworkTables

4. **Automatic External Visibility** ✅
   - Data automatically available to Shuffleboard/AdvantageScope
   - No additional code needed for external access
   - Perfect for Driver Station display

5. **Error Handling** ✅
   - Centralized exception handling
   - Default values for missing data
   - Type conversion handled automatically

6. **Best of Both Worlds** ✅
   - RobotState benefits: centralized, type-safe, clean API
   - NetworkTables benefits: external visibility, decoupled, standard

7. **Easier Testing** ✅
   - Can mock RobotState more easily than NetworkTables
   - Can provide test implementations
   - Centralized state makes testing simpler

### Comparison to Alternatives

| Feature | Raw NetworkTables | RobotState + NetworkTables |
|---------|------------------|---------------------------|
| **Type Safety** | ❌ Runtime (string keys) | ✅ Compile-time (methods) |
| **Key Management** | ❌ Scattered in code | ✅ Centralized constants |
| **Error Handling** | ⚠️ Manual | ✅ Centralized |
| **API Clarity** | ⚠️ Verbose | ✅ Clean getters/setters |
| **External Visibility** | ✅ Yes | ✅ Yes (automatic) |
| **Refactoring** | ❌ Find/replace strings | ✅ Change constant once |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Subsystems                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   Shooter    │  │    Intake    │  │    Swerve    │     │
│  │  Subsystem   │  │  Subsystem   │  │  Subsystem   │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                  │                  │              │
│         └──────────────────┼──────────────────┘              │
│                            │                                 │
│                    Call Setters on                          │
│                    RobotState                               │
│                            │                                 │
└────────────────────────────┼─────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   RobotState    │
                    │   (Singleton)   │
                    │                 │
                    │  - Getters      │
                    │  - Setters      │
                    │  - Key Constants│
                    │  - Error Handle │
                    └────────┬────────┘
                             │
                    Publishes to / Reads from
                             │
                             ▼
                    ┌─────────────────┐
                    │  NetworkTables  │
                    │  (WPILib)       │
                    └────────┬────────┘
                             │
                ┌────────────┼────────────┐
                │            │            │
                ▼            ▼            ▼
        ┌───────────┐  ┌──────────┐  ┌──────────────┐
        │   LED     │  │Shuffle-  │  │   Driver     │
        │Subsystem  │  │  board   │  │  Station     │
        │ (Reads    │  │ (Reads   │  │  (Reads      │
        │  from     │  │  from    │  │   from       │
        │RobotState)│  │Network-  │  │ Network-     │
        │           │  │ Tables)  │  │  Tables)     │
        └───────────┘  └──────────┘  └──────────────┘
```

## Implementation

### Step 1: Create RobotState Class

```java
package frc.robot;

import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.IntegerPublisher;
import edu.wpi.first.networktables.IntegerSubscriber;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.networktables.StringSubscriber;

// Note: We use both Publishers (for writing) and Subscribers (for reading)
// This ensures we always get the latest value from NetworkTables, even if
// external code writes directly to NetworkTables, bypassing RobotState setters.

/**
 * Centralized robot state management with NetworkTables integration.
 * 
 * Provides type-safe getters and setters that automatically publish/read
 * from NetworkTables. This gives us:
 * - Type safety (compile-time checking)
 * - Centralized key management
 * - Automatic external visibility (Shuffleboard, Driver Station)
 * - Clean API for subsystems
 */
public class RobotState {
    private static RobotState INSTANCE;
    
    // NetworkTables instance
    private final NetworkTableInstance ntInstance = NetworkTableInstance.getDefault();
    
    // ============================================
    // NetworkTables Key Constants
    // ============================================
    // Centralized key management - change keys here, affects all code
    private static final class Keys {
        // Shooter state
        private static final String SHOOTER_ACTIVE = "RobotState/Shooter/Active";
        private static final String SHOOTER_AT_SPEED = "RobotState/Shooter/AtSpeed";
        private static final String SHOOTER_READY = "RobotState/Shooter/Ready";  // Computed
        
        // Intake state
        private static final String INTAKE_HAS_GAME_PIECE = "RobotState/Intake/HasGamePiece";
        private static final String INTAKE_ACTIVE = "RobotState/Intake/Active";
        
        // Swerve state
        private static final String SWERVE_ALIGNED_WITH_TARGET = "RobotState/Swerve/AlignedWithTarget";
        private static final String SWERVE_AT_TARGET_DISTANCE = "RobotState/Swerve/AtTargetDistance";
        
        // LED status
        private static final String LED_STATUS = "RobotState/LED/Status";
        private static final String LED_STATUS_CODE = "RobotState/LED/StatusCode";
        
        // Match state
        private static final String ALLIANCE_COLOR = "RobotState/Match/AllianceColor";
    }
    
    // ============================================
    // Publishers (for writing to NetworkTables)
    // ============================================
    private final BooleanPublisher shooterActivePublisher = 
        ntInstance.getBooleanTopic(Keys.SHOOTER_ACTIVE).publish();
    private final BooleanPublisher shooterAtSpeedPublisher = 
        ntInstance.getBooleanTopic(Keys.SHOOTER_AT_SPEED).publish();
    private final BooleanPublisher shooterReadyPublisher = 
        ntInstance.getBooleanTopic(Keys.SHOOTER_READY).publish();
    
    private final BooleanPublisher intakeHasGamePiecePublisher = 
        ntInstance.getBooleanTopic(Keys.INTAKE_HAS_GAME_PIECE).publish();
    private final BooleanPublisher intakeActivePublisher = 
        ntInstance.getBooleanTopic(Keys.INTAKE_ACTIVE).publish();
    
    private final BooleanPublisher swerveAlignedPublisher = 
        ntInstance.getBooleanTopic(Keys.SWERVE_ALIGNED_WITH_TARGET).publish();
    private final BooleanPublisher swerveAtDistancePublisher = 
        ntInstance.getBooleanTopic(Keys.SWERVE_AT_TARGET_DISTANCE).publish();
    
    private final StringPublisher ledStatusPublisher = 
        ntInstance.getStringTopic(Keys.LED_STATUS).publish();
    private final IntegerPublisher ledStatusCodePublisher = 
        ntInstance.getIntegerTopic(Keys.LED_STATUS_CODE).publish();
    
    private final StringPublisher allianceColorPublisher = 
        ntInstance.getStringTopic(Keys.ALLIANCE_COLOR).publish();
    
    // ============================================
    // Local State (for fast reads)
    // ============================================
    // Maintain local state for fast access. Subscribers will update this
    // when NetworkTables values change (including external writes).
    
    private boolean shooterActive = false;
    private boolean shooterAtSpeed = false;
    private boolean shooterReady = false;
    
    private boolean intakeHasGamePiece = false;
    private boolean intakeActive = false;
    
    private boolean swerveAlignedWithTarget = false;
    private boolean swerveAtTargetDistance = false;
    
    private String ledStatus = "UNKNOWN";
    private int ledStatusCode = 0;
    
    private String allianceColor = "UNKNOWN";
    
    // ============================================
    // Subscribers (for detecting NetworkTables changes)
    // ============================================
    // Subscribers detect when NetworkTables values change (including external writes)
    // and update local state. This gives us fast reads while staying in sync.
    
    private final BooleanSubscriber shooterActiveSubscriber = 
        ntInstance.getBooleanTopic(Keys.SHOOTER_ACTIVE).subscribe(false);
    private final BooleanSubscriber shooterAtSpeedSubscriber = 
        ntInstance.getBooleanTopic(Keys.SHOOTER_AT_SPEED).subscribe(false);
    private final BooleanSubscriber shooterReadySubscriber = 
        ntInstance.getBooleanTopic(Keys.SHOOTER_READY).subscribe(false);
    
    private final BooleanSubscriber intakeHasGamePieceSubscriber = 
        ntInstance.getBooleanTopic(Keys.INTAKE_HAS_GAME_PIECE).subscribe(false);
    private final BooleanSubscriber intakeActiveSubscriber = 
        ntInstance.getBooleanTopic(Keys.INTAKE_ACTIVE).subscribe(false);
    
    private final BooleanSubscriber swerveAlignedSubscriber = 
        ntInstance.getBooleanTopic(Keys.SWERVE_ALIGNED_WITH_TARGET).subscribe(false);
    private final BooleanSubscriber swerveAtDistanceSubscriber = 
        ntInstance.getBooleanTopic(Keys.SWERVE_AT_TARGET_DISTANCE).subscribe(false);
    
    private final StringSubscriber ledStatusSubscriber = 
        ntInstance.getStringTopic(Keys.LED_STATUS).subscribe("UNKNOWN");
    private final IntegerSubscriber ledStatusCodeSubscriber = 
        ntInstance.getIntegerTopic(Keys.LED_STATUS_CODE).subscribe(0);
    
    private final StringSubscriber allianceColorSubscriber = 
        ntInstance.getStringTopic(Keys.ALLIANCE_COLOR).subscribe("UNKNOWN");
    
    private RobotState() {
        // Private constructor - singleton pattern
        // Set default values (publishes to NetworkTables, subscribers will pick them up)
        setShooterActive(false);
        setShooterAtSpeed(false);
        setIntakeHasGamePiece(false);
        setIntakeActive(false);
        setSwerveAlignedWithTarget(false);
        setSwerveAtTargetDistance(false);
    }
    
    public static RobotState getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RobotState();
        }
        return INSTANCE;
    }
    
    // ============================================
    // Shooter State Getters/Setters
    // ============================================
    
    public void setShooterActive(boolean active) {
        // Update local state immediately (fast)
        this.shooterActive = active;
        // Publish to NetworkTables
        shooterActivePublisher.set(active);
        // Update computed state
        updateShooterReady();
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public boolean isShooterActive() {
        return shooterActive;
    }
    
    public void setShooterAtSpeed(boolean atSpeed) {
        // Update local state immediately (fast)
        this.shooterAtSpeed = atSpeed;
        // Publish to NetworkTables
        shooterAtSpeedPublisher.set(atSpeed);
        // Update computed state
        updateShooterReady();
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public boolean isShooterAtSpeed() {
        return shooterAtSpeed;
    }
    
    /**
     * Computed state: Shooter is ready to shoot (active + at speed)
     * Updates both local state and NetworkTables.
     */
    private void updateShooterReady() {
        // Compute from current local state
        boolean ready = shooterActive && shooterAtSpeed;
        // Update local state
        this.shooterReady = ready;
        // Publish to NetworkTables
        shooterReadyPublisher.set(ready);
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public boolean isShooterReady() {
        return shooterReady;
    }
    
    // ============================================
    // Intake State Getters/Setters
    // ============================================
    
    public void setIntakeHasGamePiece(boolean hasGamePiece) {
        // Update local state immediately (fast)
        this.intakeHasGamePiece = hasGamePiece;
        // Publish to NetworkTables
        intakeHasGamePiecePublisher.set(hasGamePiece);
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public boolean hasGamePiece() {
        return intakeHasGamePiece;
    }
    
    public void setIntakeActive(boolean active) {
        // Update local state immediately (fast)
        this.intakeActive = active;
        // Publish to NetworkTables
        intakeActivePublisher.set(active);
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public boolean isIntakeActive() {
        return intakeActive;
    }
    
    // ============================================
    // Swerve State Getters/Setters
    // ============================================
    
    public void setSwerveAlignedWithTarget(boolean aligned) {
        // Update local state immediately (fast)
        this.swerveAlignedWithTarget = aligned;
        // Publish to NetworkTables
        swerveAlignedPublisher.set(aligned);
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public boolean isSwerveAlignedWithTarget() {
        return swerveAlignedWithTarget;
    }
    
    public void setSwerveAtTargetDistance(boolean atDistance) {
        // Update local state immediately (fast)
        this.swerveAtTargetDistance = atDistance;
        // Publish to NetworkTables
        swerveAtDistancePublisher.set(atDistance);
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public boolean isSwerveAtTargetDistance() {
        return swerveAtTargetDistance;
    }
    
    // ============================================
    // LED Status Getters/Setters
    // ============================================
    
    public void setLEDStatus(String status) {
        // Update local state immediately (fast)
        this.ledStatus = status;
        // Publish to NetworkTables
        ledStatusPublisher.set(status);
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public String getLEDStatus() {
        return ledStatus;
    }
    
    public void setLEDStatusCode(int code) {
        // Update local state immediately (fast)
        this.ledStatusCode = code;
        // Publish to NetworkTables
        ledStatusCodePublisher.set(code);
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public int getLEDStatusCode() {
        return ledStatusCode;
    }
    
    // ============================================
    // Match State Getters/Setters
    // ============================================
    
    public void setAllianceColor(String color) {
        // Update local state immediately (fast)
        this.allianceColor = color;
        // Publish to NetworkTables
        allianceColorPublisher.set(color);
    }
    
    /**
     * Fast read from local state. Local state is kept in sync with NetworkTables
     * via subscribers that detect changes (including external writes).
     */
    public String getAllianceColor() {
        return allianceColor;
    }
    
    // ============================================
    // Periodic Update (Required)
    // ============================================
    // This method MUST be called periodically (e.g., from Robot.robotPeriodic())
    // to sync local state with NetworkTables changes (including external writes).
    
    public void periodic() {
        // Sync local state with NetworkTables subscribers
        // This detects changes from external writes (Driver Station, other code)
        // and updates local state accordingly
        
        // Check if NetworkTables values have changed
        if (shooterActiveSubscriber.get() != shooterActive) {
            shooterActive = shooterActiveSubscriber.get();
            // Note: Don't republish - this was an external change
        }
        
        if (shooterAtSpeedSubscriber.get() != shooterAtSpeed) {
            shooterAtSpeed = shooterAtSpeedSubscriber.get();
            updateShooterReady();  // Recompute derived state
        }
        
        if (intakeHasGamePieceSubscriber.get() != intakeHasGamePiece) {
            intakeHasGamePiece = intakeHasGamePieceSubscriber.get();
        }
        
        if (intakeActiveSubscriber.get() != intakeActive) {
            intakeActive = intakeActiveSubscriber.get();
        }
        
        if (swerveAlignedSubscriber.get() != swerveAlignedWithTarget) {
            swerveAlignedWithTarget = swerveAlignedSubscriber.get();
        }
        
        if (swerveAtDistanceSubscriber.get() != swerveAtTargetDistance) {
            swerveAtTargetDistance = swerveAtDistanceSubscriber.get();
        }
        
        String newLedStatus = ledStatusSubscriber.get();
        if (!newLedStatus.equals(ledStatus)) {
            ledStatus = newLedStatus;
        }
        
        int newLedStatusCode = (int) ledStatusCodeSubscriber.get();
        if (newLedStatusCode != ledStatusCode) {
            ledStatusCode = newLedStatusCode;
        }
        
        String newAllianceColor = allianceColorSubscriber.get();
        if (!newAllianceColor.equals(allianceColor)) {
            allianceColor = newAllianceColor;
        }
        
        // Update computed states
        updateShooterReady();
        
        // Update match state if needed
        // (DriverStation state can be read here if needed)
    }
}
```

### Step 2: Subsystems Use RobotState

**ShooterSubsystem Example:**

```java
public class ShooterSubsystem extends SubsystemBase {
    private final RobotState robotState = RobotState.getInstance();
    
    @Override
    public void periodic() {
        // ... existing shooter control code ...
        
        // Update RobotState (automatically publishes to NetworkTables)
        robotState.setShooterActive(isShooterActive);
        robotState.setShooterAtSpeed(isAtTargetSpeed());
    }
}
```

**CandleSubsystem Example:**

```java
public class CandleSubsystem extends SubsystemBase {
    private final RobotState robotState = RobotState.getInstance();
    private final CANdle candle = new CANdle(CANdleConstants.CANDLE_ID);
    
    @Override
    public void periodic() {
        // Read from RobotState (type-safe, clean API, fast local state access)
        boolean shooterReady = robotState.isShooterReady();
        boolean hasGamePiece = robotState.hasGamePiece();
        boolean aligned = robotState.isSwerveAlignedWithTarget();
        
        // Determine LED status
        LEDStatusMode status = determineStatus(shooterReady, hasGamePiece, aligned);
        setLEDStatus(status);
        
        // Publish LED status back to RobotState
        robotState.setLEDStatus(status.name());
        robotState.setLEDStatusCode(status.ordinal());
    }
    
    private LEDStatusMode determineStatus(boolean shooterReady, 
                                         boolean hasGamePiece, 
                                         boolean aligned) {
        // Priority-based status determination
        if (shooterReady && hasGamePiece && aligned) {
            return LEDStatusMode.READY_TO_SHOOT;
        }
        // ... rest of logic ...
    }
}
```

**Important: Call RobotState.periodic() in Robot.robotPeriodic()**

```java
// Robot.java
@Override
public void robotPeriodic() {
    CommandScheduler.getInstance().run();
    RobotState.getInstance().periodic();  // Sync local state with NetworkTables
}
```

## Important Design Decision: Hybrid Approach (Local State + Subscribers)

### The Best of Both Worlds

**Problem with Local State Only:**
- If external code writes directly to NetworkTables (bypassing RobotState setters)
- Or if Driver Station writes to NetworkTables
- Local state in RobotState would become **stale/incorrect**

**Problem with Subscribers Only:**
- NetworkTables reads have slight overhead (~1ms)
- Not as fast as local variable access

**Solution: Hybrid Approach (Local State + Subscribers)**
- **Maintain local state** for fast reads (getters return local variables)
- **Use subscribers** to detect NetworkTables changes
- **Sync local state** in `periodic()` when subscribers detect changes
- **Setters update both** local state and NetworkTables immediately

**How It Works:**
1. **Setters**: Update local state immediately (fast) + publish to NetworkTables
2. **Getters**: Return local state (fast, no NetworkTables read)
3. **Periodic**: Check subscribers for changes, sync local state if changed

**Benefits:**
- ✅ **Fast reads** (local variable access, no NetworkTables overhead)
- ✅ **Always up-to-date** (subscribers detect external writes, sync in periodic)
- ✅ **NetworkTables is source of truth** (subscribers detect all changes)
- ✅ **Best performance** (fast reads, minimal overhead)

**Trade-offs:**
- ⚠️ Must call `periodic()` regularly (e.g., from `Robot.robotPeriodic()`)
- ⚠️ Small delay for external writes (synced in next periodic call, usually <20ms)

**This is the recommended approach** - gives you fast reads while maintaining correctness!

## Benefits Summary

1. **Type Safety**: `robotState.isShooterReady()` vs `nt.getBooleanTopic("RobotState/Shooter/Ready").subscribe(false).get()`
2. **Centralized Keys**: Change key once in `Keys` class, affects all code
3. **Clean API**: Simple getters/setters, no NetworkTables complexity exposed
4. **Automatic External Visibility**: Data automatically in Shuffleboard
5. **Error Handling**: Centralized default values and error handling
6. **Computed State**: Can compute derived state (e.g., `isShooterReady()`)
7. **Easy Testing**: Can mock RobotState or provide test implementation
8. **Fast Reads + Always Up-to-Date**: Local state for fast reads, subscribers sync on changes (including external writes)
9. **Best Performance**: Fast local variable access with automatic sync to NetworkTables

## Usage Pattern

**Writing State (Subsystems):**
```java
RobotState.getInstance().setShooterActive(true);
```

**Reading State (Other Subsystems):**
```java
boolean ready = RobotState.getInstance().isShooterReady();
```

**External Access (Shuffleboard/Driver Station):**
- Automatically available via NetworkTables
- Can read directly from NetworkTables using the same keys
- Or use RobotState getters if accessing from robot code

## Migration Path

1. Create `RobotState` class with key constants
2. Add getters/setters for each state variable
3. Update subsystems to use `RobotState` instead of direct NetworkTables
4. Update `CandleSubsystem` to read from `RobotState`
5. Verify external visibility in Shuffleboard

This approach gives you the best of both worlds: RobotState's clean API with NetworkTables' external visibility!
