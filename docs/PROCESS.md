# nubstack build process

Build one plugin end to end before starting the next. Do not open parallel plugin
implementations.

For a snapshot of completed work and current gaps, see [PROGRESS.md](PROGRESS.md).

## Build order

1. spine (shared library)
2. aegis
3. harbor
4. gatekeep
5. loom
6. spotlight
7. ledger
8. cull
9. amber
10. tether
11. cairn

## Where plugin docs live

Keep operator-facing docs next to the code people install:

| Layout | README location | Design notes |
| --- | --- | --- |
| Single-module plugin (`harbor`, `loom`, …) | `<module>/README.md` | Optional `<module>/docs/` from templates when needed |
| Multi-module plugin (`aegis`) | Product umbrella `aegis/README.md` | `aegis/docs/` (PRD + architecture) |
| Code-only aegis modules | Thin pointer README in `aegis-paper/`, `aegis-velocity/`, `aegis-common/` | Link to `aegis/README.md` |

`aegis/` is not a second repository. It is the shared product folder for a plugin
that ships two jars (`aegis-paper`, `aegis-velocity`) plus `aegis-common`.

Every public plugin README must cover purpose, features, requirements, install,
configuration (types/defaults), commands, permissions, and short examples.

## Per-plugin workflow

1. For flagship or multi-module plugins, fill `docs/PRD.md` and
   `docs/ARCHITECTURE.md` from the templates (under the product folder for
   aegis).
2. Add failing tests for the first vertical slice.
3. Implement until tests pass.
4. Expand coverage for edge cases, failure paths, and platform lifecycle.
5. Update the module (or product) README (features, install, config, commands,
   permissions, examples).
6. Confirm `./gradlew build` and `./scripts/check-writing-policy.sh`.
7. Merge to `main` (or push a version tag) so CI attaches the jar.
8. Mark the plugin status in the root README.

## Definition of done

- Builds in CI on the pinned Minecraft and Java versions
- Boots on the target platform in CI where a boot workflow exists
- README covers features, install, config, commands, permissions, and examples
- Release tag publishes the jar
- Flagship plugins (aegis, harbor, loom, spotlight) include design notes and measurable performance notes when relevant

## Mineflayer

Black-box scenarios live under `testbed/` and `scripts/mf/` when a plugin needs
live-server behavior. CI uses offline mode so no Mojang accounts are required.
