package frc.lib;

import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import java.util.function.Function;

/**
 * Utility for creating pre-populated {@link SendableChooser} instances from enums.
 *
 * <p>Eliminates the boilerplate of iterating over enum constants, calling
 * {@code setDefaultOption}/{@code addOption}, and handling empty enums.
 *
 * <p><b>Usage examples:</b>
 * <pre>{@code
 * // Uses toString() for display names, first constant is default
 * SendableChooser<AutoMode> chooser = SendableChooserUtil.fromEnum(AutoMode.class);
 *
 * // Specific default value, toString() for display names
 * SendableChooser<AutoMode> chooser =
 *     SendableChooserUtil.fromEnum(AutoMode.class, AutoMode.DriveTestAuto);
 *
 * // Custom display name function with specific default
 * SendableChooser<DiagnosticTestRegistry> chooser =
 *     SendableChooserUtil.fromEnum(DiagnosticTestRegistry.class,
 *         DiagnosticTestRegistry.SWERVE_ANGLE_DRIFT,
 *         DiagnosticTestRegistry::getDisplayName);
 * }</pre>
 */
public final class SendableChooserUtil {

    private SendableChooserUtil() {} // Prevent instantiation

    /**
     * Creates a {@link SendableChooser} populated with all constants of the given enum.
     *
     * <p>The first enum constant (ordinal 0) is used as the default selection.
     * Display names are generated using each constant's {@code toString()} method.
     *
     * @param <E>       The enum type
     * @param enumClass The enum class to populate from
     * @return A new SendableChooser containing all enum constants
     */
    public static <E extends Enum<E>> SendableChooser<E> fromEnum(Class<E> enumClass) {
        E[] constants = enumClass.getEnumConstants();
        return fromEnum(enumClass, constants.length > 0 ? constants[0] : null);
    }

    /**
     * Creates a {@link SendableChooser} populated with all constants of the given enum,
     * with a specific default value.
     *
     * <p>Display names are generated using each constant's {@code toString()} method.
     *
     * @param <E>          The enum type
     * @param enumClass    The enum class to populate from
     * @param defaultValue The constant to mark as the default selection
     * @return A new SendableChooser containing all enum constants
     */
    public static <E extends Enum<E>> SendableChooser<E> fromEnum(
            Class<E> enumClass, E defaultValue) {
        return fromEnum(enumClass, defaultValue, Enum::toString);
    }

    /**
     * Creates a {@link SendableChooser} populated with all constants of the given enum,
     * with a specific default value and a custom function for generating display names.
     *
     * <p>This is the most flexible overload — use it when enum constants need custom
     * labels in the SmartDashboard dropdown (e.g., {@code getDisplayName()} instead of
     * {@code toString()}).
     *
     * @param <E>             The enum type
     * @param enumClass       The enum class to populate from
     * @param defaultValue    The constant to mark as the default selection
     * @param displayNameFunc Function that maps each enum constant to its display name
     * @return A new SendableChooser containing all enum constants
     */
    public static <E extends Enum<E>> SendableChooser<E> fromEnum(
            Class<E> enumClass, E defaultValue, Function<E, String> displayNameFunc) {
        SendableChooser<E> chooser = new SendableChooser<>();
        for (E constant : enumClass.getEnumConstants()) {
            String displayName = displayNameFunc.apply(constant);
            if (constant == defaultValue) {
                chooser.setDefaultOption(displayName, constant);
            } else {
                chooser.addOption(displayName, constant);
            }
        }
        return chooser;
    }
}
