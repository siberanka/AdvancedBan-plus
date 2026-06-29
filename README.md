# AdvancedBan Plus

## English

Modernized AdvancedBan build for Bukkit/Spigot/Paper, Folia, BungeeCord and Velocity networks.

Version: `2026.06.29.9`
Authors: Leoko, siberanka
License: GPL-3.0

### Overview

AdvancedBan Plus provides bans, tempbans, IP bans, mutes, tempmutes, warnings, notes, kicks, history, configurable layouts, MySQL/HSQLDB storage, multi-language message files and hardened production behavior for modern Minecraft networks.

This fork keeps the original AdvancedBan workflow familiar while adding current platform support, safer defaults and compatibility features for larger proxy/server deployments.

### Features

- Bukkit, Spigot and Paper support through the Bukkit adapter.
- Folia support through the Bundle jar with `folia-supported: true` metadata and Folia scheduler integration.
- BungeeCord and Waterfall-style proxy support.
- Velocity support with command, login, chat, command mute and event handling.
- Ban, tempban, IP ban, mute, tempmute, warn, tempwarn, note, kick, history, banlist and check commands.
- Configurable messages and layouts through YAML files.
- MySQL/MariaDB external storage or local HSQLDB storage.
- Optional LiteBans API compatibility through `litebans-api-support: false`.
- Optional LiteBans-compatible database format through `Database.database-format: litebans`.
- Non-destructive database-format switching; existing AdvancedBan tables are not deleted or auto-migrated.
- Typed LiteBans ID lookups to avoid wrong-table resolution when LiteBans tables share the same numeric id.
- Custom Discord webhook audit notifications with per-event toggles and colors for ban, unban, mute, unmute, warn, unwarn, note, kick, banned join attempts and muted chat/command attempts.
- LiteBans-style staff alerts when banned players try to join or muted players try to chat/use blocked commands, with configurable cooldowns.
- Simple Voice Chat mute integration on Bukkit/Paper. Active `/mute` and `/tempmute` punishments cancel outgoing microphone packets when Simple Voice Chat is installed.
- GitHub release update checker for `siberanka/AdvancedBan-plus`.
- `/advancedban update` command and admin update notifications.
- Bounded `error.log` stack-trace logging in the plugin folder.
- YAML maintenance for `config.yml`, `Messages.yml` and `Layouts.yml` with backups before automatic repairs.
- Command payload length checks and per-sender moderation command rate limits.
- HTTP timeout protection for update, UUID and external lookups.
- Safer shutdown/reload paths for database, API bridge and configuration state.
- Diagnostic `error.log` entries include localized possible-solution hints for known failure classes.
- Thread-safe IP cache and stronger null/exception handling around database reads.
- Modern bStats API usage for Bukkit, Bungee and Velocity builds.

### Supported Versions

- Java runtime: Java 21 required.
- Bukkit/Spigot/Paper target: Minecraft 1.21.x, built against Spigot API `1.21.11-R0.2-SNAPSHOT` with Bukkit `api-version: 1.21`.
- Folia: use the Bundle jar. Scheduler-sensitive work uses Folia async/global/entity scheduler APIs through a runtime bridge.
- Paper/Folia-style forks: supported when they provide compatible Bukkit/Paper APIs and Java 21 runtime behavior.
- BungeeCord/Waterfall-style proxies: built against BungeeCord API `1.20-R0.2-SNAPSHOT`.
- Velocity: built against Velocity API `3.4.0`; Velocity requires at least Java 21.
- Future Minecraft/Java server lines are not claimed as runtime-supported until a dedicated compatibility release is produced.

### Configuration Highlights

