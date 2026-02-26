# TestDashboard: Assessment and Design

## Executive Summary

Based on analysis of the two existing test implementations (`SwerveAngleDriftTestCommand` and `LedStateTestCommand`), introducing `TestDashboard` would provide **significant benefits** in code quality, maintainability, and developer experience. The current implementation shows clear patterns of repetitive boilerplate that could be eliminated.

The proposed design **completely eliminates the need for both `PARAM_PREFIX` and `RESULT_PREFIX` constants** by automatically constructing prefixes from the test instance using `getTestName()`. This provides a type-safe, consistent API with symmetric naming that reduces boilerplate and prevents common errors.

The class is named `TestDashboard` - a simple, concise name that clearly indicates it's for SmartDashboard interactions in diagnostic tests.

## Current Implementation Analysis

### SwerveAngleDriftTestCommand

**Parameter Initialization (lines 136-143):**
```java
private static final String PARAM_PREFIX = "DiagnosticTests/Swerve Angle Drift Test/Parameters/";

SmartDashboard.putNumber(PARAM_PREFIX + "ModuleNumber", 0);
SmartDashboard.putNumber(PARAM_PREFIX + "Angle", 90.0);
SmartDashboard.putNumber(PARAM_PREFIX + "NumberOfCycles", 10);
SmartDashboard.putNumber(PARAM_PREFIX + "AngleTolerance", 2.0);
SmartDashboard.putNumber(PARAM_PREFIX + "MaxWaitTime", 1.0);
SmartDashboard.putNumber(PARAM_PREFIX + "MinHoldTime", 0.5);
```

**Parameter Reading (lines 160-165):**
```java
moduleNumber = (int) SmartDashboard.getNumber(PARAM_PREFIX + "ModuleNumber", 0);
testAngleDegrees = SmartDashboard.getNumber(PARAM_PREFIX + "Angle", 90.0);
numberOfCycles = (int) SmartDashboard.getNumber(PARAM_PREFIX + "NumberOfCycles", 10);
angleToleranceDegrees = SmartDashboard.getNumber(PARAM_PREFIX + "AngleTolerance", 2.0);
maxWaitTimeSeconds = SmartDashboard.getNumber(PARAM_PREFIX + "MaxWaitTime", 1.0);
minHoldTimeSeconds = SmartDashboard.getNumber(PARAM_PREFIX + "MinHoldTime", 0.5);
```

**Issues Identified:**
1. **Prefix repetition**: `PARAM_PREFIX + "ParameterName"` appears 12 times (6 puts + 6 gets)
2. **Default value duplication**: Default values (0, 90.0, 10, 2.0, 1.0, 0.5) appear twice - once in `initializeParameters()` and once in `initialize()`
3. **Manual type casting**: `(int)` casts required for integer parameters, error-prone
4. **String concatenation**: 12 string concatenations that could be typos
5. **No compile-time safety**: Parameter name typos only caught at runtime
6. **PARAM_PREFIX management**: Every test must define and maintain a `PARAM_PREFIX` constant
7. **Error-prone**: Test name must match exactly in the prefix string
8. **Maintenance burden**: If test name changes, prefix must be updated manually

### LedStateTestCommand

**Parameter Initialization (lines 71-84):**
```java
private static final String PARAM_PREFIX = "DiagnosticTests/LED State Test/Parameters/";

SmartDashboard.putData(PARAM_PREFIX + "LedState", ledStateChooser);
SmartDashboard.putNumber(PARAM_PREFIX + "Duration", 3.0);
```

**Parameter Reading (lines 93-110):**
```java
SendableChooser<String> chooser = (SendableChooser<String>) SmartDashboard.getData(PARAM_PREFIX + "LedState");
String selectedStateName = null;
if (chooser != null) {
    selectedStateName = chooser.getSelected();
}
if (selectedStateName == null) {
    selectedStateName = CandleSubsystem.LedStates.None.name();
}
// ... error handling ...
duration = SmartDashboard.getNumber(PARAM_PREFIX + "Duration", 3.0);
```

**Issues Identified:**
1. **Complex chooser retrieval**: Requires casting, null checking, and error handling (17 lines of code)
2. **Default value duplication**: Duration default (3.0) appears in both methods
3. **Inconsistent patterns**: Different approach for SendableChooser vs. simple parameters
4. **Error-prone**: Type casting and null checks can be forgotten
5. **PARAM_PREFIX management**: Must define and maintain prefix constant

## Benefits of TestDashboard

### 1. **Eliminates PARAM_PREFIX Constant**

**Current Problem:**
- Every test must define: `private static final String PARAM_PREFIX = "DiagnosticTests/[TestName]/Parameters/";`
- Test name must match exactly in the prefix string
- If test name changes, prefix must be updated manually
- Risk of inconsistency across tests

**With TestDashboard (Option 1 - Separate Methods):**
```java
// NO PARAM_PREFIX constant needed!

@Override
public void initializeParameters() {
    // Prefix automatically constructed from getTestName()
    TestDashboard.putParamInt(this, "ModuleNumber", 0);
    TestDashboard.putParamDouble(this, "Angle", 90.0);
}

@Override
public void initialize() {
    // Same automatic prefix construction
    moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);
    testAngleDegrees = TestDashboard.getParamDouble(this, "Angle", 90.0);
}
```

**With TestDashboard (Option 2 - Unified Methods):**
```java
// NO PARAM_PREFIX constant needed!

@Override
public void initializeParameters() {
    // Prefix automatically constructed from getTestName()
    TestDashboard.putInt(this, DataType.PARAMETER, "ModuleNumber", 0);
    TestDashboard.putDouble(this, DataType.PARAMETER, "Angle", 90.0);
}

@Override
public void initialize() {
    // Same automatic prefix construction
    moduleNumber = TestDashboard.getInt(this, DataType.PARAMETER, "ModuleNumber", 0);
    testAngleDegrees = TestDashboard.getDouble(this, DataType.PARAMETER, "Angle", 90.0);
}
```

