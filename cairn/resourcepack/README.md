# cairn resource pack

Custom models for the grave Item Display (`cairn:grave`) and the transferable key
(`cairn:grave_key`).

## Install

- **Testbed (recommended):** Fabric already forces this pack. Accept the prompt when
  you `/server cairn`. The zip is served at
  `http://127.0.0.1:8080/resourcepacks/cairn-resourcepack.zip` by the docs nginx.
- Client manual: open `../cairn-resourcepack.zip` (or this folder) in Resource Packs.
- Other servers: set `resource-pack=` to a reachable HTTPS/HTTP URL of the zip,
  `resource-pack-sha1=` to its SHA-1, and `require-resource-pack=true`.

Without this pack, graves still work: the display shows a stone item and the key
is a named trial key.
