package com.george_vi.georgetp.back;

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
import java.util.*;

public class BackTeleportManager {
    Map<UUID, BackTeleportEntry> allBacks = new HashMap<>();
    File file;
    YamlConfiguration config;

    final GTPlugin plugin;

    public boolean dirty;
    int saveCounter;

    public BackTeleportManager(GTPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "backs.yml");

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException err) {
                plugin.getLogger().severe("Can't save backs.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        allBacks.clear();

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
            int remainingTicks = config.getInt(key + ".remaining-ticks", 0);
            if (world != null && Bukkit.getWorld(world) != null)
                allBacks.put(uuid, new BackTeleportEntry(new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch), remainingTicks));
        }
    }

    public int runBackCommand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || GTPlugin.coolDownCheck(player) || !player.hasPermission("georgestp.back"))
            return 1;

        BackTeleportEntry entry = allBacks.get(player.getUniqueId());

        if (entry == null) {
            player.sendMessage(Component.text("Your don't have any non-expired previous locations!").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return 1;
        }

        player.sendActionBar(Component.text("Teleporting to your previous location...").color(GTPlugin.mainThemeColor));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
        GTPlugin.teleportManager.addTeleportNoBack(player, entry.location, plugin.getConfig().getInt("tp-standstill"));
        allBacks.remove(player.getUniqueId());
        dirty = true;
        return 1;
    }


    public void tick() {
        List<UUID> toRemove = new LinkedList<>();
        allBacks.forEach((id, entry) -> {
            if (entry.tick())
                toRemove.add(id);
        });

        if (!toRemove.isEmpty())
            dirty = true;

        for (UUID id : toRemove)
            allBacks.remove(id);

        saveCounter++;
        if (saveCounter >= 1200) {
            saveCounter = 0;
            if (dirty) {
                dirty = false;

                saveAllBacks();
            }
        }
    }

    public void saveAllBacks() {
        config = new YamlConfiguration();
        for (String k : new HashSet<>(config.getKeys(false))) config.set(k, null);

        allBacks.forEach((uuid, entry) -> {
            String base = uuid.toString();
            config.set(base + ".world", entry.location.getWorld().getName());
            config.set(base + ".x", entry.location.getX());
            config.set(base + ".y", entry.location.getY());
            config.set(base + ".z", entry.location.getZ());
            config.set(base + ".yaw", entry.location.getYaw());
            config.set(base + ".pitch", entry.location.getPitch());
            config.set(base + ".remaining-ticks", entry.expiration);
        });

        try {
            config.save(file);
        } catch (IOException err) {
            plugin.getLogger().severe("Can't save backs.yml");
        }
    }

    public void addBack(Player player, Location location) {
        if (!plugin.getConfig().getBoolean("back-enabled"))
            return;
        allBacks.put(player.getUniqueId(), new BackTeleportEntry(location, plugin.getConfig().getInt("back-expiration")));
        dirty = true;
    }
}
