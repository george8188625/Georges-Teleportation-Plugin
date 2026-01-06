package com.george_vi.georgetp;

import com.george_vi.georgetp.GTPlugin;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class HomeManager {
    Map<UUID, Map<String, Location>> allHomes = new HashMap<>();
    File file;
    YamlConfiguration config;

    final GTPlugin plugin;

    boolean dirty;
    int saveCounter;

    public HomeManager(GTPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "homes.yml");

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException err) {
                plugin.getLogger().severe("Can't save homes.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        allHomes.clear();

        for (String key : config.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            var homeMap = config.get(key);
            if (!(homeMap instanceof ConfigurationSection homeMapConfig))
                continue;
            Map<String, Location> playerHomes = new HashMap<>();

            for (String homeKey : homeMapConfig.getKeys(false)) {

                String world = homeMapConfig.getString(homeKey + ".world", null);
                String name = homeMapConfig.getString(homeKey + ".name", null);
                double x = homeMapConfig.getDouble(homeKey + ".x", 0);
                double y = homeMapConfig.getDouble(homeKey + ".y", 0);
                double z = homeMapConfig.getDouble(homeKey + ".z", 0);
                float yaw = (float) homeMapConfig.getDouble(homeKey + ".yaw", 0f);
                float pitch = (float) homeMapConfig.getDouble(homeKey + ".pitch", 0f);
                if (world != null && name != null) {
                    World w = Bukkit.getWorld(world);
                    if (w != null)
                        playerHomes.put(name.replace('◦', '.'), new Location(w, x, y, z, yaw, pitch));
                }
            }
            allHomes.put(uuid, playerHomes);
        }
    }

    public int runSetHomeCommand(CommandContext<CommandSourceStack> ctx, boolean specifyHome) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || !player.hasPermission("georgestp.home"))
            return 1;

        String home = specifyHome ? ctx.getArgument("home", String.class) : "main";

        Map<String, Location> playerHomes = allHomes.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());

        if (!playerHomes.containsKey(home) && playerHomes.size() >= plugin.getConfig().getInt("home-limit")) {
            player.sendMessage(GTPlugin.langUtil.getMessage("home-limit"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return 1;
        }

        Location location = ctx.getSource().getLocation();
        playerHomes.put(home, location);
        player.sendActionBar(GTPlugin.langUtil.getMessage("home-set"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
        dirty = true;
        return 1;
    }

    public int runDelHomeCommand(CommandContext<CommandSourceStack> ctx, boolean specifyHome) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || !player.hasPermission("georgestp.home"))
            return 1;

        String home = specifyHome ? ctx.getArgument("home", String.class) : null;

        Map<String, Location> playerHomes = allHomes.get(player.getUniqueId());

        if (playerHomes == null || playerHomes.isEmpty()) {
            player.sendMessage(home == null ? GTPlugin.langUtil.getMessage("home-missing") : GTPlugin.langUtil.getMessage("home-missing-name", Map.of("home", home)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return 1;
        }

        if (home != null) {
            if (playerHomes.remove(home) != null) {
                player.sendMessage(GTPlugin.langUtil.getMessage("home-removed", Map.of("home", home)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
                dirty = true;
            } else {
                player.sendMessage(GTPlugin.langUtil.getMessage("home-missing-name", Map.of("home", home)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            }
            return 1;
        }

        if (playerHomes.size() == 1) {
            allHomes.remove(player.getUniqueId());
            player.sendMessage(GTPlugin.langUtil.getMessage("home-removed-unspecified"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
            dirty = true;
        } else {
            player.sendMessage(GTPlugin.langUtil.getMessage("home-specify"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
        }

        return 1;
    }

    public int runHomeCommand(CommandContext<CommandSourceStack> ctx, boolean specifyHome) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || GTPlugin.coolDownCheck(player) || !player.hasPermission("georgestp.home"))
            return 1;

        String home = specifyHome ? ctx.getArgument("home", String.class) : null;

        Map<String, Location> playerHomes = allHomes.get(player.getUniqueId());

        if (playerHomes == null || playerHomes.isEmpty()) {
            player.sendMessage(GTPlugin.langUtil.getMessage("home-missing"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return 1;
        }

        Location location = specifyHome ? playerHomes.get(home) : (playerHomes.size() == 1 ? (playerHomes.values().iterator().next()) : null);

        if (location == null) {
            player.sendMessage(specifyHome ? GTPlugin.langUtil.getMessage("home-missing-name", Map.of("home", home)) : GTPlugin.langUtil.getMessage("home-specify"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
        } else {
            player.sendActionBar(GTPlugin.langUtil.getMessage("home-teleporting"));
            GTPlugin.teleportManager.addTeleport(player, location, plugin.getConfig().getInt("tp-standstill"));
        }

        return 1;
    }

    public void tick() {
        saveCounter++;
        if (saveCounter >= 1200) {
            saveCounter = 0;
            if (dirty) {
                dirty = false;
                saveAllHomes();
            }
        }
    }

    public void saveAllHomes() {
        config = new YamlConfiguration();
        for (String k : new HashSet<>(config.getKeys(false))) config.set(k, null);

        allHomes.forEach((uuid, homeMap) -> {
            homeMap.forEach((name, location) -> {
                // This is a hacky way to not separate paths at '.' characters
                String base = uuid.toString() + "." + name.replace('.', '◦');
                config.set(base + ".world", location.getWorld().getName());
                config.set(base + ".name", name);
                config.set(base + ".x", location.getX());
                config.set(base + ".y", location.getY());
                config.set(base + ".z", location.getZ());
                config.set(base + ".yaw", location.getYaw());
                config.set(base + ".pitch", location.getPitch());
            });
        });

        try {
            config.save(file);
        } catch (IOException err) {
            plugin.getLogger().severe("Can't save homes.yml");
        }
    }

    public CompletableFuture<Suggestions> suggestHome(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getExecutor() instanceof Player player)
            for (String string : allHomes.getOrDefault(player.getUniqueId(), Collections.emptyMap()).keySet())
                if (string.startsWith(builder.getRemainingLowerCase()))
                    builder.suggest(string);
        return builder.buildFuture();
    }

}
