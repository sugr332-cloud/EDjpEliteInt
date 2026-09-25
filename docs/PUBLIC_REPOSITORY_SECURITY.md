# Public Repository Security Requirements

**Status:** Active specification
**Date:** 2026-09-15
**Scope:** `sugr332-cloud/EDjpEliteIntel`

This document defines the security requirements for keeping EDjpEliteIntel publicly visible/open-source while preventing credentials, personal data, local environment details, and runtime secrets from entering the repository.

## 1. Public-by-default policy

The source code, tests, deterministic game-data logic, C-CORE, localization resources, implementation plans, and non-secret configuration examples may be public.

Repository visibility itself is not treated as a security boundary. Anything that must remain secret must be kept outside Git and supplied at runtime through the user's local environment or an appropriate secret store.

The project MUST NOT rely on a private repository to protect secrets.

## 2. Never commit

The following MUST NOT be committed, either in the current tree or in future commits:

- API keys and access tokens (Gemini, Anthropic, OpenAI, GitHub, etc.)
- OAuth credentials, refresh tokens, session cookies, private credentials, or authentication caches
- Private keys, certificates, `.pem`, `.key`, or equivalent secret material
- `.env` files containing real secrets
- agy / Antigravity authentication credentials or credential caches
- Real user databases, SQLite databases, or application state containing personal/runtime data
- Real Elite Dangerous Journal data when it contains user-specific information that is not intended for publication
- Commander identity, account identifiers, private friend/contact information, or other personal data
- Real machine-specific configuration containing credentials or sensitive paths
- Server passwords, RCON passwords, bearer tokens, tunnel credentials, or similar infrastructure secrets
- Logs or diagnostic dumps containing the above information

Example configuration files MUST contain placeholders only.

## 3. Local-path and environment-data policy

Code and documentation MUST NOT hard-code personal Windows paths such as `C:\Users\<name>\...` or machine-specific server paths when an environment variable, application data directory, or configurable path can be used.

Use placeholders or environment variables, for example:

```text
%APPDATA%
%LOCALAPPDATA%
${ELITEINTEL_DATA_DIR}
```

A username appearing only as an intentionally anonymized example is acceptable; a real user's path copied from a development machine is not.

## 4. Database separation

Database schema, migrations, deterministic rules, fixtures, and test data that contain no personal information MAY be public.

A real runtime database MUST remain outside the repository.

The distinction is:

```text
public repository
  ├─ schema / migrations
  ├─ deterministic rules
  ├─ sanitized fixtures
  └─ tests

local machine only
  ├─ real DB
  ├─ Journal/state
  ├─ credentials
  └─ user-specific runtime data
```

The existing §R.12 DB non-pollution rules remain mandatory: LLM/agy output must not directly write to the DB, and values used for entity lookup/storage must pass deterministic dictionary/table validation unless the command is explicitly allowlisted as free-text storage.

## 5. agy / Antigravity security boundary

`agy` is an agent-capable CLI, not merely an HTTP API client. Public repository status therefore MUST NOT be used as justification for giving agy unrestricted access to the development machine.

For v2-P3 and later:

1. agy is used as a tool-call JSON generator.
2. Deterministic EliteIntel code executes the resulting command.
3. agy MUST NOT directly execute DB writes or game actions.
4. agy shell/file capabilities MUST be disabled or restricted where the environment permits it.
5. If this restriction cannot be verified, agy MUST NOT be run in an environment that has DB access or other sensitive runtime access.
6. The agy working directory SHOULD be an empty temporary directory rather than the repository/DB directory when practical.
7. DB paths, DB connection information, credentials, API keys, and other secrets MUST NOT be supplied to agy through prompts, environment variables, or CLI arguments.
8. agy stdout/stderr MUST NOT be blindly persisted to repository files.
9. `--dangerously-skip-permissions` MUST NOT be used for project implementation.
10. Development-time agy controls in §R.13 and runtime agy controls in §R.6 are separate requirements and MUST remain separate.

