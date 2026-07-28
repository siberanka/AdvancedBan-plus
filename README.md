# AdvancedBan Plus

## English

Production-focused modernization of AdvancedBan for Bukkit, Spigot, Paper, Folia,
BungeeCord and Velocity.

- Version: `2026.07.28.2`
- Authors: Leoko, siberanka
- License: GPL-3.0

### Install

Download and install only:

`AdvancedBan-Bundle-2026.07.28.2-RELEASE.jar`

The Bundle is the only production artifact. It detects the host platform and contains
the Bukkit/Paper/Folia, BungeeCord and Velocity adapters. JARs produced inside the
individual module directories are build-time artifacts and must not be installed
separately.

Latest release: <https://github.com/siberanka/AdvancedBan-plus/releases/latest>

### Platform Matrix

| Platform | Supported line | Runtime |
| --- | --- | --- |
| Bukkit, Spigot, Paper and compatible forks | Minecraft `1.16.x` through `26.x` | Use the Java version required by the server |
| Folia and compatible region-threaded forks | Folia-capable Minecraft releases through `26.x` | Use the Java version required by Folia |
| BungeeCord and compatible forks | Current Bungee API, including legacy backend networks | Use the Java version required by the proxy |
| Velocity | Velocity `3.4.x` and `3.5.x` compatible API surface | Java 21 or newer |

The Bundle is compiled to Java 11 bytecode. Server requirements still take priority:
the 1.16 family uses Java 11/16 depending on the exact server build, modern 1.17-1.19
servers use Java 17, 1.20-1.21 servers use Java 21, and the 26.x line uses Java 25.
`plugin.yml` declares `api-version: 1.16`, so newer Bukkit-family servers do not reject
the plugin solely because of an unnecessarily high API floor.

Folia support is implemented in the Bundle's Bukkit adapter. It declares
`folia-supported: true` and routes global, async and player/entity work through the
appropriate runtime schedulers. The `folia/` module is a compile-time compatibility
guard, not a second production plugin.

No plugin can truthfully guarantee every untested third-party fork. Compatibility is
continuously checked against the oldest API baseline, a modern 1.21 API profile and a
26.x Paper API profile in GitHub Actions.

### Features

- Permanent and temporary bans, IP bans, mutes, warnings and notes.
- Kick, unban, unmute, unwarn, unnote, history, check and list workflows.
- Configurable warning actions, layouts, permissions, exempt players and durations.
- MySQL/MariaDB through HikariCP or bounded local HSQLDB storage.
- Atomic default- and LiteBans-format punishment writes, rollback on partial failure
  and duplicate active ban/mute suppression.
- Optional LiteBans-compatible database tables without deleting or automatically
  migrating existing AdvancedBan data.
- Optional LiteBans API compatibility for `Database`, `Events`, `Entry`,
  `PlayerProvider` and `RandomID`, disabled by default.
- Discord webhook embeds with per-event enable switches, colors, identity, icons,
  fields, templates, limits and mention protection.
- Cooldown-controlled staff notifications for banned joins and muted chat/command
  attempts.
- Simple Voice Chat microphone muting for active mute and tempmute punishments.
- GitHub update checks, `/advancedban update`, admin notices and latest-release links.
- Fully configurable operational/player messages in `Messages.yml`.
- YAML maintenance for `config.yml`, `Messages.yml` and `Layouts.yml`, including
  backup-before-repair and optional unknown-key cleanup.
- Size-limited and rotated `plugins/AdvancedBan/error.log` with localized remediation
  hints for recognized failures.
- Command payload limits, per-sender rate limiting, bounded notification/UUID caches,
  structured size-limited JSON parsing, IPv4/IPv6 validation and outbound HTTP
  timeouts.
- Fail-closed login/chat/command handling when configured database lockdown is active.
- Fail-closed load/reload/unload lifecycle with task cancellation, cache cleanup,
  database pool reconstruction and online-player revalidation.
- RedisBungee synchronization support on compatible Bungee networks.
- bStats metrics for Bukkit, BungeeCord and Velocity.

AdvancedBan Plus is a moderation plugin. It does not create custom inventories,
crafting recipes or item-transfer mechanics, so it has no plugin-owned item path from
which an inventory dupe can be produced. The relevant replay risk is duplicate
punishment persistence; this is guarded by per-target concurrency control,
precondition checks and an atomic history/active-record transaction.

