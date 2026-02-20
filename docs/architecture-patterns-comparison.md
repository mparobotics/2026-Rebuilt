# Dependency Injection vs Singleton Pattern Comparison

## Overview

This document compares the **Dependency Injection** pattern (currently used in this codebase) with the **Singleton** pattern (used by KnightKrawlers and many FRC teams) for managing subsystems and cross-subsystem communication.

---

## Dependency Injection Pattern (Current Approach)

### How It Works

Subsystems are created in `RobotContainer` and passed as constructor parameters to commands and other subsystems that need them.

**Example from your codebase:**

```java
// RobotContainer.java
public class RobotContainer {
    private final SwerveSubsystem m_drive = new SwerveSubsystem();
    private final ShooterSubsystem m_shooter = new ShooterSubsystem();
    private final IntakeSubsystem m_intake = new IntakeSubsystem();
    
    public RobotContainer() {
        // Pass subsystems to commands
        new TeleopSwerve(m_drive, ...);
        new AutoAlign(m_drive, true);
    }
}

// TeleopSwerve.java
public class TeleopSwerve extends Command {
    private SwerveSubsystem m_SwerveSubsystem;  // Injected dependency
    
    public TeleopSwerve(SwerveSubsystem swerveSubsystem, ...) {
        this.m_SwerveSubsystem = swerveSubsystem;  // Dependency injected
        addRequirements(m_SwerveSubsystem);
    }
}
```

### Pros ✅

1. **Explicit Dependencies**
   - Dependencies are visible in constructor signatures
   - Easy to see what a class needs to function
   - Self-documenting code

2. **Testability**
   - Easy to create mock/test versions of subsystems
   - Can inject test doubles for unit testing
   - No global state to worry about

3. **Flexibility**
   - Can have multiple instances if needed (e.g., for testing)
   - Easy to swap implementations
   - Supports dependency inversion principle

4. **Type Safety**
   - Compiler enforces correct dependencies
   - IDE autocomplete works well
   - Refactoring tools can track dependencies

5. **No Hidden Dependencies**
   - All dependencies are explicit
   - No surprise "where did this come from?" moments
   - Easier to understand code flow

### Cons ❌

1. **Constructor Parameter Lists**
   - Can get long if a class needs many dependencies
   - Example: `CandleSubsystem(shooter, intake, swerve, vision, ...)`

2. **Wiring Complexity**
   - Must pass dependencies through multiple layers
   - `RobotContainer` becomes the central wiring point
   - More boilerplate code

3. **Cross-Subsystem Access**
   - If `CandleSubsystem` needs to read from `ShooterSubsystem`, must pass it in constructor
   - Can create circular dependency issues
   - Requires planning dependency graph

4. **Access from Anywhere**
   - Can't easily access subsystems from utility classes or static methods
   - Must thread dependencies through call chain

---

## Singleton Pattern (KnightKrawlers Approach)

### How It Works

Each subsystem has a private static instance and a public `getInstance()` method that returns the single instance.

**Example:**

```java
// ShooterSubsystem.java
public class ShooterSubsystem extends SubsystemBase {
    private static ShooterSubsystem INSTANCE;
    
    private ShooterSubsystem() {
        // Private constructor prevents external instantiation
    }
    
    public static ShooterSubsystem getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ShooterSubsystem();
        }
        return INSTANCE;
    }
}

// CandleSubsystem.java
public class CandleSubsystem extends SubsystemBase {
    @Override
    public void periodic() {
        // Access any subsystem from anywhere
        if (ShooterSubsystem.getInstance().isShooterActive()) {
            setColor(Color.GREEN);
        }
    }
}
```

### Pros ✅

1. **Global Access**
   - Can access subsystems from anywhere in code
   - No need to thread dependencies through constructors
   - Very convenient for cross-subsystem communication

2. **Simple Wiring**
   - No constructor parameter lists
   - No need to pass dependencies around
   - Less boilerplate code

3. **Single Instance Guarantee**
   - Only one instance exists (matches FRC reality - one robot)
   - Prevents accidental multiple instances
   - Aligns with physical hardware (one drivetrain, one shooter, etc.)

