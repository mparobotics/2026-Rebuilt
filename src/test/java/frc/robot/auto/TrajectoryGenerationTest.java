package frc.robot.auto;

import static org.junit.jupiter.api.Assertions.*;

import com.pathplanner.lib.config.ModuleConfig;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.path.GoalEndState;
import com.pathplanner.lib.path.PathConstraints;
import com.pathplanner.lib.path.PathPlannerPath;
import com.pathplanner.lib.path.PathPoint;
import com.pathplanner.lib.path.Waypoint;
import com.pathplanner.lib.trajectory.PathPlannerTrajectory;
import com.pathplanner.lib.trajectory.PathPlannerTrajectoryState;

import edu.wpi.first.hal.HAL;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.system.plant.DCMotor;

import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.SwerveConstants;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests that PathPlanner trajectory generation produces valid, non-degenerate
 * trajectories for our robot configuration.
 *
 * <p>All test paths are constructed programmatically so they are immune to
 * changes in the team's actual auto path files (8FuelPath, DriveTestPath, etc.).
 *
 * <p>Motivation: In simulation, FollowPathCommand finished after one execute()
 * cycle because the generated trajectory had totalTime=0.0. The root cause was
 * that ModuleConfig.maxDriveVelocityMPS was set to the software speed limit
 * (3.0 m/s) instead of the motor's physical free speed (~5.35 m/s), causing
 * PathPlanner to compute zero available torque for acceleration.
 *
 * <p>See docs/auto-sim-log-analysis.md (Section 6) on the jmm-auto-mode-debug
 * branch for the full root cause analysis.
 *
 * <p>Set {@link #DEBUG} to {@code true} for verbose output during development.
 */
class TrajectoryGenerationTest {

    /** Set to true to enable verbose output (trajectory states, config values, etc.) */
    private static final boolean DEBUG = false;

    // Standard constraints used by most tests — similar to our real path constraints
    private static final PathConstraints STANDARD_CONSTRAINTS =
        new PathConstraints(3.0, 3.0, 2 * Math.PI, 4 * Math.PI);

    @BeforeAll
    static void initHAL() {
        // HAL must be initialized for PathPlannerPath internals to work.
        HAL.initialize(500, 0);
    }

    // =========================================================================
    // Test path factories — programmatic paths independent of path files
    // =========================================================================

    /**
     * A simple 2-meter straight line from (1,1) to (3,1), heading east.
     * The simplest possible non-trivial path.
     */
    private static PathPlannerPath createStraightLinePath() {
        List<Waypoint> waypoints = PathPlannerPath.waypointsFromPoses(
            new Pose2d(1.0, 1.0, Rotation2d.fromDegrees(0)),
            new Pose2d(3.0, 1.0, Rotation2d.fromDegrees(0))
        );
        PathPlannerPath path = new PathPlannerPath(
            waypoints, STANDARD_CONSTRAINTS, null,
            new GoalEndState(0.0, Rotation2d.fromDegrees(0)));
        path.preventFlipping = true;
        return path;
    }

    /**
     * An S-curve with 3 waypoints: starts at (1,1) heading up-right, curves
     * through (3,3) heading right, then curves back down to (5,1) heading
     * down-right. Total distance ~5.7m. Tests curved trajectory generation.
     */
    private static PathPlannerPath createSCurvePath() {
        List<Waypoint> waypoints = PathPlannerPath.waypointsFromPoses(
            new Pose2d(1.0, 1.0, Rotation2d.fromDegrees(45)),    // start heading up-right
            new Pose2d(3.0, 3.0, Rotation2d.fromDegrees(0)),     // midpoint heading right
            new Pose2d(5.0, 1.0, Rotation2d.fromDegrees(-45))    // end heading down-right
        );
        PathPlannerPath path = new PathPlannerPath(
            waypoints, STANDARD_CONSTRAINTS, null,
            new GoalEndState(0.0, Rotation2d.fromDegrees(-45)));
        path.preventFlipping = true;
        return path;
    }

    // =========================================================================
    // Debug helpers — output is suppressed unless DEBUG = true
    // =========================================================================

    private void debugPrint(String msg) {
        if (DEBUG) System.out.println(msg);
    }

    private void debugPrintf(String fmt, Object... args) {
        if (DEBUG) System.out.printf(fmt, args);
    }

    private void dumpTrajectory(String label, PathPlannerTrajectory traj) {
        if (!DEBUG) return;
        var states = traj.getStates();
        System.out.println("\n=== " + label + " ===");
        System.out.printf("  totalTime = %.6f s%n", traj.getTotalTimeSeconds());
        System.out.printf("  stateCount = %d%n", states.size());
        System.out.printf("  isFinite(totalTime) = %b%n", Double.isFinite(traj.getTotalTimeSeconds()));
        for (int i = 0; i < states.size(); i++) {
            PathPlannerTrajectoryState s = states.get(i);
            System.out.printf("  state[%2d] t=%8.4f  pose=(%7.3f, %7.3f, %7.2f°)  vel=%7.3f m/s  heading=%7.2f°  fieldSpeeds=(vx=%.3f, vy=%.3f, omega=%.3f)%n",
                i, s.timeSeconds,
                s.pose.getX(), s.pose.getY(), s.pose.getRotation().getDegrees(),
                s.linearVelocity,
                s.heading.getDegrees(),
                s.fieldSpeeds.vxMetersPerSecond, s.fieldSpeeds.vyMetersPerSecond,
                s.fieldSpeeds.omegaRadiansPerSecond);
        }
    }

    private void dumpPathPoints(String label, PathPlannerPath path) {
        if (!DEBUG) return;
        List<PathPoint> points = path.getAllPathPoints();
        System.out.println("\n--- " + label + " path points (" + points.size() + ") ---");
        for (int i = 0; i < points.size(); i++) {
            PathPoint p = points.get(i);
            System.out.printf("  point[%2d] pos=(%7.3f, %7.3f)  distAlongPath=%.4f  waypointRelPos=%.4f%n",
                i, p.position.getX(), p.position.getY(),
                p.distanceAlongPath, p.waypointRelativePos);
        }
    }

    private void dumpConfig(String label, RobotConfig config) {
        if (!DEBUG) return;
        System.out.println("\n--- " + label + " ---");
        System.out.printf("  massKG = %.2f%n", config.massKG);
        System.out.printf("  MOI = %.2f%n", config.MOI);
        System.out.printf("  numModules = %d%n", config.numModules);
        System.out.printf("  isHolonomic = %b%n", config.isHolonomic);
        System.out.printf("  wheelFrictionForce = %.4f N%n", config.wheelFrictionForce);
        System.out.printf("  maxTorqueFriction = %.4f Nm%n", config.maxTorqueFriction);
        for (int i = 0; i < config.numModules; i++) {
            System.out.printf("  moduleLocation[%d] = (%7.4f, %7.4f)  pivotDist=%.4f%n",
                i, config.moduleLocations[i].getX(), config.moduleLocations[i].getY(),
                config.modulePivotDistance[i]);
        }
        ModuleConfig mc = config.moduleConfig;
        System.out.printf("  ModuleConfig:%n");
        System.out.printf("    wheelRadiusMeters = %.4f%n", mc.wheelRadiusMeters);
        System.out.printf("    maxDriveVelocityMPS = %.3f%n", mc.maxDriveVelocityMPS);
        System.out.printf("    maxDriveVelocityRadPerSec = %.3f%n", mc.maxDriveVelocityRadPerSec);
        System.out.printf("    wheelCOF = %.3f%n", mc.wheelCOF);
        System.out.printf("    driveCurrentLimit = %.1f A%n", mc.driveCurrentLimit);
        System.out.printf("    torqueLoss = %.4f Nm%n", mc.torqueLoss);
        System.out.printf("    driveMotor stallTorque = %.3f Nm, freeSpeed = %.1f rad/s%n",
            mc.driveMotor.stallTorqueNewtonMeters, mc.driveMotor.freeSpeedRadPerSec);
    }

    // =========================================================================
    // Test: Robot configuration values are sane
    // =========================================================================
    @Test
    void testRobotConfigValues() {
        RobotConfig config = AutoConstants.ROBOT_CONFIG;
        dumpConfig("AutoConstants.ROBOT_CONFIG", config);

        assertTrue(config.massKG > 0, "Mass must be positive");
        assertTrue(config.MOI > 0, "MOI must be positive");
        assertEquals(4, config.numModules, "Should have 4 swerve modules");
        assertTrue(config.isHolonomic, "Swerve robot should be holonomic");
        assertTrue(config.wheelFrictionForce > 0, "Friction force must be positive");
        assertTrue(config.maxTorqueFriction > 0, "Max torque friction must be positive");

        ModuleConfig mc = config.moduleConfig;
        assertTrue(mc.wheelRadiusMeters > 0, "Wheel radius must be positive");
        assertTrue(mc.maxDriveVelocityMPS > 0, "Max velocity must be positive");
        assertTrue(mc.torqueLoss >= 0, "Torque loss must be non-negative");

        // Critical check: torqueLoss should be LESS than the stall torque, otherwise
        // the robot can never accelerate from rest
        double stallTorque = mc.driveMotor.stallTorqueNewtonMeters;
        debugPrintf("%n  CRITICAL CHECK: torqueLoss (%.4f) vs stallTorque (%.4f) => %s%n",
            mc.torqueLoss, stallTorque,
            mc.torqueLoss < stallTorque ? "OK — robot can accelerate" : "PROBLEM — torqueLoss >= stallTorque!");
        assertTrue(mc.torqueLoss < stallTorque,
            "torqueLoss must be less than stall torque, otherwise robot can never accelerate. "
            + "torqueLoss=" + mc.torqueLoss + " stallTorque=" + stallTorque);
    }

    // =========================================================================
    // Test: Straight line path generates a valid trajectory
    // =========================================================================
    @Test
    void testStraightLinePath() {
        PathPlannerPath path = createStraightLinePath();
        dumpPathPoints("Straight line (2m)", path);

        PathPlannerTrajectory traj = path.generateTrajectory(
            new ChassisSpeeds(), Rotation2d.kZero, AutoConstants.ROBOT_CONFIG);
        dumpTrajectory("Straight line (2m)", traj);

        assertTrue(traj.getTotalTimeSeconds() > 0,
            "Straight line trajectory should have non-zero totalTime. Got: "
            + traj.getTotalTimeSeconds());

        var first = traj.getInitialState();
        var last = traj.getEndState();
        double dist = first.pose.getTranslation().getDistance(last.pose.getTranslation());
        debugPrintf("  Start-to-end distance = %.4f m%n", dist);
        assertTrue(dist > 1.0, "Start and end should be at least 1.0m apart. Got: " + dist);
    }

    // =========================================================================
    // Test: S-curve path generates a valid trajectory
    // =========================================================================
    @Test
    void testSCurvePath() {
        PathPlannerPath path = createSCurvePath();
        dumpPathPoints("S-curve (~5.7m)", path);

        PathPlannerTrajectory traj = path.generateTrajectory(
            new ChassisSpeeds(), Rotation2d.fromDegrees(45), AutoConstants.ROBOT_CONFIG);
        dumpTrajectory("S-curve (~5.7m)", traj);

        assertTrue(traj.getTotalTimeSeconds() > 0,
            "S-curve trajectory should have non-zero totalTime. Got: "
            + traj.getTotalTimeSeconds());

        // Verify states span real distance
        var states = traj.getStates();
        double maxDist = 0;
        for (int i = 1; i < states.size(); i++) {
            double d = states.get(i).pose.getTranslation().getDistance(
                states.get(0).pose.getTranslation());
            maxDist = Math.max(maxDist, d);
        }
        debugPrintf("  Max distance from first state = %.4f m%n", maxDist);
        assertTrue(maxDist > 1.0, "States should span at least 1.0m. Got: " + maxDist);
    }

    // =========================================================================
    // Test: S-curve after flipPath (simulating red alliance)
    // =========================================================================
    @Test
    void testSCurvePath_flipped() {
        PathPlannerPath path = createSCurvePath();
        PathPlannerPath flipped = path.flipPath();
        dumpPathPoints("S-curve flipped", flipped);

        PathPlannerTrajectory traj = flipped.generateTrajectory(
            new ChassisSpeeds(), Rotation2d.fromDegrees(-45 + 180), AutoConstants.ROBOT_CONFIG);
        dumpTrajectory("S-curve flipped", traj);

        assertTrue(traj.getTotalTimeSeconds() > 0,
            "Flipped S-curve trajectory should have non-zero totalTime. Got: "
            + traj.getTotalTimeSeconds());
    }

    // =========================================================================
    // Test: S-curve after mirrorPath (simulating right-side auto)
    // =========================================================================
    @Test
    void testSCurvePath_mirrored() {
        PathPlannerPath path = createSCurvePath();
        PathPlannerPath mirrored = path.mirrorPath();
        dumpPathPoints("S-curve mirrored", mirrored);

        PathPlannerTrajectory traj = mirrored.generateTrajectory(
            new ChassisSpeeds(), Rotation2d.fromDegrees(-45), AutoConstants.ROBOT_CONFIG);
        dumpTrajectory("S-curve mirrored", traj);

        assertTrue(traj.getTotalTimeSeconds() > 0,
            "Mirrored S-curve trajectory should have non-zero totalTime. Got: "
            + traj.getTotalTimeSeconds());
    }

    // =========================================================================
    // Test: Prove the old config was broken and the current config is fixed
    //
    // OLD BUG: maxDriveVelocityMPS was set to 3.0 m/s (software speed limit).
    // At 3.0 m/s the motor draws 94.6A, but driveCurrentLimit = 40A.
    // Both torqueLoss and forward-pass torque clamp to 40A → same torque
    // → zero available torque → zero acceleration → totalTime = 0.
    //
    // FIX: maxDriveVelocityMPS is now the motor's theoretical free speed
    // (~5.35 m/s), where the motor draws only 3.6A — well below the 40A limit.
    // =========================================================================
    @Test
    void testRootCause_currentLimitCausesZeroAcceleration() {
        DCMotor motor = DCMotor.getNeoVortex(1).withReduction(SwerveConstants.driveGearRatio);
        double wheelRadius = SwerveConstants.wheelDiameter / 2.0;
        double currentLimit = SwerveConstants.driveContinuousCurrentLimit;  // 40A

        // --- Part 1: Prove the OLD config (maxSpeed = 3.0 m/s) was broken ---
        double oldMaxSpeed = 3.0;  // the old (buggy) value
        ModuleConfig oldMC = new ModuleConfig(
            wheelRadius, oldMaxSpeed, 1.2, motor, currentLimit, 1);

        double oldMaxSpeedRad = oldMaxSpeed / wheelRadius;
        double oldCurrentAtMaxSpeed = motor.getCurrent(oldMaxSpeedRad, 12.0);
        double oldClampedCurrent = Math.min(oldCurrentAtMaxSpeed, currentLimit);
        double oldTorqueLoss = Math.max(motor.getTorque(oldClampedCurrent), 0.0);

        double stallCurrent = motor.getCurrent(0, 12.0);
        double clampedStallCurrent = Math.min(stallCurrent, currentLimit);
        double stallTorque = motor.getTorque(clampedStallCurrent);
        double oldAvailableTorque = stallTorque - oldTorqueLoss;

        debugPrint("\n=== OLD CONFIG (BROKEN) — maxDriveVelocityMPS = 3.0 m/s ===");
        debugPrintf("  currentAtMaxSpeed = %.2f A  (clamped to %.0f A → %.2f A)%n",
            oldCurrentAtMaxSpeed, currentLimit, oldClampedCurrent);
        debugPrintf("  torqueLoss = %.6f Nm%n", oldTorqueLoss);
        debugPrintf("  stallCurrent = %.2f A  (clamped to %.0f A → %.2f A)%n",
            stallCurrent, currentLimit, clampedStallCurrent);
        debugPrintf("  stallTorque = %.6f Nm%n", stallTorque);
        debugPrintf("  availableTorque = %.6f − %.6f = %.6f Nm  ← ZERO!%n",
            stallTorque, oldTorqueLoss, oldAvailableTorque);

        // Confirm the bug: both currents clamp to 40A → same torque → zero available
        assertEquals(oldClampedCurrent, clampedStallCurrent, 0.001,
            "BUG CONFIRMED: with maxSpeed=3.0, both currents clamp to " + currentLimit + "A");
        assertEquals(0.0, oldAvailableTorque, 0.001,
            "BUG CONFIRMED: available torque should be zero with old config");

        // Generate trajectory with old config → totalTime should be 0
        PathPlannerPath path = createSCurvePath();
        RobotConfig oldConfig = new RobotConfig(52, 6.8, oldMC,
            SwerveConstants.FRONT_LEFT, SwerveConstants.FRONT_RIGHT,
            SwerveConstants.BACK_RIGHT, SwerveConstants.BACK_LEFT);
        PathPlannerTrajectory oldTraj = path.generateTrajectory(
            new ChassisSpeeds(), Rotation2d.fromDegrees(45), oldConfig);
        debugPrintf("  OLD trajectory totalTime = %.6f s (expected: 0.0)%n",
            oldTraj.getTotalTimeSeconds());
        assertEquals(0.0, oldTraj.getTotalTimeSeconds(), 0.001,
            "Old config should produce a zero-duration trajectory");

        // --- Part 2: Verify the CURRENT config (fix applied) works ---
        ModuleConfig fixedMC = AutoConstants.MODULE_CONFIG;
        double fixedMaxSpeedRad = fixedMC.maxDriveVelocityMPS / fixedMC.wheelRadiusMeters;
        double fixedCurrentAtMaxSpeed = motor.getCurrent(fixedMaxSpeedRad, 12.0);
        double fixedClampedCurrent = Math.min(fixedCurrentAtMaxSpeed, fixedMC.driveCurrentLimit);
        double fixedTorqueLoss = Math.max(motor.getTorque(fixedClampedCurrent), 0.0);
        double fixedAvailableTorque = stallTorque - fixedTorqueLoss;

        debugPrintf("%n=== FIXED CONFIG — maxDriveVelocityMPS = %.3f m/s ===\n",
            fixedMC.maxDriveVelocityMPS);
        debugPrintf("  currentAtMaxSpeed = %.2f A  (below %.0f A limit? %b)%n",
            fixedCurrentAtMaxSpeed, fixedMC.driveCurrentLimit,
            fixedCurrentAtMaxSpeed < fixedMC.driveCurrentLimit);
        debugPrintf("  torqueLoss = %.6f Nm%n", fixedTorqueLoss);
        debugPrintf("  availableTorque = %.6f − %.6f = %.6f Nm  ← NON-ZERO!%n",
            stallTorque, fixedTorqueLoss, fixedAvailableTorque);

        // Current at theoretical free speed should be well below the 40A limit
        assertTrue(fixedCurrentAtMaxSpeed < fixedMC.driveCurrentLimit,
            "FIXED: current at max speed (" + fixedCurrentAtMaxSpeed
            + "A) should be below limit (" + fixedMC.driveCurrentLimit + "A)");
        assertTrue(fixedAvailableTorque > 1.0,
            "FIXED: available torque should be substantial. Got: " + fixedAvailableTorque);

        // Generate trajectory with fixed config → should have real duration
        PathPlannerTrajectory fixedTraj = path.generateTrajectory(
            new ChassisSpeeds(), Rotation2d.fromDegrees(45), AutoConstants.ROBOT_CONFIG);
        dumpTrajectory("S-curve — FIXED CONFIG", fixedTraj);
        assertTrue(fixedTraj.getTotalTimeSeconds() > 0.5,
            "FIXED: trajectory should have meaningful totalTime. Got: "
            + fixedTraj.getTotalTimeSeconds());
    }

    // =========================================================================
    // Test: Verify path points have non-zero distanceAlongPath spread
    // =========================================================================
    @Test
    void testPathPointsAreDistinct() {
        debugPrint("\n=== Path point distance check ===");
        checkPathPointSpread("straight line", createStraightLinePath());
        checkPathPointSpread("S-curve", createSCurvePath());
        checkPathPointSpread("S-curve flipped", createSCurvePath().flipPath());
        checkPathPointSpread("S-curve mirrored", createSCurvePath().mirrorPath());
    }

    private void checkPathPointSpread(String label, PathPlannerPath path) {
        List<PathPoint> points = path.getAllPathPoints();
        debugPrintf("  %s: %d points%n", label, points.size());
        assertTrue(points.size() >= 2, label + ": Path should have at least 2 points");

        Translation2d first = points.get(0).position;
        Translation2d last = points.get(points.size() - 1).position;
        double dist = first.getDistance(last);
        debugPrintf("    first=(%7.3f, %7.3f)  last=(%7.3f, %7.3f)  dist=%.4f m%n",
            first.getX(), first.getY(), last.getX(), last.getY(), dist);
        assertTrue(dist > 0.5,
            label + ": First and last path points should be >0.5m apart. Got: " + dist);

        double totalDist = points.get(points.size() - 1).distanceAlongPath;
        debugPrintf("    totalDistanceAlongPath = %.4f m%n", totalDist);
        assertTrue(totalDist > 0.5,
            label + ": Total distance along path should be >0.5m. Got: " + totalDist);
    }
}
