package frc.robot.test;

import com.ctre.phoenix6.hardware.Pigeon2;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import frc.lib.test.DiagnosticTest;
import frc.lib.test.TestDashboard;
import frc.robot.Subsystems.SwerveSubsystem;

/**
 * Diagnostic test that demonstrates the odometry reset bug (issue 8.1/8.2)
 * and quantifies its impact on closed-loop path following.
 *
 * <p><b>Phase 1 — Reset & Observe:</b> Resets the robot pose to
 * {@code (targetX, targetY, targetHeading)} using the same buggy code as
 * {@code startAutoAt}, then observes whether the odometry heading is corrupted
 * on subsequent cycles due to pigeon setYaw latency and stale SimulationManager
 * pose.
 *
 * <p><b>Phase 2 — Closed-Loop L-Path:</b> Resets the robot pose to a
 * <em>different</em> heading ({@code driveHeading}) at the same position, then
 * drives an L-shaped path using a simple proportional controller. Because the
 * pigeon settled at {@code targetHeading} during Phase 1, the Phase 2 reset to
 * {@code driveHeading} triggers genuine pigeon latency (e.g., 90° → 0°),
 * causing heading corruption on the first drive cycle.
 *
 * <p><b>L-Path geometry (computed from driveHeading):</b>
 * <pre>
 *   Start → (targetX, targetY, driveHeading)
 *     │
 *     │ Leg 1: 1.0m "forward" (in direction of driveHeading)
 *     ▼
 *   WP1 → (same heading)
 *     │
 *     │ Leg 2: 1.0m "right" + 90° CW turn
 *     ▼
 *   WP2 → (driveHeading − 90°)
 * </pre>
 *
 * <p>With default parameters (target=90°, drive=0°, position 5.0, 4.0):
 * <pre>
 *   Phase 1: Reset to heading 90°, observe. Pigeon settles at 90°.
 *   Phase 2: Reset to heading 0° (pigeon: 90° → 0° = real latency)
 *
 *   Start (5.0, 4.0) heading 0° (+X)
 *     │
 *     │ Leg 1: drive +X to (6.0, 4.0)
 *     ▼
 *   WP1 (6.0, 4.0) heading 0°
 *     │
 *     │ Leg 2: turn to −90°, drive −Y to (6.0, 3.0)
 *     ▼
 *   WP2 (6.0, 3.0) heading −90°
 * </pre>
 *
 * <p><b>Multi-trial:</b> The entire Phase 1 + Phase 2 sequence repeats for
 * {@code NumberOfTrials} trials (default 10). After each drive phase, the pigeon
 * is at {@code driveHeading}, so the next trial's Phase 1 reset to
 * {@code targetHeading} is a genuine heading change with real pigeon latency.
 * A summary table at the end shows per-trial corruption and deviation statistics.
 *
 * @see <a href="docs/auto-sim-log-analysis.md">Section 8.1 and 8.2</a>
 */
public class OdometryResetTestCommand extends Command implements DiagnosticTest {

    // ============================================================================
    // Phase Management
    // ============================================================================

    private enum Phase {
        OBSERVING,           // Post-reset heading observation (N cycles)
        DRIVING_LEG1,        // Drive to waypoint 1 (forward)
        DRIVING_LEG2,        // Drive to waypoint 2 (turn + forward)
        STOPPING,            // Stop the robot
        COMPLETE
    }

    // ============================================================================
    // Tuning Constants
    // ============================================================================

    // Closed-loop proportional control
    private static final double DRIVE_KP = 2.0;        // position P gain (m/s per m error)
    private static final double HEADING_KP = 3.0;      // heading P gain (rad/s per rad error)
    private static final double MAX_DRIVE_SPEED = 1.0;  // m/s speed cap
    private static final double MAX_ROT_SPEED = 2.0;    // rad/s rotation cap

    // Waypoint arrival thresholds
    private static final double POSITION_TOLERANCE = 0.05;    // meters
    private static final double HEADING_TOLERANCE_DEG = 3.0;  // degrees
    private static final double SETTLE_SPEED = 0.05;          // m/s — robot must be nearly stopped

    // Timeouts and limits
    private static final double LEG_TIMEOUT_SEC = 5.0;
    private static final double LEG_LENGTH = 1.0;             // meters per leg
    private static final int MAX_OBSERVATION_CYCLES = 20;
    private static final double HEADING_CORRUPTION_THRESHOLD = 5.0; // degrees

    // Drive sample storage
    private static final int MAX_DRIVE_SAMPLES = 600;

    // Console output: print every Nth drive cycle (plus first, last, and anomalies)
    private static final int DRIVE_PRINT_INTERVAL = 10;
    private static final double DEVIATION_ALERT_THRESHOLD = 0.1; // meters

    // ============================================================================
    // Instance Fields
    // ============================================================================

    private final SwerveSubsystem swerveSubsystem;

