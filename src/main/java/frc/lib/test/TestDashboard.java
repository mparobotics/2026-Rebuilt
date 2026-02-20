package frc.lib.test;

import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * Helper class for managing SmartDashboard interactions in diagnostic tests.
 * 
 * <p>This class eliminates the need for {@code PARAM_PREFIX} and {@code RESULT_PREFIX} constants
 * by automatically constructing prefixes from the test instance using {@link DiagnosticTest#getTestName()}.
 * 
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Automatic prefix construction: {@code "DiagnosticTests/" + testName + "/Parameters/"} or {@code "/Results/"}</li>
 *   <li>Type-safe methods: No manual type casting required</li>
 *   <li>Symmetric naming: Clear distinction between params (inputs) and results (outputs)</li>
 *   <li>Simplified SendableChooser handling: Built-in null checking and default values</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * public class MyTest extends Command implements DiagnosticTest {
 *     @Override
 *     public String getTestName() {
 *         return "My Test";
 *     }
 *     
 *     @Override
 *     public void initializeParameters() {
 *         // No PARAM_PREFIX constant needed!
 *         TestDashboard.putParamInt(this, "ModuleNumber", 0);
 *         TestDashboard.putParamDouble(this, "Angle", 90.0);
 *     }
 *     
 *     @Override
 *     public void initialize() {
 *         // Read parameters - prefix automatically constructed
 *         int module = TestDashboard.getParamInt(this, "ModuleNumber", 0);
 *         double angle = TestDashboard.getParamDouble(this, "Angle", 90.0);
 *     }
 *     
 *     @Override
 *     public void execute() {
 *         // Publish results - no RESULT_PREFIX constant needed!
 *         TestDashboard.putResultInt(this, "Config/Module", module);
 *         TestDashboard.putResultDouble(this, "Summary/TotalDrift", totalDrift);
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Prefix Format:</b>
 * <ul>
 *   <li>Params: {@code "DiagnosticTests/Test: " + testName + "/Parameters/" + paramName}</li>
 *   <li>Results: {@code "DiagnosticTests/Test: " + testName + "/Results/" + resultName}</li>
 * </ul>
 * 
 * <p>The "Test: " prefix groups all test-related parameter and result fields together in SmartDashboard.
 *
 * <p>Result names can include path separators (e.g., {@code "Config/Module"}) to organize results into groups.
 */
public class TestDashboard {
    
    private static final String BASE_PREFIX = "DiagnosticTests/";
    private static final String PARAMS_SUFFIX = "/Parameters/";
    private static final String RESULTS_SUFFIX = "/Results/";
    
    /**
     * Enum to distinguish between params (inputs) and results (outputs).
     * Used internally for prefix construction.
     */
    private enum DataType {
        PARAM,  // Input params (read/write)
        RESULT  // Output results (write-only)
    }
    
    /**
     * Constructs the prefix for a test based on the test name and data type.
     * 
     * <p>The test name is prefixed with "Test: " to group all test-related fields together
     * in SmartDashboard (e.g., "DiagnosticTests/Test: My Test/Parameters/").
     *
     * @param test The test instance (must not be null)
     * @param type Whether this is a PARAM or RESULT
     * @return The constructed prefix (e.g., "DiagnosticTests/Test: My Test/Parameters/")
     * @throws IllegalArgumentException if test is null
     * @throws IllegalStateException if test name is null or empty
     */
    private static String getPrefix(DiagnosticTest test, DataType type) {
        if (test == null) {
            throw new IllegalArgumentException("Test instance cannot be null");
        }
        String testName = test.getTestName();
        if (testName == null || testName.isEmpty()) {
            throw new IllegalStateException("Test name cannot be null or empty. Implement getTestName() properly.");
        }
        String suffix = (type == DataType.PARAM) ? PARAMS_SUFFIX : RESULTS_SUFFIX;
        return BASE_PREFIX + "Test: " + testName + suffix;
    }
    
    // ============================================================================
    // Param Methods (Inputs - Read/Write)
    // ============================================================================
    
    /**
     * Puts an integer param value to SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to set
     */
    public static void putParamInt(DiagnosticTest test, String paramName, int defaultValue) {
        SmartDashboard.putNumber(getPrefix(test, DataType.PARAM) + paramName, defaultValue);
    }
    
    /**
     * Gets an integer param value from SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to return if not found
     * @return The param value, or defaultValue if not found
     */
    public static int getParamInt(DiagnosticTest test, String paramName, int defaultValue) {
        return (int) SmartDashboard.getNumber(getPrefix(test, DataType.PARAM) + paramName, defaultValue);
    }
    
    /**
     * Puts a double param value to SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to set
     */
    public static void putParamDouble(DiagnosticTest test, String paramName, double defaultValue) {
        SmartDashboard.putNumber(getPrefix(test, DataType.PARAM) + paramName, defaultValue);
    }
    
    /**
     * Gets a double param value from SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to return if not found
     * @return The param value, or defaultValue if not found
     */
    public static double getParamDouble(DiagnosticTest test, String paramName, double defaultValue) {
        return SmartDashboard.getNumber(getPrefix(test, DataType.PARAM) + paramName, defaultValue);
    }
    
    /**
     * Puts a boolean param value to SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to set
     */
    public static void putParamBoolean(DiagnosticTest test, String paramName, boolean defaultValue) {
        SmartDashboard.putBoolean(getPrefix(test, DataType.PARAM) + paramName, defaultValue);
    }
    
    /**
     * Gets a boolean param value from SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to return if not found
     * @return The param value, or defaultValue if not found
     */
    public static boolean getParamBoolean(DiagnosticTest test, String paramName, boolean defaultValue) {
        return SmartDashboard.getBoolean(getPrefix(test, DataType.PARAM) + paramName, defaultValue);
    }
    
    /**
     * Puts a string param value to SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to set
     */
    public static void putParamString(DiagnosticTest test, String paramName, String defaultValue) {
        SmartDashboard.putString(getPrefix(test, DataType.PARAM) + paramName, defaultValue);
    }
    
    /**
     * Gets a string param value from SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to return if not found
     * @return The param value, or defaultValue if not found
     */
    public static String getParamString(DiagnosticTest test, String paramName, String defaultValue) {
        return SmartDashboard.getString(getPrefix(test, DataType.PARAM) + paramName, defaultValue);
    }
    
    /**
     * Puts a SendableChooser param to SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param chooser The SendableChooser to put (must not be null)
     */
    public static void putParamChooser(DiagnosticTest test, String paramName, SendableChooser<?> chooser) {
        SmartDashboard.putData(getPrefix(test, DataType.PARAM) + paramName, chooser);
    }
    
    /**
     * Gets a SendableChooser param from SmartDashboard.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @return The SendableChooser, or null if not found
     */
    @SuppressWarnings("unchecked")
    public static SendableChooser<String> getParamChooser(DiagnosticTest test, String paramName) {
        return (SendableChooser<String>) SmartDashboard.getData(getPrefix(test, DataType.PARAM) + paramName);
    }
    
    /**
     * Gets the selected value from a SendableChooser param.
     * Handles null checking and returns the default value if the chooser or selection is null.
     * 
     * @param test The test instance
     * @param paramName The name of the param
     * @param defaultValue The default value to return if chooser is null or no selection
     * @return The selected value, or defaultValue if not available
     */
    public static String getParamChooserSelected(DiagnosticTest test, String paramName, String defaultValue) {
        SendableChooser<String> chooser = getParamChooser(test, paramName);
        if (chooser != null && chooser.getSelected() != null) {
            return chooser.getSelected();
        }
        return defaultValue;
    }
    
    // ============================================================================
    // Result Methods (Outputs - Write-Only)
    // ============================================================================
    
    /**
     * Puts an integer result value to SmartDashboard.
     * 
     * @param test The test instance
     * @param resultName The name of the result (can include path separators, e.g., "Config/Module")
     * @param value The value to set
     */
    public static void putResultInt(DiagnosticTest test, String resultName, int value) {
        SmartDashboard.putNumber(getPrefix(test, DataType.RESULT) + resultName, value);
    }
    
    /**
     * Puts a double result value to SmartDashboard.
     * 
     * @param test The test instance
     * @param resultName The name of the result (can include path separators, e.g., "Summary/TotalDrift")
     * @param value The value to set
     */
    public static void putResultDouble(DiagnosticTest test, String resultName, double value) {
        SmartDashboard.putNumber(getPrefix(test, DataType.RESULT) + resultName, value);
    }
    
    /**
     * Puts a boolean result value to SmartDashboard.
     * 
     * @param test The test instance
     * @param resultName The name of the result (can include path separators)
     * @param value The value to set
     */
    public static void putResultBoolean(DiagnosticTest test, String resultName, boolean value) {
        SmartDashboard.putBoolean(getPrefix(test, DataType.RESULT) + resultName, value);
    }
    
    /**
     * Puts a string result value to SmartDashboard.
     * 
     * @param test The test instance
     * @param resultName The name of the result (can include path separators, e.g., "Status/Message")
     * @param value The value to set
     */
    public static void putResultString(DiagnosticTest test, String resultName, String value) {
        SmartDashboard.putString(getPrefix(test, DataType.RESULT) + resultName, value);
    }
}
