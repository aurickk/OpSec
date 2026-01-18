<p align="center">
<img src="https://github.com/user-attachments/assets/152bd1b3-75d6-4855-ba0e-b26924233363" alt="goofy fedora hat icon" width="15%"/>
</p>

<h1 align="center">OpSec</h1>

<p align="center">A client-side Minecraft mod that provides protection against client fingerprinting, tracking exploits, and other privacy focused features.</p>

> [!WARNING]
> This mod is still at an experimental phase. Use at your own risk.

## What it does

- **[Brand Spoofing](#brand-spoofing)** - Change client brand name to Vanilla, Fabric, or Forge
- **[Channel Spoofing](#channel-spoofing)** - Hide or fake mod channels to prevent mod detection
- **[Mod Whitelist](#mod-whitelist)** - Exempt specific mods from channel spoofing and translation protection
- **[Isolate Pack Cache](#isolate-pack-cache)** - Isolate resource packs per-account to prevent tracking
- **[Block Local URLs](#block-local-urls)** - Automatically fail local requests from server resource packs 
- **[Translation Exploit Protection](#translation-exploit-protection)** - Protect against keybind probing
- **[Meteor Fix](#meteor-fix)** - Disable Meteor Client's broken translation protection
- **[Chat Signing Control](#chat-signing-control)** - Configure chat message signing behavior
- **[Telemetry Blocking](#telemetry-blocking)** - Disable data collection sent to Mojang

## Requirements

- **Minecraft** 1.21.1 – 1.21.11+
- **Fabric Loader** 0.16.0+
- **Fabric API** (matching your Minecraft version)

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version
2. Download the latest [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version
3. Download the latest `opsec-[minecraft_version]-[version].jar` from the [Releases](https://github.com/aurickk/OPSEC/releases/) page
4. Place both mods in your `.minecraft/mods` folder
5. Launch Minecraft

## Configurations

The settings menu is accessible via the `OpSec` button in the multiplayer server selection menu footer or [Mod Menu](https://modrinth.com/mod/modmenu).

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
| **Meteor Fix** | Toggle | Disable Meteor Client's broken translation protection (only shown when Meteor is installed) |
| **Signing Mode** | Dropdown | Configure [chat signing](#chat-signing-control) behavior:<br/>• **OFF**: Strip signatures (maximum privacy)<br/>• **ON**: Default Minecraft behavior<br/>• **AUTO**: Only sign when required (recommended) |
| **Disable Telemetry** | Toggle | Enable/disable [telemetry blocking](#telemetry-blocking) |

#### Whitelist Tab

| Setting | Type | Description |
|---------|------|-------------|
| **Enable Whitelist** | Toggle | Enable the [mod whitelist](#mod-whitelist) feature |
| **Installed Mods** | List | Toggle individual mods ON/OFF to exempt them from protection |

#### Miscellaneous Tab

| Setting | Type | Description |
|---------|------|-------------|
| **Show Alerts** | Toggle | Display chat messages when tracking is detected |
| **Show Toasts** | Toggle | Display popup notifications for important events |
| **Log Detections** | Toggle | Log all detection events to game log for transparency |

### Understanding Alerts

- **Translation Exploit Detected**: Server is probing your keybind
- **Resource Pack Fingerprinting Detected**: Suspicious resource pack URL detected
- **Local URL Scan Detected**: Resource pack attempted to scan local network 

## Feature Details

### Brand Spoofing

Servers can detect your client brand (Vanilla, Fabric, Forge, etc.) to fingerprint you or restrict modded clients.

OpSec intercepts the client brand packet sent to servers and replaces it with your chosen brand. You can appear as:
- **Vanilla**
- **Fabric** 
- **Forge** 

> [!IMPORTANT]
> Server plugins like [AntiSpoof](https://github.com/GigaZelensky/AntiSpoof) can detect the discrepancy between the client brand name and mod channels and flag clients for spoofing if [Channel Spoofing](#channel-spoofing) wasen't enabled.

---

### Channel Spoofing

Servers can query your registered network channels to detect which mods you have installed.

When enabled, OpSec spoofs mod channels that are registered with the server based on your selected brand:
- **Vanilla mode**: Blocks ALL mod channels (pure vanilla client)
- **Fabric mode**: Only allows Fabric API channels, blocks other mods
- **Forge mode**: Only allows Forge channels, blocks other mods

> [!WARNING]
> May break server-dependent mod(s) if not whitelisted. Use the [Mod Whitelist](#mod-whitelist) to exempt specific mods like [VoiceChat](https://modrinth.com/plugin/simple-voice-chat).

---

### Mod Whitelist

Some mods require server communication to function properly (e.g., VoiceChat, Xaero's Minimap waypoint sharing). The whitelist allows you to exempt specific mods from channel spoofing and translation exploit protection.

When enabled:
- **Brand is forced to Fabric** to maintain compatibility
- Whitelisted mods can register their channels and translation keys normally
- Non-whitelisted mods remain hidden from the server

> [!NOTE]
> Only mods that register network channels or translation keys are shown in the whitelist. Library mods and internal Fabric API modules are automatically filtered out.

---

### Isolate Pack Cache
Based on [LiquidBounce](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloadQueue.java).

Server-required resource packs could be used fingerprint client instance across accounts.

https://alaggydev.github.io/posts/cytooxien/

Instead of storing all resource packs in a shared cache (`~/.minecraft/downloads/`), OpSec creates separate cache directories for each account UUID.

---

### Block Local URLs

Based on [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer)

Malicious servers can send resource pack URLs to probe your local network devices.

https://alaggydev.github.io/posts/cytooxien/

OpSec detects resource pack URLs pointing to local/private IP addresses and redirects them to an invalid address (`http://0.0.0.0:0/opsec-blocked`), tricking the server into thinking that requests failed naturally.

---

### Translation Exploit Protection

Servers can send translatable text in signs and anvils containing keys like `key.attack` or `key.hide_icons` to probe which keys you have bound or mod UI elements your client can resolve. This can reveal the client's installed mods.

https://wurst.wiki/sign_translation_vulnerability

OpSec intercepts translation keys and blocks Minecraft from resolving it while also returning Vanilla default key bind values to appear like a default Vanilla client.

Spoofing mod keybinds (Returns raw translation keys/fallback instead of keybind values):
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

### Meteor Fix

Meteor client has their own key protection implementation which can lead to a guaranteed detection with the translation key exploit.

Sometimes the server uses a fallback value so that instead of expecting the raw key from a Vanilla client its expecting the fallback value instead.

`Key doesn't exist → returns fallbackvalue`

Meteor's key spoofing implementation:

```
1. When the server sends a sign with {"translate":"key.meteor-client.open-gui", "fallback":"⟦FALLBACK⟧"}:
2. Meteor intercepts during AbstractSignEditScreen constructor
3. Detects "meteor-client" in the key
4. REPLACES the TranslatableTextContent with PlainTextContent.Literal("key.meteor-client.open-gui") to prevent Minecraft from resolving it to key bind values
```

When the server uses a sign exploit with fallback value on Meteor Client:
```
'key.meteor-client.open-gui' 'Right Shift'→'key.meteor-client.open-gui'
```
What a Vanilla response would actaully be:
```
'key.meteor-client.open-gui' '⟦FALLBACK⟧'→'⟦FALLBACK⟧'
```
OpSec's bandaid fix for Meteor is to blacklist the `AbstractSignEditScreenMixin` Mixin to disable Meteor's broken translation protection. Allowing OpSec's protection to take over, which already handle fallbacks correctly to match the Vanilla response.

---

### Chat Signing Control

Based on [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Cryptographic signatures by default are attached to every chat messages. Removing them makes it impossible to track and associate your chat messages with your Minecraft client, and, by extension, Microsoft account.

**Modes:**
- **OFF**: Strip all chat signatures, but prevents you from chatting in servers that enforces secure chat.
- **Auto**: Only sign messages when the server enforces secure chat.
- **ON**: Default Minecraft behavior, signs every messages.

---

### Telemetry Blocking

From [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Minecraft collects and sends telemetry data to Mojang, including:
- Game events and player actions
- Performance metrics
- Client configuration
- Usage statistics

OpSec blocks telemetry sending to Mojang when telemetry blocking is enabled. Does not effect gameplay.

## Building from Source

### Prerequisites

- **Java 21** or higher
- **Gradle** (included via wrapper)

### Building the Minecraft Mod

1. **Clone the repository**
   ```bash
   git clone https://github.com/aurickk/OPSEC.git
   cd OPSEC
   ```

2. **Build all versions**
   ```bash
   # Windows
   .\gradlew.bat build
   
   # Linux/Mac
   ./gradlew build
   ```

3. **Build a specific version**
   ```bash
   # Build for a specific version
   ./gradlew chiseledBuild -Pchisel=1.21.4
   ./gradlew chiseledBuild -Pchisel=1.21.11
   ```

Output JARs are located in `versions/<minecraft_version>/build/libs/`:
| Build Version | Supports |
|---------------|----------|
| 1.21.4 | 1.21.1 – 1.21.5 |
| 1.21.6 | 1.21.6 – 1.21.8 |
| 1.21.9 | 1.21.9 – 1.21.10 |
| 1.21.11 | 1.21.11+ |


## References

- [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) - Local URL blocking and sign translation protection
- [LiquidBounce](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloadQueue.java) - Cached server resource pack isolation
- [No Chat Reports](https://modrinth.com/mod/no-chat-reports) - Chat signing control and telemetry blocking
- [No Prying Eyes](https://github.com/Daxanius/NoPryingEyes?tab=readme-ov-file) - Secure chat enforcement detection
- [MixinSquared](https://github.com/Bawnorton/MixinSquared) - Mixin cancellation for Meteor Fix
- [Stonecutter](https://stonecutter.kikugie.dev/) - Multi-version build system