    // Test parameters (read from SmartDashboard in initialize())
    private double targetX;
    private double targetY;
    private double targetHeadingDeg;
    private double driveHeadingDeg;
    private int observationCycles;
    private boolean includeDrivePhase;

    // Phase management
    private Phase currentPhase;
    private double legStartTime;

    // Waypoints (computed from drive heading)
    private Pose2d startPose;   // Phase 1 reset pose (targetHeading)
    private Pose2d drivePose;   // Phase 2 reset pose (driveHeading)
    private Pose2d wp1;
    private Pose2d wp2;

    // --- Phase 1: Reset observation data ---
    private int obsCycleCount;
    private double pigeonYawBeforeReset;
    private double odometryHeadingBeforeReset;
    private double pigeonYawAfterReset;
    private double odometryHeadingAfterReset;
    private double[] obsPigeonYaw;
    private double[] obsOdoHeading;
    private double[] obsOdoX;
    private double[] obsOdoY;

    // --- Phase 2: Drive sample data ---
    private int driveSampleCount;
    private int[] driveLeg;           // 1 = leg1, 2 = leg2
    private double[] driveTime;       // elapsed time since drive start
    private double[] drivePoseX;
    private double[] drivePoseY;
    private double[] drivePoseHeading;
    private double[] drivePigeonYaw;
    private double[] driveCmdVx;      // commanded field-relative vX
    private double[] driveCmdVy;      // commanded field-relative vY
    private double[] driveCmdOmega;   // commanded omega
    private double[] driveLateralDev; // perpendicular distance from ideal path

    // Drive phase tracking
    private double driveStartTime;
    private boolean leg1Complete;
    private double leg1FinalX, leg1FinalY, leg1FinalHeading;
    private double leg1MaxDev;
    private int leg1Cycles;

    // Phase 2 reset tracking (reset from targetHeading → driveHeading)
    private double phase2PigeonYawBeforeReset;
    private double phase2OdoHeadingBeforeReset;
    private double phase2PigeonYawAfterReset;
    private double phase2OdoHeadingAfterReset;

    // --- Multi-trial tracking ---
    private int numberOfTrials;
    private int currentTrial;            // 0-based
    private int[] trialCorruptedCycles;
    private double[] trialMaxHeadingError;
    private boolean[] trialPigeonHadLatency;
    private double[] trialMaxLateralDev;
    private double[] trialFinalPosError;
    private double[] trialFinalHeadingError;

    // ============================================================================
    // Constructor
    // ============================================================================

    public OdometryResetTestCommand(SwerveSubsystem swerveSubsystem) {
        this.swerveSubsystem = swerveSubsystem;
        addRequirements(swerveSubsystem);
    }

    // ============================================================================
    // DiagnosticTest Interface
    // ============================================================================

    @Override
    public String getTestName() {
        return "Odometry Reset Test";
    }

    @Override
    public String getTestDescription() {
        return "Multi-trial test: Phase 1 resets to TargetHeading and observes heading corruption. "
             + "Phase 2 resets to DriveHeading and drives a closed-loop L-path. "
             + "Repeats for NumberOfTrials trials to measure consistency.";
    }

    @Override
    public void initializeParameters() {
        TestDashboard.putParamDouble(this, "TargetX", 5.0);
        TestDashboard.putParamDouble(this, "TargetY", 4.0);
        TestDashboard.putParamDouble(this, "TargetHeading", 90.0);
        TestDashboard.putParamDouble(this, "DriveHeading", 0.0);
        TestDashboard.putParamInt(this, "ObservationCycles", 10);
        TestDashboard.putParamBoolean(this, "IncludeDrivePhase", true);
        TestDashboard.putParamInt(this, "NumberOfTrials", 10);
    }

    // ============================================================================
    // Command Lifecycle
    // ============================================================================