4. **Easy Cross-Subsystem Access**
   - `CandleSubsystem` can easily read from any subsystem
   - Perfect for status/state aggregation
   - No circular dependency issues

5. **Matches FRC Reality**
   - There's only one robot
   - There's only one instance of each subsystem
   - Singleton enforces this at the code level

### Cons ❌

1. **Hidden Dependencies**
   - Dependencies are not visible in class signatures
   - Hard to see what a class depends on
   - Can create "spaghetti dependencies"

2. **Testing Challenges**
   - Hard to inject test doubles
   - Must reset singleton state between tests
   - Global state can cause test interference

3. **Tight Coupling**
   - Classes are tightly coupled to specific implementations
   - Can't easily swap implementations
   - Violates dependency inversion principle

4. **Initialization Order**
   - Must be careful about initialization order
   - `getInstance()` might be called before subsystem is initialized
   - Can cause null pointer exceptions if not careful

5. **Thread Safety (Usually Not an Issue in FRC)**
   - In multi-threaded environments, need synchronization
   - FRC code runs single-threaded, so not a concern

---

## RobotContainer-as-Service-Locator Pattern (Alternative Approach)

### How It Works

Pass `RobotContainer` itself to subsystems that need access to multiple subsystems, and add getter methods to `RobotContainer` for each subsystem.

**Example:**

```java
// RobotContainer.java
public class RobotContainer {
    private final SwerveSubsystem m_drive = new SwerveSubsystem();
    private final ShooterSubsystem m_shooter = new ShooterSubsystem();
    private final IntakeSubsystem m_intake = new IntakeSubsystem();
    private final CandleSubsystem m_candle = new CandleSubsystem(this);  // Pass 'this'
    
    // Getters for subsystems
    public SwerveSubsystem getDrive() { return m_drive; }
    public ShooterSubsystem getShooter() { return m_shooter; }
    public IntakeSubsystem getIntake() { return m_intake; }
    public CandleSubsystem getCandle() { return m_candle; }
}

// CandleSubsystem.java
public class CandleSubsystem extends SubsystemBase {
    private final RobotContainer container;
    
    public CandleSubsystem(RobotContainer container) {
        this.container = container;
    }
    
    @Override
    public void periodic() {
        // Access other subsystems through container
        if (container.getShooter().isShooterActive()) {
            setColor(Color.GREEN);
        }
        if (container.getIntake().hasGamePiece()) {
            setColor(Color.BLUE);
        }
    }
}
```

### Pros ✅

1. **Single Dependency**
   - Only need to pass `RobotContainer` (one parameter)
   - Avoids long constructor parameter lists
   - Cleaner than passing many individual subsystems

2. **Explicit Access Point**
   - Clear that you're accessing subsystems through a container
   - `container.getShooter()` is more explicit than `ShooterSubsystem.getInstance()`
   - Makes it obvious where subsystems come from

3. **Testable**
   - Can create a mock `RobotContainer` for testing
   - Can inject test doubles through container
   - Better than singleton for testing

4. **Centralized Management**
   - All subsystems are managed in one place
   - Easy to see all subsystem instances
   - `RobotContainer` becomes the "registry" of subsystems

5. **No Global State**
   - Avoids global singleton state
   - Still allows dependency injection
   - More flexible than singleton

6. **Type Safety**
   - Getter methods provide type safety
   - IDE autocomplete works well
   - Compiler catches errors

### Cons ❌

1. **Dependency on RobotContainer**
   - Creates coupling to `RobotContainer` class
   - `RobotContainer` becomes a "god object"
   - Can create circular dependencies if not careful

2. **Less Explicit Dependencies**
   - Not immediately clear which subsystems are actually used
   - Must read code to see `container.getShooter()` calls
   - Less self-documenting than direct dependency injection

3. **Potential for Overuse**
   - Easy to pass `RobotContainer` everywhere
   - Can lead to tight coupling
   - May violate single responsibility principle

4. **Initialization Order**
   - Must ensure `RobotContainer` is fully initialized before passing `this`
   - Can cause issues if subsystems access container in constructor
   - Need to be careful about initialization order

