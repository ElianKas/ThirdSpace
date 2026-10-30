---
name: technology-evaluation
description: Evaluate software technology stacks, architectures, frameworks, libraries, SDKs, and dependencies for new or existing projects. Use when selecting a stack, assessing proposed technologies, comparing alternatives, or making architectural decisions before implementation.
---

# Technology Evaluation

## Goal

Recommend technologies and architectures based on project
requirements, evidence, and explicit trade-offs.

Do not automatically endorse the user's preferred stack.
Do not make architectural changes or install dependencies.

## 1. Understand Requirements

Identify:
- Product goals and core functionality
- Target platforms and environments
- Functional and non-functional requirements
- Performance, security, and reliability needs
- Offline and synchronization requirements
- Budget, timeline, and operational constraints
- Developer experience and existing expertise
- Expected maintenance and future evolution

Distinguish requirements from preferences and assumptions.

Ask focused questions when missing information could
materially change the recommendation.

## 2. Evaluate Existing Context

For existing projects:
- Inspect the current stack and architecture.
- Identify established dependencies and conventions.
- Consider migration costs and compatibility constraints.
- Prefer incremental improvements where appropriate.

For new projects:
- Identify viable architectural approaches.
- Avoid unnecessary complexity and premature scaling.

## 3. Compare Technology Options

Evaluate relevant candidates against:
- Suitability for the actual requirements
- Platform and ecosystem compatibility
- Development and maintenance complexity
- Performance and scalability
- Security and reliability
- Community and ecosystem maturity
- Vendor lock-in and portability
- Hosting, infrastructure, and operating costs
- Long-term maintainability

Include meaningful alternatives, but avoid comparisons
that do not affect the decision.

## 4. Evaluate Dependencies

For proposed libraries, SDKs, and packages:
- Identify their specific purpose.
- Check compatibility with target platforms and tooling.
- Consider maintenance status and release maturity.
- Examine licensing and security implications.
- Evaluate integration complexity and transitive dependencies.
- Prefer existing project dependencies when suitable.
- Avoid adding packages for trivial functionality.

Never invent package versions, compatibility claims,
maintenance status, or benchmark results.

When current documentation cannot be accessed, clearly
identify which claims require external verification.

## 5. Make a Recommendation

Provide:
- Recommended architecture and technology stack
- Justification for major choices
- Important alternatives and trade-offs
- Proposed dependencies and their responsibilities
- Risks, unknowns, and verification needs
- Optional proof-of-concept recommendations

Clearly separate:
- Verified findings
- Reasoned recommendations
- Unverified assumptions

## 6. Decision and Approval

Present significant architectural decisions for user approval.

Do not:
- Initialize projects
- Install dependencies
- Modify application code
- Change infrastructure
- Commit to a technology choice on behalf of the user

After approval, the selected architecture can be documented
in the project's architecture records and project context.

## 7. Expected Output

Structure the evaluation as:

1. Requirements Summary
2. Candidate Architectures and Technologies
3. Comparison and Trade-offs
4. Recommended Stack
5. Proposed Dependencies
6. Risks and Open Questions
7. Decisions Requiring Approval

Keep the evaluation proportional to project complexity.
