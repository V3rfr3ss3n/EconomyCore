# TNE cross-server balance reconciliation — proposed implementation

> **Status: design only; no automatic writes.** Production servers run TNE 0.1.5.3, while this branch's `TNECore.version` is 0.1.4.2. Port any implementation against the exact deployed 0.1.5.3 source first.

## Observed incident (Lobby + Vanilla)

- Both Paper servers configured with `Data.Sync.Type: Redis`, Redis at `172.18.0.1:6379`, index `1`, and MariaDB `s1_tne` with prefix `tne_`.
- Redis is reachable from both containers and accepts authenticated PING.
- `PUBSUB NUMSUB tne:balance` reports **1**; the subscriber/publisher is container `172.18.0.5`. Lobby is `172.18.0.4`.
- Lobby log (TNE 0.1.5.3): `Cross-server synchronization is disabled; Redis will not be initialized.`
- The healthy server publishes `tne:balance` messages, but the Lobby does not subscribe.
- Config-sharing `SharingPin` and `Hub` are distinct from Redis subscriber initialization. Redis logical DB index does not namespace PUB/SUB.
- `SQLHoldings.store` writes account/Server.Name/region/currency/holdings_type/amount to SQL already. Blind import is **not appropriate**.

## Required fix, in order

1. **Diagnose the 0.1.5.3 feature gate.** Find the exact condition producing the quoted log line; expose effective `Data.Sync.Enabled` state and reason on startup. Do not silently skip Redis initialization when `Type: Redis` is selected; print an actionable warning including config key (never secrets).
2. **Make connectivity observable.** On startup and reconnect log Redis host/port/index (not credentials), successful subscribed channel list, server ID and connection state. Add a health command that reports subscribers and last applied message time, with no balance values or tokens exposed.
3. **Guard against unintended replays.** Add unique message IDs and per-account/currency/region revisions or a transactional outbox/inbox. A plain last-writer-wins incoming absolute balance is susceptible to stale overwrites after disconnection. Ensure the sender's own messages are ignored by stable server ID.
4. **Treat MariaDB as authoritative only after explicit operator selection and reconciliation.** Reads must not replace dirty in-memory balances on a live node; writes must use an optimistic revision or transactional compare-and-swap. A message should notify peers to reload by revision, not unconditionally overwrite a newer balance.

## Safe one-time reconciliation operation (future implementation)

Explicit command `/tne reconcile --dry-run`:
- Require operator permission, maintenance mode (both servers stopped or economy writes suspended), and a verified MariaDB backup.
- Enumerate all distinct keys `(account_id, server_name, region, currency_id, holdings_type)` and candidate values from DB plus any backed-up local data. Do not assume `Server.Name` matches intended economic scope.
- Report per-key values, timestamps/revisions **only when reliable**, and classify: same / DB-only / cache-only / conflicting / malformed / unknown currency.
- Do **not** guess which value is newer from amount size or file mtime.
- Require explicit selection of a source for each conflict (DB or backed-up server snapshot), export the plan to an immutable file, and calculate a checksum.

Explicit `/tne reconcile --apply <plan-checksum>`:
- Recheck maintenance lock, backup marker, row versions and currency/region mappings.
- Apply updates in one database transaction (or resumable idempotent batches) with a conflict/audit table; never overwrite a modified row.
- Record before/after values and selected source in an audit log. Support rollback from backup.
- Clear or reload stale caches only **after commit**, then start one node at a time, assert Redis subscriptions and compare balances on Lobby and Vanilla.
- Default mode remains read-only; must never run during plugin startup.

## Acceptance tests

- Disabled-sync configuration logs the exact missing flag and does not claim Redis connected.
- Both enabled nodes: `PUBSUB NUMSUB tne:balance` is at least 2 while both run, with subscriber identities matching nodes.
- Disconnect/reconnect Redis: no stale balance replaces a newer version, no double application, and missed updates reconcile from durable DB.
- Test two simultaneous transfers, a server restart during update, and a late packet.
- Dry-run leaves DB byte-for-byte logically unchanged; apply requires backup, explicit plan checksum, stopped writers, and is idempotent.
- Keep region/currency/handler and `Core.Server.Name` consistent; verify account UUIDs and Vault provider on both servers.

## Immediate operator checks (read-only)

```sh
redis-cli --askpass PUBSUB NUMSUB tne:balance
# Compare the two deployed data.yml settings, especially any Sync.Enabled and Security.Token fields.
# Compare deployed TNE jar versions/hashes with source revision before patching.
```

**Do not reset Redis DB, wipe accounts, or force a balance export from an active node.** The existing SQL schema is the primary stored state and should be backed up and inspected before reconciliation.
