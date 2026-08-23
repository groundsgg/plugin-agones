# Static Server Discovery Design

## Goal

Allow Velocity proxies running `plugin-agones` to register a small set of explicitly configured, non-Agones backends such as the temporary Stage buildserver.

## Configuration

`GROUNDS_STATIC_SERVERS` is optional. Its value is a comma-separated list of `name=host:port` entries, for example:

```text
buildserver=buildserver:25565
```

Whitespace around entries is ignored. Names and hosts must be non-empty, ports must be in `1..65535`, and duplicate names or malformed entries fail plugin configuration rather than being silently ignored. An absent or blank value means no static servers and preserves current production behavior.

## Ownership and reconciliation

At startup the plugin continues removing the placeholder backends baked into the Velocity image, then registers the configured static servers. Static servers use the role `static`; they are never lobby candidates.

Agones polling owns only servers it registered from Agones. Reconciliation may remove a previously managed Agones server that is no longer running, but must never remove a configured static server or a backend owned by another plugin. A static name takes precedence over an Agones GameServer with the same name and the collision is logged.

Static registration must still work when the Kubernetes client cannot initialize. Agones polling may remain disabled in that case.

## Deployment path

The Stage proxies will receive `GROUNDS_STATIC_SERVERS=buildserver=buildserver:25565` after the plugin is released and bundled into a Velocity image. The buildserver will then be switched from public online-mode access to a ClusterIP backend using modern Velocity forwarding. That deployment work is intentionally separate from this plugin change.
