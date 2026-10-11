# Contributing to nubstack

This repository is a private showcase monorepo. External contributions are not
accepted unless the copyright holder explicitly agrees. The standards below
still apply to every change made in this tree.

## Writing policy

- Never use the Unicode em dash character (U+2014) in project-authored files.
- Never include development-tool attribution, generator notices, or commentary
  about automated assistants in project-facing artifacts.
- Prefer concise, specific language. Avoid filler and marketing cliches.
- Do not add documentation that only restates the code.

Run `./scripts/check-writing-policy.sh` before committing. CI runs the same check.
The denylist lives in `scripts/writing-policy/forbidden-terms.txt` for CI only.

## Git

- Use [Conventional Commits](https://www.conventionalcommits.org/).
- Lowercase description, imperative mood, one logical change per commit.
- Never commit secrets, build outputs, IDE metadata, or temporary debug code.
- Never fabricate history or invent milestones after the fact.
- Do not push local-only tooling directories (see `.gitignore`).

## Test-driven development

1. Write a failing test for the intended behavior.
2. Confirm it fails for the expected reason.
3. Implement the smallest change that passes.
4. Refactor without changing behavior.
5. Re-run relevant tests, then the full verification suite before merge or release.

Do not weaken or delete tests only to make the build pass.
Do not claim tests passed without running them.

## Architecture

- Prefer the smallest complete solution.
- Separate commands, listeners, services, repositories, configuration, and domain logic at real boundaries.
- No speculative features, unnecessary dependencies, or unrelated refactors in a feature change.
- Preserve existing conventions unless there is a concrete reason to change them.

## Minecraft platform rules

- Declare supported Minecraft versions, platforms, and Java versions explicitly.
- Use the API for the declared platform. Paper, Velocity, and Fabric are not interchangeable.
- Never block the main thread with database, network, or heavy work.
- Clean up tasks, listeners, executors, and connections on shutdown.
- Validate configuration. Document permissions and defaults.
- Optional integrations must fail gracefully when absent.

## Security and persistence

- No hardcoded secrets.
- Parameterized database queries only.
- Enforce authorization in the execution path.
- Treat configuration and persisted data as public interfaces.
- Migrate breaking schema or config changes explicitly.

## Definition of done

A change is done when:

- Applicable tests pass
- Writing-policy checks pass
- The project builds
- Docs and config examples match behavior
- No secrets or unrelated files are in the diff
- Known limitations are stated clearly

## Releases

Plugin jars are published from GitHub Actions on version tags.
See `.github/workflows/release.yml`.
