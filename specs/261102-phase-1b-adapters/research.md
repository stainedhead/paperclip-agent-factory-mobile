# Research: Phase 1b shared adapters

**Date:** 2026-11-02 · **Source PRD:** [phase-1b-adapters-PRD.md](./phase-1b-adapters-PRD.md)

## Research questions

1. How are the first board user created and a pairing challenge approved against a fresh authenticated server in CI (Better Auth sign-up route and body, bootstrap_pending handling, `/cli-auth/challenges/:id/approve` and the session cookie it needs)? Read server/src and record the exact requests.
2. What are the exact request and response bodies (including error bodies and status codes) of every endpoint the gateway ports use? Record per endpoint with file:line, starting from the Phase 1a research answers.
3. Which SQLDelight and Ktor versions are compatible with Kotlin 2.4.21 and the Android KMP library plugin 9.5.0? Check the catalog candidates before adding them.
4. What does Ktor's Darwin engine surface for TLS and certificate failures, and how can they be mapped to `CertificateInvalid` testably on the simulator (self-signed loopback stub)?
5. Which Security.framework and LocalAuthentication APIs are callable from Kotlin/Native without cinterop definitions, and what does Keychain behave like on the simulator (accessibility, biometry access control)?
6. Can the Phase 1a fakes' behaviors (decide-then-poll, cursor paging, approval state machine) be reproduced by a MockEngine server stub without re-implementing the server, to run the contract suites against real adapters?

## Industry standards

OWASP MASVS L2 storage, crypto, auth and network controls (already in the PRD NFR).

## Existing implementations

The Paperclip web app (`~/Code/Test/paperclip/ui/src`) and server source (`server/src`, `packages/shared/src`) are the behavioral reference. Phase 1a code and ADR-010 to ADR-037 are the in-repo precedent.

## API documentation

Pinned server 2026.1023.0 (`@paperclipai/shared` types and route source). Answers are recorded here with file and line.

## Best practices

Test first; fixtures from the server's own types; one commit per slice task; every decision that surprises goes into an ADR.

## Open questions

1. Is full SPKI pin enforcement required in 1b or can it land in a later security pass? (Default: hook and tests in 1b, enforcement stretch.)
2. Should the feed cache keep more than the last page per company? (Default: last loaded pages up to 200 items.)
3. How should the app behave when the server's anonymous health omits `version`? (Default: show nothing before pairing, warn after.)

## References

[phase-1b-adapters-PRD.md](./phase-1b-adapters-PRD.md)