    @Override
    public void initialize() {
        // Read parameters
        targetX = TestDashboard.getParamDouble(this, "TargetX", 5.0);
        targetY = TestDashboard.getParamDouble(this, "TargetY", 4.0);
        targetHeadingDeg = TestDashboard.getParamDouble(this, "TargetHeading", 90.0);
        driveHeadingDeg = TestDashboard.getParamDouble(this, "DriveHeading", 0.0);
        observationCycles = Math.min(
            TestDashboard.getParamInt(this, "ObservationCycles", 10),
            MAX_OBSERVATION_CYCLES);
        includeDrivePhase = TestDashboard.getParamBoolean(this, "IncludeDrivePhase", true);
        numberOfTrials = Math.max(1, TestDashboard.getParamInt(this, "NumberOfTrials", 10));

        // Compute waypoints and poses (same for all trials)
        computeWaypoints();
        startPose = new Pose2d(targetX, targetY, Rotation2d.fromDegrees(targetHeadingDeg));
        drivePose = new Pose2d(targetX, targetY, Rotation2d.fromDegrees(driveHeadingDeg));

        // Allocate per-trial result arrays
        trialCorruptedCycles = new int[numberOfTrials];
        trialMaxHeadingError = new double[numberOfTrials];
        trialPigeonHadLatency = new boolean[numberOfTrials];
        trialMaxLateralDev = new double[numberOfTrials];
        trialFinalPosError = new double[numberOfTrials];
        trialFinalHeadingError = new double[numberOfTrials];

        // Print header
        System.out.println(String.format("=== Odometry Reset Test Started (%d trials) ===", numberOfTrials));
        System.out.println(String.format("Phase 1 (observe) heading: %.2f°", targetHeadingDeg));
        System.out.println(String.format("Phase 2 (drive)   heading: %.2f°  (delta = %.2f°)",
            driveHeadingDeg, normalizeHeadingError(targetHeadingDeg, driveHeadingDeg)));
        System.out.println(String.format("Start position: (%.2f, %.2f)", targetX, targetY));
        System.out.println(String.format("WP1: (%.2f, %.2f, %.2f°)", wp1.getX(), wp1.getY(), wp1.getRotation().getDegrees()));
        System.out.println(String.format("WP2: (%.2f, %.2f, %.2f°)", wp2.getX(), wp2.getY(), wp2.getRotation().getDegrees()));
        System.out.println(String.format("Drive phase: %s  |  Observation cycles: %d",
            includeDrivePhase ? "ENABLED" : "DISABLED", observationCycles));

        // Dashboard config
        TestDashboard.putResultDouble(this, "Config/TargetX", targetX);
        TestDashboard.putResultDouble(this, "Config/TargetY", targetY);
        TestDashboard.putResultDouble(this, "Config/TargetHeading", targetHeadingDeg);
        TestDashboard.putResultDouble(this, "Config/DriveHeading", driveHeadingDeg);
        TestDashboard.putResultInt(this, "Config/NumberOfTrials", numberOfTrials);

        // Start first trial
        currentTrial = 0;
        startTrial();
    }

    /**
     * Initializes (or re-initializes) state for a new trial and performs the
     * Phase 1 reset. Called once from {@code initialize()} and again from
     * {@code STOPPING} for subsequent trials.
     */
    private void startTrial() {
        System.out.println(String.format("\n========== Trial %d/%d ==========", currentTrial + 1, numberOfTrials));

        // Reset per-trial observation state
        obsCycleCount = 0;
        obsPigeonYaw = new double[observationCycles];
        obsOdoHeading = new double[observationCycles];
        obsOdoX = new double[observationCycles];
        obsOdoY = new double[observationCycles];

        // Reset per-trial drive state
        driveSampleCount = 0;
        driveLeg = new int[MAX_DRIVE_SAMPLES];
        driveTime = new double[MAX_DRIVE_SAMPLES];
        drivePoseX = new double[MAX_DRIVE_SAMPLES];
        drivePoseY = new double[MAX_DRIVE_SAMPLES];
        drivePoseHeading = new double[MAX_DRIVE_SAMPLES];
        drivePigeonYaw = new double[MAX_DRIVE_SAMPLES];
        driveCmdVx = new double[MAX_DRIVE_SAMPLES];
        driveCmdVy = new double[MAX_DRIVE_SAMPLES];
        driveCmdOmega = new double[MAX_DRIVE_SAMPLES];
        driveLateralDev = new double[MAX_DRIVE_SAMPLES];

        leg1Complete = false;
        leg1MaxDev = 0;
        leg1Cycles = 0;

        // Record state BEFORE reset
        pigeonYawBeforeReset = swerveSubsystem.getPigeon().getYaw().getValueAsDouble();
        odometryHeadingBeforeReset = swerveSubsystem.getPose().getRotation().getDegrees();

        // ---- PERFORM THE PHASE 1 RESET (same buggy code as startAutoAt) ----
        Pigeon2 pigeon = swerveSubsystem.getPigeon();
        SwerveDrivePoseEstimator odometry = swerveSubsystem.getOdometry();
        SwerveModulePosition[] positions = swerveSubsystem.getPositions();

        pigeon.setYaw(startPose.getRotation().getDegrees());
        odometry.resetPosition(startPose.getRotation(), positions, startPose);
        // ---- END BUGGY CODE ----

        // Record state after reset
        pigeonYawAfterReset = pigeon.getYaw().getValueAsDouble();
        odometryHeadingAfterReset = swerveSubsystem.getPose().getRotation().getDegrees();

        System.out.println(String.format("Phase 1: Reset to %.2f° — pigeon before=%.2f° after=%.2f°  odo before=%.2f° after=%.2f°",
            targetHeadingDeg, pigeonYawBeforeReset, pigeonYawAfterReset,
            odometryHeadingBeforeReset, odometryHeadingAfterReset));

        currentPhase = Phase.OBSERVING;
        TestDashboard.putResultString(this, "Status/Message",
            String.format("Trial %d/%d — Observing...", currentTrial + 1, numberOfTrials));
    }