### Important Configuration

Every bundled `config.yml` option includes a short comment. Existing YAML files are
checked during load and reload; missing defaults can be restored after a backup.

```yaml
# Disabled by default. Enables the bundled LiteBans API compatibility layer.
litebans-api-support: false

Database:
  # default or litebans. Switching never deletes old tables or rows.
  database-format: default

UpdateChecker:
  Enabled: true

Check:
  GeoLookup:
    # Disabled by default because enabling it shares player IP addresses externally.
    Enabled: false
    URL: "https://ipapi.co/%IP%/json/"
    Key: "country_name"

VoiceChat:
  MuteIntegration:
    Enabled: true

Security:
  MaxReasonLength: 255
  MaxArgumentLength: 256
  MaxTotalCommandLength: 2048
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
  MaxFileBytes: 2097152
  MaxBackupsPerFile: 10
  RemoveUnknownEntries: false
```

`Database.database-format: litebans` writes new records to LiteBans-compatible
`litebans_bans`, `litebans_mutes`, `litebans_warnings` and `litebans_kicks` tables.
It does not drop, rewrite or silently migrate existing AdvancedBan records. Take a
verified database backup before changing a production network's format, and do not
let two punishment plugins concurrently own the same live tables.

`litebans-api-support: true` enables the compatibility implementation and event bridge.
It is intended for integrations using the API surface shipped in this repository; it
is not a claim that private, undocumented LiteBans internals can be reproduced.

Webhook URLs are restricted to Discord HTTPS domains by default. Mentions are
neutralized unless explicitly allowed, payload and field sizes are capped, outbound
requests have timeouts, and repeated attempt notifications are throttled. All webhook
titles, descriptions, fields and staff messages are in `Messages.yml`.

`Check.GeoLookup.Enabled` is disabled by default. Enabling it sends a validated
literal player IP to the configured HTTPS JSON endpoint when an authorized moderator
uses `/check`; review local privacy requirements before enabling it.

### Build and Verification

Requirements: Maven 3.9+ and JDK 11+ for the baseline build. Use JDK 25 for the 26.x
Paper profile.

```bash
mvn -B -ntp clean verify
mvn -B -ntp -Ppaper-1.21 clean verify
mvn -B -ntp -Ppaper-26 clean verify
```

The verification suite includes unit tests for database, LiteBans storage/API,
webhooks, update checks, YAML maintenance, security bounds and concurrent punishment
creation. A post-package integration test opens the real shaded Bundle and verifies
platform descriptors, entry points, Folia metadata, version/author metadata and Java
11 class compatibility. GitHub Actions repeats the matrix on Java 11, 21 and 25.

### Production Notes

- Test database-format changes and proxy-wide deployments on a staging network.
- Keep `LockdownOnError`, command limits, HTTP timeouts and bounded logging enabled.
- Prefer a graceful server stop over plugin-manager hot reloads. The lifecycle is
  defensive, but third-party hot reloaders cannot safely reconstruct every server API.
- Store `config.yml` securely because it can contain database and webhook credentials.
- Report reproducible problems through the repository issue forms and attach redacted
  logs, platform/version data and exact reproduction steps.

---

## Türkçe

Bukkit, Spigot, Paper, Folia, BungeeCord ve Velocity için production odaklı
modernize edilmiş AdvancedBan sürümüdür.

- Sürüm: `2026.07.28.2`
- Geliştiriciler: Leoko, siberanka
- Lisans: GPL-3.0

### Kurulum

Yalnızca şu dosyayı indirip kurun:

`AdvancedBan-Bundle-2026.07.28.2-RELEASE.jar`

Production için desteklenen tek artefakt Bundle JAR'dır. Çalıştığı platformu algılar
ve Bukkit/Paper/Folia, BungeeCord ve Velocity adaptörlerini birlikte içerir. Modül
klasörlerinde oluşan küçük JAR'lar yalnız derleme içindir; ayrı plugin olarak
kurulmamalıdır.

Son sürüm: <https://github.com/siberanka/AdvancedBan-plus/releases/latest>

### Platform Matrisi

