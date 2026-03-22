// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import java.util.List;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;

import frc.robot.Constants.ShooterConstants;
import org.moundsparkacademy.frc.tuning.TunableProvider;
import org.moundsparkacademy.frc.tuning.Tuner;
import org.moundsparkacademy.frc.tuning.TuningParameter;

public class ShooterSubsystem extends SubsystemBase implements TunableProvider {

  public boolean isShooterActive = false; //Shooter True

  public final SparkMax shooterMotor = new SparkMax(ShooterConstants.SHOOTER_ID, MotorType.kBrushless);
  private final SparkMax kickerMotor = new SparkMax(ShooterConstants.KICKER_ID, MotorType.kBrushless);
  private final SparkMax hoodMotor = new SparkMax(ShooterConstants.HOOD_ID, MotorType.kBrushless);
  private final SparkMax indexerMotor = new SparkMax(ShooterConstants.INDEXER_ID, MotorType.kBrushless);

  private double shooterCmd = 0.0;
  private double kickerCmd = 0.0;
  private double indexerCmd = 0.0;

  private double shooterSpeed = ShooterConstants.SHOOTER_SPEED;
  private double kickerSpeed = ShooterConstants.KICKER_SPEED;
  private double indexerSpeed = ShooterConstants.INDEXER_SPEED;

  private final PIDController hoodController = new PIDController(
      ShooterConstants.HOOD_KP,
      0.0,
      0.0
  );
  private double hoodTargetPosition = ShooterConstants.HOOD_ANGLE_LOW;
  private boolean hoodActive = false;

  public enum HoodAngle {
    LOW,
    MED,
    HIGH
  }

  //LightSubsystem m_lightSubsystem = new LightSubsystem();

