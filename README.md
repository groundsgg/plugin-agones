# plugin-agones

Agones integration for the Grounds gameserver stack. Bridges Agones gameserver
lifecycle (Ready/Allocated) with platform-specific player events and exposes
discovered gameservers to the Velocity proxy.

## Modules

- `common` — shared Agones REST client, state helpers, gameserver models
- `velocity` — proxy plugin: discovers running gameservers via the Kubernetes API
  and registers them with Velocity
- `paper` — Paper plugin: keeps the gameserver's Agones state in sync with the
  player count
- `minestom` — Minestom library: same responsibility as `paper`, for Minestom-based
  gameservers

## Build

```bash
./gradlew build
```

## Development

Run in dev mode with live reload using DevSpace in a Kubernetes cluster:

```bash
cd velocity
devspace use namespace games
devspace dev
```

```bash
cd paper
devspace use namespace games
devspace dev
```

## License

Licensed under the GNU Affero General Public License v3.0
