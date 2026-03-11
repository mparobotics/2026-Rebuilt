// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.Subsystems.IntakeSubsystem;


public class LeftNeutralZoneAuto extends SequentialCommandGroup {
  // Tune these values to change how far/fast the auto drives.
  private static final double INITIAL_BACKUP_DISTANCE_METERS = 3.6;
  // Positive value; the auto will negate it to drive backwards.
  private static final double INITIAL_BACKUP_SPEED_MPS = 3.0;
  private static final double FORWARD_DISTANCE_METERS = 4.0;
  private static final double FORWARD_SPEED_MPS = 3.0;

  // Intake: 1.0 = full power, 0.0 = off.
  private static final double INTAKE_POWER = 1.0;

  public LeftNeutralZoneAuto(SwerveSubsystem drive, IntakeSubsystem intake) {
    this(
      drive,
      intake,
      INITIAL_BACKUP_DISTANCE_METERS,
      INITIAL_BACKUP_SPEED_MPS,
      FORWARD_DISTANCE_METERS,
      FORWARD_SPEED_MPS,
      INTAKE_POWER);
  }

  public LeftNeutralZoneAuto(
    SwerveSubsystem drive,
    IntakeSubsystem intake,
    double driveDistanceMeters,
    double driveSpeedMps) {
    // Preserve the existing overload while letting you set the speed easily:
    // - driveDistanceMeters controls the initial backup distance
    // - driveSpeedMps is used for both the backup and forward drives
    this(drive, intake, driveDistanceMeters, driveSpeedMps, FORWARD_DISTANCE_METERS, driveSpeedMps, INTAKE_POWER);
  }

  public LeftNeutralZoneAuto(
    SwerveSubsystem drive,
    IntakeSubsystem intake,
    double backupDistanceMeters,
    double backupSpeedMps,
    double forwardDistanceMeters,
    double forwardSpeedMps,
    double intakePower) {
    addRequirements(drive, intake);
    final double backupSpeedMpsClamped =
      -MathUtil.clamp(Math.abs(backupSpeedMps), 0.0, SwerveConstants.maxSpeed);
    final double forwardSpeedMpsClamped =
      MathUtil.clamp(Math.abs(forwardSpeedMps), 0.0, SwerveConstants.maxSpeed);
    final double intakePowerClamped = MathUtil.clamp(intakePower, -1.0, 1.0);

    addCommands(
      new InstantCommand(intake::lowerIntake, intake),

      // 1) Drive backwards 3.6m.
      driveStraightDistanceMeters(drive, backupSpeedMpsClamped, backupDistanceMeters),

      // 2) Turn 90 degrees left.
      turnRelativeDegrees(drive, 90.0),

      // 3) Start intake while driving forward 4m.
      Commands.runOnce(() -> intake.setIntakePower(intakePowerClamped), intake),
      driveStraightDistanceMeters(drive, forwardSpeedMpsClamped, forwardDistanceMeters),

      // 4) Turn 180 degrees with intake still running.
      turnRelativeDegrees(drive, 180.0),

      // 5) Drive forward 4m again (intake still running).
      driveStraightDistanceMeters(drive, forwardSpeedMpsClamped, forwardDistanceMeters),

      Commands.runOnce(() -> intake.setIntakePower(0.0), intake)
    );
  }

  private static Command driveStraightDistanceMeters(SwerveSubsystem drive, double speedMps, double distanceMeters) {
    final Pose2d[] startPose = new Pose2d[1];
    final double timeoutSeconds =
      Math.abs(distanceMeters / Math.max(0.1, Math.abs(speedMps))) + 1.0;

    return Commands.sequence(
      Commands.runOnce(() -> startPose[0] = drive.getPose(), drive),
      Commands.runEnd(
        () -> drive.drive(speedMps, 0.0, 0.0, false),
        () -> drive.drive(0.0, 0.0, 0.0, false),
        drive)
        .until(() ->
          drive.getPose().getTranslation().getDistance(startPose[0].getTranslation()) >= distanceMeters)
        .withTimeout(timeoutSeconds)
    );
  }

  private static Command turnRelativeDegrees(SwerveSubsystem drive, double degrees) {
    final double[] startYawRad = new double[1];
    final double targetDeltaRad = Math.toRadians(degrees);

    return Commands.sequence(
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
      Commands.run(() -> {
        double targetYawRad = startYawRad[0] + targetDeltaRad;
        double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
        double omegaRadiansPerSecond =
          MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
        drive.drive(0.0, 0.0, omegaRadiansPerSecond, false);
      }, drive).until(() -> {
        double targetYawRad = startYawRad[0] + targetDeltaRad;
        double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
        return Math.abs(errorRad) < Math.toRadians(3.0);
      }),
      Commands.runOnce(() -> drive.drive(0.0, 0.0, 0.0, false), drive)
    );
  }
}
