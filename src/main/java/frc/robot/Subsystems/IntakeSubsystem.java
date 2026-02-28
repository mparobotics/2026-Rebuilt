// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.ArmFeedforward;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.IntakeConstants;

import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;

public class IntakeSubsystem extends SubsystemBase {

  private final SparkMax intakeMotor = new SparkMax(IntakeConstants.INTAKE_ID, MotorType.kBrushless);
  private final SparkMax intakeArmMotor = new SparkMax(IntakeConstants.INTAKE_ARM_ID, MotorType.kBrushless);

  private RelativeEncoder intakeArmEncoder = intakeArmMotor.getEncoder();

  private PIDController intakeArmPID = new PIDController(IntakeConstants.INTAKE_ARM_kP, IntakeConstants.INTAKE_ARM_kI, IntakeConstants.INTAKE_ARM_kD);

  private ArmFeedforward intakeArmFeedForward = new ArmFeedforward(0,0,0);

  public double targetPosition;

  private boolean intakeOn = false;
  private boolean intakeUp = true;

  private final TrapezoidProfile.Constraints armConstraints =
    new TrapezoidProfile.Constraints(
      IntakeConstants.INTAKE_ARM_MAX_VEL_DEG_PER_SEC,
      IntakeConstants.INTAKE_ARM_MAX_ACCEL_DEG_PER_SEC2);

  private TrapezoidProfile.State armSetpoint = new TrapezoidProfile.State(0.0, 0.0);
  private TrapezoidProfile.State armGoal = new TrapezoidProfile.State(0.0, 0.0);
  private double lastTimestampSec = 0.0;

  /** Creates a new IntakeSubsystem. */
  public IntakeSubsystem() {
    SparkMaxConfig intakeConfig = new SparkMaxConfig();
      intakeConfig.inverted(false);
      intakeConfig.idleMode(IdleMode.kCoast);

    intakeMotor.configure(intakeConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);

    SparkMaxConfig intakeArmConfig = new SparkMaxConfig();
      intakeArmConfig.inverted(true);
      intakeArmConfig.idleMode(IdleMode.kBrake);
      // Convert motor rotations -> arm degrees (assumes INTAKEConstants.GEAR_RATIO is motor:arm reduction).
      intakeArmConfig.encoder.positionConversionFactor(360.0 / IntakeConstants.GEAR_RATIO);

    intakeArmMotor.configure(intakeArmConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    // On enable, assume the arm is sitting on the floor at 0° and hold there.
    intakeArmEncoder.setPosition(IntakeConstants.INTAKE_ARM_LOWERED_POSITION);
    intakeArmPID.reset();
    targetPosition = IntakeConstants.INTAKE_ARM_LOWERED_POSITION;

    armSetpoint = new TrapezoidProfile.State(targetPosition, 0.0);
    armGoal = new TrapezoidProfile.State(targetPosition, 0.0);
    lastTimestampSec = Timer.getFPGATimestamp();
  }

  public void toggleIntake() {
    if (!intakeOn) {
      intakeOn = true;
      intakeMotor.set(IntakeConstants.INTAKE_SPEED);
    }
    else {
      intakeOn = false;
      intakeMotor.set(0);
    }
  }
  
  public void setIntakePower(double power) {
    double clampedPower = Math.max(-1.0, Math.min(1.0, power));
    intakeOn = Math.abs(clampedPower) > 0.0;
    intakeMotor.set(clampedPower * IntakeConstants.INTAKE_SPEED);
  }
  

  public void setTargetPosition(double position) {
    targetPosition = Math.max(IntakeConstants.INTAKE_ARM_MINIMUM, Math.min(IntakeConstants.INTAKE_ARM_MAXIMUM, position));
    armGoal = new TrapezoidProfile.State(targetPosition, 0.0);
  }

  public void raiseIntake() {
    setTargetPosition(IntakeConstants.INTAKE_ARM_RAISED_POSITION);
    intakeArmPID.reset();
    intakeUp = true;
  }

  public void lowerIntake() {
    setTargetPosition(IntakeConstants.INTAKE_ARM_LOWERED_POSITION);
    intakeArmPID.reset();
    intakeUp = false;
  }
  
  public void moveIntake() {
    if (intakeUp){
      lowerIntake();
    }
    else {
      raiseIntake();
    }
  }

  public double getArmPosition() {
    // With positionConversionFactor set, encoder position is already in degrees.
    return intakeArmEncoder.getPosition();
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    double nowSec = Timer.getFPGATimestamp();
    double dtSec = nowSec - lastTimestampSec;
    lastTimestampSec = nowSec;
    dtSec = MathUtil.clamp(dtSec, 0.0, 0.05);

    double currentDegrees = getArmPosition();
    armSetpoint = new TrapezoidProfile(armConstraints).calculate(dtSec, armGoal, armSetpoint);

    double output = intakeArmFeedForward.calculate(Units.degreesToRadians(currentDegrees), 0)
        + intakeArmPID.calculate(currentDegrees, armSetpoint.position);

    // Limit output so the arm moves slower/gentler (especially on the way down).
    boolean movingUp = armSetpoint.position > currentDegrees;
    double maxOutput = movingUp
        ? IntakeConstants.INTAKE_ARM_MAX_OUTPUT_UP
        : IntakeConstants.INTAKE_ARM_MAX_OUTPUT_DOWN;
    if (!movingUp && currentDegrees <= IntakeConstants.INTAKE_ARM_FLOOR_SLOW_ZONE_DEG){
      maxOutput = Math.min(maxOutput, IntakeConstants.INTAKE_ARM_MAX_OUTPUT_DOWN_NEAR_FLOOR);
    }
    output = MathUtil.clamp(output, -maxOutput, maxOutput);
    intakeArmMotor.set(output);

    SmartDashboard.putNumber("IntakeArm/TargetDeg", targetPosition);
    SmartDashboard.putNumber("IntakeArm/SetpointDeg", armSetpoint.position);
    SmartDashboard.putNumber("IntakeArm/SetpointVelDegPerSec", armSetpoint.velocity);
    SmartDashboard.putNumber("IntakeArm/PositionDeg", currentDegrees);
    SmartDashboard.putNumber("IntakeArm/Output", output);
  }
}
