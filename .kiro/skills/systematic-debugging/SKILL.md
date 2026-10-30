---
name: systematic-debugging
description: Systematically investigate software bugs, runtime errors, failing tests, and unexpected behavior. Use when diagnosing failures or fixing defects in any programming language or platform.
---

# Systematic Debugging

## Goal

Identify the root cause of a problem before applying a fix.

## Investigation

1. Reproduce the reported problem when feasible.
2. Collect relevant error messages, logs, and observations.
3. Identify the affected components and dependencies.
4. Distinguish verified facts from hypotheses.
5. Form a specific, testable root-cause hypothesis.
6. Test the hypothesis before modifying code.

## Implementation

1. Prefer the smallest change that addresses the root cause.
2. Preserve existing behavior outside the affected scope.
3. Avoid speculative fixes and unrelated refactoring.
4. Follow the project's existing conventions.
5. Respect agent permissions and approval requirements.

## Verification

1. Rerun the failing test or reproduction scenario.
2. Verify the original problem is resolved.
3. Run relevant regression checks.
4. Report what was actually executed.
5. Document any remaining uncertainty.

## Failure Handling

- Do not repeatedly apply unverified fixes.
- After three unsuccessful correction attempts, stop.
- Summarize attempted approaches and request guidance.

## Reporting

Provide:
- Observed symptoms
- Verified root cause or remaining hypotheses
- Changes made, if authorized
- Verification results
- Remaining risks