  public ShooterSubsystem() {
    SparkMaxConfig shootConfig = new SparkMaxConfig();
      shootConfig.smartCurrentLimit(ShooterConstants.SHOOTER_CURRENT_LIMIT_AMPS);
      shootConfig.inverted(true);
      shootConfig.idleMode(IdleMode.kCoast);
      shootConfig.voltageCompensation(ShooterConstants.SHOOTER_VOLTAGE_COMP);

    SparkMaxConfig feedConfig = new SparkMaxConfig();
      feedConfig.smartCurrentLimit(ShooterConstants.KICKER_CURRENT_LIMIT_AMPS);
      feedConfig.inverted(false);
      feedConfig.idleMode(IdleMode.kBrake);
      feedConfig.voltageCompensation(ShooterConstants.SHOOTER_VOLTAGE_COMP);

    SparkMaxConfig hoodConfig = new SparkMaxConfig();
      hoodConfig.inverted(true);
      hoodConfig.idleMode(IdleMode.kBrake);

    SparkMaxConfig indexConfig = new SparkMaxConfig();
      indexConfig.inverted(true);
      indexConfig.idleMode(IdleMode.kBrake);

    

    shooterMotor.configure(shootConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    kickerMotor.configure(feedConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    hoodMotor.configure(hoodConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    indexerMotor.configure(indexConfig, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);

    hoodController.setTolerance(ShooterConstants.HOOD_TOLERANCE);
  }

    public void toggleShooter() {
      if (!isShooterActive) {
        isShooterActive = true;
        shooterMotor.set(shooterSpeed);
      }
      else {
        isShooterActive = false;
        shooterMotor.set(0);
      }
    }


    public void runShooter(boolean shooterOn) {
      if (shooterOn) {
        isShooterActive = true;
        shooterMotor.set(shooterSpeed);
      } else {
        isShooterActive = false;
        shooterMotor.set(0);
      }
    }

    public void setShooterSpeed(double speed) {
      isShooterActive = Math.abs(speed) > 0.0;
      shooterCmd = speed;
      shooterMotor.set(speed);
    }

    public double getShooterVelocityRpm() {
      return shooterMotor.getEncoder().getVelocity();
    }


    public void runKicker(boolean kickerOn){
      setKickerSpeed(kickerOn ? kickerSpeed : 0);
    }

    public void setKickerSpeed(double speed) {
      kickerCmd = speed;
      kickerMotor.set(speed);
    }

    public void setHoodAngle(HoodAngle angle) {
      switch (angle) {
        case LOW:
          hoodTargetPosition = ShooterConstants.HOOD_ANGLE_LOW;
          break;
        case MED:
          hoodTargetPosition = ShooterConstants.HOOD_ANGLE_MED;
          break;
        case HIGH:
          hoodTargetPosition = ShooterConstants.HOOD_ANGLE_HIGH;
          break;
        default:
          hoodTargetPosition = ShooterConstants.HOOD_ANGLE_HIGH;
      }
      hoodTargetPosition = Math.max(
          ShooterConstants.HOOD_MIN_ROTATIONS,
          Math.min(ShooterConstants.HOOD_MAX_ROTATIONS, hoodTargetPosition));
      hoodController.reset();
      hoodActive = true;
    }

    public double getHoodPosition() {
      return hoodMotor.getEncoder().getPosition();
    }

    public void runIndexer(boolean indexerOn) {
      setIndexerSpeed(indexerOn ? indexerSpeed : 0);
    }

    public void setIndexerSpeed(double speed) { 
      indexerCmd = speed;
      indexerMotor.set(speed);
    }

    public void AutoToggleShoot (boolean AutoShootOn) {
      setKickerSpeed(AutoShootOn ? 0 : kickerSpeed);
      setShooterSpeed(AutoShootOn ? 0 : shooterSpeed);
    }

    public void AutoToggleKickIndex (boolean AutoIndexKickOn) {
      setKickerSpeed(AutoIndexKickOn ? 0 : kickerSpeed);
      // Originally referenced ShooterConstants.SHOOTER_SPEED here, believed to be
      // a copy-paste error — this line sets the indexer, not the shooter.
      setIndexerSpeed(AutoIndexKickOn ? 0 : indexerSpeed);
    }

  @Override
  public String getSubsystemName() { return "Shooter"; }

  @Override
  public List<Tuner> getTuners() {
    return List.of(
        new Tuner("Hood", List.of(
            new TuningParameter("kP",
                hoodController::getP,
                hoodController::setP,
                0, Double.POSITIVE_INFINITY)
        )),
        new Tuner("Shooter", List.of(
            new TuningParameter("Speed",
                () -> shooterSpeed,
                value -> shooterSpeed = value,
                0, 1.0)
        )),
        new Tuner("Kicker", List.of(
            new TuningParameter("Speed",
                () -> kickerSpeed,
                value -> kickerSpeed = value,
                0, 1.0)
        )),
        new Tuner("Indexer", List.of(
            new TuningParameter("Speed",
                () -> indexerSpeed,
                value -> indexerSpeed = value,
                0, 1.0)
        ))
    );
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    SmartDashboard.putBoolean("Is Shooter Active", isShooterActive);
    SmartDashboard.putNumber("Hood Target Position", hoodTargetPosition);
    SmartDashboard.putNumber("Hood Position", getHoodPosition());
    SmartDashboard.putNumber("Shooter/Cmd", shooterCmd);
    SmartDashboard.putNumber("Shooter/VelocityRPM", getShooterVelocityRpm());
    SmartDashboard.putNumber("Kicker/Cmd", kickerCmd);
    SmartDashboard.putNumber("Kicker/VelocityRPM", kickerMotor.getEncoder().getVelocity());
    SmartDashboard.putNumber("Indexer/Cmd", indexerCmd);
    SmartDashboard.putNumber("Indexer/VelocityRPM", indexerMotor.getEncoder().getVelocity());

    
    if (hoodActive) {
      double output = hoodController.calculate(getHoodPosition(), hoodTargetPosition);
      output = Math.max(-ShooterConstants.HOOD_MAX_OUTPUT, Math.min(ShooterConstants.HOOD_MAX_OUTPUT, output));

      if (hoodController.atSetpoint()) {
        hoodMotor.set(0);
        hoodActive = false;
      } else {
        hoodMotor.set(output);
      }
    } else {
      hoodMotor.set(0);
    }
  }
}
