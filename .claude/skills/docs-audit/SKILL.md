---
name: docs-audit
description: >
  Full audit of the docs/ folder — checks every documentation page against the actual codebase for accuracy,
  completeness, and organization. Finds outdated info, deprecated references, missing features, wrong file paths,
  incorrect API docs, and structural issues. Use this skill whenever the user wants to audit docs, check if
  documentation is up-to-date, verify docs accuracy, clean up stale documentation, or improve docs organization.
  Also trigger when the user mentions "docs check", "documentation review", "docs quality", "outdated docs",
  or asks if their docs are correct or complete.
---

# Documentation Audit

Perform a thorough audit of all documentation in `docs/` against the actual codebase. The goal is to ensure every piece of documentation accurately reflects the current state of the code — nothing outdated, nothing missing, nothing wrong.

## Why This Matters

Documentation that lies is worse than no documentation. Users who follow outdated instructions waste hours debugging phantom issues. Developers who see wrong API references lose trust in all docs. This audit exists to catch every mismatch between docs and reality.

## Audit Process

### Phase 1: Inventory

Start by building a map of what exists.

1. **List all doc pages** — scan `docs/` recursively for `.md` files (skip `node_modules/`, `.vitepress/cache/`, `.vitepress/dist/`)
2. **Read the VitePress config** (`docs/.vitepress/config.ts`) to understand the sidebar/nav structure
3. **List all major code areas** — scan the source tree to identify modules, packages, commands, API routes, config classes, events, and features

Create a mental checklist: every code feature should have corresponding documentation, and every doc page should describe something that actually exists.

### Phase 2: Accuracy Check (Cross-Reference)

For each documentation page, read it fully and verify every claim against the codebase. Use this systematic checklist:

#### Config Documentation (`docs/config/`)
- [ ] Every config key documented actually exists in the config data classes
- [ ] Default values shown match the actual defaults in code
- [ ] Config key names use the correct casing (snake_case)
- [ ] Example TOML blocks are valid and use current syntax
- [ ] No removed/renamed config options are still documented
- [ ] New config options added in code are documented

#### API Reference (`docs/reference/api.md`, `docs/reference/websocket.md`)
- [ ] Every endpoint listed actually exists in the route definitions
- [ ] HTTP methods are correct (GET/POST/PUT/DELETE)
- [ ] Request/response body examples match the actual data models
- [ ] Auth requirements are correctly documented
- [ ] No removed endpoints are still listed
- [ ] New endpoints are documented

#### Commands Reference (`docs/reference/commands.md`)
- [ ] Every command listed exists in the command dispatcher/registry
- [ ] Command syntax matches the actual argument parsing
- [ ] Subcommands are complete and accurate
- [ ] Command descriptions match what the code does
- [ ] No removed commands are still listed
- [ ] New commands are documented

#### Events Reference (`docs/reference/events.md`)
- [ ] Event names/types match the sealed class definitions in `Events.kt`
- [ ] Event payload fields are accurate
- [ ] No removed events are still listed
- [ ] New events are documented

#### Guide Pages (`docs/guide/`)
- [ ] File paths mentioned actually exist
- [ ] Class/function names mentioned exist in the codebase
- [ ] Version numbers are current
- [ ] Setup instructions work with the current codebase
- [ ] Feature descriptions match actual behavior
- [ ] Port ranges, naming patterns, and conventions are accurate

#### Developer Docs (`docs/developer/`)
- [ ] Architecture diagrams/trees match the actual package structure
- [ ] SDK API methods exist and have correct signatures
- [ ] Bridge plugin commands/features are accurate
- [ ] Module descriptions match current functionality

### Phase 3: Completeness Check

Identify features or components that exist in the codebase but have no documentation:

1. **Scan for undocumented commands** — compare registered commands against `docs/reference/commands.md`
2. **Scan for undocumented API endpoints** — compare route definitions against `docs/reference/api.md`
3. **Scan for undocumented config options** — compare config classes against `docs/config/`
4. **Scan for undocumented events** — compare Events.kt against `docs/reference/events.md`
5. **Scan for undocumented features** — look for major functionality not covered in any guide page
6. **Check module coverage** — every module listed in the project should have relevant docs

### Phase 4: Organization & UX/DX Quality

Check documentation structure and user experience:

#### Navigation & Structure
- [ ] VitePress sidebar matches actual file structure (no dead links)
- [ ] Page ordering makes sense (intro → basics → advanced)
- [ ] Related content is grouped logically
- [ ] No orphaned pages (exist on disk but not in sidebar)
- [ ] No dead sidebar links (in config but file missing)

#### Content Quality
- [ ] Each page has a clear purpose and doesn't overlap heavily with another
- [ ] Code examples are syntactically valid
- [ ] Internal links between pages work (relative paths are correct)
- [ ] Consistent terminology across all pages
- [ ] No placeholder/TODO content left in published docs
- [ ] Frontmatter is present and correct for VitePress

#### DX Best Practices
- [ ] Quick Start gets users running in under 5 minutes
- [ ] Common tasks are easy to find (not buried in long pages)
- [ ] Error scenarios and troubleshooting are covered
- [ ] Copy-pasteable examples where appropriate
- [ ] Config reference shows all options with types and defaults

### Phase 5: Report & Fix

#### Generate Report

Output a structured report organized by severity:

```
## Documentation Audit Report

### Critical (Wrong/Misleading Information)
Things that would cause users to fail or waste time if followed.

### Outdated (Stale but Not Harmful)
Information that's no longer accurate but won't break anything.

### Missing (Undocumented Features)
Features or options that exist but have no docs coverage.

### Organization Issues
Structural problems, dead links, navigation issues.

### Suggestions
Non-critical improvements for better UX/DX.
```

For each finding, include:
- **What's wrong** — specific description
- **Where** — file path and line/section
- **Evidence** — what the code actually says vs. what the docs say
- **Fix** — concrete suggestion or the exact change needed

#### Apply Fixes

After presenting the report, ask the user if they want you to apply fixes. When applying:

- Fix critical issues first (wrong information)
- Then outdated content
- Then add missing documentation
- Then structural improvements
- Keep changes minimal and precise — don't rewrite pages that just need a line fix
- Preserve the existing writing style and tone
- For new content, match the format and depth of surrounding docs

## Important Guidelines

- **Read the actual code, not just CLAUDE.md** — CLAUDE.md is a summary and may itself be outdated. Always verify against source files.
- **Check imports and dependencies** — a function referenced in docs might have been moved or renamed.
- **Verify examples work** — config examples should use real, valid syntax. API examples should use actual endpoints and fields.
- **Don't flag style preferences** — focus on factual accuracy. "This could be worded better" is not an audit finding unless it's actively misleading.
- **Be thorough but efficient** — use grep/glob to verify claims rather than reading every source file line by line. Target your investigation based on what the docs claim.