| Platform | Desteklenen seri | Çalışma ortamı |
| --- | --- | --- |
| Bukkit, Spigot, Paper ve uyumlu forklar | Minecraft `1.16.x` - `26.x` | Sunucunun istediği Java sürümünü kullanın |
| Folia ve uyumlu region-threaded forklar | Folia destekli sürümler - `26.x` | Folia'nın istediği Java sürümünü kullanın |
| BungeeCord ve uyumlu forklar | Güncel Bungee API; eski backend ağları dahil | Proxy'nin istediği Java sürümünü kullanın |
| Velocity | Velocity `3.4.x` ve `3.5.x` uyumlu API yüzeyi | Java 21 veya üzeri |

Bundle Java 11 bytecode olarak derlenir. Buna rağmen sunucunun Java gereksinimi
önceliklidir: 1.16 serisinin tam buildine göre Java 11/16, modern 1.17-1.19
sunucularında Java 17, 1.20-1.21 sunucularında Java 21 ve 26.x serisinde Java 25
kullanılır. `plugin.yml` içindeki `api-version: 1.16`, eski destek tabanını korur.

Folia uyumluluğu Bundle'ın Bukkit adaptöründedir. `folia-supported: true` bildirilir;
global, async ve oyuncu/entity işlemleri çalışma anında doğru scheduler'a yönlendirilir.
`folia/` modülü ikinci bir production plugin değil, derleme zamanı uyumluluk
kontrolüdür.

Hiçbir plugin denenmemiş tüm üçüncü taraf forkları koşulsuz garanti edemez. Bu nedenle
uyumluluk; en eski API tabanı, modern 1.21 profili ve 26.x Paper API profiliyle GitHub
Actions üzerinde sürekli doğrulanır.

### Özellikler

- Kalıcı/geçici ban, IP ban, mute, warn ve note işlemleri.
- Kick, unban, unmute, unwarn, unnote, geçmiş, kontrol ve liste komutları.
- Ayarlanabilir warn aksiyonları, layoutlar, izinler, muaf oyuncular ve süreler.
- HikariCP ile MySQL/MariaDB veya sınırlandırılmış yerel HSQLDB.
- Varsayılan ve LiteBans formatında atomik ceza yazımı, kısmi hatada rollback ve
  yinelenen aktif ban/mute engelleme.
- Mevcut veriyi silmeden isteğe bağlı LiteBans uyumlu veritabanı tabloları.
- Varsayılan kapalı LiteBans `Database`, `Events`, `Entry`, `PlayerProvider` ve
  `RandomID` API uyumluluğu.
- Event bazında açılıp kapatılabilen; renk, kimlik, logo, alan ve şablonları
  özelleştirilebilen Discord webhook embedleri.
- Banlı giriş ve muteli chat/komut denemeleri için cooldown kontrollü yetkili uyarıları.
- Aktif mute/tempmute cezalarında Simple Voice Chat mikrofon susturma.
- GitHub güncelleme kontrolü, `/advancedban update` ve son release yönlendirmesi.
- `Messages.yml` üzerinden ayarlanabilen oyuncu, yetkili ve operasyon mesajları.
- `config.yml`, `Messages.yml`, `Layouts.yml` için yedekli eksik-anahtar tamiri ve
  isteğe bağlı kullanılmayan anahtar temizliği.
- Bilinen hatalarda çözüm önerisi içeren, boyut limitli ve döndürülen
  `plugins/AdvancedBan/error.log`.
- Komut payload sınırları, kullanıcı bazlı rate limit, sınırlandırılmış bildirim/UUID
  cache'leri, boyut limitli yapısal JSON ayrıştırma, IPv4/IPv6 doğrulaması ve HTTP
  timeoutları.
- Veritabanı lockdown etkinken şüpheli giriş/chat/komut akışlarında fail-closed davranış.
- Task iptali, cache temizliği, bağlantı havuzunu yeniden kurma ve çevrimiçi
  oyuncuları yeniden doğrulama içeren fail-closed load/reload/unload yaşam döngüsü.
- Uyumlu Bungee ağlarında RedisBungee senkronizasyonu.
- Bukkit, BungeeCord ve Velocity için bStats.

AdvancedBan Plus özel envanter, crafting tarifi veya eşya transfer mekaniği oluşturmaz.
Bu nedenle pluginin yönettiği bir eşya yolu üzerinden envanter dupesi üretilemez.
Bu projedeki ilgili replay riski aynı cezanın eşzamanlı yazılmasıdır; hedef bazlı
eşzamanlılık kontrolü, önkoşul doğrulaması ve atomik history/active kayıt işlemiyle
engellenir.

