<p align="center">
<img src="https://github.com/user-attachments/assets/085ae33d-eee9-4624-b7e2-d65ef565342d" alt="duper trooper mogging my whole family" width="15%"/>
</p>

<p align="center">A client-side Minecraft mod that provides protection against client fingerprinting, tracking exploits, and other privacy focused features.</p>


> [!WARNING]
> This is a passion project mostly built with AI. Everything is tested but don't rely on this for actual security. If you want something proven, use [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) **(See [EP Compatibility](#exploitpreventer-compatibility) and [Meteor Client notes](#pre-patched-meteor-client))**. OpSec just tries to offer more features and interactive customization on top of the basics.

## What it does

- **[Brand Spoofing](#brand-spoofing)** - Change client brand name to Vanilla or Fabric
- **[Channel Spoofing](#channel-spoofing)** - Hide or fake mod channels to prevent mod detection
- **[Isolate Pack Cache](#isolate-pack-cache)** - Isolate resource packs per-account to prevent tracking
- **[Block Local URLs](#block-local-urls)** - Block resource pack redirects to local/private addresses
- **[Bypass Server Pack Requirement](#bypass-server-pack-requirement)** - Let the user toggle server resource pack(s) whereas in vanilla wouldn't allow you to do so
- **[Key Resolution Protection](#key-resolution-protection)** - Protect against key resolution mod detection in any server packet
- **[Meteor Fix](#meteor-fix)** - Disable Meteor Client's broken key resolution protection
- **[Mod Whitelist](#mod-whitelist)** - Automatically or manually exempt mods from channel spoofing and key resolution protection
- **[Chat Signing Control](#chat-signing-control)** - Configure chat message signing behavior
- **[Account Manager](#account-manager)** - Switch between Minecraft accounts using session tokens
- **[Telemetry Blocking](#telemetry-blocking)** - Disable data collection sent to Mojang

> If you're interested in servers or plugins that are using tracking related exploits then look in the [Hall of Shame](https://github.com/NikOverflow/ExploitPreventer/blob/master/HALL_OF_SHAME.md).

## Requirements

- **Minecraft** 1.21.1 – 26.1.2
- **Fabric Loader** 0.16.0+ (0.18.5+ for MC 26.1.x)
- **Fabric API** (matching your Minecraft version)

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version
2. Download the latest [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version
3. Download the latest `opsec-[minecraft_version]+[version].jar` from the [Releases](https://github.com/aurickk/OpSec/releases/) page
4. Place both mods in your `.minecraft/mods` folder
5. Launch Minecraft

## Configurations

The settings menu is accessible via the `OpSec` button in the multiplayer server selection menu header or via [Mod Menu](https://modrinth.com/mod/modmenu).

<img width="1465" height="820" alt="image" src="https://github.com/user-attachments/assets/c69a768b-60ac-4f78-9705-184f6c4e4495" />


If settings are changed while connected to a server it is recommended to reconnect to the server to ensure changes are applied.

#### Identity Tab

| Setting | Description |
|---------|-------------|
| **Spoof Brand** | Enable/disable [brand spoofing](#brand-spoofing) |
| **Brand Type** | Select which brand to appear as (Vanilla/Fabric) |
| **Spoof Channels** | Enable/disable [channel spoofing](#channel-spoofing) |

#### Protection Tab

| Setting | Description |
|---------|-------------|
| **Isolate Pack Cache** | Enable/disable [cache isolation](#isolate-pack-cache) |
| **Block Local Pack URLs** | Enable/disable [local URL blocking](#block-local-urls) |
| **Bypass Server Pack Requirement** | Configure [server pack bypass](#bypass-server-pack-requirement) behavior:<br/>• **MANUAL** (default): Default vanilla behavior on push. You can still toggle any server pack.<br/>• **ASK**: Server resource pack not applied but with consent screen to ask if the pack(s) should be applied<br/>• **ALWAYS ON**: Server resource pack not applied by default. You can still toggle any server pack |
| **Clear Cache** | Delete all cached server resource packs |
| **Key Resolution Spoofing** | Enable/disable [key resolution protection](#key-resolution-protection) |
| **Fake Default Keybinds** | Return default vanilla keybind values instead of actual bindings |
| **Meteor Fix** | Disable Meteor Client's broken key resolution protection (only shown when Meteor is installed) |
| **Signing Mode** | Configure [chat signing](#chat-signing-control) behavior:<br/>• **OFF**: Strip signatures (maximum privacy)<br/>• **ON**: Default Minecraft behavior<br/>• **AUTO**: Only sign when required (recommended) |
| **Disable Telemetry** | Enable/disable [telemetry blocking](#telemetry-blocking) |

#### Whitelist Tab

| Setting | Description |
|---------|-------------|
| **Whitelist Mode** | Select whitelist behavior:<br/>• **OFF**: All mod content blocked<br/>• **AUTO**: Mods with network channels are automatically whitelisted (default)<br/>• **CUSTOM**: Manually select which mods to whitelist |
| **Installed Mods** | Toggle individual mods ON/OFF to exempt them from protection (CUSTOM mode only) |

#### Miscellaneous Tab

| Setting | Description |
|---------|-------------|
| **Show Alerts** | Display chat messages when tracking is detected |
| **Show Toasts** | Display popup notifications for important events |
| **Log Detections** | Log all detection events to game log for transparency |

#### Accounts Tab

| Setting | Description |
|---------|-------------|
| **Saved Accounts** | List of added accounts with login/logout and remove buttons |
| **Refresh All** | Revalidate all account tokens (invalid tokens marked red) |
| **Add Session Token** | Add a new account using a session (access) token |
| **Import** | Import accounts from a JSON file |
| **Export** | Export accounts to a JSON file |

### Debug Commands

Use `/opsec` in-game to access debug information:

| Command | Description |
|---------|-------------|
| `/opsec` | Show available commands |
| `/opsec info` | Show overview of all tracked mods |
| `/opsec info <mod>` | Show details for a specific mod (translation keys, key-bind key, channels) |
| `/opsec channels` | Show all tracked network channels with whitelist status |

### Understanding Alerts

- **Key Resolution Exploit Detected**: Server is probing your keybind
- **Resource Pack Fingerprinting Detected**: Suspicious resource pack URL detected
- **Local URL Scan Detected**: Resource pack redirect targeted a local/private address

## Feature Details

### Brand Spoofing

Servers can query your client brand to detect whether you're running a modded client. OpSec intercepts the brand packet and replaces it with your chosen brand:

- **Vanilla** - Appear as an unmodified Minecraft client
- **Fabric** - Appear as a standard Fabric client (default)

The brand setting also determines how [Channel Spoofing](#channel-spoofing) and [Key Resolution Protection](#key-resolution-protection) behave for each mode.

> [!IMPORTANT]
> Server plugins like [AntiSpoof](https://github.com/GigaZelensky/AntiSpoof) can detect the discrepancy between the client brand name and mod channels and flag clients for spoofing if [Channel Spoofing](#channel-spoofing) wasn't enabled.

---

### Isolate Pack Cache
Based on [LiquidBounce](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloadQueue.java).

Server-required resource packs could be used to fingerprint client instance across accounts.

https://alaggydev.github.io/posts/cytooxien/

Instead of storing all resource packs in a shared cache (`~/.minecraft/downloads/`), OpSec creates separate cache directories for each account UUID.

---

### Block Local URLs

Taken from [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) by [NikOverFlow](https://github.com/NikOverflow)

Malicious servers can send resource pack URLs that redirect to your local network to probe for devices and services.

https://alaggydev.github.io/posts/cytooxien/

OpSec checks if a redirect or normal request targets a local address, then blocks the connection.

---

### Bypass Server Pack Requirement

Servers can push required resource packs the client is forced to apply. Declining them or toggling required server resource pack(s) is impossible on vanilla client. And fake accepting them is detectable with the key resolution exploit by probing the client's resource pack key response.

Minecraft still accepts and downloads these packs as normal but OpSec lets you toggle the pack textures at the client level. The language file of the server resource pack is preserved because servers can probe translation keys (e.g. via `{"translate": "some.pack.key"}`) to detect whether the pack is actually applied, and a vanilla client with the pack loaded would resolve those keys to the pack-defined value.

With Opsec installed, server resource pack(s) appears as a normal user-toggleable entry in the resource pack menu so you can flip between stripped and fully-loaded.

**Modes:**
- **MANUAL** (default): Required packs apply fully like vanilla on push. Optional packs follow vanilla toggle semantics. The user can still unequip any server pack from the pack menu to strip it while keeping lang loaded.
- **ASK**: Required packs are stripped on push and a consent overlay prompts `[Continue]` / `[Load Pack For Real]`.
- **ALWAYS ON**: All server packs are stripped on push. No overlay. You can still toggle them back.


---

### Key Resolution Protection

Servers can send translatable text containing keys like `key.attack` or `key.hide_icons` in any server packet to probe which keys you have bound or mod UI elements your client can resolve. This can reveal the client's installed mods.

https://wurst.wiki/sign_translation_vulnerability

OpSec tracks when translation keys are being resolved during server packet processing and blocks Minecraft from resolving them based on your selected brand mode:

#### Mode-Specific Behavior

- **Vanilla mode**: Blocks all mod keys, returns default keybind values for vanilla keys
- **Fabric mode**: Allows Fabric API keys and whitelisted mod keys, blocks everything else

When **Fake Default Keybinds** is disabled, vanilla keybinds resolve to their actual values.

#### Examples

Spoofing mod keybinds (Returns raw keys/fallback value instead of keybind values):
```
[key.meteor-client.open-commands] '.'→'key.meteor-client.open-commands'
[key.meteor-client.open-gui] 'Right Shift'→'key.meteor-client.open-gui'
```

Spoofing vanilla keybinds with **Fake Default Keybinds** enabled (Returns default keybinds):
```
[key.hotbar.6] 'Q'→'6'
[key.hotbar.7] 'E'→'7'
[key.hotbar.8] 'R'→'8'
```

---

### Meteor Fix

Meteor client has their own key protection implementation which can lead to a guaranteed detection with the key resolution exploit.

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

<img width="847" height="107" alt="image" src="https://github.com/user-attachments/assets/e157ae3f-6beb-4823-aca0-9c61573264e2" />

What a Vanilla response would actaully be:
```
'key.meteor-client.open-gui' '⟦FALLBACK⟧'→'⟦FALLBACK⟧'
```
OpSec's bandaid fix for Meteor is to blacklist the `AbstractSignEditScreenMixin` Mixin to disable Meteor's broken key resolution protection. Allowing OpSec's protection to take over, which already handle fallbacks correctly to match the Vanilla response.

<img width="901" height="107" alt="image" src="https://github.com/user-attachments/assets/506b9c73-6747-40f8-9a56-52c0353034b4" />

---

### ExploitPreventer Compatibility

For users that prefers [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer)'s core protection implementation but still need OpSec's additional features, both can be installed alongside each other. Overlapping features are automatically disabled to let EP handle them, note that you would lose OpSec features such as channels spoofing. The following OpSec features are deferred to EP:

- [Brand Spoofing](#brand-spoofing)
- [Channel Spoofing](#channel-spoofing)
- [Isolate Pack Cache](#isolate-pack-cache)
- [Block Local URLs](#block-local-urls)
- [Key Resolution Protection](#key-resolution-protection)
- [Mod Whitelist](#mod-whitelist)

These settings are grayed out in the config screen but your saved preferences are preserved. If you remove EP later, they restore automatically.

Features that don't overlap remain fully functional: alerts, chat signing, account manager, telemetry blocking, and [Meteor Fix](#meteor-fix).

#### Pre-patched Meteor Client

If you use Meteor Client with EP but **without** OpSec, you need a Meteor build that fixes the faulty sign translation protection such as [NikOverflow's patched build](https://github.com/NikOverflow/meteor-client/releases/tag/fix-sign) which removes the broken sign protection.

If you use continued to use OpSec, this is handled automatically by [Meteor Fix](#meteor-fix) regardless of Meteor version.

---

### Channel Spoofing

Servers can query your registered network channels to detect which mods you have installed.

When enabled, OpSec spoofs mod channels that are registered with the server based on your selected brand:
- **Vanilla mode**: Blocks ALL mod channels (pure vanilla client)
- **Fabric mode**: Only allows Fabric API channels and whitelisted mods, blocks other mods

> [!WARNING]
> May break server-dependent mod(s) if not whitelisted. Use the [Mod Whitelist](#mod-whitelist) to exempt specific mods like [VoiceChat](https://modrinth.com/plugin/simple-voice-chat) or disable channel spoofing.

---

### Mod Whitelist

Some mods require server communication to function properly (e.g., VoiceChat, Xaero's Minimap waypoint sharing). The whitelist allows you to exempt specific mods from channel spoofing and key resolution protection.

<img width="853" height="478" alt="whitelist settings menu" src="https://github.com/user-attachments/assets/6ae423de-dd98-47c1-a617-f6df747c9293" />

**Modes:**
- **OFF**: All mod content is blocked
- **AUTO** (default): Mods that register network channels are automatically whitelisted as they are the most likely to have server-side functionalities
- **CUSTOM**: Manually select which mods to whitelist from the installed mod list

When the whitelist is active (AUTO or CUSTOM):
- **Brand is forced to Fabric** since you are revealing Fabric mods
- Whitelisted mods can register their channels and translation keys normally
- Non-whitelisted mods remain hidden from the server

> [!NOTE]
> Only mods that register network channels, translatable keys and keybind keys are shown in the whitelist.

---

### Chat Signing Control

Based on [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Cryptographic signatures by default are attached to every chat messages. Removing them makes it impossible to track and associate your chat messages with your Minecraft client, and, by extension, Microsoft account.

**Modes:**
- **OFF**: Strip all chat signatures, but prevents you from chatting in servers that enforces secure chat.
- **Auto**: Only sign messages when the server enforces secure chat.
- **ON**: Default Minecraft behavior, signs every messages.

---

### Account Manager

Based on [Meteor Client](https://github.com/MeteorDevelopment/meteor-client).

Add Minecraft accounts with session tokens and switch between them without restarting the game. 

- **Session Token Login** - Add accounts using access tokens 
- **Refresh Token** - Fetch new session tokens for expired accounts
- **Offline Account** - Add username-only accounts without authentication
- **Account Switching** - Click an account to login, click again to logout to original account
- **Token Validation** - Refresh to check if tokens are still valid (expired tokens marked red)
- **Import/Export** - Backup and restore accounts via JSON files

> [!NOTE]
> Session tokens expire after some time. Use the Refresh button to check validity.

---

### Telemetry Blocking

From [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Minecraft collects and sends telemetry data to Mojang, including:
- Game events and player actions
- Performance metrics
- Client configuration
- Usage statistics

OpSec blocks telemetry sending to Mojang when telemetry blocking is enabled. Does not effect gameplay.

---


## Building from Source

### Prerequisites

- **Java 25**
- **Gradle** (included via wrapper)

### Building the Minecraft Mod

1. **Clone the repository**
   ```bash
   git clone https://github.com/aurickk/OpSec.git
   cd OpSec
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
   ./gradlew :1.21.4:build
   ./gradlew :1.21.11:build
   ./gradlew :26.1:build
   ```

Output JARs are located in `versions/<minecraft_version>/build/libs/`:
| Build Version | Supports |
|---------------|----------|
| 1.21.1 | 1.21 – 1.21.1 |
| 1.21.4 | 1.21.2 – 1.21.5 |
| 1.21.6 | 1.21.6 – 1.21.8 |
| 1.21.9 | 1.21.9 – 1.21.10 |
| 1.21.11 | 1.21.11 |
| 26.1 | 26.1 – 26.1.2 |


## References

- [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) - Local URL blocking and server key resolution protection anti-measures
- [LiquidBounce](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloadQueue.java) - Cached server resource pack isolation
- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) - Session token sign in
- [No Chat Reports](https://modrinth.com/mod/no-chat-reports) - Chat signing control and telemetry blocking
- [No Prying Eyes](https://github.com/Daxanius/NoPryingEyes?tab=readme-ov-file) - Secure chat enforcement detection
- [MixinSquared](https://github.com/Bawnorton/MixinSquared) - Mixin cancellation for Meteor Fix
- [Stonecutter](https://stonecutter.kikugie.dev/) - Multi-version build system
- [Fabric API](https://github.com/FabricMC/fabric-api) - Fabric translation and keybind keys

## Disclaimer

OpSec is a privacy tool designed to protect players from unwanted client fingerprinting and tracking. It is not intended or encouraged for use in bypassing server rules, evading bans, or gaining unfair advantages. Users are responsible for complying with the rules and terms of service of any server they connect to.
