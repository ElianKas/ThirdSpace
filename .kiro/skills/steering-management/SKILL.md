---
name: steering-management
description: Maintain and improve project steering documents as a software project evolves. Use when reviewing project context, documenting approved architectural decisions, updating established conventions, or reconciling steering files with significant project changes.
---

# Steering Management

## Goal

Keep project steering documents accurate, concise,
consistent, and useful throughout development.

Steering describes long-lived project context,
decisions, and conventions.

Do not use steering as a changelog, task tracker,
or exhaustive feature inventory.

## 1. Determine Whether an Update Is Needed

Evaluate changes to:
- Product goals or fundamental requirements
- Technology stack and architecture
- Repository organization and module boundaries
- Established development conventions
- Build, testing, and verification practices
- Security and operational constraints

Do not update steering for ordinary implementation
details, small bug fixes, or routine feature additions.

Only proceed when the change affects long-lived
project context.

## 2. Inspect Current State

Read the relevant steering documents and inspect
the actual project when necessary.

Identify:
- Existing documented facts
- Previously approved decisions
- Established conventions
- Outdated or contradictory statements
- Missing information with lasting relevance

Distinguish verified facts, approved decisions,
inferences, and unknowns.

Do not assume that existing steering is correct
merely because it is documented.

## 3. Determine the Appropriate Change

Choose the smallest useful adjustment:
- Update an existing statement
- Add newly established project context
- Correct outdated information
- Remove obsolete guidance
- Consolidate redundant information
- Split a document when its scope becomes unclear
- Create a new document when genuinely necessary

Do not require fixed filenames or a fixed number
of steering documents.

Preserve useful existing context.

## 4. Manage Conventions and Decisions

A repeated implementation pattern is not
automatically a binding convention.

Before documenting a new convention:
- Determine whether it is intentional.
- Check consistency with existing architecture.
- Identify relevant trade-offs.
- Obtain approval for significant new rules.

Do not silently turn incidental implementation
choices into mandatory project standards.

Respect previously approved architectural decisions.

If new evidence contradicts an approved decision,
report the conflict and request guidance.

## 5. Maintain Context Quality

Keep steering:
- Concise and actionable
- Relevant across multiple development tasks
- Consistent with the actual project
- Free of unnecessary duplication
- Organized according to project needs

Avoid copying:
- Full implementation histories
- Large code examples
- Temporary debugging notes
- Individual task reports
- Exhaustive lists of minor features

Prefer linking to dedicated documentation when
detailed information is necessary.

## 6. Respect Steering Inclusion

Inspect the inclusion configuration of existing
steering documents.

Consider whether information should be:
- Always available
- Loaded for relevant files or contexts
- Retrieved explicitly when needed

Avoid placing large amounts of rarely needed
information into always-included context.

Do not assume every steering document is
automatically available to every custom agent.

Verify relevant agent resource configuration.

## 7. Approval and Execution

Before modifying steering:
- Summarize the proposed changes.
- Identify affected documents.
- Explain why the changes are necessary.
- Request approval for significant architectural
  or convention changes.

After approval:
- Delegate file modifications to the builder.
- Delegate independent verification to the reviewer.
- Confirm consistency with the actual project.
- Report what changed and what remains uncertain.

Do not modify unrelated application code.

## 8. Expected Output

When reviewing steering, provide:

1. Relevant Project Changes
2. Current Steering Assessment
3. Proposed Updates
4. Decisions Requiring Approval
5. Verification Approach

If no update is necessary, state that briefly
without creating or modifying files.