```yaml
litebans-api-support: false

UpdateChecker:
  Enabled: true

DiscordWebhook:
  Enabled: false
  WebhookUrl: ""
  Username: "AdvancedBan Plus"
  ServerName: "Global"
  AllowDiscordDomainsOnly: true
  AllowMentions: false
  Events:
    Ban:
      Enabled: true
    Unban:
      Enabled: true
    MutedChatAttempt:
      Enabled: true
  Colors:
    Ban: 15158332
    Unban: 3066993
    MutedChatAttempt: 15105570

StaffNotifications:
  Enabled: true
  BannedJoin:
    Enabled: true
    CooldownMillis: 30000
  MutedChat:
    Enabled: true
    CooldownMillis: 10000
  MutedCommand:
    Enabled: true
    CooldownMillis: 10000

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

`litebans-api-support` exposes compatible `litebans.api.Database`, `Events`, `Entry`, `PlayerProvider` and `RandomID` classes for plugins that query LiteBans-style ban, mute, warn and kick state. It is disabled by default to avoid surprising behavior on networks that also run LiteBans.

`Database.database-format` defaults to `default`, which keeps the original AdvancedBan tables: `Punishments` and `PunishmentHistory`. Setting it to `litebans` writes punishments to LiteBans-compatible tables such as `litebans_bans`, `litebans_mutes`, `litebans_warnings` and `litebans_kicks`. Existing AdvancedBan rows are not dropped or migrated automatically. Back up production databases before changing formats.

`DiscordWebhook` posts fully customizable embed notifications to Discord. Webhook URLs are restricted to Discord domains by default, mentions are neutralized unless explicitly enabled, payload sizes are capped, and every event can be toggled/colored separately from `config.yml`. Titles, descriptions and fields are controlled from `Messages.yml`.

`StaffNotifications` sends permission-based in-game/proxy alerts for banned join attempts and muted chat/command attempts. `CooldownMillis` is configurable per alert type to avoid spam during automated reconnect, chat floods or blocked-command floods.

`VoiceChat.MuteIntegration.Enabled` is Bukkit/Paper-only. When Simple Voice Chat is installed, AdvancedBan registers a voicechat API plugin and cancels microphone packets from actively muted players. If Simple Voice Chat is not installed, the hook is skipped safely.

`UpdateChecker.Enabled` checks the latest GitHub release from `https://github.com/siberanka/AdvancedBan-plus/releases/latest`. `/advancedban update` can be used by users with `ab.update`, while startup notifications are sent to online users with `ab.update.notify`.

`ErrorLog` writes stack traces to `plugins/AdvancedBan/error.log` and rotates bounded backups (`error.log.1`, `error.log.2`, ...). Stack entries are size-capped and JNDI-style payload text is neutralized before writing.

`YamlMaintenance` checks `config.yml`, `Messages.yml` and `Layouts.yml` on load/reload. Missing entries are copied from bundled defaults after a backup. Unknown-entry removal is available but disabled by default to avoid deleting custom admin sections.

### Safety Notes

- Use `Database.database-format: default` unless you explicitly need LiteBans-compatible database rows.
- Do not switch production database formats without a verified backup.
- LiteBans-format mode is designed for database compatibility. Running multiple punishment plugins against the same live tables should be tested carefully on a staging server.
- Keep `ErrorLog`, `YamlMaintenance`, command rate limits, staff-alert throttles and HTTP timeouts enabled on production servers.

### Installation

Use the Bundle jar from GitHub releases:

- `AdvancedBan-Bundle-2026.06.29.9-RELEASE.jar`

The Bundle jar is the supported production artifact for Bukkit, Spigot, Paper, Folia, BungeeCord and Velocity. Do not install the module jars from local build folders as production plugins; they are build modules used to assemble the Bundle.

### Build

```bash
mvn clean package
```

On Windows hosts where the JDK trust store does not include the system certificates, this equivalent command can be used:

