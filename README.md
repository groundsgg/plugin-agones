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

## Velocity discovery configuration

The Velocity module reads its configuration from environment variables. Every
key is optional; the defaults preserve the historic prod behaviour, so existing
deployments do not need to set anything.

| Env var                          | Default                                         | Notes                                                                 |
| -------------------------------- | ----------------------------------------------- | --------------------------------------------------------------------- |
| `GROUNDS_AGONES_NAMESPACE`       | `games`                                         | Falls back to `POD_NAMESPACE` (Downward API) before the default       |
| `GROUNDS_AGONES_LABEL_SELECTOR`  | `grounds/server-type in (lobby,game,match)`     | Empty string disables k8s-side label filtering                        |
| `GROUNDS_AGONES_LOBBY_LABEL`     | `grounds/server-type`                           | Empty string treats every running GameServer as a lobby               |
| `GROUNDS_AGONES_LOBBY_VALUE`     | `lobby`                                         | Value of `lobbyLabel` that marks a GameServer as a lobby              |
| `GROUNDS_AGONES_RUNNING_STATES`  | `Ready,Allocated,Reserved`                      | Comma-separated Agones states considered "running"                    |
| `GROUNDS_AGONES_POLL_INTERVAL`   | `2s`                                            | Accepts `Ns`, `Nm`, `Nh`                                              |
| `GROUNDS_AGONES_ADDRESS_TYPE`    | `PodIP`                                         | Which entry of `status.addresses` to dial (`PodIP`, `ExternalIP`, …)  |
| `GROUNDS_AGONES_PORT`            | `25565`                                         | TCP port on the GameServer                                            |

Typical Helm chart wiring uses a `ConfigMap` consumed via `envFrom`, plus
`POD_NAMESPACE` from the Downward API for clusters where the proxy should
watch its own namespace:

```yaml
env:
- name: POD_NAMESPACE
  valueFrom:
    fieldRef: { fieldPath: metadata.namespace }
envFrom:
- configMapRef:
    name: agones-discovery-config
```

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
