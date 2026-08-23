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
- `minestom` — Grounds Minestom runtime module: same responsibility as `paper`,
  exposed through `GroundsModuleProvider` for runtime composition

## Minestom runtime module

The Minestom target publishes a `GroundsModuleProvider` via `ServiceLoader`.
Grounds Minestom runtime can discover it through the provider service file and
install it for `MINIGAME` server types. The module id is `grounds.agones`.

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
| `GROUNDS_STATIC_SERVERS`         | _(none)_                                        | Comma-separated `name=host:port` static Velocity backends             |

`GROUNDS_STATIC_SERVERS` is validated strictly: names and hosts must be non-empty, ports must be
between `1` and `65535`, and names must be unique. For example, Stage proxies can use:

```text
GROUNDS_STATIC_SERVERS=buildserver=buildserver:25565
```

This only registers the backend with Velocity; configuring the backend itself for Velocity
forwarding remains a separate deployment concern.

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