5. **Refactoring Challenges**
   - If `RobotContainer` structure changes, affects all dependent classes
   - Harder to extract subsystems into separate modules
   - Less modular than pure dependency injection

---

## Side-by-Side Comparison

### Example: CandleSubsystem Reading from ShooterSubsystem

#### Dependency Injection Approach

```java
// RobotContainer.java
private final ShooterSubsystem m_shooter = new ShooterSubsystem();
private final CandleSubsystem m_candle = new CandleSubsystem(m_shooter);

// CandleSubsystem.java
public class CandleSubsystem extends SubsystemBase {
    private final ShooterSubsystem shooter;
    
    public CandleSubsystem(ShooterSubsystem shooter) {
        this.shooter = shooter;
    }
    
    @Override
    public void periodic() {
        if (shooter.isShooterActive()) {
            setColor(Color.GREEN);
        }
    }
}
```

**Pros:** Explicit dependency, testable, clear  
**Cons:** Must pass through RobotContainer, longer constructor

#### Singleton Approach

```java
// RobotContainer.java
// No need to pass anything - subsystems access each other directly

// CandleSubsystem.java
public class CandleSubsystem extends SubsystemBase {
    @Override
    public void periodic() {
        if (ShooterSubsystem.getInstance().isShooterActive()) {
            setColor(Color.GREEN);
        }
    }
}
```

**Pros:** Simple, no wiring needed, easy cross-subsystem access  
**Cons:** Hidden dependency, harder to test, less explicit

#### RobotContainer-as-Service-Locator Approach

```java
// RobotContainer.java
public class RobotContainer {
    private final ShooterSubsystem m_shooter = new ShooterSubsystem();
    private final CandleSubsystem m_candle = new CandleSubsystem(this);
    
    public ShooterSubsystem getShooter() { return m_shooter; }
}

// CandleSubsystem.java
public class CandleSubsystem extends SubsystemBase {
    private final RobotContainer container;
    
    public CandleSubsystem(RobotContainer container) {
        this.container = container;
    }
    
    @Override
    public void periodic() {
        if (container.getShooter().isShooterActive()) {
            setColor(Color.GREEN);
        }
    }
}
```

**Pros:** Single dependency, explicit access point, testable, no global state  
**Cons:** Coupling to RobotContainer, less explicit about which subsystems used

---

## NetworkTables Pattern (WPILib Standard)

### How It Works

Use WPILib's NetworkTables (a key-value store) to publish and subscribe to subsystem state. Subsystems publish their state to NetworkTables, and other subsystems read from NetworkTables.

**Example:**

```java
// ShooterSubsystem.java - Publisher
public class ShooterSubsystem extends SubsystemBase {
    private final BooleanPublisher shooterActivePublisher = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Shooter/Active")
            .publish();
    
    @Override
    public void periodic() {
        // Publish state to NetworkTables
        shooterActivePublisher.set(isShooterActive);
    }
}

// CandleSubsystem.java - Subscriber
public class CandleSubsystem extends SubsystemBase {
    private final BooleanSubscriber shooterActiveSubscriber = 
        NetworkTableInstance.getDefault()
            .getBooleanTopic("RobotState/Shooter/Active")
            .subscribe(false);  // Default value if not available
    
    @Override
    public void periodic() {
        // Read state from NetworkTables
        if (shooterActiveSubscriber.get()) {
            setColor(Color.GREEN);
        }
    }
}
```

### Pros ✅

1. **WPILib Standard**
   - Built into WPILib, no custom code needed
   - Well-documented and supported
   - Thread-safe and efficient

2. **Decoupled Communication**
   - Subsystems don't need direct references to each other
   - Publisher and subscriber are completely decoupled
   - No circular dependency issues

3. **External Visibility**
   - Data automatically available to Shuffleboard, AdvantageScope, etc.
   - Great for debugging and monitoring
   - Can be viewed from driver station

4. **Multiple Subscribers**
   - Multiple subsystems can subscribe to the same data
   - No need to pass references around
   - Easy to add new consumers