> **Revision note (2026-09-25 / LLM unification):** Runtime AI conversation is unified on the LLM path (LM Studio + Gemma 4 E4B) and runtime `agy` is retired and archived as unused under `archive/agy-runtime/` (`ELITEINTEL_INTEGRATION_PLAN.md` §R.6). Development-time `agy` use continues. Items 1–8 above therefore no longer describe a runtime path; items 3, 7 and 8 apply equally to the LLM provider (LLM output MUST NOT directly execute DB writes or game actions; DB paths/credentials MUST NOT be placed in prompts; raw LLM responses MUST NOT be blindly persisted to repository files). Item 9 and the development-time controls (§6) remain in force for development-time `agy` use.
>
> If the upstream cloud LLM paths are ever configured, their API keys (item "API keys and access tokens" in §2) MUST be supplied only through local configuration/environment and MUST NOT be committed or included in logs/diagnostic dumps.

## 6. Development-time implementation control

The implementation plan is the source of truth for agy-assisted development.

Before each implementation phase, agy MUST produce a START REPORT containing at least:

- target phase
- current plan revision / plan-file commit
- files allowed to change
- files forbidden to change
- commands it intends to run
- tests it intends to run
- expected outputs
- identified risks

It MUST stop before making changes when the workflow requires explicit approval.

During implementation:

- No automatic next-phase start.
- No unapproved plan changes.
- No broad refactor outside the phase scope.
- No unapproved subagent/background task that can modify files.
- A file outside the allowed scope is a STOP condition.
- A required specification change is a STOP condition.
- A failing required test is a STOP condition unless the phase explicitly defines the failure as expected and records it.

After implementation, agy MUST produce an END REPORT containing:

- PASS / PARTIAL / BLOCKED / FAILED
- exact changed files
- summary of each change
- tests and results
- unperformed checks
- plan deviation, if any
- Git state before/after
- commit SHA if a commit was explicitly authorized
- confirmation that the next phase was not started automatically

## 7. Git history audit before public release

Removing a secret from the current working tree is not sufficient. Before making the repository public, the complete Git history MUST be checked for accidentally committed secrets and sensitive files.

If a credential has ever been committed, it MUST be revoked/rotated first. Removing the file from the latest commit does not invalidate the exposed credential.

If necessary, the Git history MUST be rewritten using an appropriate history-cleaning procedure, followed by verification that the secret is no longer present in reachable history.

The release audit MUST include at least:

- current working tree
- tracked files
- ignored/untracked files that could be accidentally added
- commit history
- configuration examples
- CI/workflow files
- logs and test fixtures
- packaged/distribution artifacts

## 8. Automated secret scanning

Before public release and periodically thereafter, run a secret-scanning tool against the repository and its history where practical.

GitHub Secret Scanning / Push Protection SHOULD be enabled when available. This is a detection layer, not permission to commit secrets.

CI SHOULD reject obvious secret patterns where practical, but false positives MUST be reviewed rather than bypassed by weakening the scan.

## 9. Public-release checklist

A public release is security-ready only when all of the following are true:

- [ ] No real API keys/tokens/credentials are present.
- [ ] No agy authentication material is present.
- [ ] No real user/runtime DB is present.
- [ ] No sensitive Journal or personal runtime data is present.
- [ ] No real passwords, RCON credentials, bearer tokens, or tunnel credentials are present.
- [ ] No unnecessary machine-specific absolute paths are present.
- [ ] Example configuration contains placeholders only.
- [ ] Git history has been checked, not only HEAD.
- [ ] Any previously exposed credential has been revoked/rotated.
- [ ] Secret scanning has been run.
- [ ] CI/workflows have been checked for embedded secrets.
- [ ] Distribution/package artifacts have been checked.
- [ ] agy permissions and working-directory restrictions have been verified for the actual environment before v2-P3.
- [ ] The public repository does not contain anything whose confidentiality depends on repository visibility.

## 10. Change-control rule

This security specification is part of the implementation constraints, not optional documentation.

Any future phase that needs to introduce a new credential, personal-data source, external service secret, database path, or privileged agy capability MUST stop and update this security specification and the relevant phase scope before implementation.

No AI assistant, including agy, may silently weaken these requirements.
