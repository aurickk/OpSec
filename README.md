<p align="center">
<img src="https://github.com/user-attachments/assets/152bd1b3-75d6-4855-ba0e-b26924233363" alt="goofy fedora hat icon" width="15%"/>
</p>

<h1 align="center">Incognito</h1>

<p align="center">A client-side Minecraft mod that provides protection against client fingerprinting, tracking exploits, and other privacy focused features.</p>

> [!WARNING]
> This mod is still at an experimental phase. Use at your own risk.

## What it does

- **[Brand Spoofing](#brand-spoofing)** - Change client brand name to Vanilla, Fabric, or Forge
- **[Channel Spoofing](#channel-spoofing)** - Hide or fake mod channels to prevent mod detection
- **[Isolate Pack Cache](#isolate-pack-cache)** - Isolate resource packs per-account to prevent tracking
- **[Block Local URLs](#block-local-urls)** - Automatically fail local requests from server resource packs 
- **[Translation Exploit Protection](#translation-exploit-protection)** - Protect against keybind probing
- **[Chat Signing Control](#chat-signing-control)** - Configure chat message signing behavior
- **[Telemetry Blocking](#telemetry-blocking)** - Disable data collection sent to Mojang

## Requirements

- **Minecraft** 1.21.1 – 1.21.10
- **Fabric Loader** 0.15.0+
- **Fabric API** (matching your Minecraft version)

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version
2. Download the latest [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version
3. Download the latest `incognito-[minecraft_version]-[version].jar` from the [Releases](https://github.com/aurickk/Incognito/releases/) page
4. Place both mods in your `.minecraft/mods` folder
5. Launch Minecraft

### Configurations

The settings menu is accessible via the draggable `Incognito` button in the multiplayer server selection menu or [Mod Menu](https://modrinth.com/mod/modmenu).

<img width="455" height="166" alt="incognito button" src="https://github.com/user-attachments/assets/d5808219-f08a-447b-b01e-81cc1e8e3dab" />

If settings are changed while connected to a server it is recommended to reconnect to the server to ensure changes are applied.

#### Identity Tab

| Setting | Type | Description |
|---------|------|-------------|
| **Spoof Brand** | Toggle | Enable/disable [brand spoofing](#brand-spoofing) |
| **Brand Type** | Dropdown | Select which brand to appear as (Vanilla/Fabric/Forge) |
| **Spoof Channels** | Toggle | Enable/disable [channel spoofing](#channel-spoofing) |

#### Protection Tab

| Setting | Type | Description |
|---------|------|-------------|
| **Isolate Pack Cache** | Toggle | Enable/disable [cache isolation](#isolate-pack-cache) |
| **Block Local Pack URLs** | Toggle | Enable/disable [local URL blocking](#block-local-urls) |
| **Clear Cache** | Button | Delete all cached server resource packs |
| **Spoof Translation Keys** | Toggle | Enable/disable [translation exploit protection](#translation-exploit-protection) |
| **Signing Mode** | Dropdown | Configure [chat signing](#chat-signing-control) behavior:<br/>• **Always**: Strip signatures (maximum privacy)<br/>• **Off**: Default Minecraft behavior<br/>• **Auto**: Only sign when required (recommended) |
| **Disable Telemetry** | Toggle | Enable/disable [telemetry blocking](#telemetry-blocking) |

#### Misc Tab

| Setting | Type | Description |
|---------|------|-------------|
| **Show Alerts** | Toggle | Display chat messages when tracking is detected |
| **Show Toasts** | Toggle | Display popup notifications for important events |
| **Log Detections** | Toggle | Log all detection events to game log for transparency |

### Understanding Alerts

- **Translation Exploit Detected**: Server is probing your keybind
- **Resource Pack Fingerprinting Detected**: Suspicious resource pack URL detected
- **Local URL Scan Detected**: Resource pack attempted to scan local network 

**Alert Types:**
- **Chat Messages**: Detailed information about what was detected/spoofed
- **Toast Notifications**: Pop-up alerts in the top-right corner
- **Console Logs**: Full technical details in the game log

## Feature Details

### Brand Spoofing

Servers can detect your client brand (Vanilla, Fabric, Forge, etc.) to fingerprint you or restrict modded clients.

Incognito intercepts the client brand packet sent to servers and replaces it with your chosen brand. You can appear as:
- **Vanilla**
- **Fabric** 
- **Forge** 

> [!IMPORTANT]
> Server plugins like [AntiSpoof](https://github.com/GigaZelensky/AntiSpoof) can detect the discrepancy between the client brand name and mod channels and flag clients for spoofing if [Channel Spoofing](#channel-spoofing) wasen't enabled.

---

### Channel Spoofing

Servers can query your registered network channels to detect which mods you have installed.

When enabled, Incognito spoof mod channels that are registered with the server based on your selected brand:
- **Vanilla mode**: Blocks ALL mod channels (pure vanilla client)
- **Fabric mode**: Only allows Fabric API channels, blocks other mods
- **Forge mode**: Only allows Forge channels, blocks other mods

> [!WARNING]
> May break mods that require server-side components (such as [VoiceChat](https://modrinth.com/plugin/simple-voice-chat)), as their communication channels will be blocked.

---

### Isolate Pack Cache
Based on [Meteor Client's fix](https://github.com/MeteorDevelopment/meteor-client/commit/e241e7d555cffe7687a045758ae6b8a9dc05a6e8).

Server-required resource packs could be used fingerprint client instance across accounts.

https://alaggydev.github.io/posts/cytooxien/

Instead of storing all resource packs in a shared cache (`~/.minecraft/downloads/`), Incognito creates separate cache directories for each account UUID.

---

### Block Local URLs

Based on [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer)

Malicious servers can send resource pack URLs to probe your local network devices.

https://alaggydev.github.io/posts/cytooxien/

Incognito detects resource pack URLs pointing to local/private IP addresses and redirects them to an invalid address (`http://0.0.0.0:0/incognito-blocked`), tricking the server into thinking that requests failed naturally.

---

### Translation Exploit Protection

Servers can send specially crafted text (in books, signs, , anvils, etc) containing translation keys like `key.attack` to probe which keys you have bound. This could reveal the client's installed mods.

https://wurst.wiki/sign_translation_vulnerability

Incognito intercepts translation lookups for keybind-related keys and returns spoofed default values

Spoofing mod keybinds (Returns raw translation keys instead of keybind values):
```
[key.meteor-client.open-commands] '.'→'key.meteor-client.open-commands'
[key.meteor-client.open-gui] 'Right Shift'→'key.meteor-client.open-gui'
```

Spoofing vanilla keybinds (Returns default keybinds):
```
[key.hotbar.6] 'Q'→'6'
[key.hotbar.7] 'E'→'7'
[key.hotbar.8] 'R'→'8'
```

---

### Chat Signing Control

Based on [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Cryptographic signatures by default are attached to every chat messages. Removing them makes it impossible to track and associate your chat messages with your Minecraft client, and, by extension, Microsoft account.

**Modes:**
- **Always**: Strip all chat signatures, but prevents you from chatting in servers that enforces secure chat.
- **Auto**: Only sign messages when the server enforces secure chat.
- **Off**: Default Minecraft behavior, signs every messages.

---

### Telemetry Blocking

From [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Minecraft collects and sends telemetry data to Mojang, including:
- Game events and player actions
- Performance metrics
- Client configuration
- Usage statistics

Incognito blocks telemetry sending to Mojang when telemetry blocking is enabled. Does not effect gameplay.

## Building from Source

### Prerequisites

- **Java 21** or higher
- **Gradle** (included via wrapper)

### Building the Minecraft Mod

1. **Clone the repository**
   ```bash
   git clone https://github.com/aurickk/Incognito.git
   cd Pay-Everyone
   ```

2. **Build the mod**
   ```bash
   # Windows
   .\gradlew.bat build
   
   # Linux/Mac
   ./gradlew build
   ```

Output JARs are in `legacy/build/libs/` (1.21.1 - 1.21.8) and `modern/build/libs/` (1.21.9 - 1.21.10).

## References

- [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) - Local URL blocking and sign translation protection
- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client/commit/e241e7d555cffe7687a045758ae6b8a9dc05a6e8) - Cached server resource pack isolation
- [No Chat Reports](https://modrinth.com/mod/no-chat-reports) - Chat signing control and telemetry blocking
- [No Prying Eyes](https://github.com/Daxanius/NoPryingEyes?tab=readme-ov-file) - Secure chat enforcement detection
