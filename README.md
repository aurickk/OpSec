<p align="center">
<img src="https://github.com/user-attachments/assets/085ae33d-eee9-4624-b7e2-d65ef565342d" alt="duper trooper mogging my whole family" width="15%"/>
</p>

<p align="center">A client-side Minecraft mod that provides protection against client fingerprinting, tracking exploits, and other privacy focused features.</p>

## What it does

- **[Brand Spoofing](#brand-spoofing)** - Change client brand name to Vanilla, Fabric, or Forge
- **[Channel Spoofing](#channel-spoofing)** - Hide or fake mod channels to prevent mod detection
- **[Isolate Pack Cache](#isolate-pack-cache)** - Isolate resource packs per-account to prevent tracking
- **[Block Local URLs](#block-local-urls)** - Automatically fail local requests from server resource packs 
- **[Translation Exploit Protection](#translation-exploit-protection)** - Protect against key resolution mod detection
- **[Meteor Fix](#meteor-fix)** - Disable Meteor Client's broken translation protection
- **[Mod Whitelist](#mod-whitelist)** - Exempt specific mods from channel spoofing and translation protection
- **[Chat Signing Control](#chat-signing-control)** - Configure chat message signing behavior
- **[Account Manager](#account-manager)** - Switch between Minecraft accounts using session tokens
- **[Telemetry Blocking](#telemetry-blocking)** - Disable data collection sent to Mojang

## Requirements

- **Minecraft** 1.21.1 – 1.21.11
- **Fabric Loader** 0.16.0+
- **Fabric API** (matching your Minecraft version)

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version
2. Download the latest [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version
3. Download the latest `opsec-[minecraft_version]+[version].jar` from the [Releases](https://github.com/aurickk/OpSec/releases/) page
4. Place both mods in your `.minecraft/mods` folder
5. Launch Minecraft

## Configurations

The settings menu is accessible via the `OpSec` button in the multiplayer server selection menu footer or [Mod Menu](https://modrinth.com/mod/modmenu).

<img width="376" height="141" alt="opsec button" src="https://github.com/user-attachments/assets/01b68390-0610-42d4-bced-30b86d3d76e8" />

<img width="852" height="477" alt="opsec setting menu" src="https://github.com/user-attachments/assets/130a9ffb-a316-44c1-a4d5-0c55a51967d2" />

If settings are changed while connected to a server it is recommended to reconnect to the server to ensure changes are applied.

#### Identity Tab

| Setting | Description |
|---------|-------------|
| **Spoof Brand** | Enable/disable [brand spoofing](#brand-spoofing) |
| **Brand Type** | Select which brand to appear as (Vanilla/Fabric/Forge) |
| **Spoof Channels** | Enable/disable [channel spoofing](#channel-spoofing) |

#### Protection Tab

| Setting | Description |
|---------|-------------|
| **Isolate Pack Cache** | Enable/disable [cache isolation](#isolate-pack-cache) |
| **Block Local Pack URLs** | Enable/disable [local URL blocking](#block-local-urls) |
| **Clear Cache** | Delete all cached server resource packs |
| **Spoof Translation Keys** | Enable/disable [translation exploit protection](#translation-exploit-protection) |
| **Fake Default Keybinds** | Return default vanilla keybind values instead of actual bindings |
| **Meteor Fix** | Disable Meteor Client's broken translation protection (only shown when Meteor is installed) |
| **Signing Mode** | Configure [chat signing](#chat-signing-control) behavior:<br/>• **OFF**: Strip signatures (maximum privacy)<br/>• **ON**: Default Minecraft behavior<br/>• **AUTO**: Only sign when required (recommended) |
| **Disable Telemetry** | Enable/disable [telemetry blocking](#telemetry-blocking) |

#### Whitelist Tab

| Setting | Description |
|---------|-------------|
| **Enable Whitelist** | Enable the [mod whitelist](#mod-whitelist) feature |
| **Installed Mods** | Toggle individual mods ON/OFF to exempt them from protection |

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
| `/opsec info <mod>` | Show details for a specific mod (translation keys, keybinds, channels) |
| `/opsec channels` | Show all tracked network channels with whitelist status |

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

### Isolate Pack Cache
Based on [LiquidBounce](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloadQueue.java).

Server-required resource packs could be used to fingerprint client instance across accounts.

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

OpSec intercepts translation keys and blocks Minecraft from resolving them based on your selected brand mode:

#### Mode-Specific Behavior

- **Vanilla mode**: Blocks all mod keys, returns default keybind values for vanilla keys
- **Fabric mode**: Allows Fabric API keys and whitelisted mod keys, blocks everything else
- **Forge mode**: Returns fabricated Forge/FML translation values (e.g., `fml.menu.mods` → `"Mods"`), blocks other mod keys

When **Fake Default Keybinds** is disabled, vanilla keybinds resolve to their actual values.

#### Examples

Spoofing mod keybinds (Returns raw translation keys/fallback instead of keybind values):
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

Forge mode fabrication (Returns fake Forge values):
```
[fml.menu.mods] 'fml.menu.mods'→'Mods'
[forge.configgui.forgeCloudsEnabled] 'forge.configgui.forgeCloudsEnabled'→'Use Forge cloud renderer'
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

<img width="847" height="107" alt="image" src="https://github.com/user-attachments/assets/e157ae3f-6beb-4823-aca0-9c61573264e2" />

What a Vanilla response would actaully be:
```
'key.meteor-client.open-gui' '⟦FALLBACK⟧'→'⟦FALLBACK⟧'
```
OpSec's bandaid fix for Meteor is to blacklist the `AbstractSignEditScreenMixin` Mixin to disable Meteor's broken translation protection. Allowing OpSec's protection to take over, which already handle fallbacks correctly to match the Vanilla response.

<img width="901" height="107" alt="image" src="https://github.com/user-attachments/assets/506b9c73-6747-40f8-9a56-52c0353034b4" />

---
### Channel Spoofing

Servers can query your registered network channels to detect which mods you have installed.

When enabled, OpSec spoofs mod channels that are registered with the server based on your selected brand:
- **Vanilla mode**: Blocks ALL mod channels (pure vanilla client)
- **Fabric mode**: Only allows Fabric API channels and whitelisted mods, blocks other mods
- **Forge mode**: Imitate Forge channels, blocks all mod channels

> [!WARNING]
> May break server-dependent mod(s) if not whitelisted. Use the [Mod Whitelist](#mod-whitelist) to exempt specific mods like [VoiceChat](https://modrinth.com/plugin/simple-voice-chat) or disable channel spoofing.

---

### Mod Whitelist

Some mods require server communication to function properly (e.g., VoiceChat, Xaero's Minimap waypoint sharing). The whitelist allows you to exempt specific mods from channel spoofing and translation exploit protection.

<img width="853" height="478" alt="whitelist settings menu" src="https://github.com/user-attachments/assets/6ae423de-dd98-47c1-a617-f6df747c9293" />

When enabled:
- **Brand is forced to Fabric** to since you are revealing Fabric mods
- Whitelisted mods can register their channels and translation keys normally
- Non-whitelisted mods remain hidden from the server

> [!NOTE]
> Only mods that register network channels or translation keys are shown in the whitelist.

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

- **Java 21** or higher
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
   ```

Output JARs are located in `versions/<minecraft_version>/build/libs/`:
| Build Version | Supports |
|---------------|----------|
| 1.21.4 | 1.21.1 – 1.21.5 |
| 1.21.6 | 1.21.6 – 1.21.8 |
| 1.21.9 | 1.21.9 – 1.21.10 |
| 1.21.11 | 1.21.11 |


## References

- [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) - Local URL blocking and sign translation protection
- [LiquidBounce](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloadQueue.java) - Cached server resource pack isolation
- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) - Session token sign in
- [No Chat Reports](https://modrinth.com/mod/no-chat-reports) - Chat signing control and telemetry blocking
- [No Prying Eyes](https://github.com/Daxanius/NoPryingEyes?tab=readme-ov-file) - Secure chat enforcement detection
- [MixinSquared](https://github.com/Bawnorton/MixinSquared) - Mixin cancellation for Meteor Fix
- [Stonecutter](https://stonecutter.kikugie.dev/) - Multi-version build system
- [Forge](https://github.com/MinecraftForge/MinecraftForge) - Forge translation and keybind keys
- [Fabric API](https://github.com/FabricMC/fabric-api) - Fabric translation and keybind keys