    @Override
    public void execute() {
        switch (currentPhase) {
            case OBSERVING:
                executeObserving();
                break;
            case DRIVING_LEG1:
                executeDriveLeg(1, wp1);
                break;
            case DRIVING_LEG2:
                executeDriveLeg(2, wp2);
                break;
            case STOPPING:
                swerveSubsystem.drive(0, 0, 0, false);
                recordTrialResults();
                currentTrial++;
                if (currentTrial < numberOfTrials) {
                    startTrial();
                } else {
                    currentPhase = Phase.COMPLETE;
                }
                break;
            case COMPLETE:
                break;
        }
    }

    @Override
    public boolean isFinished() {
        return currentPhase == Phase.COMPLETE;
    }

    @Override
    public void end(boolean interrupted) {
        // Always stop the robot
        swerveSubsystem.drive(0, 0, 0, false);

        int completedTrials = currentTrial;

        if (interrupted) {
            System.out.println(String.format(
                "\n=== Odometry Reset Test INTERRUPTED (completed %d/%d trials) ===",
                completedTrials, numberOfTrials));
            TestDashboard.putResultString(this, "Status/Message", "Interrupted");
        }

        // Print summary table if any trials completed
        if (completedTrials > 0) {
            printMultiTrialSummary(completedTrials);
        }
    }

    // ============================================================================
    // Phase Execution Methods
    // ============================================================================

    private void executeObserving() {
        if (obsCycleCount >= observationCycles) {
            // Print observation summary before transitioning
            System.out.println("--- Observation complete ---");
            printObservationSummary();

            if (includeDrivePhase) {
                // The pigeon is now settled at targetHeadingDeg from Phase 1.
                // Phase 2 uses a DIFFERENT heading (driveHeadingDeg), so the
                // setYaw(driveHeading) call triggers real pigeon latency
                // (e.g., 90° → 0° transition), causing heading corruption on
                // the first drive cycle.
                System.out.println(String.format(
                    "\n--- Phase 2: Reset to driveHeading=%.2f° (pigeon is at ~%.2f°) ---",
                    driveHeadingDeg, targetHeadingDeg));
                performPoseReset();
                System.out.println(String.format(
                    "Phase 2 reset: pigeonYaw before=%.2f° after=%.2f°  odoHeading before=%.2f° after=%.2f°",
                    phase2PigeonYawBeforeReset, phase2PigeonYawAfterReset,
                    phase2OdoHeadingBeforeReset, phase2OdoHeadingAfterReset));

                boolean pigeonHasLatency = Math.abs(phase2PigeonYawAfterReset - driveHeadingDeg) > HEADING_CORRUPTION_THRESHOLD;
                System.out.println(String.format("  Pigeon latency: %s (pigeon=%.2f° vs target=%.2f°)",
                    pigeonHasLatency
                        ? "YES — pigeon has NOT caught up (corruption expected on drive cycle 1)"
                        : "NO — pigeon already updated (no corruption expected)",
                    phase2PigeonYawAfterReset, driveHeadingDeg));

                System.out.println("\n--- Starting closed-loop L-path drive ---");
                System.out.println(String.format("Leg 1: Drive to WP1 (%.2f, %.2f, %.2f°)",
                    wp1.getX(), wp1.getY(), wp1.getRotation().getDegrees()));
                currentPhase = Phase.DRIVING_LEG1;
                driveStartTime = Timer.getFPGATimestamp();
                legStartTime = driveStartTime;
                TestDashboard.putResultString(this, "Status/Message", "Driving Leg 1...");
            } else {
                // No drive phase — record results and advance to next trial
                recordTrialResults();
                currentTrial++;
                if (currentTrial < numberOfTrials) {
                    startTrial();
                } else {
                    currentPhase = Phase.COMPLETE;
                }
            }
            return;
        }

        // Record observation sample
        double pigeonYaw = swerveSubsystem.getPigeon().getYaw().getValueAsDouble();
        Pose2d odoPose = swerveSubsystem.getPose();
        double odoHeading = odoPose.getRotation().getDegrees();

        obsPigeonYaw[obsCycleCount] = pigeonYaw;
        obsOdoHeading[obsCycleCount] = odoHeading;
        obsOdoX[obsCycleCount] = odoPose.getX();
        obsOdoY[obsCycleCount] = odoPose.getY();

        double headingError = normalizeHeadingError(targetHeadingDeg, odoHeading);

        System.out.println(String.format(
            "  Cycle %2d: pigeonYaw=%7.2f°  odoHeading=%7.2f°  odoPos=(%.2f, %.2f)  headingError=%6.2f°%s",
            obsCycleCount + 1, pigeonYaw, odoHeading,
            odoPose.getX(), odoPose.getY(), headingError,
            headingError > HEADING_CORRUPTION_THRESHOLD ? "  *** HEADING CORRUPTED ***" : ""));

        obsCycleCount++;
    }