5. **Persistent Across Restarts**
   - NetworkTables persists data (with some limitations)
   - Can survive robot code restarts
   - Useful for debugging

6. **Already in Your Codebase**
   - You're already using NetworkTables for swerve states
   - Consistent with existing patterns
   - Team already familiar with it

### Cons ❌

1. **String-Based Keys**
   - Keys are strings, prone to typos
   - No compile-time checking
   - Refactoring is harder (find/replace strings)

2. **Performance Overhead**
   - NetworkTables has serialization overhead
   - Slightly slower than direct method calls
   - Usually negligible, but not zero

3. **Type Safety**
   - Less type-safe than direct method calls
   - Must handle type conversions
   - Runtime errors if types don't match

4. **Default Values**
   - Must provide default values for subscribers
   - Need to handle "not available" cases
   - Can lead to stale data if publisher stops

5. **Not Ideal for Control**
   - NetworkTables is better for state sharing than control
   - Commands should still use direct subsystem access
   - More suited for read-only state

6. **Debugging Complexity**
   - Harder to trace data flow
   - Can't easily see who's reading/writing
   - NetworkTables viewer helps but adds complexity

### When to Use NetworkTables

**Good for:**
- ✅ Status/state sharing (like RobotState)
- ✅ Monitoring and debugging
- ✅ Cross-subsystem read-only state
- ✅ Data that needs to be visible externally
- ✅ Decoupled communication

**Not ideal for:**
- ❌ Control flow (commands should use direct access)
- ❌ High-frequency updates (though usually fine)
- ❌ Bidirectional communication
- ❌ When you need compile-time type safety

---

## FRC-Specific Considerations

### Why Singleton Makes Sense for FRC

1. **Physical Reality**
   - There's only one robot
   - There's only one instance of each subsystem
   - Singleton matches this reality

2. **Cross-Subsystem Communication**
   - Many subsystems need to read from others
   - `CandleSubsystem` needs to read from multiple subsystems
   - `RobotState` needs to aggregate from all subsystems
   - Singleton makes this easy

3. **Status/Aggregation Subsystems**
   - `CandleSubsystem` is a "status display" subsystem
   - It needs to read from many other subsystems
   - Dependency injection would require many constructor parameters
   - Singleton avoids this

4. **WPILib Command Framework**
   - CommandScheduler manages subsystem lifecycle
   - Subsystems are registered with scheduler
   - Singleton doesn't conflict with this

### When Dependency Injection is Better

1. **Commands**
   - Commands should use dependency injection
   - They're created dynamically and need explicit dependencies
   - Your current approach is correct here

2. **Testability**
   - If you do extensive unit testing
   - Dependency injection is easier to mock
   - But many FRC teams don't do much unit testing

3. **Multiple Implementations**
   - If you might have different implementations
   - But in FRC, you typically have one implementation

---

## Pattern Comparison Summary

| Aspect | Dependency Injection | Singleton | RobotContainer Locator | NetworkTables |
|--------|---------------------|-----------|----------------------|---------------|
| **Constructor Parameters** | Many (one per subsystem) | None | One (RobotContainer) | None |
| **Explicit Dependencies** | ✅ Very explicit | ❌ Hidden | ⚠️ Somewhat explicit | ❌ Hidden (string keys) |
| **Testability** | ✅ Excellent | ❌ Difficult | ✅ Good | ⚠️ Moderate |
| **Cross-Subsystem Access** | ❌ Requires passing all | ✅ Easy | ✅ Easy | ✅ Easy |
| **Global State** | ✅ None | ❌ Global | ✅ None | ⚠️ NetworkTables state |
| **Coupling** | ✅ Low | ⚠️ Medium | ❌ High (to RobotContainer) | ✅ Very Low (decoupled) |
| **Boilerplate** | ❌ More | ✅ Less | ⚠️ Medium | ⚠️ Medium (publisher/subscriber) |
| **Type Safety** | ✅ Compile-time | ✅ Compile-time | ✅ Compile-time | ❌ Runtime (string keys) |
| **External Visibility** | ❌ No | ❌ No | ❌ No | ✅ Yes (Shuffleboard, etc.) |
| **Performance** | ✅ Fastest | ✅ Fast | ✅ Fast | ⚠️ Slightly slower |
| **WPILib Standard** | ✅ Yes (pattern) | ✅ Yes (pattern) | ✅ Yes (pattern) | ✅ Yes (built-in) |

