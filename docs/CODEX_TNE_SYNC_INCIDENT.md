# Codex work order: TNE Redis synchronization and safe reconciliation

## Verified production incident (2026-10-23)

Lobby and Vanilla show different money balances, although both TNE instances point to the same MariaDB database.

- Both Minecraft Paper containers are connected to Pterodactyl Docker network `pterodactyl_nw` (`172.18.0.0/16`, gateway `172.18.0.1`).
- Vanilla candidate: `172.18.0.5`, Docker container UUID `f915802f-fe04-45e8-9a91-0e81a9aa9023`. This instance publishes to and subscribes to Redis `tne:balance`.
- Lobby: `172.18.0.4`, Docker container UUID `b68987f0-dc08-4d9f-928b-af9f14c7f297`. Running `TheNewEconomy 0.1.5.3`. Lobby is **not subscribed**.
- `redis-cli --askpass ping` => `PONG`; TCP connections to `172.18.0.1:6379` succeeded from all four containers.
- Redis `PUBSUB CHANNELS` => `tne:balance`; `PUBSUB NUMSUB tne:balance` => **1**; `CLIENT LIST` shows a Jedis `flags=P db=1 cmd=subscribe` connection from `172.18.0.5` and its command connection. No client from Lobby.
- Redis `MONITOR` confirms `PUBLISH tne:balance` from `172.18.0.5` carrying virtual holdings; do not copy personal player IDs or amounts into tests.
- Both running `data.yml`: `Data.Sync.Type: Redis`, host `172.18.0.1`, port `6379`, index `1`, `Data.Database.Type: Maria`, MariaDB host `192.168.178.70`, DB `s1_tne`, prefix `tne_`.
- Both `config.yml`: `Core.Region.MultiRegion: false`, `DefaultRegion: TNE_SYSTEM`, `Core.Server.Name: Main Server`.
- Config sharing differs: instance .5 `SharingPin: 000001`, `Hub: none`; Lobby .4 `SharingPin: 000000`, `Hub: 000001`. Do not conflate config sharing with balance sync; determine whether imported Hub config overrides effective sync flag.
- **Critical log from Lobby:** `[TheNewEconomy] Cross-server synchronization is disabled; Redis will not be initialized.` MariaDB Hikari connection starts successfully. This is the immediate reason the Lobby has no Redis subscriber. Investigate the precise gate in the deployed code; `Data.Sync.Type: Redis` alone is insufficient.
- Redis PUB/SUB is independent of the logical DB index; both index 1 settings are fine.

## Source version mismatch

The currently accessible repository `V3rfr3ss3n/EconomyCore` on `main` sets `TNECore.version = "0.1.4.2"`, while production Lobby runs **0.1.5.3**. Find the source matching the deployed build (other branches/tags/upstream or deployment artifact) before implementing the feature-gate fix. Explicitly document any unavailable source or untested assumptions.

Relevant paths:
- `Core/src/net/tnemc/core/TNECore.java` registers Jedis pool based on sync type in older code;
- `Core/src/net/tnemc/core/channel/BalanceHandler.java` publishes absolute balances to `tne:balance` and applies received holdings;
- `Core/src/net/tnemc/core/io/storage/datables/sql/standard/SQLHoldings.java` already persists amounts to SQL by account ID, `Core.Server.Name`, region, currency UUID and holdings handler;
- `Core/resources/data.yml` has Redis, storage and config hub settings;
- design context: `docs/cross-server-balance-reconciliation.md` in PR #4.

## Ordered implementation requirements

### P0: Fix subscriber initialization (do this first)

1. Locate the exact 0.1.5.3 conditional emitting `Cross-server synchronization is disabled; Redis will not be initialized.`
2. Identify the effective config key(s) and defaults. Check `Data.Sync.Enabled` and any security token/other gating as hypotheses, **not assumed facts**.
3. Add startup diagnostics: effective synchronization state, safely redacted reason for disablement, Redis connect/subscription success, channel names, node identity and reconnect state. Never log passwords, tokens or balances.
4. Fix false negatives or config propagation bugs, without automatically enabling intentionally disabled synchronization.
5. Unit/integration tests for disabled and enabled cases and configuration Hub propagation. Keep credentials secret.

### P1: Reliable consistency

1. Ensure reconnect and resubscribe after Redis outages.
2. Prevent stale/out-of-order PUB/SUB balance messages from overwriting newer state. Messages can be lost when a consumer is offline. Prefer MariaDB-authoritative revisions/CAS or a transactional outbox; do not rely on last writer wins with absolute amounts.
3. Add tests for competing updates, lost messages, duplicate/reordered delivery, reconnect, restarts and DB failure.
4. Document the expected channel subscriber count and verification commands.

### P2: Safe reconciliation / migration (never startup-auto-import)

1. Existing data is already in MariaDB, so **do not indiscriminately migrate in-memory balances to SQL**. They may be stale.
2. Implement `--dry-run` report comparing backed-up data sources to SQL by composite key: account, server name, region, currency, holdings handler. Include consistent/conflicting/missing/malformed classifications, never assume larger amount is newer.
3. Apply only offline/in maintenance with writers suspended, a verified backup, explicit per-conflict source selection, transaction/CAS checks, immutable audit details and idempotent reruns.
4. After commit clear/reload stale caches carefully, then test both server displays and Redis subscriptions.
5. Keep dry run truly read-only and require explicit approval for any mutation.

## Acceptance criteria

- With two authorized and correctly configured Paper nodes online, both subscribe to `tne:balance` and their intended balances agree in both directions after updates.
- Disabled sync reports precise actionable cause instead of silently skipping Redis.
- Redis interruption/restart never reverts money and does not duplicate a transaction.
- Dry-run produces audit of conflicts and makes no persistent changes.
- Applying reconciliation requires backup, maintenance lock, explicit plan and tests.

## Scope / safety

Repository-only code investigation and an implementation PR are authorized. **Do not modify production database, deploy, restart servers, overwrite balances or reset Redis.** Do not include passwords/security tokens or personally identifying player information in submitted output. Note that repo docs and draft PR are a plan, not evidence of a completed fix.
