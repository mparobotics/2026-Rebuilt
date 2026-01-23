# MVP Team Process – Requirements to Code

A lightweight guide for translating robot requirements to software work. This document is designed for both students and mentors working in a 6-week FRC build season.

---

## Process Overview

For information on defining your robot strategy and mapping it to capabilities, see the [Strategy Definition Process](strategy-definition-process.md) document.

---


### The Big Picture

Your robot needs to do things. Those things are **capabilities**. Hardware and electronics provide the physical components required by capabilities—these physical components are organized into the robot's **subsystems**. Your software controls these subsystems to drive the capabilities. Here's how we connect the dots:

```
Strategy
└── Capability
    └── Sub-Capability (leaf)
        └── Command / CommandGroup
            └── Subsystem(s) Used
```

**Key insight:** Capabilities describe *what* the robot does. Subsystems describe *how* the hardware is organized. Commands orchestrate subsystems to implement capabilities. One subsystem can support many capabilities. **User stories** are work items that implement software artifacts (commands, subsystems, utilities, etc.)—they reference capabilities, commands and subsystems but aren't part of the hierarchy.

### Capability Tree

A **capability tree** captures what your robot must do, organized hierarchically. It's implementation-agnostic—it doesn't care about motors, sensors, or code structure. It just describes behaviors.

