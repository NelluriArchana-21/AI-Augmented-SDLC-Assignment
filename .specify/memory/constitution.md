<!--
Sync Impact Report
Version change: 0.0.0 → 1.0.0
Modified principles: none (initial project constitution)
Added sections: Additional Constraints; Development Workflow
Removed sections: none
Deferred items: none
-->

# AI-Augmented SDLC Constitution

## Core Principles

### I. Spec-First Delivery
Every requirement MUST be captured as an explicit specification before implementation begins. This repository will use the Spec Kit workflow to define scope, acceptance criteria, contracts, and validation expectations before code is written. This requirement exists so that behavior is reviewable, traceable, and aligned with the assignment’s learning goals instead of being inferred from implementation alone.

### II. Deterministic and Traceable Execution
Each application in this repository MUST expose clear, explicit contracts for inputs, outputs, and state changes. The deterministic task manager MUST define task behavior precisely, and the reminder agent MUST normalize ambiguous natural-language inputs into structured JSON with explicit assumptions. Deterministic contracts make validation, harness checks, and human review reproducible.

### III. Test-First and Evidence-Based Validation
No feature, fix, or refactor is complete until it is backed by a failing validation check that is then resolved. Automated tests, contract checks, and independent harness validation MUST be used to confirm behavior. This principle requires evidence rather than confidence and prevents unverified changes from being merged.

### IV. Independent Safety and Security
All work in this repository MUST avoid committing credentials, API keys, secrets, or undocumented dependencies. Independent validation and harness-based checks MUST be used wherever feasible to assess compliance without relying on the same implementation path. This protects the repository and ensures the evaluation remains trustworthy.

### V. Simplicity, Maintainability, and Reviewability
The codebase MUST favor clear, minimal, and explainable designs over clever or hidden abstractions. When complexity is required, it MUST be justified in the specification and review process. Explicit naming, narrow responsibilities, and readable structure are required so that future changes remain low-risk and comprehensible.

## Additional Constraints

This repository is organized around two applications with distinct responsibilities:

- Part 1A: a deterministic Smart Todo Management System built with Java 21, Spring Boot, Maven, and JUnit 5.
- Part 1B: a reminder agent that interprets natural-language inputs and returns structured JSON outputs.
- All work MUST align with the assignment’s spec-driven development model and independent harness validation approach.
- No secrets, credentials, or sensitive runtime values may be committed to version control.
- Acceptance evidence MUST include relevant tests, contract checks, and any independent validation used to confirm correctness.

## Development Workflow

All work in this repository MUST follow the Spec Kit lifecycle in order:

1. Constitution
2. Specify
3. Clarify
4. Plan
5. Tasks
6. Analyze
7. Implement
8. Independent harness validation
9. Feedback and repair
10. Revalidation and human acceptance

This sequence is mandatory because each phase produces the evidence and decision points needed for a reliable SDLC. Work may not skip ahead from implementation to acceptance without the corresponding validation artifacts.

## Governance

This constitution is the governing baseline for all repository work. It supersedes informal practices and undocumented assumptions. Any change to scope, behavior, architecture, or acceptance criteria MUST be reflected in the governing specification and reviewed before it is treated as final.

Amendments MUST follow a documented change process: capture the reason for change, update the constitution text, assign a SemVer-style version bump, and record the effective date. The repository must not silently change governance rules without traceable rationale.

Versioning policy:
- MAJOR: backward-incompatible governance or principle changes
- MINOR: new principle or material expansion of existing guidance
- PATCH: clarifications, wording fixes, and non-semantic refinements

Compliance review expectations:
- Every pull request or change set MUST show how it aligns with the relevant specification and validation evidence.
- Deviations from this constitution require explicit rationale and review approval.
- Unexplained omissions, skipped validation, or undocumented exceptions are non-compliant.

**Version**: 1.0.0 | **Ratified**: 2026-11-07 | **Last Amended**: 2026-11-07
