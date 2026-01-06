package com.george_vi.georgetp;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
import java.util.concurrent.CompletableFuture;

public class WarpManager {
    Map<String, Location> allWarps = new HashMap<>();
    File file;
    YamlConfiguration config;

    final GTPlugin plugin;

    public boolean dirty;
    int saveCounter;

    public WarpManager(GTPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "warps.yml");

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException err) {
                plugin.getLogger().severe("Can't save warps.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        allWarps.clear();

        for (String key : config.getKeys(false)) {

            String world = config.getString(key + ".world", null);
            double x = config.getDouble(key + ".x", 0);
            double y = config.getDouble(key + ".y", 0);
            double z = config.getDouble(key + ".z", 0);
            float yaw = (float) config.getDouble(key + ".yaw", 0f);
            float pitch = (float) config.getDouble(key + ".pitch", 0f);
            if (world != null && Bukkit.getWorld(world) != null)
                allWarps.put(key.replace('◦', '.'), new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch));
        }
    }

    public int runSetWarpCommand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || !player.hasPermission("georgestp.setwarp"))
            return 1;
        String warpName = ctx.getArgument("warp", String.class);

        Location location = ctx.getSource().getLocation();
        allWarps.put(warpName, location);
        player.sendMessage(GTPlugin.langUtil.getMessage("warp-set"));
        dirty = true;
        return 1;
    }

    public int runSetAlignedWarpCommand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || !player.hasPermission("georgestp.setwarp"))
            return 1;
        String warpName = ctx.getArgument("warp", String.class);

        Location location = ctx.getSource().getLocation();
        double x = location.x();
        double y = location.y();
        double z = location.z();
        float yaw = location.getYaw();
        x = Math.round(x - 0.5) + 0.5;
        y = Math.round(y - 0.5) + 0.5;
        z = Math.round(z - 0.5) + 0.5;
        if (yaw < 45 && yaw > -45)
            yaw = 0;
        else if (yaw < -45 && yaw > -135)
            yaw = -90;
        else if (yaw < -135 || yaw > 135)
            yaw = 180;
        else if (yaw < 135 && yaw > 45)
            yaw = 90;

        allWarps.put(warpName, new Location(location.getWorld(), x, y, z, yaw, 0f));
        player.sendMessage(GTPlugin.langUtil.getMessage("warp-set"));
        dirty = true;
        return 1;
    }

    public int runDelWarpCommand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || !player.hasPermission("georgestp.setwarp"))
            return 1;
        String warpName = ctx.getArgument("warp", String.class);

        if (allWarps.remove(warpName) == null)
            player.sendMessage(GTPlugin.langUtil.getMessage("warp-doesnt-exist", Map.of("warp", warpName)));
        else
            player.sendMessage(GTPlugin.langUtil.getMessage("warp-removed"));
        dirty = true;
        return 1;
    }

    public int runWarpCommand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || GTPlugin.coolDownCheck(player) || !player.hasPermission("georgestp.warp"))
            return 1;
        String warpName = ctx.getArgument("warp", String.class).toLowerCase();
        Location location = allWarps.get(warpName);
        if (location == null) {
            player.sendMessage(GTPlugin.langUtil.getMessage("warp-doesnt-exist", Map.of("warp", warpName)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
        } else {
            player.sendActionBar(GTPlugin.langUtil.getMessage("warp-teleporting", Map.of("warp", warpName)));
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

               saveAllWarps();
            }
        }
    }

    public void saveAllWarps() {
        config = new YamlConfiguration();
        for (String k : new HashSet<>(config.getKeys(false))) config.set(k, null);

        allWarps.forEach((id, location) -> {
            // This is a hacky way to not separate paths at '.' characters
            String base = id.replace('.', '◦');
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
            plugin.getLogger().severe("Can't save warps.yml");
        }
    }

    public CompletableFuture<Suggestions> suggestWarp(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String string : allWarps.keySet()) {
            if (string.startsWith(builder.getRemainingLowerCase()))
                builder.suggest(string);
        }
        return builder.buildFuture();
    }

    public Location getWarp(Player player, String id) {
        return allWarps.get(id);
    }
}
