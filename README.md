# Slick Fun Mod

A Fabric 1.21.1 mod of portable utilities, joke items, an armoury and a handful of things that
only pretend to destroy the world. Works client side, server side, or both.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Needs a JDK 21.

## Releases

`releases/` holds the current build plus a `latest.json` manifest:

```json
{
  "version": "1.15.0",
  "file": "slick-fun-mod-1.15.0.jar",
  "minecraft": "1.21.1"
}
```

To publish a new build: put the jar in `releases/`, bump `version` and `file` in `latest.json`,
delete the previous jar, and push. Every installed copy picks it up within two minutes.

## Auto-update

The mod watches this repository and installs new builds on its own.

* It reads `releases/latest.json` every two minutes, on a background daemon thread so a slow
  response never touches the game.
* If the manifest names a newer version it downloads that jar into `mods/` as
  `<name>.jar.update`. **Never** as a `.jar` - Fabric refuses to start when two jars declare
  the same mod id, so a download that is finished, half finished or never applied is equally
  harmless.
* The swap happens at the next launch, before mods load. It deletes the old jar **first** and
  only renames the new one into place once that has definitely succeeded, because the reverse
  order would leave two jars behind on any failure and the game would not start at all.
* A running JVM holds its own jar open, so the swap cannot happen mid-game. That is why it
  takes a restart rather than being instant.

Operators are told in chat when a build is waiting.

Turn it off in `config/slickfun.properties`:

```properties
auto_update=false
```

## Layout

| Path | What lives there |
|---|---|
| `src/main/java/com/slickfun/item` | items |
| `src/main/java/com/slickfun/block` | blocks and their block entities |
| `src/main/java/com/slickfun/screen` | screen handlers, all reusing vanilla types |
| `src/main/java/com/slickfun/util` | managers, shared logic |
| `src/main/java/com/slickfun/update` | the auto-updater |
| `src/main/java/com/slickfun/mixin` | mixins |
| `src/main/resources/assets/slickfun` | textures, models, lang |
| `src/main/resources/data/slickfun` | recipes, advancements, loot, damage types |

There is no client entrypoint doing rendering work: every screen reuses a vanilla screen handler
type, which is what lets the mod run server-side only and still show its menus.
