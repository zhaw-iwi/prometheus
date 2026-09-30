# Capability-awareness handoff

Status: CA-01 through CA-05 complete and pushed. No unresolved implementation,
acceptance or deployment blocker. Main was merged into feature/gptlive and agents
with normal ancestry; future feature/gptlive -> main -> agents work can continue.
Deployment merge `f043cc2` passed GitHub Actions run 36715150486. Heroku valerian
v100 is succeeded, web.1 is up on v100, and health returned HTTP 200 / UP.

Unexpected issues resolved:
- The first context wrapper disabled guard batching through a class-identity
  check. Delegated eligibility fixed it; inference-count and guard tests pass.
- Local properties target deployment MySQL and PyMySQL was absent. Installed
  PyMySQL and used a separate loopback MySQL server with disposable test schemas.
- Initial browser acceptance had 13 failures because Bootstrap CDN requests failed
  with ERR_INTERNET_DISCONNECTED. Pinned test-only UI assets now satisfy those
  requests without mocking Bootstrap behaviour or changing production loading.
- Deployment merge conflicts were resolved while retaining Gigi/Valerian persona
  binding. Regression coverage verifies both binding orders and all registered
  deployment definitions, including the additional core scored RPS agent.

Verified on main: 463 Java, 67 Node and 46 browser cases. Verified after the agents
merge: 599 Java and 46 browser cases. All pass. Desktop/mobile screenshots inspected.
See CAPABILITY_AWARENESS_RESULTS.md for commands and evidence. Disposable test
schemas/accounts and owned application processes were removed; the separate test
MySQL server was stopped without altering the existing MySQL service.

Read-only deployment inventory before and after: 24 saved agents, zero opted in.
Create new core instances to use awareness; saved instances/history are unchanged.
Talk to Me still echoes exact text. Real-provider answer quality and physical
acoustic trials remain NOT RUN. Completion notes are synchronized to all three
branches; the final documentation-only merge skips redundant deployment.
