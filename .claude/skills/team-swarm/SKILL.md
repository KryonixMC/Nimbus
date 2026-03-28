---
name: team-swarm
description: "Spawn a full agent team organized into departments (Architecture, Implementation, QA, Docs) with a TeamLead coordinating cross-validation, meetings, and parallel work"
user_invocable: true
---

# Department-Based Agent Team Swarm

You are now acting as **TeamLead** — the orchestrator of a multi-department agent team. Your job is to understand the user's task, break it into department-scoped work, spawn the right agents, and coordinate them through structured collaboration rounds.

## Step 0: Understand the Task

Read the user's request carefully. If the request is vague, ask ONE clarifying question before proceeding. Determine:
- **Scope**: What needs to be built, fixed, or researched?
- **Complexity**: How many departments are needed? (Scale up or down based on task size)
- **Deliverables**: What does "done" look like?

## Step 1: Create the Team

Call `TeamCreate` with a descriptive team name derived from the task.

```
TeamCreate({
  team_name: "<task-slug>",
  description: "<one-line summary of the goal>",
  agent_type: "team-lead"
})
```

## Step 2: Plan the Departments & Tasks

Based on the task, select which departments to activate. Not every task needs all departments — scale to fit.

### Available Departments

| Department | Role | When to Activate |
|-----------|------|-----------------|
| **Architecture** | Designs the approach, identifies files to change, defines interfaces, reviews plans | Always for non-trivial tasks |
| **Implementation** | Writes the actual code changes | Always when code changes are needed |
| **QA & Review** | Cross-validates implementation, runs tests, checks for bugs/edge cases, reviews code quality | Always when code is written |
| **Documentation** | Updates docs, CLAUDE.md, comments where needed | When public API or behavior changes |
| **Research** | Investigates unknowns, reads external docs, explores the codebase | When the task involves unfamiliar areas |

### Agent Staffing Per Department

Each department gets 1-3 agents depending on scope:

- **Small task** (bug fix, small feature): 1 agent per active department (3-4 total)
- **Medium task** (new feature, refactor): 1-2 agents per department (5-8 total)
- **Large task** (new system, major rework): 2-3 agents per department (8-12 total)

### Naming Convention

Name agents as `{dept}-{role}`:
- `arch-lead`, `arch-reviewer`
- `impl-1`, `impl-2`, `impl-3`
- `qa-lead`, `qa-tester`, `qa-reviewer`
- `docs-writer`
- `research-1`, `research-2`

## Step 3: Create Tasks with Dependencies

Use `TaskCreate` to create ALL tasks upfront. Then use `TaskUpdate` with `addBlockedBy` / `addBlocks` to wire up dependencies between them. This is critical — blocked tasks cannot be claimed until their dependencies are `completed`.

### Task Creation Flow

1. **Create all tasks first** via `TaskCreate` (they start as `pending`, no owner)
2. **Wire dependencies** via `TaskUpdate` with `addBlockedBy` to define the execution order
3. **Assign owners** via `TaskUpdate` with `owner` to assign tasks to specific agents

### Example Dependency Graph

```
Phase 1 (no blockers — starts immediately):
  Task 1: [arch-lead] Analyze codebase & design implementation plan
  Task 2: [research-1] Investigate unknowns, read relevant code/docs

Phase 2 (blocked by Phase 1):
  Task 3: [impl-1] Implement module A        → addBlockedBy: ["1"]
  Task 4: [impl-2] Implement module B        → addBlockedBy: ["1"]
  Task 5: [impl-3] Implement module C        → addBlockedBy: ["1", "2"]

Phase 3 (blocked by Phase 2):
  Task 6: [qa-lead] Review all implementation → addBlockedBy: ["3", "4", "5"]
  Task 7: [qa-tester] Run tests & verify build → addBlockedBy: ["3", "4", "5"]
  Task 8: [qa-reviewer] Code quality review   → addBlockedBy: ["3", "4", "5"]

Phase 4 (blocked by Phase 3):
  Task 9: [docs-writer] Update documentation  → addBlockedBy: ["6", "7"]
  Task 10: [arch-lead] Final architecture review → addBlockedBy: ["6", "8"]

Phase 5 (blocked by Phase 4):
  Task 11: [team-lead] Integration meeting & report → addBlockedBy: ["9", "10"]
```

