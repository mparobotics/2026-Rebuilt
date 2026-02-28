# LED Status Architecture with Driver Station Display

## Overview

This document describes the architecture for displaying robot status indicators on both:
1. **Physical LEDs** on the robot (visible when driver can see robot)
2. **Driver Station** display (visible when driver can't see robot, looking at camera feed)

## Architecture: NetworkTables-Based

We use **NetworkTables** as the single source of truth for robot status, which allows both the LED subsystem and Driver Station to display the same information.

### Why NetworkTables?

1. **Single Source of Truth**: Publish status once, consume in multiple places
2. **Automatic Driver Station Access**: NetworkTables data is automatically available to Driver Station/Shuffleboard
3. **No Duplicate Logic**: Status determination happens once, displayed in two places
4. **Already in Codebase**: Consistent with existing NetworkTables usage for swerve states
5. **Decoupled**: Subsystems don't need direct references to each other

## Architecture Diagram

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
│                    Publish State to                          │
│                    NetworkTables                             │
│                            │                                 │
└────────────────────────────┼─────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  NetworkTables  │
                    │  (Single Source │
                    │   of Truth)     │
                    └────────┬────────┘
                             │
                ┌────────────┼────────────┐
                │            │            │
                ▼            ▼            ▼
        ┌───────────┐  ┌──────────┐  ┌──────────────┐
        │   LED     │  │Shuffle-  │  │   Driver     │
        │Subsystem  │  │  board   │  │  Station     │
        │ (Physical │  │ (Display)│  │  (Display)   │
        │   LEDs)   │  │          │  │              │
        └───────────┘  └──────────┘  └──────────────┘
```

## Implementation

### Step 1: Subsystems Publish State to NetworkTables

Each subsystem publishes its relevant state to NetworkTables in its `periodic()` method.

**Example: ShooterSubsystem**

```java
public class ShooterSubsystem extends SubsystemBase {
    // NetworkTables publishers
    private final BooleanPublisher shooterActivePublisher = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Shooter/Active")
            .publish();
    
    private final BooleanPublisher shooterAtSpeedPublisher = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Shooter/AtSpeed")
            .publish();
    
    @Override
    public void periodic() {
        // ... existing code ...
        
        // Publish state to NetworkTables
        shooterActivePublisher.set(isShooterActive);
        shooterAtSpeedPublisher.set(isAtTargetSpeed());
    }
}
```

**Example: IntakeSubsystem**

```java
public class IntakeSubsystem extends SubsystemBase {
    private final BooleanPublisher hasGamePiecePublisher = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Intake/HasGamePiece")
            .publish();
    
    @Override
    public void periodic() {
        // ... existing code ...
        
        // Publish state to NetworkTables
        hasGamePiecePublisher.set(hasGamePiece());
    }
}
```

### Step 2: CandleSubsystem Reads from NetworkTables and Controls LEDs

`CandleSubsystem` subscribes to NetworkTables and determines LED status based on robot state.

```java
public class CandleSubsystem extends SubsystemBase {
    private final CANdle candle = new CANdle(CANdleConstants.CANDLE_ID);
    
    // NetworkTables subscribers
    private final BooleanSubscriber shooterActiveSubscriber = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Shooter/Active")
            .subscribe(false);
    
    private final BooleanSubscriber shooterAtSpeedSubscriber = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Shooter/AtSpeed")
            .subscribe(false);
    
    private final BooleanSubscriber hasGamePieceSubscriber = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Intake/HasGamePiece")
            .subscribe(false);
    
    private final BooleanSubscriber alignedWithTargetSubscriber = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Swerve/AlignedWithTarget")
            .subscribe(false);
    
    // Status mode enum
    public enum LEDStatusMode {
        OFF,
        READY_TO_SHOOT,      // Green: Shooter ready + aligned + has game piece
        SHOOTER_ACTIVE,      // Yellow: Shooter spinning but not ready
        HAS_GAME_PIECE,      // Blue: Has game piece but not ready to shoot
        ALIGNED,             // Cyan: Aligned with target but not ready
        DISABLED_RED,        // Red: Disabled, red alliance
        DISABLED_BLUE,       // Blue: Disabled, blue alliance
        ERROR                // Red flashing: Error state
    }
    
    @Override
    public void periodic() {
        LEDStatusMode status = determineStatus();
        setLEDStatus(status);
    }
    
    private LEDStatusMode determineStatus() {
        // Priority-based status determination
        if (DriverStation.isDisabled()) {
            // Show alliance color when disabled
            return DriverStation.getAlliance()
                .orElse(Alliance.Blue) == Alliance.Red 
                ? LEDStatusMode.DISABLED_RED 
                : LEDStatusMode.DISABLED_BLUE;
        }
        
        // Ready to shoot: shooter active + at speed + aligned + has game piece
        if (shooterActiveSubscriber.get() 
            && shooterAtSpeedSubscriber.get() 
            && alignedWithTargetSubscriber.get() 
            && hasGamePieceSubscriber.get()) {
            return LEDStatusMode.READY_TO_SHOOT;
        }
        
        // Shooter active but not ready
        if (shooterActiveSubscriber.get()) {
            return LEDStatusMode.SHOOTER_ACTIVE;
        }
        
        // Has game piece but not ready to shoot
        if (hasGamePieceSubscriber.get()) {
            return LEDStatusMode.HAS_GAME_PIECE;
        }
        
        // Aligned but not ready
        if (alignedWithTargetSubscriber.get()) {
            return LEDStatusMode.ALIGNED;
        }
        
        return LEDStatusMode.OFF;
    }
    
    private void setLEDStatus(LEDStatusMode status) {
        RGBWColor color;
        switch (status) {
            case READY_TO_SHOOT:
                color = new RGBWColor(0, 255, 0, 0);  // Green
                break;
            case SHOOTER_ACTIVE:
                color = new RGBWColor(255, 255, 0, 0);  // Yellow
                break;
            case HAS_GAME_PIECE:
                color = new RGBWColor(0, 0, 255, 0);  // Blue
                break;
            case ALIGNED:
                color = new RGBWColor(0, 255, 255, 0);  // Cyan
                break;
            case DISABLED_RED:
                color = new RGBWColor(255, 0, 0, 0);  // Red
                break;
            case DISABLED_BLUE:
                color = new RGBWColor(0, 0, 255, 0);  // Blue
                break;
            case ERROR:
                color = new RGBWColor(255, 0, 0, 0);  // Red (flashing handled separately)
                break;
            default:
                color = new RGBWColor(0, 0, 0, 0);  // Off
        }
        
        candle.setControl(new SolidColor(LED_START_INDEX, LED_END_INDEX)
            .withColor(color));
    }
}
```

### Step 3: Publish LED Status to NetworkTables for Driver Station Display

`CandleSubsystem` also publishes its determined status to NetworkTables so Driver Station can display it.

```java
public class CandleSubsystem extends SubsystemBase {
    // ... existing code ...
    
    // Publisher for Driver Station display
    private final StringPublisher ledStatusPublisher = 
        NetworkTableInstance.getDefault()
            .getStringTopic("RobotState/LED/Status")
            .publish();
    
    private final IntegerPublisher ledStatusCodePublisher = 
        NetworkTableInstance.getDefault()
            .getIntegerTopic("RobotState/LED/StatusCode")
            .publish();
    
    @Override
    public void periodic() {
        LEDStatusMode status = determineStatus();
        setLEDStatus(status);
        
        // Publish status for Driver Station display
        ledStatusPublisher.set(status.name());
        ledStatusCodePublisher.set(status.ordinal());
    }
}
```

### Step 4: Display Status on Driver Station/Shuffleboard

Create a Shuffleboard tab or use SmartDashboard to display the LED status indicators.

**Option A: Using SmartDashboard (Simple)**

```java
// In CandleSubsystem.periodic()
SmartDashboard.putString("Robot Status", status.name());
SmartDashboard.putBoolean("Ready to Shoot", 
    status == LEDStatusMode.READY_TO_SHOOT);
SmartDashboard.putBoolean("Has Game Piece", 
    hasGamePieceSubscriber.get());
SmartDashboard.putBoolean("Shooter Ready", 
    shooterActiveSubscriber.get() && shooterAtSpeedSubscriber.get());
```

**Option B: Using Shuffleboard (More Visual)**

Create a Shuffleboard tab with color-coded indicators that match the LED colors.

```java
// In RobotContainer or a dedicated StatusDisplay class
public void configureShuffleboard() {
    ShuffleboardTab statusTab = Shuffleboard.getTab("Robot Status");
    
    // Status indicator (text)
    statusTab.add("Status", 
        NetworkTableInstance.getDefault()
            .getStringTopic("RobotState/LED/Status")
            .subscribe("UNKNOWN"));
    
    // Color-coded boolean indicators
    statusTab.add("Ready to Shoot", 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Shooter/Ready")
            .subscribe(false))
        .withWidget(BuiltInWidgets.kBooleanBox)
        .withProperties(Map.of("colorWhenTrue", "#00FF00"));  // Green
    
    statusTab.add("Has Game Piece", 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Intake/HasGamePiece")
            .subscribe(false))
        .withWidget(BuiltInWidgets.kBooleanBox)
        .withProperties(Map.of("colorWhenTrue", "#0000FF"));  // Blue
    
    statusTab.add("Aligned", 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Swerve/AlignedWithTarget")
            .subscribe(false))
        .withWidget(BuiltInWidgets.kBooleanBox)
        .withProperties(Map.of("colorWhenTrue", "#00FFFF"));  // Cyan
}
```

## NetworkTables Key Naming Convention

Use a consistent naming convention for NetworkTables keys:

```
RobotState/
  ├── Shooter/
  │   ├── Active (boolean)
  │   ├── AtSpeed (boolean)
  │   └── Ready (boolean)  // Computed: Active && AtSpeed
  ├── Intake/
  │   └── HasGamePiece (boolean)
  ├── Swerve/
  │   └── AlignedWithTarget (boolean)
  └── LED/
      ├── Status (string)  // Current LED status mode name
      └── StatusCode (int) // Current LED status mode ordinal
```

## Benefits of This Architecture

1. **Single Source of Truth**: Status is determined once, displayed in multiple places
2. **No Duplicate Logic**: LED subsystem and Driver Station use the same data
3. **Automatic Synchronization**: LEDs and Driver Station always show the same status
4. **Easy to Extend**: Add new status indicators by publishing to NetworkTables
5. **Debugging**: Can see status in Shuffleboard even when robot LEDs aren't visible
6. **Consistent**: Uses same pattern as existing swerve state publishing

## Testing

1. **Unit Testing**: Can create mock NetworkTables publishers/subscribers
2. **Integration Testing**: Verify LEDs and Driver Station show same status
3. **Driver Testing**: Verify Driver Station display is visible when robot is behind hub

## Future Enhancements

1. **Status History**: Log status changes to NetworkTables for analysis
2. **Custom Dashboard**: Create a custom Driver Station dashboard with visual indicators
3. **Audio Alerts**: Add audio alerts on Driver Station for critical status changes
4. **Status Patterns**: Add flashing/pulsing patterns for different statuses