    /**
     * Performs the same buggy pose reset as startAutoAt(), but using the
     * drive heading (different from the observation heading). Since the pigeon
     * is still at targetHeadingDeg from Phase 1, this triggers real pigeon
     * latency during the setYaw() call.
     */
    private void performPoseReset() {
        Pigeon2 pigeon = swerveSubsystem.getPigeon();
        SwerveDrivePoseEstimator odometry = swerveSubsystem.getOdometry();
        SwerveModulePosition[] positions = swerveSubsystem.getPositions();

        // Record state before reset
        phase2PigeonYawBeforeReset = pigeon.getYaw().getValueAsDouble();
        phase2OdoHeadingBeforeReset = swerveSubsystem.getPose().getRotation().getDegrees();

        // ---- SAME BUGGY CODE AS startAutoAt(), using driveHeading ----
        pigeon.setYaw(drivePose.getRotation().getDegrees());
        odometry.resetPosition(drivePose.getRotation(), positions, drivePose);
        // ---- END BUGGY CODE ----

        // Record state after reset (same cycle)
        phase2PigeonYawAfterReset = pigeon.getYaw().getValueAsDouble();
        phase2OdoHeadingAfterReset = swerveSubsystem.getPose().getRotation().getDegrees();

        // Dashboard
        TestDashboard.putResultDouble(this, "Phase2Reset/PigeonBefore", phase2PigeonYawBeforeReset);
        TestDashboard.putResultDouble(this, "Phase2Reset/PigeonAfter", phase2PigeonYawAfterReset);
        TestDashboard.putResultDouble(this, "Phase2Reset/OdoHeadingBefore", phase2OdoHeadingBeforeReset);
        TestDashboard.putResultDouble(this, "Phase2Reset/OdoHeadingAfter", phase2OdoHeadingAfterReset);
    }

    private void executeDriveLeg(int legNumber, Pose2d waypoint) {
        double now = Timer.getFPGATimestamp();

        // Command drive toward waypoint
        double[] cmd = computeDriveCommand(waypoint);
        swerveSubsystem.drive(cmd[0], cmd[1], cmd[2], true);

        // Record sample
        recordDriveSample(legNumber, cmd, now);

        // Check arrival or timeout
        boolean arrived = isAtWaypoint(waypoint);
        boolean timedOut = (now - legStartTime) > LEG_TIMEOUT_SEC;

        if (arrived || timedOut) {
            if (timedOut && !arrived) {
                System.out.println(String.format("  *** Leg %d TIMED OUT after %.1f sec ***",
                    legNumber, LEG_TIMEOUT_SEC));
            }

            if (legNumber == 1) {
                // Record leg 1 final state
                Pose2d finalPose = swerveSubsystem.getPose();
                leg1Complete = true;
                leg1FinalX = finalPose.getX();
                leg1FinalY = finalPose.getY();
                leg1FinalHeading = finalPose.getRotation().getDegrees();
                leg1Cycles = countLegSamples(1);
                leg1MaxDev = maxLateralDeviation(1);

                System.out.println(String.format(
                    "  Leg 1 complete: pos=(%.3f, %.3f) heading=%.2f° maxDev=%.4f m  cycles=%d  %s",
                    leg1FinalX, leg1FinalY, leg1FinalHeading, leg1MaxDev,
                    leg1Cycles, arrived ? "ARRIVED" : "TIMEOUT"));

                // Transition to leg 2
                System.out.println(String.format("  Leg 2: Drive to WP2 (%.2f, %.2f, %.2f°)",
                    wp2.getX(), wp2.getY(), wp2.getRotation().getDegrees()));
                currentPhase = Phase.DRIVING_LEG2;
                legStartTime = now;
                TestDashboard.putResultString(this, "Status/Message", "Driving Leg 2...");
            } else {
                // Leg 2 complete — stop
                System.out.println(String.format(
                    "  Leg 2 complete: pos=(%.3f, %.3f) heading=%.2f°  %s",
                    swerveSubsystem.getPose().getX(),
                    swerveSubsystem.getPose().getY(),
                    swerveSubsystem.getPose().getRotation().getDegrees(),
                    arrived ? "ARRIVED" : "TIMEOUT"));
                currentPhase = Phase.STOPPING;
                TestDashboard.putResultString(this, "Status/Message", "Stopping...");
            }
        }
    }

    // ============================================================================
    // Per-Trial Result Recording
    // ============================================================================