**Benefits:**
- ✅ **No PARAM_PREFIX constant needed** - eliminated entirely
- ✅ **Automatic prefix construction** - uses `getTestName()` from test instance
- ✅ **Consistent format** - all tests use same prefix pattern automatically
- ✅ **Maintainable** - if test name changes, prefix updates automatically
- ✅ **Error prevention** - can't have mismatched test names in prefix

### 2. **Eliminates Boilerplate Code**

**Current (SwerveAngleDriftTestCommand):**
- 12 lines for 6 parameters (6 puts + 6 gets)
- 12 string concatenations
- 2 manual type casts
- 1 PARAM_PREFIX constant definition

**With TestDashboard:**
```java
// initializeParameters()
TestDashboard.putParamInt(this, "ModuleNumber", 0);
TestDashboard.putParamDouble(this, "Angle", 90.0);
TestDashboard.putParamInt(this, "NumberOfCycles", 10);
TestDashboard.putParamDouble(this, "AngleTolerance", 2.0);
TestDashboard.putParamDouble(this, "MaxWaitTime", 1.0);
TestDashboard.putParamDouble(this, "MinHoldTime", 0.5);

// initialize()
moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);
testAngleDegrees = TestDashboard.getParamDouble(this, "Angle", 90.0);
numberOfCycles = TestDashboard.getParamInt(this, "NumberOfCycles", 10);
angleToleranceDegrees = TestDashboard.getParamDouble(this, "AngleTolerance", 2.0);
maxWaitTimeSeconds = TestDashboard.getParamDouble(this, "MaxWaitTime", 1.0);
minHoldTimeSeconds = TestDashboard.getParamDouble(this, "MinHoldTime", 0.5);
```

**Benefits:**
- ✅ No manual type casting needed
- ✅ Type-safe getters (`getInt()` vs `getDouble()`)
- ✅ Consistent API pattern
- ✅ **Code reduction**: Eliminates 2 type casts, removes PARAM_PREFIX constant
- ✅ Makes intent clearer

### 3. **Prevents Default Value Mismatches**

**Current Problem:**
- Default values must be manually kept in sync between `initializeParameters()` and `initialize()`
- Easy to introduce bugs if defaults differ:
  ```java
  // initializeParameters()
  SmartDashboard.putNumber(PARAM_PREFIX + "Angle", 90.0);  // Default: 90.0
  
  // initialize() - BUG: Different default!
  testAngleDegrees = SmartDashboard.getNumber(PARAM_PREFIX + "Angle", 45.0);  // Default: 45.0
  ```

**With TestDashboard:**
- Can use constants for defaults to ensure consistency:
  ```java
  // Single source of truth for defaults
  private static final double DEFAULT_ANGLE = 90.0;
  
  TestDashboard.putParamDouble(this, "Angle", DEFAULT_ANGLE);
  testAngleDegrees = TestDashboard.getParamDouble(this, "Angle", DEFAULT_ANGLE);
  ```

**Benefits:**
- ✅ Prevents default value mismatches
- ✅ Single source of truth for parameter definitions
- ✅ Compile-time safety

### 4. **Simplifies Complex Parameter Types**

**Current (LedStateTestCommand - SendableChooser):**
```java
// initializeParameters()
SendableChooser<String> ledStateChooser = new SendableChooser<>();
// ... populate chooser ...
SmartDashboard.putData(PARAM_PREFIX + "LedState", ledStateChooser);

// initialize() - Complex retrieval
SendableChooser<String> chooser = (SendableChooser<String>) SmartDashboard.getData(PARAM_PREFIX + "LedState");
String selectedStateName = null;
if (chooser != null) {
    selectedStateName = chooser.getSelected();
}
if (selectedStateName == null) {
    selectedStateName = CandleSubsystem.LedStates.None.name();
}
// ... error handling ...
```

**With TestDashboard:**
```java
// initializeParameters()
TestDashboard.putParamChooser(this, "LedState", ledStateChooser);

// initialize() - Simple retrieval with built-in null handling
String selectedStateName = TestDashboard.getParamChooserSelected(
    this, 
    "LedState", 
    CandleSubsystem.LedStates.None.name()  // default
);
```

**Benefits:**
- ✅ Reduces 17 lines to 2 lines (~88% code reduction)
- ✅ Handles null checking and defaults automatically
- ✅ Consistent error handling
- ✅ Less error-prone

### 5. **Improves Code Readability**

**Current:**
```java
moduleNumber = (int) SmartDashboard.getNumber(PARAM_PREFIX + "ModuleNumber", 0);
```

**With TestDashboard:**
```java
moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);
```

**Benefits:**
- ✅ Clearer intent: "get an integer" vs "get a number and cast to int"
- ✅ No type casting clutter
- ✅ Self-documenting method names
- ✅ No string concatenation visible

### 6. **Reduces Error-Prone Patterns**

**Current Issues:**
1. **String typos**: `PARAM_PREFIX + "ModuleNumber"` vs `PARAM_PREFIX + "ModuleNumbr"` (typo)
2. **Type mismatches**: Forgetting `(int)` cast, getting wrong type
3. **Default mismatches**: Different defaults in put vs get
4. **Null handling**: Forgetting null checks for SendableChooser
5. **Prefix mismatches**: Test name in prefix doesn't match `getTestName()`

