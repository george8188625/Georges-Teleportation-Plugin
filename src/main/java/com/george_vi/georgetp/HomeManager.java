package com.george_vi.georgetp;

import com.george_vi.georgetp.GTPlugin;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class HomeManager {
    Map<UUID, Location> allHomes = new HashMap<>();
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

            String world = config.getString(key + ".world", null);
            double x = config.getDouble(key + ".x", 0);
            double y = config.getDouble(key + ".y", 0);
            double z = config.getDouble(key + ".z", 0);
            float yaw = (float) config.getDouble(key + ".yaw", 0f);
            float pitch = (float) config.getDouble(key + ".pitch", 0f);
            if (world != null && Bukkit.getWorld(world) != null)
                allHomes.put(uuid, new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch));
        }
    }

    public int runSetHomeCommand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || !player.hasPermission("georgestp.home"))
            return 1;

        Location location = ctx.getSource().getLocation();
        allHomes.put(player.getUniqueId(), location);
        player.sendActionBar(GTPlugin.langUtil.getMessage("home-set"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
        dirty = true;
        return 1;
    }

    public int runHomeCommand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || GTPlugin.coolDownCheck(player) || !player.hasPermission("georgestp.home"))
            return 1;

        Location location = allHomes.get(player.getUniqueId());
        if (location == null) {
            player.sendActionBar(GTPlugin.langUtil.getMessage("home-missing"));
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

        allHomes.forEach((uuid, location) -> {
            String base = uuid.toString();
            config.set(base + ".world", location.getWorld().getName());
            config.set(base + ".x", location.getX());
            config.set(base + ".y", location.getY());
            config.set(base + ".z", location.getZ());
            config.set(base + ".yaw", location.getYaw());
            config.set(base + ".pitch", location.getPitch());
        });

        try {
            config.save(file);
        } catch (IOException err) {
            plugin.getLogger().severe("Can't save homes.yml");
        }
    }
}