    /**
     * Computes and stores summary results for the current trial, then prints
     * a one-line summary to the console.
     */
    private void recordTrialResults() {
        int t = currentTrial;

        // Phase 1 results
        int corrupted = 0;
        double maxError = 0;
        for (int i = 0; i < obsCycleCount; i++) {
            double error = normalizeHeadingError(targetHeadingDeg, obsOdoHeading[i]);
            if (error > maxError) maxError = error;
            if (error > HEADING_CORRUPTION_THRESHOLD) corrupted++;
        }
        trialCorruptedCycles[t] = corrupted;
        trialMaxHeadingError[t] = maxError;

        // Phase 2 results
        if (includeDrivePhase && driveSampleCount > 0) {
            trialPigeonHadLatency[t] = Math.abs(phase2PigeonYawAfterReset - driveHeadingDeg) > HEADING_CORRUPTION_THRESHOLD;
            trialMaxLateralDev[t] = Math.max(maxLateralDeviation(1), maxLateralDeviation(2));
            Pose2d finalPose = swerveSubsystem.getPose();
            trialFinalPosError[t] = finalPose.getTranslation().getDistance(wp2.getTranslation());
            trialFinalHeadingError[t] = normalizeHeadingError(
                wp2.getRotation().getDegrees(),
                finalPose.getRotation().getDegrees());
        }

        // One-line summary
        if (includeDrivePhase && driveSampleCount > 0) {
            System.out.println(String.format(
                "  ► Trial %d: corruption=%d/%d  pigeonLatency=%s  maxDev=%.4fm  posErr=%.4fm  hdgErr=%.2f°",
                currentTrial + 1, corrupted, obsCycleCount,
                trialPigeonHadLatency[t] ? "YES" : "NO",
                trialMaxLateralDev[t], trialFinalPosError[t], trialFinalHeadingError[t]));
        } else {
            System.out.println(String.format(
                "  ► Trial %d: corruption=%d/%d  maxHdgError=%.2f°",
                currentTrial + 1, corrupted, obsCycleCount, maxError));
        }
    }

    // ============================================================================
    // Drive Control
    // ============================================================================

    /**
     * Computes field-relative drive commands using proportional control.
     * Returns [vx, vy, omega] in field frame.
     */
    private double[] computeDriveCommand(Pose2d waypoint) {
        Pose2d current = swerveSubsystem.getPose();

        // Position error in field frame
        double xError = waypoint.getX() - current.getX();
        double yError = waypoint.getY() - current.getY();

        // Heading error (shortest path, in radians)
        double headingErrorRad = waypoint.getRotation().minus(current.getRotation()).getRadians();

        // Proportional control with speed limits
        double vx = clamp(DRIVE_KP * xError, -MAX_DRIVE_SPEED, MAX_DRIVE_SPEED);
        double vy = clamp(DRIVE_KP * yError, -MAX_DRIVE_SPEED, MAX_DRIVE_SPEED);
        double omega = clamp(HEADING_KP * headingErrorRad, -MAX_ROT_SPEED, MAX_ROT_SPEED);

        return new double[]{vx, vy, omega};
    }

    /**
     * Checks if the robot has arrived at the waypoint.
     */
    private boolean isAtWaypoint(Pose2d waypoint) {
        Pose2d current = swerveSubsystem.getPose();

        double posError = current.getTranslation().getDistance(waypoint.getTranslation());
        double headingError = normalizeHeadingError(
            waypoint.getRotation().getDegrees(),
            current.getRotation().getDegrees());

        // Also check that the robot is moving slowly (settled)
        ChassisSpeeds speeds = swerveSubsystem.getChassisSpeeds();
        double speed = Math.sqrt(
            speeds.vxMetersPerSecond * speeds.vxMetersPerSecond +
            speeds.vyMetersPerSecond * speeds.vyMetersPerSecond);

        return posError < POSITION_TOLERANCE
            && headingError < HEADING_TOLERANCE_DEG
            && speed < SETTLE_SPEED;
    }

    // ============================================================================
    // Data Recording
    // ============================================================================

    private void recordDriveSample(int legNumber, double[] cmd, double now) {
        if (driveSampleCount >= MAX_DRIVE_SAMPLES) return;

        Pose2d pose = swerveSubsystem.getPose();
        double pigeonYaw = swerveSubsystem.getPigeon().getYaw().getValueAsDouble();
        double lateralDev = computeLateralDeviation(legNumber, pose);

        int i = driveSampleCount;
        driveLeg[i] = legNumber;
        driveTime[i] = now - driveStartTime;
        drivePoseX[i] = pose.getX();
        drivePoseY[i] = pose.getY();
        drivePoseHeading[i] = pose.getRotation().getDegrees();
        drivePigeonYaw[i] = pigeonYaw;
        driveCmdVx[i] = cmd[0];
        driveCmdVy[i] = cmd[1];
        driveCmdOmega[i] = cmd[2];
        driveLateralDev[i] = lateralDev;

        // Console output: first cycle, every Nth cycle, anomalies
        int legCycle = countLegSamples(legNumber);
        boolean shouldPrint = (legCycle == 1)
            || (legCycle % DRIVE_PRINT_INTERVAL == 0)
            || (lateralDev > DEVIATION_ALERT_THRESHOLD);

        if (shouldPrint) {
            System.out.println(String.format(
                "  [L%d C%3d t=%.2fs] pos=(%.3f,%.3f) hdg=%.1f° pigeon=%.1f° cmd=(%.2f,%.2f,%.2f) dev=%.4fm%s",
                legNumber, legCycle, driveTime[i],
                drivePoseX[i], drivePoseY[i], drivePoseHeading[i], drivePigeonYaw[i],
                driveCmdVx[i], driveCmdVy[i], driveCmdOmega[i], lateralDev,
                lateralDev > DEVIATION_ALERT_THRESHOLD ? " *** HIGH DEVIATION ***" : ""));
        }

        driveSampleCount++;

        // Real-time dashboard
        TestDashboard.putResultDouble(this, "RealTime/PoseX", pose.getX());
        TestDashboard.putResultDouble(this, "RealTime/PoseY", pose.getY());
        TestDashboard.putResultDouble(this, "RealTime/Heading", pose.getRotation().getDegrees());
        TestDashboard.putResultDouble(this, "RealTime/LateralDev", lateralDev);
    }

