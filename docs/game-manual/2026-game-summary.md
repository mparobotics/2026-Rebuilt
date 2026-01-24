# 2026 FIRST Robotics Competition Game: REBUILT™

**Presented by Haas**

## Game Overview

In **REBUILT™**, two competing alliances score fuel, cross obstacles, and climb the tower before time runs out. Alliances earn additional rewards for meeting specific scoring thresholds.

### Match Structure

- **Total Match Time**: 2 minutes 40 seconds
- **Autonomous Period**: First 20 seconds (0:20 - 0:00)
- **Teleoperated Period**: Remaining 2 minutes 20 seconds (2:20 - 0:00)
- **End Game**: Final 30 seconds (0:30 - 0:00)

### Match Flow

1. **Autonomous (20 seconds)**: Robots operate without driver control
   - Score fuel into hub
   - Some robots may climb tower (Level 1 only, max 2 robots)
   - Fuel can be pre-loaded, obtained from human player, collected at depot, or picked up from field center

2. **Teleoperated (2:20)**: Drivers control robots
   - Hub status alternates between active/inactive based on autonomous performance
   - Robots can collect fuel at any time
   - Robots can control any amount of fuel
   - Robots can climb tower (Level 1, 2, or 3 - one level per robot)

3. **End Game (final 30 seconds)**: All hubs become active
   - All robots can score
   - Robots can climb to highest tower levels for bonus points

## Field Layout

- **Field Dimensions**: Approximately 317.7in (~8.07m) by 651.2in (~16.54m)
- **Key Elements**:
  - 1 OUTPOST per alliance
  - 1 HUB per alliance
  - 1 TOWER per alliance
  - 2 DEPOTS
  - 4 BUMPS (obstacles)
  - 4 TRENCHES (obstacles)

### HUB

- **Size**: 47in × 47in (~1.19m × 1.19m) rectangular prism
- **Location**: Centered between two BUMPS, 158.6in (~4.03m) from alliance wall
- **Opening**: 41.7in (~1.06m) hexagonal opening at top
- **Height**: Front edge of opening is 72in (~1.83m) off carpet
- **Function**: Processes fuel and distributes it into neutral zone via 4 exits

### TOWER

- **Dimensions**: 49.25in (1.251m) wide × 45.0in (1.143m) deep × 78.25in (1.988m) tall
- **Location**: Integrated into alliance wall between Driver Station 2 and 3
- **Structure**:
  - TOWER BASE: 39.0in × 45.18in plate on floor
  - UPRIGHTS: Two 72.1in tall vertical frames, 32.25in apart
  - RUNGS: Three horizontal bars (1-1/4in Sch 40 pipe)
    - LOW RUNG: 27.0in from floor
    - MID RUNG: 45.0in from floor
    - HIGH RUNG: 63.0in from floor
    - 18.0in apart center-to-center

### Scoring Elements: FUEL

- **Type**: 5.91in (15.0cm) diameter high-density foam ball
- **Weight**: 0.448-0.500lb (~0.203-0.227kg)
- **Purchase**: Available on AndyMark website (am-5801)
- **Control**: Robots may control any number of FUEL after match start

## Scoring

### Hub Status During Match

The hub alternates between active and inactive based on which alliance scored more fuel during autonomous (or FMS selection):

| Timeframe | Timer Values | Hub Status (if RED won AUTO) | Hub Status (if BLUE won AUTO) |
|-----------|--------------|------------------------------|-------------------------------|
| AUTO | 0:20 - 0:00 | Both Active | Both Active |
| TRANSITION SHIFT | 2:20 - 2:10 | Both Active | Both Active |
| SHIFT 1 | 2:10 - 1:45 | RED Inactive, BLUE Active | RED Active, BLUE Inactive |
| SHIFT 2 | 1:45 - 1:20 | RED Active, BLUE Inactive | RED Inactive, BLUE Active |
| SHIFT 3 | 1:20 - 0:55 | RED Inactive, BLUE Active | RED Active, BLUE Inactive |
| SHIFT 4 | 0:55 - 0:30 | RED Active, BLUE Inactive | RED Inactive, BLUE Active |
| END GAME | 0:30 - 0:00 | Both Active | Both Active |

### Point Values

| Action | AUTO | TELEOP | Ranking Points |
|--------|------|--------|----------------|
| **FUEL** scored in active HUB | 1 | 1 | - |
| **FUEL** scored in inactive HUB | - | - | - |
| **TOWER** - Level 1 (max 2 robots in AUTO) | 15 | 10 | - |
| **TOWER** - Level 2 | - | 20 | - |
| **TOWER** - Level 3 | - | 30 | - |
| **ENERGIZED RP** (≥100 fuel threshold) | - | - | 1 |
| **SUPERCHARGED RP** (≥360 fuel threshold) | - | - | 1 |
| **TRAVERSAL RP** (≥50 tower points threshold) | - | - | 1 |
| **Win** (more match points than opponent) | - | - | 3 |
| **Tie** (same match points as opponent) | - | - | 1 |

### Tower Scoring Criteria

**Level 1**:
- Robot no longer touching carpet or TOWER BASE
- Only during AUTO
- Max 2 robots

**Level 2**:
- Robot's BUMPERS completely above LOW RUNG
- Only during TELEOP

**Level 3**:
- Robot's BUMPERS completely above MID RUNG
- Only during TELEOP

**Contact Requirements** (all levels):
- Must contact RUNGS or UPRIGHTS
- May additionally contact:
  - TOWER WALL
  - Support structure
  - FUEL
  - Another ROBOT

### Scoring Assessment Timing

- FUEL scored in HUB: Assessed for up to 3 seconds after timer displays 0:00 (after both AUTO and TELEOP)
- AUTO TOWER points: Assessed after timer displays 0:00 following AUTO
- TELEOP TOWER points: Assessed 3 seconds after timer displays 0:00 following TELEOP, or when all robots come to rest, whichever happens first
- Assessment continues for 3 seconds after HUB deactivates to account for FUEL processing time

## Key Game Mechanics

1. **Hub Alternation**: Based on autonomous performance, hubs alternate between active/inactive, creating strategic shifts in gameplay
2. **Fuel Collection**: Robots can collect fuel from multiple sources:
   - Pre-loaded in robot
   - Human player
   - Depot
   - Field center
3. **Tower Climbing**: 
   - Autonomous: Level 1 only (max 2 robots)
   - Teleoperated: One level per robot (Level 1, 2, or 3)
4. **End Game**: All hubs become active in final 30 seconds, allowing all robots to score simultaneously

## Strategy Implications

- Strong autonomous performance determines hub control advantage
- Teams must balance offense (scoring) and defense during inactive hub periods
- Tower climbing provides significant point values, especially Level 3 (30 points)
- Meeting bonus RP thresholds (ENERGIZED, SUPERCHARGED, TRAVERSAL) improves rankings
- End game strategy critical - all hubs active allows maximum scoring potential

---

*This summary is extracted from the 2026 FRC Game Manual. For complete rules and specifications, refer to the official manual.*

