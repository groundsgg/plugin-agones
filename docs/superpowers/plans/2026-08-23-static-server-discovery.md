# Static Server Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicitly configured static Velocity backends without allowing Agones reconciliation to delete them.

**Architecture:** Parse a strict optional `GROUNDS_STATIC_SERVERS` value into typed `StaticServer` entries in `DiscoveryConfig`. `DiscoveryService` registers those entries independently at startup and tracks only Agones-owned names for later removal.

**Tech Stack:** Kotlin, Velocity API, JUnit 5, Gradle

**Spec:** `docs/superpowers/specs/2026-08-23-static-server-discovery-design.md`

## Global Constraints

- The environment variable is exactly `GROUNDS_STATIC_SERVERS`.
- Its format is a comma-separated list of `name=host:port` entries.
- Absent or blank configuration preserves current behavior and yields no static servers.
- Names and hosts are non-empty, ports are in `1..65535`, duplicate names are rejected, and malformed entries fail configuration.
- Static servers have role `static` and are never added to the lobby set.
- Agones cleanup may unregister only names previously registered and tracked by Agones discovery.
- A configured static server wins a name collision with an Agones GameServer.
- Static registration remains available if Kubernetes client initialization fails.

---

### Task 1: Static configuration and ownership-aware registration

**Files:**
- Modify: `velocity/src/main/kotlin/gg/grounds/discovery/DiscoveryConfig.kt`
- Modify: `velocity/src/main/kotlin/gg/grounds/discovery/DiscoveryService.kt`
- Modify: `velocity/src/test/kotlin/gg/grounds/discovery/DiscoveryConfigTest.kt`
- Create: `velocity/src/test/kotlin/gg/grounds/discovery/ServerOwnershipTest.kt`
- Modify: `README.md`

**Interfaces:**
- Produces: `data class StaticServer(val name: String, val host: String, val port: Int)`.
- Produces: `DiscoveryConfig.staticServers: List<StaticServer>` sourced from `GROUNDS_STATIC_SERVERS`.
- Produces: an internal pure ownership helper used by `DiscoveryService` that returns stale names from the Agones-managed set only.

- [ ] **Step 1: Write failing configuration tests**

Add tests demonstrating these literal outcomes:

```kotlin
assertEquals(emptyList<StaticServer>(), DiscoveryConfig.fromEnv(emptyMap()).staticServers)
assertEquals(
    listOf(
        StaticServer("buildserver", "buildserver", 25565),
        StaticServer("metrics", "metrics.stage.svc.cluster.local", 25566),
    ),
    DiscoveryConfig.fromEnv(
        mapOf(
            "GROUNDS_STATIC_SERVERS" to
                " buildserver=buildserver:25565, metrics=metrics.stage.svc.cluster.local:25566 "
        )
    ).staticServers,
)
```

Add separate `assertThrows<IllegalArgumentException>` cases for a missing `=`, empty name, empty host, port `0`, port `65536`, a non-numeric port, and duplicate names.

- [ ] **Step 2: Run the configuration tests and verify RED**

Run:

```bash
./gradlew --no-daemon :velocity:test --tests gg.grounds.discovery.DiscoveryConfigTest
```

Expected: compilation or assertion failure because `StaticServer` and `staticServers` do not exist.

- [ ] **Step 3: Implement strict static-server parsing**

Add `StaticServer`, add `staticServers` to `DiscoveryConfig`, document `GROUNDS_STATIC_SERVERS`, and parse the optional value. Blank input returns an empty list. Split entries on commas, split each entry once on `=`, split the address at the final `:`, trim fields, validate all constraints, and throw `IllegalArgumentException` with the offending entry but without secrets or unrelated environment values.

- [ ] **Step 4: Run the configuration tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Write failing ownership tests**

Create `ServerOwnershipTest` for the internal pure helper. It must prove:

```kotlin
assertEquals(setOf("old-game"), staleManagedServerNames(setOf("live-game"), setOf("live-game", "old-game")))
assertEquals(emptySet<String>(), staleManagedServerNames(emptySet(), emptySet()))
```

The test names must state that only missing Agones-owned servers become stale and an unowned static server cannot enter the removal result.

- [ ] **Step 6: Run the ownership test and verify RED**

Run:

```bash
./gradlew --no-daemon :velocity:test --tests gg.grounds.discovery.ServerOwnershipTest
```

Expected: compilation failure because the helper does not exist.

- [ ] **Step 7: Implement static registration and Agones ownership tracking**

Change startup order to remove image placeholders, register all configured static servers with `ServerInfo(name, InetSocketAddress.createUnresolved(host, port))`, assign role `static`, and register listeners before attempting Kubernetes initialization. Only initialize `coreApi` and schedule polling when the Kubernetes client is available.

Track successful/current Agones registrations in a concurrent set. During each poll, skip an Agones server whose name belongs to a configured static server and log the collision. Cleanup must call the tested helper with running Agones names and the tracked Agones-owned set, unregister only the returned names, and clear their lobby/role/ownership state. It must not iterate over all Velocity registrations as removal candidates.

- [ ] **Step 8: Document the environment variable**

Add `GROUNDS_STATIC_SERVERS`, its `name=host:port` syntax, strict validation, and the Stage example to `README.md` without claiming that the backend itself is automatically configured for Velocity forwarding.

- [ ] **Step 9: Run formatting and the complete Velocity test suite**

Run:

```bash
./gradlew --no-daemon spotlessApply :velocity:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add README.md docs velocity/src
git commit -m "feat(velocity): support static server discovery"
```