    // ============================================================================
    // Waypoint Computation
    // ============================================================================

    /**
     * Computes the two waypoints for the L-shaped path based on the drive heading.
     * The L-path uses {@code driveHeadingDeg} (not {@code targetHeadingDeg}) so the
     * path geometry matches the heading the robot will be reset to for Phase 2.
     * <p>
     * WP1: LEG_LENGTH meters "forward" from start (in the direction of driveHeading).
     * WP2: LEG_LENGTH meters "right" from WP1 (90° CW turn).
     */
    private void computeWaypoints() {
        double headingRad = Math.toRadians(driveHeadingDeg);

        // Forward direction (direction robot will face for drive phase)
        double fwdX = Math.cos(headingRad);
        double fwdY = Math.sin(headingRad);

        // WP1: straight ahead from start
        wp1 = new Pose2d(
            targetX + LEG_LENGTH * fwdX,
            targetY + LEG_LENGTH * fwdY,
            Rotation2d.fromDegrees(driveHeadingDeg));

        // Right direction (90° CW from heading)
        // cos(θ - 90°) = sin(θ),  sin(θ - 90°) = -cos(θ)
        double rightX = Math.sin(headingRad);
        double rightY = -Math.cos(headingRad);

        // WP2: right turn from WP1
        wp2 = new Pose2d(
            wp1.getX() + LEG_LENGTH * rightX,
            wp1.getY() + LEG_LENGTH * rightY,
            Rotation2d.fromDegrees(driveHeadingDeg - 90.0));
    }

    // ============================================================================
    // Lateral Deviation
    // ============================================================================

    /**
     * Computes perpendicular distance from the robot's current position to the
     * ideal straight-line path for the given leg.
     */
    private double computeLateralDeviation(int legNumber, Pose2d pose) {
        if (legNumber == 1) {
            // Ideal: straight line from (targetX, targetY) to WP1
            return pointToLineDistance(
                pose.getX(), pose.getY(),
                targetX, targetY, wp1.getX(), wp1.getY());
        } else {
            // Ideal: straight line from WP1 to WP2
            return pointToLineDistance(
                pose.getX(), pose.getY(),
                wp1.getX(), wp1.getY(), wp2.getX(), wp2.getY());
        }
    }