### Önemli Ayarlar

Bundle içindeki her `config.yml` ayarında kısa açıklama bulunur. Mevcut YAML dosyaları
load ve reload sırasında kontrol edilir; eksik varsayılanlar yedek alındıktan sonra
tamamlanabilir.

```yaml
# Varsayılan kapalıdır. LiteBans API uyumluluk katmanını açar.
litebans-api-support: false

Database:
  # default veya litebans. Geçiş eski tablo ve kayıtları silmez.
  database-format: default

UpdateChecker:
  Enabled: true

Check:
  GeoLookup:
    # Oyuncu IP adresini dış servisle paylaşacağı için varsayılan kapalıdır.
    Enabled: false
    URL: "https://ipapi.co/%IP%/json/"
    Key: "country_name"

VoiceChat:
  MuteIntegration:
    Enabled: true

Security:
  MaxReasonLength: 255
  MaxArgumentLength: 256
  MaxTotalCommandLength: 2048
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
  MaxFileBytes: 2097152
  MaxBackupsPerFile: 10
  RemoveUnknownEntries: false
```

`Database.database-format: litebans`, yeni kayıtları LiteBans uyumlu
`litebans_bans`, `litebans_mutes`, `litebans_warnings` ve `litebans_kicks`
tablolarına yazar. Mevcut AdvancedBan kayıtlarını silmez, yeniden yazmaz veya sessizce
taşımaz. Production ağında format değiştirmeden önce doğrulanmış veritabanı yedeği
alın; aynı canlı tabloların sahipliğini iki ceza pluginine aynı anda vermeyin.

`litebans-api-support: true`, uyumluluk implementasyonunu ve event köprüsünü açar.
Bu destek depoda sunulan API yüzeyini kullanan entegrasyonlar içindir; LiteBans'ın
özel ve belgelenmemiş iç yapısını taklit etme iddiası değildir.

Webhook URL'leri varsayılan olarak Discord HTTPS alan adlarıyla sınırlıdır. Açıkça
izin verilmedikçe mentionlar etkisizleştirilir; payload/alan boyutları sınırlanır,
istek timeoutları uygulanır ve tekrar eden deneme bildirimleri yavaşlatılır. Webhook
başlık, açıklama, alan ve yetkili mesajlarının tamamı `Messages.yml` içindedir.

`Check.GeoLookup.Enabled` varsayılan olarak kapalıdır. Açılması, yetkili bir moderatör
`/check` kullandığında doğrulanmış oyuncu IP adresini ayarlanan HTTPS JSON servisine
gönderir; etkinleştirmeden önce yerel gizlilik yükümlülüklerini değerlendirin.

### Derleme ve Doğrulama

Temel derleme için Maven 3.9+ ve JDK 11+ gerekir. 26.x Paper profili için JDK 25
kullanın.

```bash
mvn -B -ntp clean verify
mvn -B -ntp -Ppaper-1.21 clean verify
mvn -B -ntp -Ppaper-26 clean verify
```

Testler; veritabanı, LiteBans depolama/API, webhook, güncelleme kontrolü, YAML bakımı,
güvenlik sınırları ve eşzamanlı ceza oluşturmayı kapsar. Paketleme sonrası entegrasyon
testi gerçek shaded Bundle'ı açarak platform descriptorlerini, giriş sınıflarını,
Folia metadata'sını, sürüm/geliştirici bilgisini ve Java 11 class uyumluluğunu
doğrular. GitHub Actions bu matrisi Java 11, 21 ve 25 üzerinde tekrarlar.

### Production Notları

- Veritabanı formatı ve proxy geneli değişiklikleri staging ağında test edin.
- `LockdownOnError`, komut sınırları, HTTP timeoutları ve limitli loglamayı açık tutun.
- Plugin-manager hot reload yerine kontrollü sunucu kapatmayı tercih edin. Yaşam
  döngüsü korumalıdır; üçüncü taraf hot-reload araçları tüm sunucu API durumunu güvenle
  yeniden oluşturamaz.
- Veritabanı ve webhook bilgileri içerebildiği için `config.yml` dosyasını koruyun.
- Tekrarlanabilen sorunları issue formlarıyla bildirin; gizli bilgileri temizlenmiş
  log, platform/sürüm bilgisi ve kesin tekrar adımlarını ekleyin.
