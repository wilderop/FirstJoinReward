package com.wilder0p.firstjoinreward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class FirstJoinReward extends JavaPlugin implements Listener {

    private long lastRewardTime = 0L;
    private File dataFile;
    private FileConfiguration dataConfig;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("FirstJoinReward enabled. Default reward: "
                + getConfig().getString("reward.material", "PETRIFIED_OAK_SLAB")
                + " x" + getConfig().getInt("reward.amount", 10));
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Only the first player online (server was empty)
        if (Bukkit.getOnlinePlayers().size() != 1) {
            return;
        }

        Player player = event.getPlayer();
        long cooldownSeconds = getConfig().getLong("cooldown-seconds", 0L);
        long now = System.currentTimeMillis();

        if (cooldownSeconds > 0 && (now - lastRewardTime) < (cooldownSeconds * 1000L)) {
            return; // still on cooldown
        }

        // Give the reward
        ItemStack reward = createRewardItem();
        if (reward == null) {
            getLogger().warning("Could not create reward item – check material name in config.yml");
            return;
        }

        var leftovers = player.getInventory().addItem(reward);
        if (!leftovers.isEmpty()) {
            // Inventory full – drop remaining items at the player's feet
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }

        lastRewardTime = now;
        saveData();

        getLogger().info(player.getName() + " received the empty-server reward.");

        // Discord webhook (async)
        if (getConfig().getBoolean("discord.enabled", false)) {
            String webhookUrl = getConfig().getString("discord.webhook-url", "");
            if (webhookUrl != null && !webhookUrl.isBlank() && !webhookUrl.contains("YOUR_WEBHOOK")) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> sendDiscordNotification(player.getName(), webhookUrl));
            }
        }
    }

    private ItemStack createRewardItem() {
        String materialName = getConfig().getString("reward.material", "PETRIFIED_OAK_SLAB");
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            return null;
        }

        int amount = Math.max(1, Math.min(getConfig().getInt("reward.amount", 10), material.getMaxStackSize()));
        ItemStack item = new ItemStack(material, amount);

        List<String> loreLines = getConfig().getStringList("reward.lore");
        if (!loreLines.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    private void sendDiscordNotification(String playerName, String webhookUrl) {
        try {
            boolean useEmbed = getConfig().getBoolean("discord.use-embed", true);
            String json;

            if (useEmbed) {
                String title = getConfig().getString("discord.embed.title", "Empty Server Reward");
                String description = getConfig().getString("discord.embed.description", "**{player}** was the first to join and received the reward!")
                        .replace("{player}", playerName);
                int color = getConfig().getInt("discord.embed.color", 5763719);

                // Minimal valid Discord embed payload
                json = """
                        {
                          "embeds": [{
                            "title": "%s",
                            "description": "%s",
                            "color": %d
                          }]
                        }
                        """.formatted(
                        escapeJson(title),
                        escapeJson(description),
                        color
                );
            } else {
                String content = getConfig().getString("discord.message", "**{player}** just claimed the empty-server reward!")
                        .replace("{player}", playerName);
                json = """
                        {
                          "content": "%s"
                        }
                        """.formatted(escapeJson(content));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                getLogger().warning("Discord webhook returned HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send Discord webhook notification", e);
        }
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ---------- Data persistence (cooldown) ----------

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            lastRewardTime = 0L;
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        lastRewardTime = dataConfig.getLong("last-reward-time", 0L);
    }

    private void saveData() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        dataConfig.set("last-reward-time", lastRewardTime);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    // ---------- Command ----------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("firstjoinreward")) {
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("firstjoinreward.reload")) {
                sender.sendMessage(Component.text("You do not have permission to do that."));
                return true;
            }
            reloadConfig();
            loadData();
            sender.sendMessage(Component.text("FirstJoinReward configuration reloaded."));
            getLogger().info(sender.getName() + " reloaded the configuration.");
            return true;
        }

        sender.sendMessage(Component.text("Usage: /firstjoinreward reload"));
        return true;
    }
}