**Structure:** Use an indented bullet list. Start broad (high-level capabilities) and drill down to specific, actionable sub-capabilities. Each capability should be prioritized using the MoSCoW-based categories (see [Capability Prioritization](#capability-prioritization) below).

**Why it matters:** The capability tree ensures you're building the right things. It helps prioritize work and ensures nothing gets missed. Commands implement leaf-level capabilities, and user stories reference these capabilities.

### Capability Prioritization

Capabilities are prioritized using categories based on the [MoSCoW method](https://en.wikipedia.org/wiki/MoSCoW_method) to reach a common understanding on the importance of each capability:

- **Needs** (MoSCoW: Must Have) - Critical capabilities required for the robot to be successful. If even one Need is not included, the project delivery should be considered a failure. These are the minimum viable capabilities.

- **Wants** (MoSCoW: Should Have) - Important capabilities but not necessary for initial delivery. While as important as Needs, they are often not as time-critical or there may be alternative ways to satisfy the requirement. These can be held back until a future delivery timebox if needed.

- **Nice to Have** (MoSCoW: Could Have) - Desirable capabilities that could improve the robot's performance or user experience for relatively little development cost. These will typically be included if time and resources permit.

- **Won't** (MoSCoW: Won't Have) - Capabilities that have been agreed by stakeholders as the least-critical, lowest-payback items, or not appropriate at this time. These are not planned into the schedule for the current delivery timebox, but may be reconsidered for inclusion in a later timebox.

**Prioritization Process:**
- Teams categorize each capability into one of these four categories
- Focus development efforts on Needs first, then Wants, then Nice to Have
- If delivery timescale is threatened, Wants and Nice to Have capabilities are the first to be removed
- Won't capabilities are explicitly excluded from the current scope

> **Note:** The **Capability Tree** is derived from the Robot Strategy (Robot WILL List) by breaking down the strategy's behaviors into specific capabilities and organizing them hierarchically.

### System Architecture (Subsystems)

The **system architecture** document captures hardware ownership. It lists each subsystem and the hardware it controls. This is a stable artifact—it doesn't change as often as user stories.

**Structure:** Simple list of subsystems, each with:
- Purpose (what it does)
- Hardware owned (motors, sensors, pneumatics, etc.)

**Why it matters:** When writing user stories, you need to know which subsystems to use. The system architecture provides that reference. It also helps ensure hardware isn't duplicated or forgotten.

**When to create:** Early in the season, once mechanical/electrical design is stable. Update it when hardware changes.

**Mapping to code:** Each subsystem in the architecture document maps to one **Subsystem** class in WPILib. The hardware listed (motors, sensors, etc.) becomes the fields and objects within that subsystem class.

### Command Specifications

The **command specifications** document describes each command that will be needed to implement the robot's capabilities. It's created early in the season (after capabilities are identified and initial hardware design is known) and evolves as commands are implemented and refined.

**Structure:** One section per command, each with:
- Description (what the command does)
- Subsystems used
- Acceptance criteria (added incrementally as stories complete)

**Why it matters:** Command specifications provide a stable reference for what each command should do. They start with a simple description and accumulate precise acceptance criteria as stories are completed. This helps ensure commands are complete and testable.

**When to create:** After capabilities are identified and initial hardware design is known, but before detailed stories are written. Update it as stories complete and add precise acceptance criteria.

**Mapping to code:** Each command in the specifications document maps to one **Command** or **CommandGroup** class in WPILib. The description captures the command's purpose, and acceptance criteria define how to test it.

### Backlog Items (User Stories + Acceptance Criteria)

Each **user story** represents a discrete work package needed to implement or refine software artifacts (commands, subsystems, utilities, etc.). Stories follow this format:

> **As a** [user/persona], **I want** [goal/behavior], **so that** [benefit/reason].

**Acceptance criteria** define when the story is "done." Use **Given-When-Then** format:

- **Given:** Initial conditions or context
- **When:** The action or trigger
- **Then:** Expected outcome (measurable, testable)

**Mapping to code:** Stories define work that creates or refines software artifacts (**Commands**, **Subsystems**, utilities, etc.).

### Scrum & Lean Principles

We use a lightweight, pragmatic approach focused on two things:

1. **Minimizing waste** – Don't build things you don't need. Don't over-document. Ship working code.
2. **Amplifying learning** – Test early. Get feedback. Adjust quickly.

**What this means in practice:**
- Write stories for work that's ready to code (not vague ideas)
- Keep stories small (one artifact, one capability)
- Test on the robot as soon as possible
- Refine the backlog just-in-time (don't plan weeks ahead)

### Cadence

**Saturday:**
- Sprint Review (show what you built)
- Retrospective (optional—what went well, what to improve)
- Sprint Planning (pick stories for next sprint)
- Sprint Work (start coding)

**Monday/Tuesday/Wednesday/Friday:**
- Standup (quick sync: what did you do, what are you doing, any blockers?)
- Sprint Work (code, test, review)

**Thursday:** Typically off or flexible.

**Sprint length:** 1 week works well for a 6-week season. Adjust if needed.

### Backlog Refinement

Refine stories **just-in-time** for the upcoming sprint. Don't spend hours planning stories you won't touch for weeks.

**When to refine:**
- During sprint planning (for next sprint)
- When a story becomes the next priority
- When new requirements come in

**What refinement means:**
- Story is clear (who, what, why)
- Acceptance criteria are testable
- Dependencies are identified
- Story maps to a specific capability

---

## Templates & Examples

### Capability Tree Template

```markdown
# Capability Tree: [High-Level Goal]

## Needs (Must Have)
- [Capability 1] - [Priority: Need]
  - [Sub-Capability 1.1]
    - [Sub-Capability 1.1.1] (leaf)
    - [Sub-Capability 1.1.2] (leaf)
  - [Sub-Capability 1.2] (leaf)

## Wants (Should Have)
- [Capability 2] - [Priority: Want]
  - [Sub-Capability 2.1] (leaf)

## Nice to Have (Could Have)
- [Capability 3] - [Priority: Nice to Have]
  - [Sub-Capability 3.1] (leaf)

## Won't (Won't Have)
- [Capability 4] - [Priority: Won't]
```

**Notes:**
- Use indented bullets to show hierarchy
- Group capabilities by priority category (Needs, Wants, Nice to Have, Won't)
- Leaf-level capabilities (no children) are what you'll write stories for
- Keep it at 3-4 levels deep max
- Use action verbs: "Align to target", "Score game piece", "Intake from floor"
- Prioritize at the capability level (not sub-capability level)

### Capability Tree Example

```markdown
# Capability Tree: Score Game Pieces Efficiently

- Auto-Align & Shoot
  - Align to Speaker
  - Align to Amp
  - Shoot at Target
- Manual Scoring
  - Score to Speaker (driver-controlled)
  - Score to Amp (driver-controlled)
- Intake Game Pieces
  - Intake from Floor
  - Intake from Source
  - Eject Game Piece
- Autonomous Scoring
  - Auto-Score Pre-Loaded Piece
  - Auto-Score from Starting Position
```

**Mapping note:** "Align to Speaker" is a leaf capability. This would be implemented by `AutoAlignToSpeakerCommand`, which uses the `Drivetrain` and `Vision` subsystems. Multiple user stories would work together to fully implement and refine this command and its supporting subsystems.

### System Architecture Template

```markdown
# System Architecture

## [Subsystem Name]

**Purpose:** [Brief description of what this subsystem does]

**Hardware Owned:**
- [Hardware component 1] (e.g., Motor, Sensor, etc.)
- [Hardware component 2]
- [Hardware component 3]

## [Subsystem Name]

**Purpose:** [Brief description]

**Hardware Owned:**
- [Hardware component 1]
- [Hardware component 2]
```

**Notes:**
- One subsystem per section
- List all hardware components (motors, encoders, sensors, pneumatics, etc.)
- Keep purpose descriptions brief (1-2 sentences)
- Update when hardware changes

### System Architecture Example

```markdown
# System Architecture

## Drivetrain

**Purpose:** Controls robot movement and positioning.

**Hardware Owned:**
- Left drive motor (TalonFX, CAN ID 1)
- Right drive motor (TalonFX, CAN ID 2)
- Left encoder (integrated with motor)
- Right encoder (integrated with motor)
- Gyro (NavX, SPI)

## Shooter

**Purpose:** Controls shooter wheel speed for scoring game pieces.

**Hardware Owned:**
- Shooter motor (TalonFX, CAN ID 3)
- Shooter encoder (integrated with motor)
- Hood servo (PWM channel 0)

## Intake

**Purpose:** Collects game pieces from floor and source.

**Hardware Owned:**
- Intake motor (TalonSRX, CAN ID 4)
- Intake beam break sensor (Digital Input 0)

## Feeder

**Purpose:** Transfers game pieces from intake to shooter.

**Hardware Owned:**
- Feeder motor (TalonSRX, CAN ID 5)
- Feeder beam break sensor (Digital Input 1)

## Vision

**Purpose:** Detects game targets and provides targeting information.

**Hardware Owned:**
- Camera (USB camera, device 0)
- Limelight (NetworkTables)

## Arm

**Purpose:** Raises and lowers game piece manipulator.

**Hardware Owned:**
- Arm motor (TalonFX, CAN ID 6)
- Arm encoder (integrated with motor)
- Arm limit switch (Digital Input 2)
```

**Mapping note:** When creating command specifications for "Auto-Align & Shoot", you'd reference this document to see that you need `Drivetrain`, `Vision`, `Shooter`, and `Feeder` subsystems.

### Command Specifications Template

```markdown
# Command Specifications

## [CommandName]

**Description:** [What the command does - simple, high-level description]

**Capability Supported:** [CapabilityName]
**Subsystems Used:** [Subsystem1, Subsystem2, ...]

**Acceptance Criteria:**
- [Precise, testable criteria added incrementally as stories complete]
- [Each criterion should be in Given-When-Then format]
```

**Notes:**
- One section per command
- Start with a simple description (what it does)
- Add precise acceptance criteria as stories complete
- Update when stories refine the command (or create new commands)

### Command Specifications Example

```markdown
# Command Specifications

## AutoAlignToSpeakerCommand

**Description:** Automatically aligns the robot with the speaker so that a game piece can be successfully shot into the speaker.

**Capability Supported:** Auto-Align & Shoot
**Subsystems Used:** Drivetrain, Vision

**Acceptance Criteria:**
- Given robot within 3 meters of speaker and target visible, when auto-align triggered, then heading error ≤ 3° within 0.75 seconds
- Given target partially occluded, when auto-align triggered, then command handles gracefully (no crash, returns error state)
- Given robot aligned (heading error ≤ 3°) and shooter ready, when shoot triggered, then completes shot within 2 seconds
```

**Mapping note:** This command implements the "Align to Speaker" capability. The initial description was created early in the season. The acceptance criteria were added incrementally as stories completed, refining the command's behavior.

### User Story Template

```markdown
## User Story

**As a** [user/persona],  
**I want** [goal/behavior],  
**so that** [benefit/reason].

**Capability:** [Reference to leaf capability from capability tree]
**Command:** [CommandName from Command Specifications] (if applicable)
**Subsystems:** [SubsystemNames from System Architecture] (if applicable)

**Acceptance Criteria:**

**Given** [initial conditions],  
**When** [action/trigger],  
**Then** [expected outcome - measurable and testable].
```

### User Story Example

```markdown
## User Story

**As a** driver,  
**I want** the robot to automatically align to the speaker target,  
**so that** I can score game pieces reliably without manual aiming.

**Capability:** Auto-Align & Shoot → Align to Speaker
**Command:** `AutoAlignToSpeakerCommand`
**Subsystems:** `Drivetrain`, `Vision`

**Acceptance Criteria:**

**Given** the robot is within 3 meters of the speaker and the target is visible,  
**When** I press the "Auto-Align" button,  
**Then** the robot rotates to face the speaker with heading error ≤ 3° within 0.75 seconds.
```

---

## WPILib Command-Based Mapping

### Commands vs. Subsystems

**Subsystems** own hardware. They encapsulate motors, sensors, and low-level control. Examples:
- `Drivetrain` – controls left/right motors
- `Shooter` – controls shooter motor, manages RPM
- `Vision` – processes camera data, provides target info

**Commands** orchestrate subsystems to implement capabilities. They define behaviors. Examples:
- `AutoAlignToSpeakerCommand` – uses `Drivetrain` and `Vision` to align
- `ShootCommand` – uses `Shooter` and `Feeder` to score

### The Mapping

| Artifact | Maps To | Purpose |
|----------|---------|---------|
| **Capability** | Behavior/Requirement | What the robot must do |
| **System Architecture** | Hardware Organization | Defines subsystems and hardware ownership |
| **Command Specifications** | Command Design | Describes what each command does and how to test it |
| **Command/CommandGroup** | WPILib Class | Code that implements the behavior |
| **Subsystem** | WPILib Class | Hardware ownership and control |
| **User Story** | Work Item | Discrete work package that creates/refines commands |
| **Acceptance Criteria** | Test Cases | Physical verification on robot |

### Important Distinctions

1. **Capabilities ≠ Subsystems**
   - One capability may use multiple subsystems
   - One subsystem supports many capabilities
   - Example: "Auto-Align & Shoot" uses `Drivetrain`, `Vision`, `Shooter`, and `Feeder`

2. **Commands implement capabilities, not subsystems**
   - Don't create "DrivetrainCommand" (too vague)
   - Create "AutoAlignToSpeakerCommand" (specific behavior)

3. **Subsystems provide capabilities, commands consume them**
   - `Shooter` subsystem provides: `setTargetRPM()`, `isAtTargetRPM()`
   - `ShootCommand` uses those methods to implement "Shoot at Target" capability

### Example: Full Traceability

```
Capability: Auto-Align & Shoot → Align to Speaker
    ↓
Command Specification: AutoAlignToSpeakerCommand
    Description: "Automatically aligns the robot with the speaker..."
    ↓
Command: AutoAlignToSpeakerCommand (WPILib class)
    ↓
Uses Subsystems:
    - Drivetrain.rotateToHeading()
    - Vision.getTargetHeading()
    ↓
User Stories: Multiple stories create and refine this command and its subsystems
    - Story #1: Initial command implementation
    - Story #3: Vision subsystem improvements
    - Story #5: Handle edge cases
    - Story #12: Performance improvements
    ↓
Acceptance Criteria: (from Command Specifications)
    - Heading error ≤ 3° within 0.75s
    - Handles occluded targets gracefully
    ↓
Test: Run command, measure heading error, verify timing
```

### Optional: Subsystem-Level Stories

Sometimes you need enabling behaviors at the subsystem level. For example:

> **As a** shooter subsystem, **I want** to reach target RPM within 1 second, **so that** commands can reliably shoot.

This is fine, but most stories should be at the capability level (what the robot does, not what a subsystem does).

---

## Quick Reference

**Workflow:**
1. Build capability tree (what robot must do)
2. Document system architecture (subsystems and hardware)
3. Create command specifications (commands needed, initial descriptions)
4. Write user stories (discrete work packages for commands, subsystems, etc.)
5. Implement stories as software artifacts (Commands, Subsystems, utilities, etc.)
6. Update command specifications with precise acceptance criteria (for command stories)
7. Test using acceptance criteria

**Remember:**
- Capabilities = what robot does
- System Architecture = hardware organization
- Command Specifications = what commands do and how to test them
- Commands = behaviors that use subsystems
- Stories = discrete work packages that create/refine software artifacts (commands, subsystems, utilities, etc.)
- Acceptance criteria = definition of done (in stories) and complete test definition (in Command Specifications)

**Questions to ask:**
- Does this story map to a capability? ✓
- Does it reference an artifact (command, subsystem, etc.)? ✓
- Can I test the acceptance criteria on the robot? ✓
- Does the artifact name describe its purpose clearly? ✓
- Is the story small enough to complete in a sprint? ✓
- Will I update Command Specifications after completing this story? (if it's a command story) ✓

---

## Next Steps

1. **Create your capability tree** – Start with your game strategy, break it down into capabilities
2. **Document system architecture** – List subsystems and their hardware (once mechanical/electrical design is stable)
3. **Create command specifications** – Identify commands needed for each capability, write initial descriptions
4. **Write your first story** – Pick an artifact (command, subsystem, etc.) to implement, write a story with precise acceptance criteria
5. **Implement the artifact** – Code it (Command, Subsystem, utility, etc.), test it, verify acceptance criteria
6. **Update command specifications** – If the story was for a command, add the precise acceptance criteria from completed stories

For more details on development workflow, see [Team Development Process](team-development-process.md).