```bash
mvn clean package "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Release `2026.06.29.9` was verified with:

- `mvn -pl core test` (`16` tests)
- `mvn clean package "-Djavax.net.ssl.trustStoreType=Windows-ROOT"`

Release artifact:

- `bundle/target/AdvancedBan-Bundle-2026.06.29.9-RELEASE.jar`

Release page:

- https://github.com/siberanka/AdvancedBan-plus/releases/tag/v2026.06.29.9

## Türkçe

Bukkit/Spigot/Paper, Folia, BungeeCord ve Velocity ağları için modernize edilmiş AdvancedBan sürümüdür.

Sürüm: `2026.06.29.9`
Geliştiriciler: Leoko, siberanka
Lisans: GPL-3.0

### Genel Bakış

AdvancedBan Plus; ban, tempban, IP ban, mute, tempmute, warn, note, kick, history, özelleştirilebilir layoutlar, MySQL/HSQLDB depolama, çoklu dil mesaj dosyaları ve modern Minecraft ağları için güçlendirilmiş production davranışları sunar.

Bu fork, klasik AdvancedBan kullanımını korurken güncel platform desteği, daha güvenli varsayılanlar ve büyük proxy/server ağları için uyumluluk özellikleri ekler.

### Özellikler

- Bukkit, Spigot ve Paper desteği.
- `folia-supported: true` metadata ve Folia scheduler entegrasyonu içeren Bundle jar üzerinden Folia desteği.
- BungeeCord ve Waterfall tarzı proxy desteği.
- Velocity desteği: komut, giriş, chat, komut mute ve event akışları.
- Ban, tempban, IP ban, mute, tempmute, warn, tempwarn, note, kick, history, banlist ve check komutlari.
- YAML üzerinden ayarlanabilir mesajlar ve layoutlar.
- MySQL/MariaDB harici depolama veya lokal HSQLDB depolama.
- `litebans-api-support: false` ile isteğe bağlı LiteBans API uyumluluğu.
- `Database.database-format: litebans` ile isteğe bağlı LiteBans uyumlu veritabanı formatı.
- Verileri silmeyen database-format geçişi; mevcut AdvancedBan tabloları silinmez ve otomatik migrate edilmez.
- LiteBans tablolarında aynı numeric ID farklı tablolarda bulunursa yanlış tabloya gitmeyi engelleyen type-aware ID çözümleme.
- Ban, unban, mute, unmute, warn, unwarn, note, kick, banlı giriş denemesi ve muteli chat/komut denemeleri için event bazlı aç/kapat ve renk ayarlı Discord webhook audit bildirimleri.
- Banlı oyuncu giriş denediğinde veya muteli oyuncu konuşmaya/engelli komut kullanmaya çalıştığında cooldown ayarlı LiteBans tarzı staff bildirimleri.
- Bukkit/Paper için Simple Voice Chat mute entegrasyonu. Simple Voice Chat kuruluysa aktif `/mute` ve `/tempmute` cezalarında mikrofon paketleri engellenir.
- `siberanka/AdvancedBan-plus` GitHub release kontrolü.
- `/advancedban update` komutu ve adminlere update bildirimi.
- Plugin klasöründe boyut limitli `error.log` hata kaydı.
- `config.yml`, `Messages.yml` ve `Layouts.yml` için yedekli YAML bakım sistemi.
- Komut payload uzunluk kontrolleri ve kullanıcı başına moderation komut rate limitleri.
- Update, UUID ve harici istekler için HTTP timeout korumaları.
- Database, API bridge ve config durumları için daha güvenli shutdown/reload akışları.
- Tanınan hata sınıflarında `error.log` kayıtlarına dil dosyasından gelen olası çözüm önerileri eklenir.
- Thread-safe IP cache ve database okuma tarafında daha güçlü null/exception kontrolleri.
- Bukkit, Bungee ve Velocity buildleri için modern bStats API kullanımı.

### Desteklenen Sürümler

- Java runtime: Java 21 gereklidir.
- Bukkit/Spigot/Paper hedefi: Minecraft 1.21.x, Spigot API `1.21.11-R0.2-SNAPSHOT` ile derlenir ve Bukkit `api-version: 1.21` kullanır.
- Folia: Bundle jarını kullanın. Scheduler hassas işlemler runtime bridge üzerinden Folia async/global/entity scheduler API'lerine taşınmıştır.
- Paper/Folia tarzı forklar: uyumlu Bukkit/Paper API ve Java 21 runtime davranışı sağladıkları sürece desteklenir.
- BungeeCord/Waterfall tarzi proxyler: BungeeCord API `1.20-R0.2-SNAPSHOT` ile derlenir.
- Velocity: Velocity API `3.4.0` ile derlenir; Velocity için en az Java 21 gerekir.
- Gelecek Minecraft/Java server sürümleri, o sürümler için ayrıca uyumluluk release'i hazırlanmadan runtime-supported kabul edilmez.

### Config Özeti

```yaml
litebans-api-support: false

UpdateChecker:
  Enabled: true

DiscordWebhook:
  Enabled: false
  WebhookUrl: ""
  Username: "AdvancedBan Plus"
  ServerName: "Global"
  AllowDiscordDomainsOnly: true
  AllowMentions: false
  Events:
    Ban:
      Enabled: true
    Unban:
      Enabled: true
    MutedChatAttempt:
      Enabled: true
  Colors:
    Ban: 15158332
    Unban: 3066993
    MutedChatAttempt: 15105570

StaffNotifications:
  Enabled: true
  BannedJoin:
    Enabled: true
    CooldownMillis: 30000
  MutedChat:
    Enabled: true
    CooldownMillis: 10000
  MutedCommand:
    Enabled: true
    CooldownMillis: 10000

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

`litebans-api-support`, LiteBans API ile derlenmiş eklentilerin ban, mute, warn ve kick durumlarını sorgulayabilmesi için `litebans.api.Database`, `Events`, `Entry`, `PlayerProvider` ve `RandomID` sınıflarını uyumlu şekilde sunar. LiteBans'ın kendisiyle aynı ağda beklenmeyen davranış oluşmaması için varsayılan olarak kapalı gelir.

