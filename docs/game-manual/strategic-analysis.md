# 2026 FRC REBUILT™ Strategic Analysis & Winning Strategies

**Goal**: Develop strategies to maximize ranking points and become an alliance captain

## Executive Summary

REBUILT™ is a game of **hub control**, **tower dominance**, and **end game execution**. Success requires balancing autonomous performance, fuel scoring efficiency, and tower climbing capability. The game's unique hub alternation mechanic creates strategic shifts that teams must exploit.

---

## Key Strategic Insights

### 1. **Autonomous Period is Critical - But Advantage is Nuanced**
- **Why**: Determines hub alternation pattern (who gets which shifts active)
- **Impact**: 
  - **Winner of AUTO**: Gets Shift 2 and Shift 4 active (hub inactive in Shift 1 and 3)
  - **Loser of AUTO**: Gets Shift 1 and Shift 3 active (hub inactive in Shift 2 and 4)
- **The Trade-off**:
  - **Winner's Scenario**: 
    - Shift 4 active → Can score fuel (good!), but robots finish Shift 4 with empty fuel systems
    - Enter end game needing to collect fuel first → Delays scoring in critical final 30 seconds
  - **Loser's Scenario**:
    - Shift 4 inactive → Cannot score fuel (bad!), but can collect fuel during Shift 4
    - Enter end game with fuel already collected → Can score immediately in final 30 seconds
- **Net Advantage Analysis**:
  - **Shift 1**: Loser scores 5-10 net points (8-12 scored minus 2-3 lost to collection delay)
  - **Shift 4**: Winner scores -2 to +7 net points (8-12 scored minus 5-10 lost to end game delay)
  - **Key Question**: Is the autonomous advantage worth it?
    - Winner gets Shift 2 and Shift 4 active
    - But both shifts have collection delay costs
    - Loser gets Shift 1 and Shift 3 active (smaller delay cost in Shift 1)
  - **Overall Net**: Winner has slight advantage (~5-15 points total), but smaller than it appears
- **Mitigating Factors**:
  - Human players can feed fuel through chute/corral during end game
  - Robots with high capacity can collect while scoring
  - Winner's Shift 4 scoring happens when opponent can't score (exclusive window)
  - Collection delays are more costly in end game (both hubs active) than in Shift 1 (just starting)
- **Strategy**: Prioritize reliable autonomous fuel scoring, but advantage is smaller and more nuanced than it initially appears
- **Trade-off**: Tower Level 1 in AUTO = 15 points, but max 2 robots can do it

