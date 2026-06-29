# AdvancedBan Plus

Modernized AdvancedBan build for Bukkit/Spigot/Paper, BungeeCord and Velocity networks.

Version: `2026.06.29.5`
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
- Optional LiteBans-compatible database format with non-destructive switching via `Database.database-format: litebans`.
- Modern bStats API usage for Bukkit/Bungee builds.
- Simple Voice Chat mute integration on Bukkit/Paper: `/mute` and `/tempmute` cancel outgoing microphone packets when Simple Voice Chat is installed.
- GitHub release update checks against `siberanka/AdvancedBan-plus`, with `/advancedban update` and admin notifications for newer releases.
- Bounded `error.log` stack-trace logging in the plugin folder for production diagnostics without unbounded disk growth.

## Platforms

- Bukkit/Spigot/Paper: built against modern Spigot API while keeping the existing Bukkit adapter.
- BungeeCord: existing adapter retained and hardened.
- Velocity: new `AdvancedBan-Velocity` module with command, login, chat, command mute and event support.

## Runtime

AdvancedBan Plus `2026.06.29.5` is built for Java 21. This follows the modern Minecraft server ecosystem in 2026 and allows the project to use current dependency lines such as HikariCP 7.x, JUnit 6.x and current platform APIs.

## Supported Versions

- Java runtime: Java 21 required.
- Bukkit/Spigot/Paper target: Minecraft 1.21.x, built against Spigot API `1.21.11-R0.2-SNAPSHOT` with Bukkit `api-version: 1.21`.
- Paper/Folia-style forks: supported when they provide compatible Bukkit/Paper APIs and Java 21 runtime behavior.
- BungeeCord/Waterfall-style proxies: built against BungeeCord API `1.20-R0.2-SNAPSHOT`.
- Velocity: built against Velocity API `3.4.0`; Velocity requires at least Java 21.
- Minecraft `26.1+`/future Java 25 server lines are not claimed as runtime-supported by this Java 21 build until a dedicated Java 25 release is produced.

## Configuration

New default options in `config.yml`:

```yaml
litebans-api-support: false

UpdateChecker:
  Enabled: true

VoiceChat:
  MuteIntegration:
    Enabled: true

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

ErrorLog:
  Enabled: true
  MaxBytes: 1048576
  Backups: 3
  MaxEntryChars: 32768

YamlMaintenance:
  Enabled: true
  BackupBeforeChanges: true
  RemoveUnknownEntries: false

Database:
  database-format: default
  MaximumPoolSize: 10
  MinimumIdle: 1
  ConnectionTimeoutMillis: 5000
  ValidationTimeoutMillis: 3000
  LeakDetectionThresholdMillis: 0
```

`litebans-api-support` exposes compatible `litebans.api.Database`, `Events`, `Entry`, `PlayerProvider` and `RandomID` classes for plugins that query LiteBans-style ban/mute/warn state. It is disabled by default to avoid surprising behavior on networks that also run LiteBans.

`Database.database-format` defaults to `default`, which keeps the original AdvancedBan tables. Setting it to `litebans` writes punishments to LiteBans-compatible tables such as `litebans_bans`, `litebans_mutes`, `litebans_warnings` and `litebans_kicks`. Switching formats is non-destructive: existing AdvancedBan rows are not dropped or migrated automatically. Back up production databases before changing formats.

`VoiceChat.MuteIntegration.Enabled` is Bukkit/Paper-only. When Simple Voice Chat is installed, AdvancedBan registers a voicechat API plugin and cancels microphone packets from actively muted players. If Simple Voice Chat is not installed, the hook is not loaded.

`UpdateChecker.Enabled` checks the latest GitHub release from `https://github.com/siberanka/AdvancedBan-plus/releases/latest`. `/advancedban update` can be used by users with `ab.update`, while startup notifications are sent to online users with `ab.update.notify`.

`ErrorLog` writes stack traces to `plugins/AdvancedBan/error.log` and rotates bounded backups (`error.log.1`, `error.log.2`, ...). Stack entries are size-capped and JNDI-style payload text is neutralized before writing.

`YamlMaintenance` checks `config.yml`, `Messages.yml` and `Layouts.yml` on load/reload. Missing entries are copied from bundled defaults after a backup. Unknown-entry removal is available but disabled by default to avoid deleting custom admin sections.

## Build

```bash
mvn clean package
```

On Windows hosts where the JDK trust store does not include the system certificates, this equivalent command can be used:

```bash
mvn clean package "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Release `2026.06.29.5` was verified with `mvn test` and `mvn clean package` using the Windows root trust-store flag in this workspace.

Build outputs:

- `bukkit/target/AdvancedBan-Bukkit-2026.06.29.5-RELEASE.jar`
- `bungee/target/AdvancedBan-Bungee-2026.06.29.5-RELEASE.jar`
- `velocity/target/AdvancedBan-Velocity-2026.06.29.5-RELEASE.jar`
- `bundle/target/AdvancedBan-Bundle-2026.06.29.5-RELEASE.jar`
