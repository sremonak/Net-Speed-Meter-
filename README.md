# Net Speed Meter

A modern, always-on internet speed meter for Android, written in Kotlin + Jetpack Compose (Material 3).

<p>
<img alt="min sdk" src="https://img.shields.io/badge/Android-8.0%2B-34D399">
<img alt="target sdk" src="https://img.shields.io/badge/target-Android%2015-38BDF8">
<img alt="kotlin" src="https://img.shields.io/badge/Kotlin-2.0-A78BFA">
</p>

## Features

### Always-on status bar meter
- **Live speed inside the status bar icon** (e.g. `1.2` `MB/s`). Choose total, download only or upload only.
- **A persistent notification** with ▼ download / ▲ upload speed and **today's usage, with Mobile and Wi-Fi shown separately**.
  The expanded view adds the total, connection type, VPN state and peak speeds.
- Quick actions: *Speed test*, *Usage details*.
- **Runs 24/7**:
  - foreground service (`specialUse` type, so the Android 15 time limit on `dataSync` doesn't apply)
  - restarts after reboot and after app updates
  - `START_STICKY`, plus an alarm restart if the task is swiped away
  - a 15-minute WorkManager watchdog
  - one-tap shortcuts to turn off battery optimization, allow OEM auto-start (Xiaomi/Oppo/Vivo/Realme/Huawei/Samsung/OnePlus…) and exclude the app from Data Saver.
- Saves battery: notification updates pause while the screen is off (usage is still counted) and resume right away when the screen turns on.
- **Quick Settings tile** with live speed.

### Data usage
- Tracks **Mobile vs Wi-Fi separately**, with download/upload for each, per day.
- **Exact mode**: once *Usage access* is granted, it uses Android's `NetworkStatsManager`, so the numbers are device-wide and include time when the app wasn't running. Without that permission it falls back to its own tracking stored in SQLite.
- 7-day, 30-day and billing-cycle views: stacked bar chart, totals, daily average and a per-day breakdown.
- **Daily and monthly mobile data limits** with alerts at 80 % and 100 %, plus a configurable billing-cycle start day.

### Network info
- **Connection:** type, whether internet access is validated, metered, roaming, VPN, captive portal, estimated bandwidth, interface, MTU, Private DNS, Data Saver.
- **Wi-Fi:** SSID, BSSID, RSSI / quality, link speed (Tx/Rx/max), frequency, band, channel, Wi-Fi standard (4/5/6/7), security type, DHCP server / lease, 5/6 GHz support.
- **Mobile:** operator, MCC/MNC, 2G/3G/4G/5G network type, signal dBm/level, roaming, data state, SIM slots.
- **IP & DNS:** IPv4/IPv6 with prefix, subnet mask, gateway, DNS servers, search domains, proxy, NAT64.
- **Public IP:** ISP, ASN, city/region/country, timezone, nearest Cloudflare edge.
- Traffic since boot, packet counts, all active network interfaces. Long-press any value to copy it.

### Speed test
- Ping and jitter, then multi-connection download (4 streams) and upload (3 streams) against Cloudflare's global edge.
- Live gauge and graph, data-used counter, connection rating and result history.

### Design
- Material 3 with a custom dark/light palette, Material You dynamic colours (Android 12+) and an AMOLED pure-black option.
- Animated logarithmic gauge, smooth live chart, usage donut, tabular numbers (no jitter).

## Install

Download the latest APK from **[Releases](../../releases)**, or from the **Actions** tab → latest *Build APK* run → `NetSpeedMeter-apk` artifact.

After installing, open the app and tap **Allow** on the *Finish setup* card:
1. **Notifications** – required for the status bar meter.
2. **Run without restrictions** – turns off battery optimization.
3. **Usage access** – exact Mobile/Wi-Fi numbers.

On Xiaomi/Oppo/Vivo/Realme etc., also open **Settings → Auto-start** in the app and lock the app in recents.

## Build

Requirements: JDK 17 and the Android SDK (or just open the project in Android Studio).

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug
```

Every push is built by GitHub Actions (`.github/workflows/android.yml`). Pushing a tag like `v1.0.1` publishes a GitHub Release with the APK.

### Signing
Release builds are signed with `keystore/netspeed-dev.p12` (credentials are in `keystore/signing.properties`), so every CI build installs as an update over the previous one.
Because that key is public in this repo, set your own private key for production releases by adding these repository secrets:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 your.jks` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |
| `SIGNING_STORE_TYPE` | `jks` or `pkcs12` (optional, default `jks`) |

## Project structure

```
app/src/main/java/com/sremonak/netspeedmeter/
├── service/     SpeedMeterService (always-on), NotificationRenderer, SpeedIconFactory,
│                BootReceiver, WatchdogWorker, SpeedTileService, DataLimitAlerts
├── monitor/     TrafficSampler, NetworkStateTracker, LiveTrafficStore
├── data/        UsageRepository, SystemStatsSource (NetworkStatsManager), UsageDatabase (SQLite)
├── info/        NetworkInfoProvider, PublicIpFetcher
├── speedtest/   SpeedTestEngine, SpeedTestHistory
├── ui/          Compose screens (Meter, Usage, Test, Info, Settings), charts, theme
└── util/        Perms (permissions & OEM auto-start deep links)
```

## Notes
- Live speed comes from kernel counters (`TrafficStats`). While a VPN is active, Android counts traffic on both the VPN and the real interface, so the live speed can read about twice the real value. *Exact mode* usage totals are not affected.
- Android 10+ hides the device's real MAC address and Android needs Location permission for the Wi-Fi name. Both are platform rules.

---

### বাংলায় সংক্ষেপে
- স্ট্যাটাস বারে সবসময় লাইভ নেট স্পিড দেখাবে। নোটিফিকেশনে ডাউনলোড/আপলোড স্পিড আর আজকের **মোবাইল ডাটা ও Wi-Fi ব্যবহার আলাদা করে** দেখা যাবে।
- অ্যাপটা সবসময় চালু থাকে: রিবুটের পর নিজে থেকে চালু হয়, ব্যাটারি অপটিমাইজেশন বন্ধ করার আর অটো-স্টার্ট চালু করার শর্টকাট আছে।
- অ্যাপের ভেতরে আছে দৈনিক/মাসিক ব্যবহারের চার্ট, ডাটা লিমিট অ্যালার্ট, স্পিড টেস্ট আর নেটওয়ার্কের বিস্তারিত তথ্য (IP, DNS, Wi-Fi সিগন্যাল, চ্যানেল, 4G/5G ইত্যাদি)।