### 2. **Hub Alternation Creates Strategic Windows**
- **Pattern**: Hubs alternate every 25 seconds during shifts
- **Opportunity**: When your hub is inactive, focus on:
  - **Priority 1**: Collecting fuel for next active period
  - **Priority 2**: Defense/field control (if applicable)
  - **Priority 3**: Positioning for end game (including tower area, but don't climb yet)
- **Important**: Do NOT climb tower during inactive shifts - this locks your robot out of fuel scoring for the rest of the match
- **Tower Strategy**: Climb ONLY during:
  - **AUTO** (Level 1, max 2 robots) - assessed immediately after AUTO
  - **END GAME** (Level 2 or 3) - assessed at match end, minimal time to fall
- **Rare Exception**: Only climb during Shift 4 if:
  - Your hub is inactive (can't score anyway)
  - You have 2+ robots that need to climb Level 3
  - They physically cannot both fit at the same time
  - One must climb first to make room for the second
  - **Even then**: Do this as late as possible (end of Shift 4, transitioning to end game)
- **Risk**: Opponent can score freely when their hub is active

### 3. **End Game is the Great Equalizer**
- **Duration**: Final 30 seconds (0:30-0:00)
- **Mechanic**: Both hubs become active simultaneously
- **Strategy**: Stockpile fuel during Shift 4, then rapid-fire score in end game
- **Tower**: Final 30 seconds is prime time for Level 3 climbs (30 points)

### 4. **Tower Climbing is High-Value but Risky**
- **Point Values**: Level 1 (10-15), Level 2 (20), Level 3 (30)
- **Risk**: One robot can only earn one level per match
- **Timing**: 
  - Level 1: Can only be earned during AUTO (assessed after AUTO ends)
  - Level 2 & 3: Can be earned during TELEOP (assessed at match end)
  - **Critical**: Points are assessed at the END of the match, not during shifts
  - Robot must maintain position until match end to earn points
- **Critical Risk - Getting Stuck on Tower**:
  - **AUTO Climb Risk**: If robot climbs during AUTO and mechanism fails, robot may be stuck on tower
  - **Consequences**: 
    - Robot cannot score fuel for entire match (2:20 of teleop)
    - Robot may block other robots from climbing during end game
    - Alliance loses 20-30+ potential fuel points
  - **Mitigation**: 
    - Ensure reliable descent mechanism
    - Test autonomous climb thoroughly
    - Consider if 15 points in AUTO is worth risk of losing 20-30+ fuel points
- **Protection**: G420 protects robots on tower in final 30 seconds

---

## Ranking Points Analysis

### Ranking Points Available Per Match:
1. **Win**: 3 RP (most important)
2. **ENERGIZED RP**: 1 RP (≥100 fuel scored)
3. **SUPERCHARGED RP**: 1 RP (≥360 fuel scored)
4. **TRAVERSAL RP**: 1 RP (≥50 tower points)
5. **Tie**: 1 RP (fallback)

### Maximum RP Per Match: 6 RP
- Win (3) + ENERGIZED (1) + SUPERCHARGED (1) + TRAVERSAL (1) = 6 RP

### RP Threshold Analysis:
- **ENERGIZED (100 fuel)**: Achievable with 2-3 robots scoring consistently
- **SUPERCHARGED (360 fuel)**: Requires exceptional fuel scoring (≈12 fuel/second average)
- **TRAVERSAL (50 tower points)**: Requires 2 robots at Level 3 (30+20) or 3 robots at Level 2 (20+20+10)

---

## Winning Strategies by Match Phase

### Phase 1: Autonomous (0:20 - 0:00)

**Primary Objective**: Win autonomous fuel count to control hub alternation pattern

**Why Hub Alternation Matters - Nuanced Analysis:**
- Both alliances get 2 active shifts and 2 inactive shifts (equal total)
- **BUT timing creates trade-offs:**
  - **Winner gets**: Shift 2 and Shift 4 active
  - **Loser gets**: Shift 1 and Shift 3 active
- **Shift 4 Trade-off Analysis**:
  - **Winner (Shift 4 active)**: 
    - ✅ Can score 8-12 points during Shift 4 (exclusive window)
    - ❌ Robots finish with empty fuel systems
    - ❌ Must collect fuel at start of end game → delays scoring
  - **Loser (Shift 4 inactive)**:
    - ❌ Cannot score during Shift 4 (misses 8-12 points)
    - ✅ Can collect fuel during Shift 4
    - ✅ Enters end game with fuel ready → can score immediately
- **Net Advantage**: Winner scores 8-12 points in Shift 4, but may lose efficiency in end game
- **Key Insight**: The advantage is real but smaller than it initially appears - both scenarios have meaningful trade-offs
- **Mitigating Factors**: Human player fuel feeds, high-capacity robots, and collection-while-scoring can reduce the disadvantage

**Strategy Options**:

#### Option A: Fuel-Focused AUTO (Recommended for Most Teams)
- **Approach**: Maximize fuel scoring in 20 seconds
- **Execution**:
  - Pre-load 8 fuel (maximum allowed)
  - Human player feeds fuel through chute
  - Collect from depot or neutral zone
  - Rapid-fire score into hub
- **Target**: 10-15+ fuel scored
- **Advantage**: Controls hub alternation, sets up teleop advantage

#### Option B: Hybrid AUTO (High-Capability Teams)
- **Approach**: 1-2 robots climb tower (Level 1), others score fuel
- **Execution**:
  - 1-2 robots: Tower Level 1 (15 points each)
  - 1-2 robots: Fuel scoring (5-10 fuel each)
- **Target**: Win fuel count + 15-30 tower points
- **Advantage**: Early tower points, still controls hub if fuel count wins
- **Critical Risk**: If robot gets stuck on tower (mechanism failure), it cannot score fuel for entire match
- **Mitigation**: Only attempt if descent mechanism is extremely reliable

#### Option C: Tower-Focused AUTO (Risky)
- **Approach**: 2 robots climb tower, minimal fuel scoring
- **Risk**: Loses hub control if opponent scores more fuel
- **Use Case**: Only if confident in tower capability and opponent is weak

**Key Considerations**:
- Hub alternation is determined by fuel count, not tower points
- Tower Level 1 in AUTO = 15 points (vs 10 in teleop)
- Max 2 robots can climb tower in AUTO
- Fuel scoring is more reliable than tower climbing
- **Critical Risk**: If robot gets stuck on tower during AUTO, it's disabled for entire match (loses 20-30+ fuel points)
- **Recommendation**: Only attempt AUTO tower climb if descent mechanism is extremely reliable, otherwise prioritize fuel scoring

---

### Phase 2: Transition Shift (2:20 - 2:10)

**Duration**: 10 seconds
**Hub Status**: Both active

**Strategy**:
- **Immediate fuel scoring**: Both hubs active = scoring opportunity
- **Positioning**: Move to optimal scoring positions
- **Fuel collection**: Begin collecting fuel for upcoming shifts
- **Tower preparation**: Position for tower climb if not done in AUTO

---

### Phase 3: Alliance Shifts (2:10 - 0:30)

**Pattern**: 4 shifts of 25 seconds each, hubs alternate

**Strategic Framework**:

#### When YOUR Hub is ACTIVE (Scoring Window):
- **Priority 1**: Score fuel rapidly
- **Priority 2**: Collect fuel for next active period
- **Priority 3**: Position for end game (but don't climb yet - wait for end game)
- **Tactics**:
  - Pre-position robots near hub before shift starts
  - Human players feed fuel through chute/corral
  - Multiple robots scoring simultaneously
  - Target: 8-12 fuel per 25-second shift
- **Important**: Do NOT climb tower during active shifts - you can score fuel now, so don't lock yourself out!

#### When YOUR Hub is INACTIVE (Preparation Window):
- **Priority 1**: Collect and stockpile fuel
- **Priority 2**: Field control/defense (if applicable)
- **Priority 3**: Position for end game (including near tower, but don't climb yet)
- **Tactics**:
  - Collect from neutral zone, depot, or opponent's hub exits
  - **Tower climbing**: Do NOT climb during inactive shifts - locks robot out of fuel scoring
  - Block opponent access (within rules)
  - Prepare for next active shift
- **Important Note**: Tower points are assessed at match end. Early climbing means robot can't score fuel for rest of match - almost always a poor trade-off.
- **Exception**: Only if robot is incapable of fuel scoring AND alliance needs tower points for TRAVERSAL RP

**Shift-by-Shift Breakdown**:

**SHIFT 1 (2:10 - 1:45)** - **TRADE-OFF ANALYSIS**:
- First shift after Transition phase
- **Winner of AUTO has INACTIVE hub here**
- **Loser of AUTO has ACTIVE hub here**
- **Context**: Both alliances just finished Transition phase (2:20-2:10) where both hubs were active
  - Both likely scored 3-5 fuel during Transition
  - Robots likely finish Transition with empty or low fuel systems

**Strategy if hub ACTIVE** (Loser of AUTO):
- Hub is ACTIVE → Can score fuel (exclusive window, winner can't score)
- **Problem**: Robots start Shift 1 with empty fuel systems (just finished scoring in Transition)
- Must collect fuel at start of Shift 1 → Delays scoring by 3-5 seconds
- **Impact**: Loses 2-3 fuel worth of scoring time (2-3 points) due to collection delay
- **But**: Still gets to score 8-12 fuel during remaining 20-22 seconds of Shift 1
- **Net**: Scores 8-12 points in Shift 1, but loses 2-3 points due to collection delay = **5-10 net points**

**Strategy if hub INACTIVE** (Winner of AUTO):
- Hub is INACTIVE → Cannot score fuel
- Robots start Shift 1 with empty fuel systems (just finished scoring in Transition)
- Can collect fuel during entire Shift 1 (25 seconds)
- **Advantage**: Enters Shift 2 (first active shift) with fuel already collected → Can score immediately
- **Disadvantage**: Missed 8-12 points that loser scored in Shift 1
- **Net**: Loses 8-12 points in Shift 1, but gains efficiency in Shift 2 (saves 2-3 points of collection time)

**Net Effect Comparison**:
- **Loser (Shift 1 active)**: Scores 5-10 net points in Shift 1 (8-12 scored minus 2-3 lost to delay)
- **Winner (Shift 1 inactive)**: Loses 8-12 points in Shift 1, but gains 2-3 points efficiency in Shift 2
- **Net advantage to loser**: ~3-7 points (smaller than it appears due to collection delay)

**SHIFT 2 (1:45 - 1:20)**:
- Hub status flips
- **Strategy**: Same as Shift 1, but roles reversed

**SHIFT 3 (1:20 - 0:55)**:
- Hub status flips again
- **Strategy**: Continue pattern, but start preparing for end game

**SHIFT 4 (0:55 - 0:30)** - **CRITICAL SHIFT - TRADE-OFF ANALYSIS**:
- Final shift before end game
- **Winner of AUTO has ACTIVE hub here**
- **Context**: Both alliances just finished Shift 3
  - Winner likely scored in Shift 2, collected in Shift 3
  - Loser likely scored in Shift 3, may have fuel ready

**Strategy if hub ACTIVE** (Winner of AUTO):
- Score fuel immediately (opponent can't score) → 8-12 points
- **Problem**: Robots finish Shift 4 with empty fuel systems
- Must collect fuel at start of end game → Delays scoring in critical final 30 seconds
- **Impact**: Loses 5-10 fuel worth of scoring time (5-10 points) in end game due to collection delay
- **Net**: Scores 8-12 points in Shift 4, but loses 5-10 points in end game = **-2 to +7 net points**

**Strategy if hub INACTIVE** (Loser of AUTO):
- Aggressive fuel collection for end game
- Position all robots for end game
- **Advantage**: Enter end game with fuel already collected → Can score immediately
- **Disadvantage**: Missed 8-12 points that winner scored in Shift 4
- **Net**: Loses 8-12 points in Shift 4, but gains 5-10 points efficiency in end game = **-3 to -7 net points**

**Net Effect**: Winner scores 8-12 points in Shift 4, but may lose 5-10 points in end game due to collection delay
- **Key Insight**: The advantage is smaller than it appears - both scenarios have trade-offs

---

### **SHIFT 1 vs SHIFT 4 Trade-off Comparison**:

| Aspect | SHIFT 1 (Loser Active) | SHIFT 4 (Winner Active) |
|--------|------------------------|--------------------------|
| **Points Scored** | 8-12 points | 8-12 points |
| **Collection Delay Cost** | 2-3 points (at start of Shift 1) | 5-10 points (at start of end game) |
| **Net Advantage** | +5 to +10 points | -2 to +7 points |
| **Why Different?** | End game is more critical (both hubs active, final 30s) | Shift 1 is less critical (just starting teleop) |
| **Overall Impact** | Loser gains ~3-7 points in Shift 1 | Winner gains ~-2 to +7 points in Shift 4 |

**Key Insight**: 
- **Shift 1 advantage to loser**: ~3-7 points (small, due to collection delay)
- **Shift 4 advantage to winner**: ~-2 to +7 points (varies, end game collection delay is more costly)
- **Net autonomous advantage**: Winner gets Shift 2 and Shift 4 active, but both have collection delay costs
- **Overall**: Autonomous winner has slight advantage, but it's smaller than it appears due to collection delays at critical moments

---

### Phase 4: End Game (0:30 - 0:00)

**Duration**: 30 seconds
**Hub Status**: Both active (maximum scoring opportunity)

**Strategy**: All-out scoring blitz

**Execution**:
1. **Fuel Scoring**:
   - All robots score simultaneously
   - Human players feed maximum fuel
   - Pre-positioned fuel from Shift 4
   - Target: 20-30+ fuel in 30 seconds

2. **Tower Climbing**:
   - Final opportunity for Level 3 (30 points)
   - G420 protects robots on tower (opponent contact = Level 3 awarded)
   - All 3 robots should attempt highest level possible
   - **Critical**: Must maintain position until match end (points assessed 3 seconds after timer hits 0:00)
   - Target: 50+ tower points (TRAVERSAL RP)

3. **Coordination**:
   - Avoid robot collisions
   - Clear scoring lanes
   - Human players maximize fuel delivery

**End Game Priorities**:
1. Tower climbing (highest point value)
2. Fuel scoring (both hubs active)
3. Avoid penalties (G420 tower protection rule)

---

## Robot Capability Priorities

### Tier 1: Essential Capabilities
1. **Reliable Fuel Scoring**
   - Accurate shooting/placing into 41.7" hexagonal opening
   - 72" height requirement
   - High capacity (8+ fuel)
   - Fast cycle time (2-3 seconds per fuel)

2. **Tower Climbing (Level 1 minimum)**
   - 27" LOW RUNG height
   - Reliable mechanism
   - Fast climb time (<10 seconds)

### Tier 2: Competitive Advantages
3. **Tower Climbing (Level 2)**
   - 45" MID RUNG height
   - 20 points (vs 10 for Level 1)
   - More complex mechanism

4. **High-Capacity Fuel System**
   - 12+ fuel capacity
   - Enables sustained scoring during active shifts

5. **Fast Fuel Collection**
   - Ground intake
   - Depot collection
   - Human player interface

### Tier 3: Elite Capabilities
6. **Tower Climbing (Level 3)**
   - 63" HIGH RUNG height
   - 30 points (highest single action value)
   - Complex mechanism, high risk/reward

7. **Autonomous Tower Climb**
   - 15 points in AUTO (vs 10 in teleop)
   - Requires exceptional autonomous programming

8. **Defensive Capability**
   - Block opponent scoring
   - Disrupt opponent tower climbs
   - Field control

---

## Alliance Strategy Considerations

### Robot Role Specialization

**Option A: Balanced Alliance (Recommended)**
- **Robot 1**: Fuel specialist (high capacity, fast scoring)
- **Robot 2**: Hybrid (fuel + Level 2 tower)
- **Robot 3**: Tower specialist (Level 3 capable)

**Option B: Fuel-Focused Alliance**
- **All 3 Robots**: Fuel scoring specialists
- **Strategy**: Overwhelm with fuel volume
- **Target**: SUPERCHARGED RP (360 fuel)
- **Risk**: Lower tower points, may miss TRAVERSAL RP

**Option C: Tower-Focused Alliance**
- **All 3 Robots**: Tower climbing capable (Level 2+)
- **Strategy**: Maximize tower points
- **Target**: TRAVERSAL RP (50 tower points)
- **Risk**: Lower fuel scoring, may miss SUPERCHARGED RP

### Alliance Coordination

**Pre-Match Planning**:
1. **Autonomous Strategy**: Coordinate fuel vs tower priorities
2. **Shift Strategy**: Plan fuel collection vs scoring roles
3. **End Game Strategy**: Coordinate tower climbs and fuel scoring

**During Match**:
1. **Communication**: Signal hub status changes
2. **Positioning**: Avoid collisions, clear lanes
3. **Fuel Management**: Coordinate human player feeds

---

## Ranking Points Optimization

### Path to 6 RP (Maximum):
1. **Win the Match** (3 RP) - Most important
2. **ENERGIZED RP** (1 RP) - ≥100 fuel (achievable)
3. **SUPERCHARGED RP** (1 RP) - ≥360 fuel (challenging)
4. **TRAVERSAL RP** (1 RP) - ≥50 tower points (achievable)

### Realistic RP Targets:

**Conservative (4-5 RP)**:
- Win (3) + ENERGIZED (1) + TRAVERSAL (1) = 5 RP
- Strategy: Balanced fuel scoring + tower climbing

**Aggressive (5-6 RP)**:
- Win (3) + ENERGIZED (1) + SUPERCHARGED (1) + TRAVERSAL (1) = 6 RP
- Strategy: Exceptional fuel scoring + tower climbing

**Fallback (1-3 RP)**:
- Tie (1) or Win (3)
- Strategy: Focus on match win if RP thresholds unattainable

---

## Tower Scoring Timing - Critical Understanding

**Important**: Tower points are NOT awarded during shifts - they are assessed at the END of the match.

### Tower Point Assessment Rules:
- **Level 1 (AUTO)**: Assessed after timer displays 0:00 following AUTO
- **Level 2 & 3 (TELEOP)**: Assessed 3 seconds after timer displays 0:00 following TELEOP, or when all robots come to rest, whichever happens first

### Strategic Implications:
1. **Robots CAN climb during shifts** - Level 2 and 3 can be achieved anytime during TELEOP
2. **BUT points are assessed at match end** - Robot must maintain position until assessment
3. **Early climbs lock out fuel scoring** - Robot on tower cannot score fuel for the rest of the match
4. **End game climbs are generally better** - Robot can score fuel during all active shifts, then climb at end
5. **G420 protection** - In final 30 seconds, opponent contact with robot on tower = Level 3 awarded

### Recommended Strategy:
**Primary Strategy: End Game Climbing (Recommended)**
- Climb during end game (0:30-0:00) - minimal time to fall, maximum fuel scoring opportunity
- Robot contributes fuel scoring during all active hub shifts
- Math: 20-30 fuel scored (20-30 points) + tower (20-30 points) = 40-60 points
- Early climb math: Tower (20-30 points) + 0 fuel = 20-30 points (worse!)

**When Early Climbing Might Make Sense (Rare):**
- Robot is incapable of fuel scoring (broken mechanism, can't reach hub height)
- Alliance has excess fuel scoring capacity (other 2 robots can handle all fuel needs)
- Robot is unreliable at fuel scoring but very reliable at staying on tower
- Need tower points for TRAVERSAL RP and willing to sacrifice fuel scoring

**Key Insight**: A robot that climbs early and stays on tower for 2+ minutes gives up 20-30+ potential fuel points. Tower Level 2 (20 points) or Level 3 (30 points) is roughly equivalent to 20-30 fuel, so early climbing only makes sense if the robot can't score fuel effectively anyway.

---

## Critical Rules Impacting Strategy

### G407: Scoring Zone Restriction
- **Rule**: Must score from ALLIANCE ZONE
- **Impact**: Limits long-range scoring
- **Strategy**: Position robots in alliance zone before scoring

### G408: Don't Catch Fuel
- **Rule**: Can't catch fuel directly from hub exits
- **Impact**: Prevents "hub camping" strategy
- **Strategy**: Collect fuel after it contacts field/other objects

### G420: Tower Protection (Final 30s)
- **Rule**: Contacting opponent on tower = Level 3 awarded
- **Impact**: Protects tower climbers in end game
- **Strategy**: Avoid opponent tower in end game, or use defensively

### G419: Collusion Prevention
- **Rule**: Can't isolate major game elements
- **Impact**: Prevents "field lockdown" strategies
- **Strategy**: Focus on scoring, not blocking

---

## Match Flow Optimization

### Ideal Match Flow:

**AUTO (0:20 - 0:00)**:
- Score 12-15 fuel
- Win autonomous (hub control)
- Optional: 1-2 robots climb tower Level 1

**Transition (2:20 - 2:10)**:
- Score 3-5 fuel (both hubs active)
- Position for shifts

**Shift 1 (2:10 - 1:45)** - Hub INACTIVE:
- Collect 15-20 fuel
- **Do NOT climb tower yet** - would lock robot out of fuel scoring (20-30 fuel = 20-30 points vs tower 20 points)

**Shift 2 (1:45 - 1:20)** - Hub ACTIVE:
- Score 10-12 fuel
- Collect additional fuel

**Shift 3 (1:20 - 0:55)** - Hub INACTIVE:
- Collect 15-20 fuel
- Position for end game

**Shift 4 (0:55 - 0:30)** - Hub ACTIVE:
- Score 8-10 fuel
- Stockpile fuel for end game
- Position for tower climbs

**End Game (0:30 - 0:00)** - Both ACTIVE:
- Score 20-25 fuel
- All 3 robots climb tower (Level 3, 3, 2 = 80 points if maintained until match end)
- **Note**: Tower points assessed at match end, not during end game
- Total: 60-70 fuel + 80 tower = 140-150 match points

---

## Tournament Ranking Strategy

### Qualification Matches:
- **Goal**: Maximize RP to become alliance captain
- **Strategy**: Consistent high RP (4-5 RP per match)
- **Focus**: Win matches + hit RP thresholds

### Alliance Selection:
- **As Captain**: Select complementary robots
  - Fuel specialist if you're tower-focused
  - Tower specialist if you're fuel-focused
  - Balanced robot for flexibility

### Playoff Matches:
- **Goal**: Win matches (RP less important)
- **Strategy**: Adapt to opponent strengths/weaknesses
- **Focus**: Match wins over RP thresholds

---

## Risk Management

### High-Risk Strategies:
1. **Tower-Focused AUTO**: Risk losing hub control
2. **Level 3 Tower Climb**: Complex, may fail
3. **Defensive Play**: May result in penalties
4. **Fuel Stockpiling**: Risk of penalties if over-collected

### Mitigation:
1. **Redundancy**: Multiple robots capable of same tasks
2. **Reliability**: Test mechanisms thoroughly
3. **Flexibility**: Adapt strategy based on match flow
4. **Rule Compliance**: Understand and follow all rules

---

## Key Takeaways

1. **Autonomous is Critical**: Win AUTO = hub control advantage
2. **Hub Alternation Creates Windows**: Exploit active periods, prepare during inactive
3. **End Game is Decisive**: Both hubs active = maximum scoring
4. **Tower Climbing is High-Value**: Level 3 = 30 points, protected in end game
5. **RP Thresholds Matter**: ENERGIZED (100), SUPERCHARGED (360), TRAVERSAL (50)
6. **Balance is Key**: Fuel scoring + tower climbing > either alone
7. **Reliability > Complexity**: Consistent performance wins matches
8. **Alliance Coordination**: Communication and role clarity essential

---

## Recommended Development Priorities

### Phase 1 (Weeks 1-3): Core Capabilities
- Reliable fuel scoring mechanism
- Tower Level 1 climb
- Autonomous fuel scoring (5-8 fuel)

### Phase 2 (Weeks 4-6): Competitive Edge
- Tower Level 2 climb
- High-capacity fuel system (12+)
- Autonomous tower Level 1
- Fast fuel collection

### Phase 3 (Weeks 7-8): Elite Features
- Tower Level 3 climb
- Defensive capabilities
- End game optimization
- Alliance coordination systems

---

## Robot Actions: What Robots CAN and CANNOT Do

This section provides a comprehensive reference of actions robots are permitted and prohibited from performing during matches, organized by category.

---

## Actions Robots CAN Do

### Fuel Scoring & Collection

1. **Score fuel into hub** - Launch/place fuel into the hexagonal opening of the hub (must be in ALLIANCE ZONE when launching)
2. **Collect fuel from neutral zone** - Pick up fuel scattered in the neutral zone
3. **Collect fuel from depot** - Gather fuel from the depot (24 fuel staged per alliance)
4. **Collect fuel from field** - Pick up fuel that has been processed through the hub and distributed
5. **Control unlimited fuel** - After match start, robots may control any number of fuel simultaneously
6. **Pre-load fuel** - Start match with up to 8 fuel fully supported by the robot
7. **Deliver fuel to human player** - Push fuel into the CORRAL (bottom opening of OUTPOST) for human player to collect
8. **Receive fuel from human player** - Collect fuel that human player feeds through CHUTE or throws from OUTPOST AREA
9. **Bulldoze fuel** - Inadvertent contact with fuel while moving (not considered "control")
10. **Deflect fuel** - Be hit by fuel that bounces off robot in random direction (not considered "control")

### Tower Climbing

11. **Climb tower Level 1** - During AUTO only, max 2 robots (robot no longer touching carpet or TOWER BASE)
12. **Climb tower Level 2** - During TELEOP (robot's BUMPERS completely above LOW RUNG)
13. **Climb tower Level 3** - During TELEOP (robot's BUMPERS completely above MID RUNG)
14. **Contact tower RUNGS** - Must contact RUNGS or UPRIGHTS to earn tower points
15. **Contact tower UPRIGHTS** - May contact UPRIGHTS to earn tower points
16. **Contact tower WALL** - May additionally contact TOWER WALL
17. **Contact tower support structure** - May contact support structures
18. **Contact FUEL on tower** - May contact FUEL while on tower
19. **Contact another robot on tower** - May contact another robot (alliance or opponent) while on tower
20. **Climb during end game** - Final 30 seconds is prime time for tower climbing

### Field Navigation & Obstacles

21. **Drive over BUMPS** - Navigate over the 6.5" tall obstacles
22. **Drive under TRENCHES** - Navigate under the 40.25" tall structures (22.25" clearance)
23. **Cross CENTER LINE** - During TELEOP (with restrictions in AUTO)
24. **Drive in ALLIANCE ZONE** - Move within own alliance zone
25. **Drive in NEUTRAL ZONE** - Move in neutral zone between alliances
26. **Drive in opponent ALLIANCE ZONE** - Move in opponent's zone (with restrictions)
27. **Leave ROBOT STARTING LINE** - Move away from starting position after match begins

### Opponent Interaction (Defensive/Offensive)

28. **Contact opponent robot with BUMPERS** - Bumper-to-bumper contact is allowed
29. **Push opponent robot** - Move opponent robot by contact (within rules)
30. **Block opponent access** - Single robot can block access to areas (within rules)
31. **Play defense** - Interfere with opponent scoring attempts (within rules)
32. **Push opponent outside ALLIANCE ZONE** - While they're attempting to score (standard gameplay)
33. **Contact opponent robot** - Normal robot-to-robot interaction is allowed
34. **PIN opponent robot** - Prevent opponent movement by contact (max 3 seconds)
35. **Collide with opponent** - Accidental collisions are part of full-contact gameplay

### Autonomous Period Actions

36. **Operate autonomously** - Run pre-programmed instructions without driver control
37. **Score fuel autonomously** - Score fuel into hub during AUTO
38. **Collect fuel autonomously** - Gather fuel from depot, neutral zone, or human player
39. **Climb tower Level 1 autonomously** - Max 2 robots can climb during AUTO
40. **Cross CENTER LINE** - During AUTO (but cannot contact opponent if completely across)

### General Movement & Positioning

41. **Drive anywhere on field** - Move to any location within field boundaries
42. **Position for scoring** - Move to optimal positions for fuel scoring or tower climbing
43. **Position for defense** - Move to block or interfere with opponents
44. **Park/stationary** - Remain in one location (within rules)
45. **Extend mechanisms** - Deploy scoring/climbing mechanisms (within expansion limits)

### Fuel Processing

46. **Collect fuel after hub processing** - Gather fuel after it contacts field/other objects (not directly from hub exit)
47. **Redirect fuel indirectly** - After fuel contacts something else, can push/redirect it

---

## Actions Robots CANNOT Do

### Fuel Scoring Restrictions

1. **Score from outside ALLIANCE ZONE** - Cannot launch fuel into hub unless BUMPERS are partially or fully within ALLIANCE ZONE (G407)
2. **Catch fuel directly from hub exit** - Cannot gain control of fuel released by hub until it contacts something else (G408)
3. **Sit under hub to catch fuel** - Cannot intentionally position under hub to collect fuel directly (G408)
4. **Redirect fuel directly from hub** - Cannot push/redirect fuel immediately from hub exit (G408)
5. **Score into inactive hub** - Fuel scored into inactive hub earns 0 points (but not a violation)

### Fuel Misuse

6. **Launch fuel at opponent robots** - Cannot deliberately use fuel to attack opponents (G404)
7. **Use fuel to climb tower** - Cannot use fuel to elevate robot for tower climbing (G404)
8. **Position fuel to block opponent tower** - Cannot use fuel to impede opponent tower access (G404)
9. **Intentionally eject fuel from field** - Cannot deliberately cause fuel to leave field (except through OUTPOST) (G405)
10. **Damage fuel** - Cannot gouge, tear, or mark fuel (G406)

### Tower Climbing Restrictions

11. **Climb on alliance robots** - Cannot fully support weight of alliance robot to climb tower (G414)
12. **Use fuel to assist climbing** - Cannot use fuel as elevation aid for tower climbing (G404)
13. **Contact opponent on tower (final 30s)** - Cannot contact opponent robot on their tower during last 30 seconds (G420)

### Field Element Interaction

14. **Grab field elements** - Cannot grab any field element except RUNGS/UPRIGHTS (G412)
15. **Grasp field elements** - Cannot grasp any field element except RUNGS/UPRIGHTS (G412)
16. **Attach to field elements** - Cannot attach to field elements (including vacuum/hook fastener to carpet) (G412)
17. **Become entangled with field** - Cannot become entangled with field elements (except RUNGS/UPRIGHTS) (G412)
18. **Suspend from field elements** - Cannot suspend from field elements (except RUNGS/UPRIGHTS) (G412)
19. **Damage field elements** - Cannot damage any field element (G411)
20. **Climb on field elements** - Cannot climb on or inside field elements (except tower) (G103)
21. **Hang from field elements** - Cannot hang from field elements (G103)
22. **Manipulate field elements** - Cannot manipulate field elements so they don't return to original shape (G103)

### Expansion & Safety Limits

23. **Exceed expansion limits** - Cannot extend beyond horizontal/vertical limits (except due to damage) (G413)
24. **Lift BUMPERS out of BUMPER ZONE** - Extensions cannot lift BUMPERS out of zone via carpet/BUMPS/TOWER BASE interaction (G410)
25. **Pose safety hazard** - Cannot be unsafe to humans, field elements, or other robots (G409)
26. **Contact outside field** - Cannot contact anything outside field (except MOMENTARY contact in CHUTE/CORRAL) (G409)
27. **Have exposed ROBOT PERIMETER** - Cannot expose corners of robot perimeter (G409)
28. **Have detached BUMPERS** - Cannot have BUMPER segments completely detach (G409)
29. **Have indeterminate team number/color** - Must maintain visible team number and alliance color (G409)

### Opponent Interaction Restrictions

30. **Damage opponent robot** - Cannot damage or functionally impair opponent robot (G416)
31. **Deliberately tip opponent** - Cannot deliberately tip over opponent robot (G417)
32. **Attach to opponent robot** - Cannot attach to opponent robot (G417)
33. **Entangle with opponent robot** - Cannot deliberately entangle with opponent robot (G417)
34. **PIN opponent >3 seconds** - Cannot prevent opponent movement for more than 3 seconds (G418)
35. **Initiate contact with non-BUMPER component** - Cannot use components outside ROBOT PERIMETER to initiate contact with opponent inside their ROBOT PERIMETER (G415)
36. **Force opponent rule violations** - Cannot force opponent to violate rules through non-standard gameplay (G210)
37. **Collude to isolate opponents** - 2+ robots cannot work together to isolate/close off major game elements (G419)
38. **Block both TRENCHES** - Cannot block both trenches to prevent zone access (G419)
39. **Block both BUMPS** - Cannot block both bumps to prevent zone access (G419)
40. **Quarantine all opponents** - Cannot isolate all opponents to small area (G419)
41. **Shut down all scoring elements** - Cannot prevent access to all scoring elements (G419)
42. **Prevent access to opponent tower** - Cannot block all access to opponent tower (G419)

### Autonomous Period Restrictions

43. **Contact opponent across CENTER LINE** - In AUTO, if BUMPERS completely across CENTER LINE, cannot contact opponent robot (G403)
44. **Intentionally interfere with opponent AUTO** - Cannot cross CENTER LINE in AUTO to interfere with opponent (G211)

### General Prohibitions

45. **Intentionally detach parts** - Cannot intentionally leave parts on field (G209)
46. **Trigger scoring sensors** - Cannot interfere with FMS or field operation (G211)
47. **Exploit 3-second assessment window** - Cannot use post-match assessment window to avoid rule violations (G211)
48. **Intentionally exceed expansion for strategy** - Cannot intentionally over-extend to climb tower or block field (G211, G413)
49. **Intentionally score large quantity from NEUTRAL ZONE** - Cannot exploit scoring from neutral zone (G211)

### Fuel Collection Restrictions

50. **Control fuel directly from hub** - Cannot gain control of fuel immediately from hub exit (must wait for contact with field/other objects) (G408)

---

## Notes on Permitted Actions

### Contact with Opponents
- **Bumper-to-bumper contact** is generally allowed and part of full-contact gameplay
- **Normal collisions** are permitted
- **Unintended tipping** from normal contact is not a violation
- **Defensive play** (pushing opponent outside zone while they score) is standard gameplay

### Field Navigation
- Robots can drive anywhere on the field
- Can navigate obstacles (BUMPS, TRENCHES) freely
- Can cross CENTER LINE during TELEOP
- Can enter opponent zones (with scoring restrictions)

### Fuel Handling
- Can control unlimited fuel after match start
- Can collect from multiple sources (depot, neutral zone, hub exits after processing, human players)
- Must be in ALLIANCE ZONE when launching fuel into hub
- Cannot catch fuel directly from hub exits

### Tower Climbing
- Must contact RUNGS or UPRIGHTS
- Can contact additional elements (TOWER WALL, support structure, FUEL, other robots)
- Cannot use fuel to assist climbing
- Cannot be supported by alliance robots

---

*This analysis is based on the 2026 FRC Game Manual. Teams should verify all rules and thresholds with official sources and team updates.*

