# AdvancedBan Plus

Modernized AdvancedBan build for Bukkit/Spigot/Paper, BungeeCord and Velocity networks.

Version: `2026.06.29.1`  
Authors: Leoko, siberanka  
License: GPL-3.0

## Features

AdvancedBan Plus provides bans, tempbans, IP bans, mutes, tempmutes, warnings, notes, kicks, history, configurable layouts, MySQL/HSQLDB storage and multi-language message files.

This fork adds:

- Velocity proxy support.
- Safer command handling with length limits and per-sender moderation command rate limits.
- HTTP timeout protection for update, UUID and geo lookups.
- Safer shutdown/reload paths for database and API bridge state.
- Thread-safe IP cache and stronger null handling around database reads.
- LiteBans API compatibility classes behind `litebans-api-support: false`.
- Modern bStats API usage for Bukkit/Bungee builds.
- Common voice chat command aliases in `MuteCommands`.

## Platforms

- Bukkit/Spigot/Paper: built against modern Spigot API while keeping the existing Bukkit adapter.
- BungeeCord: existing adapter retained and hardened.
- Velocity: new `AdvancedBan-Velocity` module with command, login, chat, command mute and event support.

## Configuration

New default options in `config.yml`:

```yaml
litebans-api-support: false

Security:
  MaxReasonLength: 255
  MaxArgumentLength: 256
  MaxTotalCommandLength: 2048
  HttpConnectTimeoutMillis: 3000
  HttpReadTimeoutMillis: 3000
  CommandRateLimit:
    Enabled: true
    WindowMillis: 1000
    MaxCommands: 6

Database:
  MaximumPoolSize: 10
  MinimumIdle: 1
  ConnectionTimeoutMillis: 5000
  ValidationTimeoutMillis: 3000
  LeakDetectionThresholdMillis: 0
```

`litebans-api-support` exposes compatible `litebans.api.Database`, `Events`, `Entry`, `PlayerProvider` and `RandomID` classes for plugins that query LiteBans-style ban/mute/warn state. It is disabled by default to avoid surprising behavior on networks that also run LiteBans.

## Build

```bash
mvn clean package
```

On Windows hosts where the JDK trust store does not include the system certificates, this equivalent command can be used:

```bash
mvn clean package "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Release `2026.06.29.1` was verified with `mvn test` and `mvn clean package` using the Windows root trust-store flag in this workspace.

Build outputs:

- `bukkit/target/AdvancedBan-Bukkit-2026.06.29.1-RELEASE.jar`
- `bungee/target/AdvancedBan-Bungee-2026.06.29.1-RELEASE.jar`
- `velocity/target/AdvancedBan-Velocity-2026.06.29.1-RELEASE.jar`
- `bundle/target/AdvancedBan-Bundle-2026.06.29.1-RELEASE.jar`
