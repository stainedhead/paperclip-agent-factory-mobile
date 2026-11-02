# Plan: Phase 2i iPhone UI

**Date:** 2026-11-02 · **Status:** Planning

## Development approach

TDD (red, green, refactor) with one commit per task. Demo graph first. One milestone per screen family; each screen gets its model test, a view preview and an XCUITest flow in the same commit group. Swift lint and format run on every commit. The stream works in its own git worktree so it can build in parallel with Phase 1b.

## Phase breakdown

1. 2i-0 shell and design system.
2. 2i-1 onboarding and factories.
3. 2i-2 Inbox.
4. 2i-3 Decision, Approval, Budget.
5. 2i-4 Factory home, Agents, Run viewer.
6. 2i-5 Work and Issue.
7. 2i-6 polish, accessibility, screenshots, docs.

## Critical path

2i-0 -> everything else. 2i-2 and 2i-3 are the core operator value; 2i-6 last.

## Testing strategy

Swift Testing per model (state mirroring, intents, lifecycle); XCUITest per screen family on the demo graph with accessibility identifiers; accessibility audits; screenshot capture; localisation completeness test; SwiftLint and swift-format strict; iOS simulator runs in ios.yml.

## Rollout strategy

One pull request per phase from a feature branch off `main`. Nothing is merged without green workflows.

## Success metrics

All acceptance criteria in `spec.md` section 9 met with evidence; gates green; coverage not reduced.