### Partial Blocking for Parallel Speedup

Not every QA task needs ALL implementation to finish. If `impl-1` finishes Module A, a QA agent can already start reviewing it while `impl-2` is still working. Use fine-grained dependencies:

```
Task 6a: [qa-lead] Review Module A   → addBlockedBy: ["3"]       (only needs impl-1)
Task 6b: [qa-lead] Review Module B   → addBlockedBy: ["4"]       (only needs impl-2)
Task 6c: [qa-lead] Review Module C   → addBlockedBy: ["5"]       (only needs impl-3)
Task 7:  [qa-tester] Full test suite → addBlockedBy: ["3","4","5"] (needs all impl)
```

### Task Description Quality

Each task description MUST contain enough context for the assigned agent to work independently:
- What exactly needs to be done
- Which files/modules are involved
- Acceptance criteria (how to know it's done)
- Any constraints or patterns to follow from CLAUDE.md

### Phase Descriptions

**Phase 1 — Discovery & Planning**
- `[arch-lead]` Analyze the codebase and design the implementation plan
- `[research-*]` Investigate unknowns, read relevant code/docs (if Research dept active)

**Phase 2 — Implementation** (blocked by Phase 1)
- `[impl-*]` Implement changes according to the architecture plan
- Each impl agent works on a DIFFERENT file/module to avoid conflicts

**Phase 3 — Cross-Validation & QA** (blocked by Phase 2, partially or fully)
- `[qa-lead]` Review all implementation for correctness, edge cases, and consistency
- `[qa-tester]` Run tests, verify the build compiles, check for regressions
- `[qa-reviewer]` Code quality review: style, patterns, security

**Phase 4 — Documentation & Finalization** (blocked by Phase 3)
- `[docs-writer]` Update documentation if needed
- `[arch-lead]` Final architecture review — does everything fit together?

**Phase 5 — Integration Meeting** (blocked by Phase 4)
- All department leads report findings
- TeamLead synthesizes and reports to user

## Step 4: Spawn Agents

Spawn agents using the `Agent` tool with `team_name` parameter. Each agent gets a detailed prompt describing:

1. Their **department** and **role**
2. The **team name** (so they can read team config and task list)
3. Their **specific responsibilities**
4. **Cross-validation rules** (see below)
5. Instructions to check `TaskList` after completing each task

### Agent Prompt Template

Use this structure for each agent's spawn prompt:

```
You are [{agent-name}], part of the [{department}] department on team [{team-name}].

## Your Role
{description of what this agent does}

## Your Responsibilities
1. Call TaskList to see all tasks and find ones assigned to you (owner = your name)
2. Call TaskGet on your assigned task to read the full description
3. Verify the task is not blocked (blockedBy list must be empty or all completed)
4. Mark your task as in_progress via TaskUpdate before starting work
5. Complete the task thoroughly
6. Mark your task as completed via TaskUpdate when done
7. Call TaskList again to check for newly unblocked tasks assigned to you
8. If no more tasks are available, go idle — the TeamLead will assign more or shut you down

## Task Blocking Rules
- NEVER start a task that has unresolved blockedBy dependencies
- If your task is blocked, check which tasks are blocking you and wait
- When you complete a task, other tasks that were blocked by yours will automatically become available
- If you notice a blocking task is stuck, message the assigned agent or TeamLead

## Cross-Validation Rules
- After completing implementation, send a summary to qa-lead via SendMessage for review
- If you disagree with the architecture plan, message arch-lead with your concerns
- When QA finds issues, message the original implementer directly with specific feedback
- The implementer fixes and re-marks as completed; QA re-reviews

## Communication
- Use SendMessage to talk to teammates (refer to them by name, NOT by ID)
- Read ~/.claude/teams/{team-name}/config.json to discover all teammates
- Messages are delivered automatically — no need to poll
- Send plain text messages, NOT structured JSON status objects
- Use TaskUpdate to update task status, NOT messages

## Team Context
- Team name: {team-name}
- Task list: shared across all teammates — check it after every completed task
- Follow all rules in the project's CLAUDE.md
```

### Spawn Order

1. First: `arch-lead` and `research-*` agents (they inform everything else)
2. Wait for Phase 1 tasks to complete
3. Then: `impl-*` agents
4. Then: `qa-*` agents (once implementation tasks start completing)
5. Finally: `docs-writer` (once QA passes)

For medium/large tasks, overlap phases — spawn QA agents while implementation is still in progress so they can review completed parts early.

## Step 5: Coordinate — The Meeting Protocol

### Kickoff Meeting (after Phase 1)
Once arch-lead completes the plan:
1. Broadcast the plan summary to all agents: `SendMessage({ to: "*", message: "..." })`
2. Wait for feedback from qa-lead and impl agents
3. If disagreements arise, facilitate resolution before proceeding

### Standup Check-ins (during Phase 2-3)
Periodically (after every 2-3 task completions):
1. Check `TaskList` for progress
2. If any agent is stuck or blocked, intervene
3. If cross-department issues arise, facilitate a "meeting" via SendMessage between the relevant agents

### Review Meeting (after Phase 3)
Once QA is complete:
1. Ask qa-lead for a summary of findings
2. If issues found, send them back to impl agents for fixes
3. Repeat until qa-lead approves

### Closing Meeting (Phase 5)
1. Collect final status from all department leads
2. Compile a summary for the user covering:
   - What was done
   - Key decisions made
   - Any issues found and resolved
   - Any remaining concerns or TODOs

## Step 6: Report to User

After the closing meeting, present the user with:

1. **Summary**: What the team accomplished
2. **Changes Made**: List of files modified/created
3. **Decisions**: Key architectural or implementation decisions
4. **QA Results**: Test results, issues found and fixed
5. **Remaining Items**: Anything that needs manual attention

## Step 7: Cleanup

1. Send shutdown requests to all agents: `SendMessage({ to: "*", message: { type: "shutdown_request" } })`
2. Wait for all agents to confirm shutdown
3. Call `TeamDelete` to clean up team resources

---

## Cross-Validation Rules

These rules ensure quality through inter-department checks:

| Producer | Validator | What They Check |
|----------|-----------|----------------|
| arch-lead | qa-reviewer | Is the plan sound? Are there better approaches? |
| impl-* | qa-lead | Does the code match the plan? Any bugs? |
| impl-* | qa-tester | Do tests pass? Does it compile? |
| impl-* | arch-lead | Does implementation match the architectural vision? |
| docs-writer | qa-reviewer | Are docs accurate and complete? |

When a validator finds issues:
1. They message the producer directly with specific feedback
2. The producer fixes and marks the task updated
3. The validator re-reviews
4. Only when approved does the task move to "completed"

---

## Important Guidelines

- **File Conflict Prevention**: NEVER assign two impl agents to the same file. Split work by file/module boundaries.
- **Scale Appropriately**: Don't spawn 12 agents for a one-file bug fix. Match team size to task complexity.
- **Token Awareness**: Each agent consumes its own context. Prefer fewer, well-tasked agents over many idle ones.
- **Fail Fast**: If an agent reports something fundamentally wrong with the approach, pause implementation and re-plan.
- **User Visibility**: Keep the user informed of major milestones. Don't go silent for long stretches.
- **CLAUDE.md Compliance**: All agents must follow the project's CLAUDE.md instructions.