**With TestDashboard:**
- Type-safe methods prevent type errors
- Consistent null handling built-in
- Automatic prefix construction prevents mismatches
- Can use constants for parameter names to reduce typos:
  ```java
  private static final String KEY_MODULE_NUMBER = "ModuleNumber";
  private static final String KEY_ANGLE = "Angle";
  
  TestDashboard.putParamInt(this, KEY_MODULE_NUMBER, 0);
  moduleNumber = TestDashboard.getParamInt(this, KEY_MODULE_NUMBER, 0);
  ```

**Benefits:**
- ✅ Fewer runtime errors
- ✅ Better IDE autocomplete support
- ✅ Easier refactoring (rename parameter key in one place)
- ✅ Automatic prefix consistency

### 7. **Enables Future Enhancements**

With a centralized helper, we could add:
- **Parameter validation**: Validate ranges, types, etc.
- **Parameter documentation**: Associate descriptions with parameters
- **Parameter groups**: Organize related parameters
- **Parameter persistence**: Save/load parameter sets
- **Parameter templates**: Predefined parameter sets for common scenarios

**Example:**
```java
// Future enhancement: Parameter validation
TestDashboard.putParamDouble(
    this, 
    "Angle", 
    90.0,
    Validator.range(0.0, 360.0)  // Optional validation
);
```

## Design Solution: Eliminating PARAM_PREFIX

### Problem

Currently, each test must manually manage a `PARAM_PREFIX` constant:

```java
private static final String PARAM_PREFIX = "DiagnosticTests/Swerve Angle Drift Test/Parameters/";

@Override
public void initializeParameters() {
    SmartDashboard.putNumber(PARAM_PREFIX + "ModuleNumber", 0);
    // ...
}

@Override
public void initialize() {
    moduleNumber = (int) SmartDashboard.getNumber(PARAM_PREFIX + "ModuleNumber", 0);
    // ...
}
```

**Issues:**
1. **Repetitive**: Every test must define `PARAM_PREFIX`
2. **Error-prone**: Test name must match exactly in the prefix string
3. **Maintenance burden**: If test name changes, prefix must be updated
4. **Inconsistency risk**: Different tests might use slightly different prefix formats

### Solution: Automatic Prefix Construction

`TestDashboard` automatically constructs the parameter prefix from the test instance using `getTestName()`. This eliminates the need for tests to manage `PARAM_PREFIX` at all.

### Design Options Evaluated

#### Option 1: Pass Test Instance to Helper Methods (Recommended) ✅

**API Design:**
```java
public class TestDashboard {
    private static final String BASE_PREFIX = "DiagnosticTests/";
    private static final String PARAMETERS_SUFFIX = "/Parameters/";
    
    /**
     * Constructs the parameter prefix for a test.
     * Format: "DiagnosticTests/[TestName]/Parameters/"
     */
    private static String getParameterPrefix(DiagnosticTest test) {
        if (test == null) {
            throw new IllegalArgumentException("Test instance cannot be null");
        }
        String testName = test.getTestName();
        if (testName == null || testName.isEmpty()) {
            throw new IllegalStateException("Test name cannot be null or empty. Implement getTestName() properly.");
        }
        return BASE_PREFIX + testName + PARAMETERS_SUFFIX;
    }
    
    // Put methods
    public static void putInt(DiagnosticTest test, String parameterName, int defaultValue) {
        SmartDashboard.putNumber(getParameterPrefix(test) + parameterName, defaultValue);
    }
    
    public static void putDouble(DiagnosticTest test, String parameterName, double defaultValue) {
        SmartDashboard.putNumber(getParameterPrefix(test) + parameterName, defaultValue);
    }
    
    public static void putBoolean(DiagnosticTest test, String parameterName, boolean defaultValue) {
        SmartDashboard.putBoolean(getParameterPrefix(test) + parameterName, defaultValue);
    }
    
    public static void putString(DiagnosticTest test, String parameterName, String defaultValue) {
        SmartDashboard.putString(getParameterPrefix(test) + parameterName, defaultValue);
    }
    
    public static void putChooser(DiagnosticTest test, String parameterName, SendableChooser<?> chooser) {
        SmartDashboard.putData(getParameterPrefix(test) + parameterName, chooser);
    }
    
    // Get methods
    public static int getInt(DiagnosticTest test, String parameterName, int defaultValue) {
        return (int) SmartDashboard.getNumber(getParameterPrefix(test) + parameterName, defaultValue);
    }
    
    public static double getDouble(DiagnosticTest test, String parameterName, double defaultValue) {
        return SmartDashboard.getNumber(getParameterPrefix(test) + parameterName, defaultValue);
    }
    
    public static boolean getBoolean(DiagnosticTest test, String parameterName, boolean defaultValue) {
        return SmartDashboard.getBoolean(getParameterPrefix(test) + parameterName, defaultValue);
    }
    
    public static String getString(DiagnosticTest test, String parameterName, String defaultValue) {
        return SmartDashboard.getString(getParameterPrefix(test) + parameterName, defaultValue);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> SendableChooser<T> getChooser(DiagnosticTest test, String parameterName) {
        return (SendableChooser<T>) SmartDashboard.getData(getParameterPrefix(test) + parameterName);
    }
    
    public static <T> T getChooserSelected(DiagnosticTest test, String parameterName, T defaultValue) {
        SendableChooser<T> chooser = getChooser(test, parameterName);
        if (chooser != null && chooser.getSelected() != null) {
            return chooser.getSelected();
        }
        return defaultValue;
    }
    
    // Optional: Also support full key path for flexibility (backward compatibility)
    public static void putInt(String fullKey, int defaultValue) {
        SmartDashboard.putNumber(fullKey, defaultValue);
    }
    
    public static int getInt(String fullKey, int defaultValue) {
        return (int) SmartDashboard.getNumber(fullKey, defaultValue);
    }
    // ... similar overloads for other types
}
```

