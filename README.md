# FirstJoinReward

Paper 26.1.2 plugin that gives a configurable item to the **first player** who joins when the server is empty.

## Features

- Configurable reward item (material, amount, lore)
- Optional cooldown (seconds) between rewards
- Discord webhook notification (plain message or embed)
- Simple `/firstjoinreward reload` command
- No permissions required to receive the reward

## Default reward

- **10× Petrified Oak Slabs** with a short lore

## Building

Requires **Java 25** and Maven.

```bash
mvn clean package
```

The jar will be in `target/FirstJoinReward.jar`.

## Installation

1. Drop `FirstJoinReward.jar` into your Paper server's `plugins/` folder.
2. Start the server once to generate `config.yml`.
3. Edit `plugins/FirstJoinReward/config.yml`:
   - Set your Discord webhook URL under `discord.webhook-url`
   - Adjust material / amount / lore / cooldown as desired
4. Run `/firstjoinreward reload` (or restart)

## Config overview

```yaml
reward:
  material: PETRIFIED_OAK_SLAB
  amount: 10
  lore:
    - "&7You were the first to join an empty server!"

cooldown-seconds: 0

discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."
  use-embed: true
  embed:
    title: "Empty Server Reward"
    description: "**{player}** was the first to join and received the reward!"
    color: 5763719
```

## Permissions

- `firstjoinreward.reload` – allows the reload command (default: op)
