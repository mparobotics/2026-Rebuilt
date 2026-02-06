## User Story: Autonomous Swerve Control

**As an** autonomous programmer,  
**I want to** command precise chassis speeds and trajectories for autonomous routines,  
**so that** the robot can execute complex paths, align with targets, and navigate the field autonomously.

### Acceptance Criteria

**Given** the robot is in auto mode and the swerve subsystem is initialized with four swerve modules,  
**When** autonomous code provides chassis speeds (x-velocity, y-velocity, rotation),  
**Then** the robot should execute the commanded movement using field-oriented kinematics.

**Given** the robot is in auto mode following a trajectory,  
**When** the autonomous code commands chassis speeds,  
**Then** the swerve drive should convert chassis speeds to individual module states and execute them.

**Given** the robot is in auto mode and vision targets are available,  
**When** following a path that requires precise positioning,  
**Then** the autonomous control should integrate with vision-assisted pose corrections for accurate navigation.

**Given** the robot is in auto mode executing a trajectory,  
**When** the commanded wheel speeds exceed physical limits,  
**Then** all wheel speeds should be proportionally desaturated while maintaining the intended trajectory direction.

**Given** the robot is in auto mode and needs to align with a target,  
**When** autonomous code commands specific rotational speeds,  
**Then** the swerve drive should maintain precise angular control for alignment operations.

**Given** the robot is in auto mode transitioning between path segments,  
**When** chassis speeds change rapidly,  
**Then** the swerve modules should smoothly transition between states without wheel slippage or jerky movement.