**Usage in Tests:**
```java
public class SwerveAngleDriftTestCommand extends Command implements DiagnosticTest {
    // NO PARAM_PREFIX needed!
    
    @Override
    public String getTestName() {
        return "Swerve Angle Drift Test";
    }
    
    @Override
    public void initializeParameters() {
        // Pass 'this' to helper methods - prefix is automatically constructed
        TestDashboard.putParamInt(this, "ModuleNumber", 0);
        TestDashboard.putParamDouble(this, "Angle", 90.0);
        TestDashboard.putParamInt(this, "NumberOfCycles", 10);
        TestDashboard.putParamDouble(this, "AngleTolerance", 2.0);
        TestDashboard.putParamDouble(this, "MaxWaitTime", 1.0);
        TestDashboard.putParamDouble(this, "MinHoldTime", 0.5);
    }
    
    @Override
    public void initialize() {
        // Read parameters - prefix automatically constructed from test name
        moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);
        testAngleDegrees = TestDashboard.getParamDouble(this, "Angle", 90.0);
        numberOfCycles = TestDashboard.getParamInt(this, "NumberOfCycles", 10);
        angleToleranceDegrees = TestDashboard.getParamDouble(this, "AngleTolerance", 2.0);
        maxWaitTimeSeconds = TestDashboard.getParamDouble(this, "MaxWaitTime", 1.0);
        minHoldTimeSeconds = TestDashboard.getParamDouble(this, "MinHoldTime", 0.5);
        // ...
    }
}
```

**Benefits:**
- ✅ **No PARAM_PREFIX constant needed** - eliminated entirely
- ✅ **Automatic prefix construction** - uses `getTestName()` from test instance
- ✅ **Type-safe** - no manual casting needed
- ✅ **Consistent** - all tests use same prefix format automatically
- ✅ **Maintainable** - if test name changes, prefix updates automatically
- ✅ **Flexible** - still supports full key path for edge cases

**Considerations:**
- Tests must pass `this` to helper methods (minor verbosity)
- Helper methods need to call `getTestName()` on each call (negligible performance impact)

#### Option 2: Context Pattern with Initialization ❌

**Drawbacks:**
- ❌ More complex - requires context management
- ❌ Error-prone - easy to forget `setContext()` or `clearContext()`
- ❌ Thread-local overhead
- ❌ Not thread-safe if tests run concurrently

**Verdict:** Not recommended - too complex for the benefit.

#### Option 3: Builder Pattern ❌

