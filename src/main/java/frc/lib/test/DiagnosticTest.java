package frc.lib.test;

/**
 * Interface that defines a common contract for all diagnostic tests.
 * 
 * <p>Diagnostic tests should implement this interface in addition to extending {@link edu.wpi.first.wpilibj2.command.Command}.
 * This interface provides metadata and result reporting capabilities while allowing tests to remain Commands
 * for integration with WPILib's CommandScheduler.
 * 
 * <p>All methods except {@link #getTestName()} have default implementations, making it easy to create simple tests
 * that only need to provide a name. More complex tests can override the default methods to provide descriptions,
 * parameter initialization, and result reporting.
 * 
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * public class MyTest extends Command implements DiagnosticTest {
 *     @Override
 *     public String getTestName() {
 *         return "My Test";
 *     }
 *     
 *     @Override
 *     public void initializeParameters() {
 *         TestDashboard.putParamDouble(this, "Speed", 0.5);
 *         TestDashboard.putParamInt(this, "Cycles", 10);
 *     }
 *     
 *     @Override
 *     public void initialize() {
 *         double speed = TestDashboard.getParamDouble(this, "Speed", 0.5);
 *         int cycles = TestDashboard.getParamInt(this, "Cycles", 10);
 *     }
 * }
 * }</pre>
 */
public interface DiagnosticTest {
    
    /**
     * Gets the display name for this test.
     * This name will be shown in the SmartDashboard test selector dropdown.
     * 
     * @return The display name of the test
     */
    String getTestName();
    
    /**
     * Gets an optional description of what this test does.
     * Can be overridden to provide helpful information about the test's purpose.
     * 
     * @return A description of the test, or "No description available" if not overridden
     */
    default String getTestDescription() {
        return "No description available";
    }
    
    /**
     * Initializes SmartDashboard parameters for this test.
     * 
     * <p>This method is called by the framework when a test is selected in the dashboard,
     * allowing the test to set up its parameter UI before execution. Tests should use
     * {@link TestDashboard} to initialize parameters with default values.
     * 
     * <p>This method has a default empty implementation. Override it to set up parameters:
     * <pre>{@code
     * @Override
     * public void initializeParameters() {
     *     TestDashboard.putParamDouble(this, "Speed", 0.5);
     *     TestDashboard.putParamInt(this, "Cycles", 10);
     * }
     * }</pre>
     */
    default void initializeParameters() {
        // Override to set up SmartDashboard parameters
    }
    
    /**
     * Gets the result of this test after it has completed.
     * 
     * <p>This method is called by the framework after a test finishes (either normally or interrupted)
     * to retrieve the test results for display in SmartDashboard. Tests should create and store
     * a {@link DiagnosticTestResult} in their {@link edu.wpi.first.wpilibj2.command.Command#end(boolean) end()}
     * method and return it here.
     * 
     * <p>This method has a default implementation that returns null. Override it to return results:
     * <pre>{@code
     * private DiagnosticTestResult result;
     * 
     * @Override
     * public void end(boolean interrupted) {
     *     if (interrupted) {
     *         result = DiagnosticTestResult.cancelled("Test was interrupted");
     *     } else {
     *         Map<String, MetricValue> metrics = new HashMap<>();
     *         metrics.put("cycles", MetricValue.of(completedCycles));
     *         result = DiagnosticTestResult.passWithMetrics("Test completed", metrics);
     *     }
     * }
     * 
     * @Override
     * public DiagnosticTestResult getResult() {
     *     return result;
     * }
     * }</pre>
     * 
     * @return The test result, or null if the test hasn't completed yet or doesn't provide results
     */
    /*

    TO BE ADDED LATER

    default DiagnosticTestResult getResult() {
        return null; // Override to return test results
    }
    */
}