## Hybrid Approach (Recommended)

You can use **multiple patterns** strategically:

1. **Subsystems: Use Singleton or RobotContainer Locator**
   - Subsystems represent physical hardware (one instance)
   - Easy cross-subsystem communication
   - Matches FRC reality
   - **RobotContainer Locator** is a good middle ground

2. **Commands: Use Dependency Injection**
   - Commands are actions, not hardware
   - Created dynamically, need explicit dependencies
   - Better for testing and clarity

**Example:**

```java
// Subsystem (Singleton)
public class ShooterSubsystem extends SubsystemBase {
    private static ShooterSubsystem INSTANCE;
    public static ShooterSubsystem getInstance() { ... }
}

// Command (Dependency Injection)
public class ShootCommand extends Command {
    private final ShooterSubsystem shooter;
    
    public ShootCommand(ShooterSubsystem shooter) {
        this.shooter = shooter;
        addRequirements(shooter);
    }
}

// Status Subsystem (Singleton, reads from other singletons)
public class CandleSubsystem extends SubsystemBase {
    @Override
    public void periodic() {
        // Read from other subsystems easily
        if (ShooterSubsystem.getInstance().isShooterActive()) {
            setColor(Color.GREEN);
        }
    }
}
```

---

## Recommendation for Your Codebase

### For CandleSubsystem Specifically

**Three viable options:**

#### Option 1: RobotContainer Locator (Recommended for your team)
**Best if:** You want to avoid singletons but also avoid long parameter lists

```java
// RobotContainer.java
private final CandleSubsystem m_candle = new CandleSubsystem(this);

// CandleSubsystem.java
public CandleSubsystem(RobotContainer container) {
    this.container = container;
}
```

**Why this works well:**
- ✅ Single dependency (just `RobotContainer`)
- ✅ No global state (unlike singleton)
- ✅ Still testable (can mock `RobotContainer`)
- ✅ Explicit access point (`container.getShooter()`)
- ✅ Matches your current dependency injection style
- ✅ Easy to add getters as needed

#### Option 2: Singleton Pattern
**Best if:** You want maximum simplicity and don't mind global state

```java
// CandleSubsystem.java
@Override
public void periodic() {
    if (ShooterSubsystem.getInstance().isShooterActive()) {
        setColor(Color.GREEN);
    }
}
```

**Why this works:**
- ✅ Simplest approach
- ✅ No constructor parameters
- ✅ Easy cross-subsystem access
- ✅ Matches KnightKrawlers pattern
- ❌ Global state (harder to test)
- ❌ Hidden dependencies

#### Option 3: Dependency Injection (Current)
**Best if:** You want maximum explicitness and testability

```java
// RobotContainer.java
private final CandleSubsystem m_candle = new CandleSubsystem(m_shooter, m_intake, m_drive);

// CandleSubsystem.java
public CandleSubsystem(ShooterSubsystem shooter, IntakeSubsystem intake, SwerveSubsystem drive) {
    this.shooter = shooter;
    this.intake = intake;
    this.drive = drive;
}
```

**Why this works:**
- ✅ Most explicit dependencies
- ✅ Best for testing
- ✅ No hidden dependencies
- ❌ Long constructor parameter list
- ❌ More boilerplate

#### Option 4: NetworkTables (WPILib Standard)
**Best if:** You want decoupled communication and external visibility

```java
// ShooterSubsystem.java - Publisher
private final BooleanPublisher shooterActivePublisher = 
    NetworkTableInstance.getDefault()
        .getBooleanTopic("RobotState/Shooter/Active")
        .publish();

// CandleSubsystem.java - Subscriber
private final BooleanSubscriber shooterActiveSubscriber = 
    NetworkTableInstance.getDefault()
        .getBooleanTopic("RobotState/Shooter/Active")
        .subscribe(false);
```