    /**
     * Perpendicular distance from point (px,py) to the line through (x1,y1)-(x2,y2).
     */
    private static double pointToLineDistance(
            double px, double py,
            double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) {
            return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }
        // |cross product| / length
        return Math.abs((py - y1) * dx - (px - x1) * dy) / len;
    }

    // ============================================================================
    // Reporting
    // ============================================================================

    private void printObservationSummary() {
        double maxError = 0;
        int corruptedCount = 0;
        for (int i = 0; i < obsCycleCount; i++) {
            double error = normalizeHeadingError(targetHeadingDeg, obsOdoHeading[i]);
            if (error > maxError) maxError = error;
            if (error > HEADING_CORRUPTION_THRESHOLD) corruptedCount++;
        }
        System.out.println(String.format("  Corrupted cycles: %d / %d  maxHeadingError: %.2f°",
            corruptedCount, obsCycleCount, maxError));
    }

    /**
     * Prints the summary table, statistics, and verdict for all completed trials.
     */
    private void printMultiTrialSummary(int completedTrials) {
        System.out.println(String.format(
            "\n=== MULTI-TRIAL SUMMARY (%d/%d trials completed) ===", completedTrials, numberOfTrials));

        // Table
        if (includeDrivePhase) {
            System.out.println("Trial | Corrupted | MaxHdgErr | PigeonLat | MaxLatDev | FinalPosErr | FinalHdgErr");
            System.out.println("------+-----------+-----------+-----------+-----------+-------------+------------");
            for (int t = 0; t < completedTrials; t++) {
                System.out.println(String.format(
                    "  %2d  |   %2d/%-2d   |  %6.2f°  |    %3s    | %7.4fm  |   %7.4fm   |   %6.2f°",
                    t + 1, trialCorruptedCycles[t], observationCycles,
                    trialMaxHeadingError[t],
                    trialPigeonHadLatency[t] ? "YES" : " NO",
                    trialMaxLateralDev[t], trialFinalPosError[t], trialFinalHeadingError[t]));
            }
        } else {
            System.out.println("Trial | Corrupted | MaxHdgErr");
            System.out.println("------+-----------+----------");
            for (int t = 0; t < completedTrials; t++) {
                System.out.println(String.format(
                    "  %2d  |   %2d/%-2d   |  %6.2f°",
                    t + 1, trialCorruptedCycles[t], observationCycles,
                    trialMaxHeadingError[t]));
            }
        }

        // Statistics
        int trialsWithCorruption = 0;
        int trialsWithLatency = 0;
        double totalCorrupted = 0;
        double maxMaxDev = 0;
        double totalMaxDev = 0;
        double maxPosErr = 0;

        for (int t = 0; t < completedTrials; t++) {
            if (trialCorruptedCycles[t] > 0) trialsWithCorruption++;
            totalCorrupted += trialCorruptedCycles[t];
            if (includeDrivePhase) {
                if (trialPigeonHadLatency[t]) trialsWithLatency++;
                if (trialMaxLateralDev[t] > maxMaxDev) maxMaxDev = trialMaxLateralDev[t];
                totalMaxDev += trialMaxLateralDev[t];
                if (trialFinalPosError[t] > maxPosErr) maxPosErr = trialFinalPosError[t];
            }
        }

        System.out.println("\n--- Statistics ---");
        System.out.println(String.format("  Trials with corruption:      %d/%d (%.0f%%)",
            trialsWithCorruption, completedTrials, 100.0 * trialsWithCorruption / completedTrials));
        System.out.println(String.format("  Average corrupted cycles:    %.1f / %d",
            totalCorrupted / completedTrials, observationCycles));

        if (includeDrivePhase) {
            System.out.println(String.format("  Trials with pigeon latency:  %d/%d (%.0f%%)",
                trialsWithLatency, completedTrials, 100.0 * trialsWithLatency / completedTrials));
            System.out.println(String.format("  Average max lateral dev:     %.4fm",
                totalMaxDev / completedTrials));
            System.out.println(String.format("  Worst max lateral dev:       %.4fm", maxMaxDev));
            System.out.println(String.format("  Worst final pos error:       %.4fm", maxPosErr));
        }

        // Verdict
        System.out.println("\n=== VERDICT ===");
        System.out.println(String.format("Phase 1 (Reset): %s — %d/%d trials showed heading corruption",
            trialsWithCorruption > 0 ? "BUG DETECTED" : "PASS",
            trialsWithCorruption, completedTrials));

        if (trialsWithCorruption > 0) {
            System.out.println("  → odometry.resetPosition() received the DESIRED heading as gyro baseline");
            System.out.println("    instead of the ACTUAL pigeon reading (which hasn't updated yet).");
        }

        if (includeDrivePhase) {
            boolean allPathsOk = true;
            for (int t = 0; t < completedTrials; t++) {
                if (trialMaxLateralDev[t] > 0.15 || trialFinalPosError[t] > 0.15) {
                    allPathsOk = false;
                    break;
                }
            }
            System.out.println(String.format("Phase 2 (Path):  %s — worst deviation=%.4fm, worst pos error=%.4fm",
                allPathsOk ? "PASS" : "DEVIATION DETECTED", maxMaxDev, maxPosErr));
        }

        System.out.println("===================================\n");

        // Dashboard
        TestDashboard.putResultInt(this, "Summary/CompletedTrials", completedTrials);
        TestDashboard.putResultInt(this, "Summary/TrialsWithCorruption", trialsWithCorruption);
        TestDashboard.putResultString(this, "Summary/ResetVerdict",
            trialsWithCorruption > 0 ? "BUG DETECTED" : "PASS");
        TestDashboard.putResultString(this, "Status/Message",
            trialsWithCorruption > 0 ? "BUG DETECTED — see console" : "PASS");
    }

    // ============================================================================
    // Utility Methods
    // ============================================================================

    /**
     * Returns the absolute heading error in [0, 180] degrees.
     */
    private static double normalizeHeadingError(double targetDeg, double actualDeg) {
        double error = Math.abs(targetDeg - actualDeg);
        if (error > 180) error = 360 - error;
        return error;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Counts drive samples for a specific leg.
     */
    private int countLegSamples(int legNumber) {
        int count = 0;
        for (int i = 0; i < driveSampleCount; i++) {
            if (driveLeg[i] == legNumber) count++;
        }
        return count;
    }

    /**
     * Finds the maximum lateral deviation for a specific leg.
     */
    private double maxLateralDeviation(int legNumber) {
        double max = 0;
        for (int i = 0; i < driveSampleCount; i++) {
            if (driveLeg[i] == legNumber && driveLateralDev[i] > max) {
                max = driveLateralDev[i];
            }
        }
        return max;
    }
}
