# CODEX.md
Project execution rules for Codex sessions

## 1. Role of this file
This file defines how Codex should work in this repository.

Priority order when information conflicts
1. Running code and tests
2. Schema and configuration in the repository
3. README.md
4. PROJECT.md
5. Other docs

If docs disagree with code, treat code as correct and update the docs as part of the current milestone.

## 2. Engineering principles
### 2.1 Design constraints
1. Modularity  
   Keep components small and focused. Prefer clear boundaries over cleverness.

2. Separation of concerns  
   UI, API, domain logic, persistence, and infrastructure should not be tangled. Routes or controllers should validate and delegate, not contain core logic.

3. Encapsulation  
   Hide internal details behind interfaces. Expose the smallest surface needed.

4. Orthogonality  
   Features should compose without hidden coupling. New capabilities should be addable with minimal refactors.

5. Determinism and reproducibility  
   Any randomness must be controlled by explicit seeds and policies. Persist specs or recipes for any generated artifact.

6. Observability  
   Provide structured logs, clear errors, and enough tracing to debug failures quickly.

### 2.2 Correctness and safety
1. Validate inputs at boundaries  
   Prefer explicit validation with actionable errors.

2. Fail loudly and clearly  
   Use consistent error shapes and codes where applicable. Avoid silent fallback except when explicitly designed.

3. Security basics by default  
   No secret material in git. No path traversal. No arbitrary code execution hooks. Least privilege.

## 3. Repository documentation contract
### 3.1 PROJECT.md is mandatory
Maintain a `PROJECT.md` at repository root as the running engineering audit trail.

Required contents
1. Short project summary
2. Milestones checklist
3. For each milestone
   1. Date
   2. Goal
   3. What changed
   4. How to run
   5. How to test
   6. Known issues and decisions
   7. Next steps

Update `PROJECT.md` at the end of every milestone before asking to commit.

### 3.2 README.md is the entry point
Update `README.md` whenever a milestone changes any of the following
1. How to run
2. How to test
3. Configuration and environment variables
4. Public API, CLI, or user facing behavior
5. Project structure or key concepts

README should stay concise and practical.

### 3.3 Keep docs in sync
If a milestone changes behavior, update the docs in the same milestone. Avoid doc drift.

## 4. Milestone based workflow
All work must be executed as one or more explicit milestones.

For each milestone
1. Define milestone scope  
   State the goal and concrete deliverables.

2. Implement the change  
   Keep changes minimal and coherent.

3. Add or update tests  
   Every milestone that changes behavior must include tests or a documented reason why tests are not applicable.

4. Run tests  
   Run the full suite or the affected subset. Fix failures before finishing.

5. Update documentation  
   Update README.md and any relevant docs. Update PROJECT.md with the milestone audit entry.

6. Stop and request commit  
   Only after steps 1 to 5 are complete, ask for confirmation to commit.

## 5. Testing rules
1. New public behavior requires tests  
   Each new endpoint, command, or public function needs at least one success test and one failure test.

2. Deterministic tests  
   Tests must be reproducible and must not depend on network access unless explicitly allowed.

3. Use temporary resources  
   Tests must not write outside temporary folders. Use fixtures for temp paths and temp databases.

4. Prefer fast feedback  
   Unit tests for pure logic. Integration tests for boundary flows. End to end tests only for critical smoke coverage.

## 6. Change management rules
### 6.1 Backward compatibility
If you change an API contract, file format, or schema, document it in README.md and PROJECT.md and provide a migration note when relevant.

### 6.2 Versioning and migrations
If the project has persisted state
1. Introduce a schema version
2. Provide a minimal migration or a clear reset instruction for early prototypes
3. Add tests for version handling where feasible

### 6.3 Error handling
Use consistent error shapes.
Include a stable error_code, a human readable message, and optional fields for details.

## 7. Code quality rules
1. Prefer clarity over cleverness
2. Keep functions short and named by intent
3. Avoid duplication, then extract shared helpers
4. Use type hints where the language supports it
5. Keep dependencies minimal and justified
6. Keep configuration centralized and explicit

## 8. Deliverables checklist per milestone
A milestone is done when
1. Functionality works as specified
2. Tests cover the new behavior
3. Tests pass
4. README.md updated if needed
5. PROJECT.md updated with the milestone entry
6. Any new scripts or commands are documented

## 9. Default file conventions
Expected files at repository root
1. README.md
2. CODEX.md
3. PROJECT.md
4. LICENSE optional
5. .gitignore

If the project grows, keep a clear structure such as
1. src or app for code
2. tests for tests
3. docs for additional documentation
4. scripts for tooling
5. examples for runnable examples

## 10. Session startup routine
At the start of a session
1. Read CODEX.md and PROJECT.md
2. Identify the next milestone or propose one
3. Confirm current state by inspecting existing code and tests
4. Implement using the milestone workflow rules above