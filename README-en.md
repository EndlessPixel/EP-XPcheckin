# EP-XPcheckin

> A clean, fair, manageable, multilingual daily XP check-in plugin for Minecraft.  
> Compatible with multiple server versions, flexible configuration, and quick to enable with minimal setup.

## Features

- **Fair XP Distribution**  
  Random XP between 1 and 10 segments (each segment covers a 10% probability range). Large rewards are rare yet reasonable, balancing fun and fairness.

- **Streak Bonus**  
  Each consecutive check-in day grants an additional 10% XP. The streak resets upon missing a day, encouraging daily participation.

- **Join Reminder**  
  Automatically reminds players who haven’t checked in when they join the server. Players can enable/disable this reminder freely.

- **Multi-language Support**  
  Includes Chinese (zh-CN) and English (en) language packs by default. Easily switch between languages via the config file, with support for adding more languages.

- **Flexible Configuration**  
  Cooldown, broadcast threshold, streak multiplier, and more can be customized in `config.yml` – no code changes required.

- **Player Commands**  
  - `/checkin` – Complete today’s check-in and receive random XP  
  - `/checkin on/off` – Enable/disable check-in reminders  
  - `/checkin info` – View personal check-in record, streak, and total XP earned  
  - `/checkin top` – View leaderboard of total XP earned (top 10 players)

- **OP Admin Commands**  
  - `/checkin reload` – Hot-reload config & language files (replaces legacy `redata` command)  
  - `/checkin record <player> <date>` – Delete a specific player’s check-in record for a given date (supports name/UUID)  
  - `/checkin look <player/UUID>` – View any player’s (including offline) check-in information

- **Full TAB Completion**  
  Smart completion for subcommands, online players, and historical check-in dates – enhanced usability.

- **Safe & Persistent Data**  
  All check-in data (including daily details) is stored in `player.yml`. Asynchronous saving ensures no loss or corruption; manual reload is supported.

- **Global Broadcast Surprise**  
  When a player’s check-in XP reaches a configured threshold, a global broadcast is sent to enhance server atmosphere.

## Permissions

- **Player Commands** (`/checkin`, `/checkin on/off`, `/checkin info`, `/checkin top`) – No permission required; all players can use them by default.  
- **Admin Commands** (`/checkin reload`, `/checkin record`, `/checkin look`) – OP only; no extra permission nodes needed.

## File Structure

All files are auto-generated at:
```
plugins/EP-XPcheckin/
├─ config.yml       # Main configuration (cooldown, multiplier, language, etc.)
├─ player.yml       # Player check-in data (core data, auto-saved)
└─ lang/            # Language files directory
   ├─ zh-CN.yml     # Chinese language pack (default)
   └─ en.yml        # English language pack (ready to switch)
```

## Data Format (`player.yml`)

Contains full check-in data for each player:
- `lastCheckInDate` – Last check-in date
- `streak` – Current consecutive days count
- `totalTimes` – Total number of check-ins
- `totalXP` – Total XP earned
- `records` – Daily details (XP earned that day, streak at that time)
- `remind` – Reminder toggle state (on/off)

## Version Support

- **Minecraft Versions**: 1.12+ – 1.21+ (compatible with mainstream versions)  
- **Server Software**: Spigot, Paper, and derivatives (Paper recommended for better performance)  
- **Plugin Version**: v1.2 (official release, full bilingual support)

## How to Use

1. Place the compiled `EP-XPcheckin-1.2.jar` into your server’s `plugins` folder.
2. Restart the server – the plugin automatically generates configuration, language, and data files.
3. (Optional) Modify `config.yml` to customize plugin parameters, then run `/checkin reload` to hot-reload.
4. In-game, type `/checkin` to start your daily check-in. All commands support TAB completion.

## Configuration (`config.yml` Key Parameters)

### Language (default zh-CN, change to en for English)
```yaml
language:
  default: zh-CN
```

### Feature Settings
```yaml
settings:
  command-cooldown: 5          # Command cooldown in ticks (20 ticks = 1 second)
  join-remind-delay: 20        # Join reminder delay in ticks
  streak-multiplier: 0.1       # Streak bonus multiplier (0.1 = +10% per day)
  broadcast-above-xp: 1000000  # XP threshold for global broadcast
```

## Author

EndlessPixel Studio – system_mini  
The plugin is actively maintained. For bug reports or feature suggestions, please contact the author.