**Why this works:**
- ✅ WPILib standard, already in your codebase
- ✅ Completely decoupled subsystems
- ✅ Automatically visible in Shuffleboard/AdvantageScope
- ✅ No constructor parameters needed
- ❌ String-based keys (no compile-time checking)
- ❌ Slightly slower than direct access
- ❌ Less type-safe

### For Other Subsystems

**Consider Singleton** for:
- Subsystems that represent physical hardware
- Subsystems that need cross-subsystem communication
- Subsystems that are accessed from many places

**Keep Dependency Injection** for:
- Commands (your current approach is good)
- Utility classes
- Test doubles

---

## Migration Path

If you want to adopt singletons:

1. **Start with CandleSubsystem** - Convert it to singleton
2. **Add getInstance() to other subsystems** - Make them singletons
3. **Keep commands using DI** - Don't change command constructors
4. **Update RobotContainer** - Can still create instances there, but also support getInstance()

**Example Migration:**

```java
// Before (Dependency Injection)
private final CandleSubsystem m_candle = new CandleSubsystem(m_shooter, m_intake);

// After (Singleton)
// In CandleSubsystem.periodic(), access other subsystems directly:
ShooterSubsystem.getInstance().isShooterActive()
IntakeSubsystem.getInstance().hasGamePiece()
```

---

## Conclusion

**For FRC robot code, there are four viable approaches:**

### NetworkTables (Best for Status Sharing)
- ✅ WPILib standard, already in your codebase
- ✅ Completely decoupled, no dependencies
- ✅ Automatically visible in Shuffleboard/AdvantageScope
- ✅ Great for monitoring and debugging
- ❌ String-based keys, less type-safe
- **Recommended if:** You want decoupled communication and external visibility, especially for read-only state

### RobotContainer Locator (Best Middle Ground)
- ✅ Single dependency, avoids long parameter lists
- ✅ No global state, still testable
- ✅ Explicit access through getters
- ✅ Matches dependency injection philosophy
- **Recommended if:** You want to avoid singletons but need easy cross-subsystem access

### Singleton Pattern (Simplest)
- ✅ Simplest approach, no wiring needed
- ✅ Easy cross-subsystem communication
- ❌ Global state, harder to test
- **Recommended if:** You prioritize simplicity and don't do much unit testing

### Dependency Injection (Most Explicit)
- ✅ Most explicit, best for testing
- ✅ No hidden dependencies
- ❌ Long parameter lists for status subsystems
- **Recommended if:** You do extensive unit testing and want maximum explicitness

**For Commands: Always use Dependency Injection**
- Commands are actions, not hardware
- Explicit dependencies are clearer
- Better for testing
- Your current approach is correct

**Final Recommendation:** 

For `CandleSubsystem` specifically, **NetworkTables is the clear winner** because:

1. **Dual Display Requirement**: You need the same status indicators on both:
   - Physical LEDs on the robot (visible when driver can see robot)
   - Driver Station display (visible when driver can't see robot, looking at camera feed)
   - NetworkTables automatically makes data available to both

2. **Single Source of Truth**: 
   - Publish status once to NetworkTables
   - `CandleSubsystem` reads from NetworkTables to control LEDs
   - Driver Station/Shuffleboard reads from NetworkTables to display status
   - No duplicate logic needed

3. **Already in Your Codebase**: 
   - You're already using NetworkTables for swerve states
   - You're already using SmartDashboard/Shuffleboard
   - Consistent with existing patterns

4. **Perfect Use Case**: 
   - LED status is read-only state (perfect for NetworkTables)
   - External visibility is essential (driver needs to see it)
   - Completely decoupled (no constructor parameters)

**Implementation Approach:**
- Subsystems publish their state to NetworkTables (e.g., `"RobotState/Shooter/Ready"`)
- `CandleSubsystem` subscribes to NetworkTables and controls LEDs
- Shuffleboard/Driver Station subscribes to same NetworkTables data and displays status indicators
- Single source of truth, dual display

**Alternative:** If you don't need Driver Station display, RobotContainer Locator is a good middle ground for compile-time type safety.
