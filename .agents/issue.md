# Capability-awareness handoff

Status: CA-01 through CA-04 complete on main; no unresolved implementation or
acceptance blocker. Next: CA-05 merge main into feature/gptlive and then agents,
verify deployment-only core opt-in/persona compatibility, push and check Heroku.

Unexpected issues resolved:
- The first context wrapper disabled guard batching through a class-identity
  check. Delegated eligibility fixed it; inference-count and guard tests pass.
- Local properties target deployment MySQL and PyMySQL was absent. Installed
  PyMySQL and used a separate loopback MySQL server with disposable test schemas.
- Initial browser acceptance had 13 failures because Bootstrap CDN requests failed
  with ERR_INTERNET_DISCONNECTED. Pinned test-only UI assets now satisfy those
  requests without mocking Bootstrap behaviour or changing production loading.

Verified: 463 Java cases, 67 Node cases, 46 browser cases. All pass. Desktop/mobile
screenshots inspected. See CAPABILITY_AWARENESS_RESULTS.md for commands and evidence.
Older saved instances remain opted out; create new core instances to use awareness.
Real-provider answer quality and physical acoustic trials remain NOT RUN.