**Drawbacks:**
- ❌ More verbose for reading parameters (need to create helper instance)
- ❌ Less intuitive for getters (can't chain as naturally)

**Verdict:** Nice API, but Option 1 is simpler and more straightforward.

### Recommended Solution: Option 1

**Final API:**
```java
// In initializeParameters()
TestDashboard.putParamInt(this, "ModuleNumber", 0);
TestDashboard.putParamDouble(this, "Angle", 90.0);

// In initialize()
moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);
testAngleDegrees = TestDashboard.getParamDouble(this, "Angle", 90.0);
```

**Key Features:**
1. **No PARAM_PREFIX constant** - completely eliminated
2. **Automatic prefix construction** - uses `getTestName()` from test instance
3. **Type-safe methods** - `getInt()`, `getDouble()`, etc.
4. **Consistent format** - all tests use same prefix pattern
5. **Simple and intuitive** - pass `this`, pass parameter name, done

## Implementation Notes

1. **Prefix Format**: `"DiagnosticTests/" + test.getTestName() + "/Parameters/"`
2. **Backward Compatibility**: Can provide overloaded methods that accept full key path for edge cases
3. **Error Handling**: If `test.getTestName()` returns null or empty, throw descriptive exception
4. **Performance**: Calling `getTestName()` on each helper call is negligible (simple string return)

## Migration Examples

### SwerveAngleDriftTestCommand

**Before:**
```java
private static final String PARAM_PREFIX = "DiagnosticTests/Swerve Angle Drift Test/Parameters/";

@Override
public void initializeParameters() {
    SmartDashboard.putNumber(PARAM_PREFIX + "ModuleNumber", 0);
    SmartDashboard.putNumber(PARAM_PREFIX + "Angle", 90.0);
    SmartDashboard.putNumber(PARAM_PREFIX + "NumberOfCycles", 10);
    SmartDashboard.putNumber(PARAM_PREFIX + "AngleTolerance", 2.0);
    SmartDashboard.putNumber(PARAM_PREFIX + "MaxWaitTime", 1.0);
    SmartDashboard.putNumber(PARAM_PREFIX + "MinHoldTime", 0.5);
}

@Override
public void initialize() {
    moduleNumber = (int) SmartDashboard.getNumber(PARAM_PREFIX + "ModuleNumber", 0);
    testAngleDegrees = SmartDashboard.getNumber(PARAM_PREFIX + "Angle", 90.0);
    numberOfCycles = (int) SmartDashboard.getNumber(PARAM_PREFIX + "NumberOfCycles", 10);
    angleToleranceDegrees = SmartDashboard.getNumber(PARAM_PREFIX + "AngleTolerance", 2.0);
    maxWaitTimeSeconds = SmartDashboard.getNumber(PARAM_PREFIX + "MaxWaitTime", 1.0);
    minHoldTimeSeconds = SmartDashboard.getNumber(PARAM_PREFIX + "MinHoldTime", 0.5);
}
```

**After:**
```java
// PARAM_PREFIX constant removed!

@Override
public void initializeParameters() {
    TestDashboard.putParamInt(this, "ModuleNumber", 0);
    TestDashboard.putParamDouble(this, "Angle", 90.0);
    TestDashboard.putParamInt(this, "NumberOfCycles", 10);
    TestDashboard.putParamDouble(this, "AngleTolerance", 2.0);
    TestDashboard.putParamDouble(this, "MaxWaitTime", 1.0);
    TestDashboard.putParamDouble(this, "MinHoldTime", 0.5);
}

@Override
public void initialize() {
    moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);
    testAngleDegrees = TestDashboard.getParamDouble(this, "Angle", 90.0);
    numberOfCycles = TestDashboard.getParamInt(this, "NumberOfCycles", 10);
    angleToleranceDegrees = TestDashboard.getParamDouble(this, "AngleTolerance", 2.0);
    maxWaitTimeSeconds = TestDashboard.getParamDouble(this, "MaxWaitTime", 1.0);
    minHoldTimeSeconds = TestDashboard.getParamDouble(this, "MinHoldTime", 0.5);
}
```

**Changes:**
- ✅ Removed `PARAM_PREFIX` constant (1 line removed)
- ✅ Eliminated 2 type casts
- ✅ Cleaner, more readable code
- ✅ Automatic prefix construction

### LedStateTestCommand

**Before:**
```java
private static final String PARAM_PREFIX = "DiagnosticTests/LED State Test/Parameters/";

@Override
public void initializeParameters() {
    SmartDashboard.putData(PARAM_PREFIX + "LedState", ledStateChooser);
    SmartDashboard.putNumber(PARAM_PREFIX + "Duration", 3.0);
}

@Override
public void initialize() {
    SendableChooser<String> chooser = (SendableChooser<String>) SmartDashboard.getData(PARAM_PREFIX + "LedState");
    String selectedStateName = null;
    if (chooser != null) {
        selectedStateName = chooser.getSelected();
    }
    if (selectedStateName == null) {
        selectedStateName = CandleSubsystem.LedStates.None.name();
    }
    // ... error handling ...
    duration = SmartDashboard.getNumber(PARAM_PREFIX + "Duration", 3.0);
}
```

**After:**
```java
// PARAM_PREFIX constant removed!

@Override
public void initializeParameters() {
    TestDashboard.putParamChooser(this, "LedState", ledStateChooser);
    TestDashboard.putParamDouble(this, "Duration", 3.0);
}

@Override
public void initialize() {
    String selectedStateName = TestDashboard.getParamChooserSelected(
        this, 
        "LedState", 
        CandleSubsystem.LedStates.None.name()
    );
    // ... error handling ...
    duration = TestDashboard.getParamDouble(this, "Duration", 3.0);
}
```

**Changes:**
- ✅ Removed `PARAM_PREFIX` constant (1 line removed)
- ✅ Simplified chooser retrieval (17 lines → 3 lines, ~82% reduction)
- ✅ Built-in null handling
- ✅ Automatic prefix construction

## Quantitative Impact

### Code Reduction

**SwerveAngleDriftTestCommand:**
- Current: 13 lines for parameter management (1 constant + 12 parameter lines)
- With TestDashboard: 12 lines (no constant needed)
- **Benefit**: Eliminates 1 constant definition, 2 type casts, improves readability

**LedStateTestCommand:**
- Current: ~21 lines for parameter management (1 constant + ~20 lines including chooser handling)
- With TestDashboard: ~5 lines
- **Benefit**: ~76% code reduction for parameter handling

### Error Prevention

**Current Risk Areas:**
1. Type casting errors: 2 per test (int parameters)
2. Default value mismatches: 6 opportunities in SwerveAngleDriftTestCommand
3. String typos: 12 opportunities per test
4. Null handling: 1 complex case in LedStateTestCommand
5. Prefix mismatches: Test name in prefix doesn't match `getTestName()`

**With TestDashboard:**
- Type casting errors: 0 (type-safe methods)
- Default value mismatches: Can be prevented with constants
- String typos: Can be reduced with constants
- Null handling: Built-in
- Prefix mismatches: 0 (automatic construction)

## Recommendations

### High Priority Benefits

1. **Eliminates PARAM_PREFIX**: No need to define and maintain prefix constants
2. **Type Safety**: Eliminate manual type casting for integer parameters
3. **Code Clarity**: Self-documenting method names (`getInt()` vs `getNumber()`)
4. **Complex Parameter Handling**: Simplify SendableChooser retrieval (~82% code reduction)

### Medium Priority Benefits

5. **Default Value Consistency**: Provide patterns to prevent mismatches
6. **Error Reduction**: Built-in null handling and validation

### Low Priority (Future Enhancements)

7. **Parameter Validation**: Range checking, type validation
8. **Parameter Documentation**: Associate descriptions with parameters
9. **Parameter Templates**: Predefined parameter sets

## Results Support

### Current Results Pattern

Both tests also use a `RESULT_PREFIX` constant to publish results to SmartDashboard:

**SwerveAngleDriftTestCommand:**
```java
private static final String RESULT_PREFIX = "DiagnosticTests/Swerve Angle Drift Test/Results/";

// Results organized into groups
SmartDashboard.putNumber(RESULT_PREFIX + "Config/Module", moduleNumber);
SmartDashboard.putNumber(RESULT_PREFIX + "Progress/CurrentCycle", currentCycle + 1);
SmartDashboard.putString(RESULT_PREFIX + "Progress/State", currentState.toString());
SmartDashboard.putNumber(RESULT_PREFIX + "RealTime/CurrentAngleEncoderDrift", currentDrift);
SmartDashboard.putNumber(RESULT_PREFIX + "Summary/TotalDrift", totalDrift);
SmartDashboard.putString(RESULT_PREFIX + "Status/Message", "Complete");
```

**LedStateTestCommand:**
```java
private static final String RESULT_PREFIX = "DiagnosticTests/LED State Test/Results/";

SmartDashboard.putString(RESULT_PREFIX + "Status", "Running");
SmartDashboard.putNumber(RESULT_PREFIX + "ElapsedTime", actualDuration);
SmartDashboard.putNumber(RESULT_PREFIX + "ActualDuration", actualDuration);
```

**Issues:**
- Same problems as `PARAM_PREFIX`: repetitive, error-prone, maintenance burden
- Results are only written (never read), so only need `put` methods
- Results often organized into logical groups (Config, Progress, RealTime, Summary, Status)

### Class Name Alternatives

Since the helper supports both parameters (inputs) and results (outputs), alternative class names to consider:

1. **`TestDashboard`** ⭐ (Recommended)
   - Simple and concise
   - Clear and descriptive
   - Covers all SmartDashboard interactions
   - Accurately describes the class's purpose

2. **`TestDashboardHelper`**
   - Clear but slightly verbose
   - Explicitly indicates it's a helper class
   - (Not chosen - using `TestDashboard` instead)

3. **`DiagnosticTestDashboard`**
   - More specific to diagnostic tests
   - Slightly longer
   - Clear about scope

4. **`TestDataHelper`**
   - Generic but clear
   - Covers both inputs and outputs
   - Less specific about SmartDashboard

**Recommendation**: Use `TestDashboard` - it's simple, concise, and accurately describes the class's purpose of managing SmartDashboard interactions for tests.

### Expanded Helper: TestDashboard with Results Support

The helper can be expanded to support both parameters and results with symmetric method naming:

```java
public class TestDashboard {
    private static final String BASE_PREFIX = "DiagnosticTests/";
    private static final String PARAMETERS_SUFFIX = "/Parameters/";
    private static final String RESULTS_SUFFIX = "/Results/";
    
    /**
     * Enum to distinguish between parameters (inputs) and results (outputs).
     */
    public enum DataType {
        PARAMETER,  // Input parameters (read/write)
        RESULT      // Output results (write-only)
    }
    
    // Prefix construction
    private static String getPrefix(DiagnosticTest test, DataType type) {
        if (test == null) {
            throw new IllegalArgumentException("Test instance cannot be null");
        }
        String testName = test.getTestName();
        if (testName == null || testName.isEmpty()) {
            throw new IllegalStateException("Test name cannot be null or empty. Implement getTestName() properly.");
        }
        String suffix = (type == DataType.PARAMETER) ? PARAMETERS_SUFFIX : RESULTS_SUFFIX;
        return BASE_PREFIX + testName + suffix;
    }
    
    // ============================================================================
    // Option 1: Separate Methods (Recommended for Common Use)
    // Clear, explicit, and symmetric naming
    // ============================================================================
    
    // Parameter Methods (read/write)
    public static void putParamInt(DiagnosticTest test, String parameterName, int defaultValue) {
        SmartDashboard.putNumber(getPrefix(test, DataType.PARAMETER) + parameterName, defaultValue);
    }
    
    public static int getParamInt(DiagnosticTest test, String parameterName, int defaultValue) {
        return (int) SmartDashboard.getNumber(getPrefix(test, DataType.PARAMETER) + parameterName, defaultValue);
    }
    
    public static void putParamDouble(DiagnosticTest test, String parameterName, double defaultValue) {
        SmartDashboard.putNumber(getPrefix(test, DataType.PARAMETER) + parameterName, defaultValue);
    }
    
    public static double getParamDouble(DiagnosticTest test, String parameterName, double defaultValue) {
        return SmartDashboard.getNumber(getPrefix(test, DataType.PARAMETER) + parameterName, defaultValue);
    }
    
    public static void putParamBoolean(DiagnosticTest test, String parameterName, boolean defaultValue) {
        SmartDashboard.putBoolean(getPrefix(test, DataType.PARAMETER) + parameterName, defaultValue);
    }
    
    public static boolean getParamBoolean(DiagnosticTest test, String parameterName, boolean defaultValue) {
        return SmartDashboard.getBoolean(getPrefix(test, DataType.PARAMETER) + parameterName, defaultValue);
    }
    
    public static void putParamString(DiagnosticTest test, String parameterName, String defaultValue) {
        SmartDashboard.putString(getPrefix(test, DataType.PARAMETER) + parameterName, defaultValue);
    }
    
    public static String getParamString(DiagnosticTest test, String parameterName, String defaultValue) {
        return SmartDashboard.getString(getPrefix(test, DataType.PARAMETER) + parameterName, defaultValue);
    }
    
    public static void putParamChooser(DiagnosticTest test, String parameterName, SendableChooser<?> chooser) {
        SmartDashboard.putData(getPrefix(test, DataType.PARAMETER) + parameterName, chooser);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> SendableChooser<T> getParamChooser(DiagnosticTest test, String parameterName) {
        return (SendableChooser<T>) SmartDashboard.getData(getPrefix(test, DataType.PARAMETER) + parameterName);
    }
    
    public static <T> T getParamChooserSelected(DiagnosticTest test, String parameterName, T defaultValue) {
        SendableChooser<T> chooser = getParamChooser(test, parameterName);
        if (chooser != null && chooser.getSelected() != null) {
            return chooser.getSelected();
        }
        return defaultValue;
    }
    
    // Result Methods (write-only, symmetric naming)
    public static void putResultInt(DiagnosticTest test, String resultName, int value) {
        SmartDashboard.putNumber(getPrefix(test, DataType.RESULT) + resultName, value);
    }
    
    public static void putResultDouble(DiagnosticTest test, String resultName, double value) {
        SmartDashboard.putNumber(getPrefix(test, DataType.RESULT) + resultName, value);
    }
    
    public static void putResultBoolean(DiagnosticTest test, String resultName, boolean value) {
        SmartDashboard.putBoolean(getPrefix(test, DataType.RESULT) + resultName, value);
    }
    
    public static void putResultString(DiagnosticTest test, String resultName, String value) {
        SmartDashboard.putString(getPrefix(test, DataType.RESULT) + resultName, value);
    }
    
    // ============================================================================
    // Option 2: Unified Methods (Optional, for Flexibility)
    // Single method that accepts DataType parameter
    // ============================================================================
    
    /**
     * Unified method to put an integer value (parameter or result).
     * 
     * @param test The test instance
     * @param type Whether this is a PARAMETER (input) or RESULT (output)
     * @param name The name of the parameter/result
     * @param value The value to set
     */
    public static void putInt(DiagnosticTest test, DataType type, String name, int value) {
        SmartDashboard.putNumber(getPrefix(test, type) + name, value);
    }
    
    /**
     * Unified method to get an integer parameter value.
     * Note: Results are write-only, so this only works for PARAMETER type.
     * 
     * @param test The test instance
     * @param type Must be PARAMETER (results are write-only)
     * @param name The name of the parameter
     * @param defaultValue The default value if not found
     * @return The parameter value
     */
    public static int getInt(DiagnosticTest test, DataType type, String name, int defaultValue) {
        if (type != DataType.PARAMETER) {
            throw new IllegalArgumentException("getInt() only supports PARAMETER type. Results are write-only.");
        }
        return (int) SmartDashboard.getNumber(getPrefix(test, type) + name, defaultValue);
    }
    
    // Similar unified methods for double, boolean, String...
    public static void putDouble(DiagnosticTest test, DataType type, String name, double value) {
        SmartDashboard.putNumber(getPrefix(test, type) + name, value);
    }
    
    public static double getDouble(DiagnosticTest test, DataType type, String name, double defaultValue) {
        if (type != DataType.PARAMETER) {
            throw new IllegalArgumentException("getDouble() only supports PARAMETER type. Results are write-only.");
        }
        return SmartDashboard.getNumber(getPrefix(test, type) + name, defaultValue);
    }
    
    // ... similar for boolean, String, etc.
}
```

**Design Decision: Provide Both Options**

1. **Separate Methods (Recommended for Common Use)**
   - `putParamInt()`, `getParamInt()` for parameters
   - `putResultInt()` for results
   - **Benefits**: Clear, explicit, symmetric naming, better IDE autocomplete
   - **Usage**: Most common case - clear intent at call site

2. **Unified Methods (Optional, for Flexibility)**
   - `putInt(test, DataType.PARAMETER, name, value)`
   - `putInt(test, DataType.RESULT, name, value)`
   - **Benefits**: Single method, flexible, useful for dynamic scenarios
   - **Usage**: Less common - when you need to switch between types dynamically

### Usage Examples

**SwerveAngleDriftTestCommand - Parameters (using separate methods):**
```java
// Before:
private static final String PARAM_PREFIX = "DiagnosticTests/Swerve Angle Drift Test/Parameters/";
SmartDashboard.putNumber(PARAM_PREFIX + "ModuleNumber", 0);
moduleNumber = (int) SmartDashboard.getNumber(PARAM_PREFIX + "ModuleNumber", 0);

// After (Option 1 - Separate Methods):
// PARAM_PREFIX constant removed!
TestDashboard.putParamInt(this, "ModuleNumber", 0);
moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);

// After (Option 2 - Unified Methods):
TestDashboard.putInt(this, DataType.PARAMETER, "ModuleNumber", 0);
moduleNumber = TestDashboard.getInt(this, DataType.PARAMETER, "ModuleNumber", 0);
```

**SwerveAngleDriftTestCommand - Results:**
```java
// Before:
private static final String RESULT_PREFIX = "DiagnosticTests/Swerve Angle Drift Test/Results/";
SmartDashboard.putNumber(RESULT_PREFIX + "Config/Module", moduleNumber);
SmartDashboard.putNumber(RESULT_PREFIX + "Progress/CurrentCycle", currentCycle + 1);
SmartDashboard.putString(RESULT_PREFIX + "Progress/State", currentState.toString());
SmartDashboard.putNumber(RESULT_PREFIX + "RealTime/CurrentAngleEncoderDrift", currentDrift);
SmartDashboard.putNumber(RESULT_PREFIX + "Summary/TotalDrift", totalDrift);
SmartDashboard.putString(RESULT_PREFIX + "Status/Message", "Complete");

// After (Option 1 - Separate Methods):
// RESULT_PREFIX constant removed!
TestDashboard.putResultInt(this, "Config/Module", moduleNumber);
TestDashboard.putResultInt(this, "Progress/CurrentCycle", currentCycle + 1);
TestDashboard.putResultString(this, "Progress/State", currentState.toString());
TestDashboard.putResultDouble(this, "RealTime/CurrentAngleEncoderDrift", currentDrift);
TestDashboard.putResultDouble(this, "Summary/TotalDrift", totalDrift);
TestDashboard.putResultString(this, "Status/Message", "Complete");

// After (Option 2 - Unified Methods):
TestDashboard.putInt(this, DataType.RESULT, "Config/Module", moduleNumber);
TestDashboard.putInt(this, DataType.RESULT, "Progress/CurrentCycle", currentCycle + 1);
TestDashboard.putString(this, DataType.RESULT, "Progress/State", currentState.toString());
TestDashboard.putDouble(this, DataType.RESULT, "RealTime/CurrentAngleEncoderDrift", currentDrift);
TestDashboard.putDouble(this, DataType.RESULT, "Summary/TotalDrift", totalDrift);
TestDashboard.putString(this, DataType.RESULT, "Status/Message", "Complete");
```

**LedStateTestCommand - Results:**
```java
// Before:
private static final String RESULT_PREFIX = "DiagnosticTests/LED State Test/Results/";
SmartDashboard.putString(RESULT_PREFIX + "Status", "Running");
SmartDashboard.putNumber(RESULT_PREFIX + "ElapsedTime", actualDuration);
SmartDashboard.putNumber(RESULT_PREFIX + "ActualDuration", actualDuration);

// After (Option 1 - Separate Methods):
// RESULT_PREFIX constant removed!
TestDashboard.putResultString(this, "Status", "Running");
TestDashboard.putResultDouble(this, "ElapsedTime", actualDuration);
TestDashboard.putResultDouble(this, "ActualDuration", actualDuration);
```

### Method Naming Symmetry

The design provides **symmetric naming** for clarity:

**Parameters (Inputs - Read/Write):**
- `putParamInt()`, `getParamInt()`
- `putParamDouble()`, `getParamDouble()`
- `putParamBoolean()`, `getParamBoolean()`
- `putParamString()`, `getParamString()`
- `putParamChooser()`, `getParamChooser()`, `getParamChooserSelected()`

**Results (Outputs - Write-Only):**
- `putResultInt()`
- `putResultDouble()`
- `putResultBoolean()`
- `putResultString()`

**Benefits of Symmetric Naming:**
- ✅ Clear distinction between parameters and results
- ✅ Consistent naming pattern (`putParam*` vs `putResult*`)
- ✅ Self-documenting code - intent is clear at call site
- ✅ Better IDE autocomplete - separate namespaces for parameters vs results

### Benefits of Results Support

1. ✅ **Eliminates RESULT_PREFIX constant** - same benefit as parameters
2. ✅ **Consistent API** - same pattern for parameters and results
3. ✅ **Automatic prefix construction** - uses test name automatically
4. ✅ **Supports result groups** - can use paths like "Config/Module", "Summary/TotalDrift"
5. ✅ **Type-safe** - `putResultInt()` vs `putResultDouble()` makes intent clear
6. ✅ **Reduced boilerplate** - eliminates another constant definition

### Complete Migration Example

**SwerveAngleDriftTestCommand - Full Migration:**

**Before:**
```java
private static final String PARAM_PREFIX = "DiagnosticTests/Swerve Angle Drift Test/Parameters/";
private static final String RESULT_PREFIX = "DiagnosticTests/Swerve Angle Drift Test/Results/";

@Override
public void initializeParameters() {
    SmartDashboard.putNumber(PARAM_PREFIX + "ModuleNumber", 0);
    // ...
}

@Override
public void initialize() {
    moduleNumber = (int) SmartDashboard.getNumber(PARAM_PREFIX + "ModuleNumber", 0);
    // ...
    SmartDashboard.putNumber(RESULT_PREFIX + "Config/Module", moduleNumber);
    // ...
}
```

**After:**
```java
// NO PREFIX CONSTANTS NEEDED!

@Override
public void initializeParameters() {
    TestDashboard.putParamInt(this, "ModuleNumber", 0);
    // ...
}

@Override
public void initialize() {
    moduleNumber = TestDashboard.getParamInt(this, "ModuleNumber", 0);
    // ...
    TestDashboard.putResultInt(this, "Config/Module", moduleNumber);
    // ...
}
```

**Total Elimination:**
- ✅ Removed `PARAM_PREFIX` constant
- ✅ Removed `RESULT_PREFIX` constant
- ✅ Eliminated all type casts
- ✅ Cleaner, more maintainable code

## Conclusion

**TestDashboard would provide significant value**, especially for:

1. **Tests with many parameters** (like SwerveAngleDriftTestCommand with 6 parameters)
   - Eliminates PARAM_PREFIX constant
   - Eliminates type casting
   - Improves readability
   - Reduces boilerplate

2. **Tests with complex parameter types** (like LedStateTestCommand with SendableChooser)
   - Dramatic code reduction (~76%)
   - Simplifies error handling
   - Consistent patterns

3. **Tests with extensive results** (like SwerveAngleDriftTestCommand with organized result groups)
   - Eliminates RESULT_PREFIX constant
   - Consistent result publishing API
   - Supports organized result groups

4. **Long-term maintainability**
   - Easier to add new parameters and results
   - Consistent patterns across all tests
   - Foundation for future enhancements
   - Automatic prefix consistency for both parameters and results

**Recommendation**: Implement `TestDashboard` with **both parameter and result support** as a **high-priority enhancement**. The benefits are clear, the implementation is straightforward, and it will improve developer experience for all future tests.

**Recommended Design:**
1. **Class Name**: `TestDashboard` (simple, concise, clearly describes purpose)
2. **Primary API**: Separate methods with symmetric naming (`putParamInt()`, `putResultInt()`, etc.)
3. **Optional API**: Unified methods with `DataType` enum for flexibility
4. **Prefix Construction**: Automatic from test instance using `getTestName()`

The recommended design completely eliminates the need for both `PARAM_PREFIX` and `RESULT_PREFIX` constants while providing:
- ✅ Type-safe, clean API
- ✅ Symmetric naming for parameters and results
- ✅ Automatic prefix construction
- ✅ Both explicit (separate methods) and flexible (unified methods) options
- ✅ Simple and intuitive to use

The minor verbosity of passing `this` is far outweighed by the benefits of eliminating prefix management entirely.
