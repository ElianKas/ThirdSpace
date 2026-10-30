---
name: project-bootstrap
description: Initialize or onboard a software project into the reusable multi-agent development workflow. Use when starting a new project, onboarding an existing codebase, or preparing project-specific agent configuration and context.
---

# Project Bootstrap

## Goal

Prepare a new or existing software project for the
multi-agent development workflow.

Adapt the configuration to the actual project.
Do not impose a fixed technology stack, directory
structure, or set of steering files.

## 1. Discover the Project

Determine whether the project is new or existing.

Identify:
- Product purpose and core requirements
- Target users and platforms
- Approved technology stack
- Architecture and major components
- Existing repository structure
- Development conventions
- Build, test, and validation tools
- Security and operational constraints

Inspect actual files before making assumptions.

Distinguish verified facts, approved decisions,
preferences, and unresolved questions.

## 2. Resolve Architectural Decisions

Check whether substantial technology and architecture
decisions have been approved.

If important decisions remain unresolved:
- Delegate technology evaluation to the planner.
- Present recommendations and trade-offs.
- Request approval before proceeding.

Do not silently select or replace major technologies.

## 3. Design Initial Project Steering

Propose steering documents appropriate to the project.

Include, where relevant:
- A concise product and project overview
- Approved technologies and architecture
- Established development conventions
- Repository organization and module responsibilities
- Verification and testing practices
- Important constraints and safety requirements

Guidelines:
- Do not require fixed filenames.
- Do not create empty placeholder documents.
- Prefer a small number of focused documents.
- Split documents only when it improves clarity.
- Avoid duplicating global agent instructions.
- Distinguish existing structure from planned structure.
- Do not treat implementation choices as approved
  conventions without sufficient evidence or approval.

The steering-management skill owns ongoing maintenance
of steering documents after initialization.

## 4. Configure Project-Specific Capabilities

Review the existing agent configuration.

Preserve the core responsibilities:
- Orchestrator: coordination and approval
- Planner: architecture and planning
- Builder: implementation and verification
- Reviewer: independent review

Keep core agent definitions technology-independent.

Recommend project-specific skills only when they provide
a clear benefit.

Prefer project steering and dedicated skills over
embedding framework-specific rules in core agents.

Do not weaken permissions or safety constraints
without explicit approval.

## 5. Establish Verification Strategy

Identify available build and test infrastructure.

Recommend appropriate verification methods.

Never invent build commands, installed tools,
or successful test results.

Mark unverified commands and capabilities clearly.

## 6. Propose Before Implementing

Present a bootstrap proposal containing:
- Confirmed project context
- Unresolved questions
- Proposed steering documents
- Proposed agent or skill adjustments
- Verification strategy
- Risks and approval requirements

Wait for explicit user approval before creating
or modifying project configuration.

## 7. Execute Approved Bootstrap

After approval:
- Delegate file changes to the builder.
- Apply only approved configuration changes.
- Preserve existing project conventions.
- Delegate independent verification to the reviewer.
- Report actual changes and verification results.

Do not deploy, publish, push, or modify production data
without explicit approval.

## 8. Handoff to Ongoing Development

After successful bootstrap:
- Record remaining unresolved decisions.
- Identify the next development task.
- Use steering-management for future updates to
  long-lived project context.
- Do not rerun the full bootstrap for ordinary features.

## Expected Output

1. Project Discovery
2. Architecture Status
3. Proposed Steering Structure
4. Recommended Skills and Configuration
5. Verification Strategy
6. Approval Requirements
7. Next Steps