`Database.database-format` varsayılan olarak `default` gelir ve klasik AdvancedBan tabloları olan `Punishments` ve `PunishmentHistory` kullanılır. `litebans` yapıldığında cezalar `litebans_bans`, `litebans_mutes`, `litebans_warnings` ve `litebans_kicks` gibi LiteBans uyumlu tablolara yazılır. Mevcut AdvancedBan satırları silinmez ve otomatik migrate edilmez. Production veritabanı formatı değiştirilmeden önce mutlaka yedek alınmalıdır.

`DiscordWebhook`, Discord'a tamamen özelleştirilebilir embed audit bildirimleri gönderir. Webhook URL'leri varsayılan olarak Discord domainleriyle sınırlandırılır, mentionlar özellikle açılmadıkça etkisizleştirilir, payload boyutları limitlenir, her event `config.yml` üzerinden ayrı açılıp kapatılabilir ve renklendirilebilir. Tüm başlık/açıklama/field içerikleri `Messages.yml` üzerinden düzenlenir.

`StaffNotifications`, banlı giriş denemeleri ve muteli chat/komut denemeleri için yetki bazlı oyun içi/proxy uyarıları gönderir. Otomatik reconnect, chat flood veya engelli komut flood durumlarında spam oluşmaması için `CooldownMillis` değerleri event bazlı ayarlanabilir.

`VoiceChat.MuteIntegration.Enabled` sadece Bukkit/Paper tarafında çalışır. Simple Voice Chat kuruluysa AdvancedBan voicechat API plugin'i kaydeder ve aktif mute cezası olan oyuncuların mikrofon paketlerini iptal eder. Simple Voice Chat yoksa hook güvenli şekilde atlanır.

`UpdateChecker.Enabled`, son GitHub release'ini `https://github.com/siberanka/AdvancedBan-plus/releases/latest` adresinden kontrol eder. `ab.update` yetkisine sahip kişiler `/advancedban update` kullanabilir; `ab.update.notify` yetkisine sahip online adminlere açılışta bildirim gönderilir.

`ErrorLog`, stack trace kayıtlarını `plugins/AdvancedBan/error.log` dosyasına yazar ve `error.log.1`, `error.log.2` gibi limitli yedeklerle rotate eder. Tek bir hata kaydı boyut limitine tabidir ve JNDI tarzı payload metinleri loga yazılmadan etkisizleştirilir.

`YamlMaintenance`, load/reload sırasında `config.yml`, `Messages.yml` ve `Layouts.yml` dosyalarını kontrol eder. Eksik entryler yedek alındıktan sonra varsayılan dosyalardan tamamlanır. Bilinmeyen entryleri silme özelliği vardır fakat custom admin bölümleri silinmesin diye varsayılan olarak kapalı gelir.

### Güvenlik Notları

- LiteBans uyumlu veritabanı satırlarına özel ihtiyaç yoksa `Database.database-format: default` kullanın.
- Production veritabanı formatı değiştirmeden önce doğrulanmış yedek alın.
- LiteBans-format modu veritabanı uyumluluğu içindir. Aynı canlı tablolara birden fazla ceza eklentisi yazacaksa önce staging sunucuda test edin.
- Production sunucularda `ErrorLog`, `YamlMaintenance`, komut rate limitleri, staff-alert throttle ayarları ve HTTP timeout ayarları açık kalmalıdır.

### Kurulum

GitHub release üzerinden Bundle jarını kullanın:

- `AdvancedBan-Bundle-2026.06.29.9-RELEASE.jar`

Bundle jar; Bukkit, Spigot, Paper, Folia, BungeeCord ve Velocity için desteklenen production artefactıdır. Lokal build klasörlerindeki modül jarlarını production plugin olarak kurmayın; onlar Bundle jarı oluşturmak için kullanılan build modülleridir.

### Build

```bash
mvn clean package
```

Windows ortamında JDK trust store sistem sertifikalarını görmüyorsa şu komut kullanılabilir:

```bash
mvn clean package "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

`2026.06.29.9` release'i şu kontrollerle doğrulanmıştır:

- `mvn -pl core test` (`16` test)
- `mvn clean package "-Djavax.net.ssl.trustStoreType=Windows-ROOT"`

Release artefactı:

- `bundle/target/AdvancedBan-Bundle-2026.06.29.9-RELEASE.jar`

Release sayfası:

- https://github.com/siberanka/AdvancedBan-plus/releases/tag/v2026.06.29.9
