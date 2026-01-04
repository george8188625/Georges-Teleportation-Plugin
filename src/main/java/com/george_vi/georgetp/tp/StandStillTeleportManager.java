package com.george_vi.georgetp.tp;

import com.george_vi.georgetp.GTPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StandStillTeleportManager {
    Map<UUID, StandStillTeleport> standStillTeleports = new HashMap<>();
    Map<UUID, AtomicInteger> coolDowns = new HashMap<>();

    final GTPlugin plugin;

    public StandStillTeleportManager(GTPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        List<UUID> coolDownsToRemove = new LinkedList<>();
        coolDowns.forEach((id, ticks) -> {
            if (ticks.decrementAndGet() <= 0)
                coolDownsToRemove.add(id);
        });

        for (UUID id : coolDownsToRemove)
            coolDowns.remove(id);

        List<StandStillTeleport> teleportsToExecute = new LinkedList<>();
        List<StandStillTeleport> teleportsToRemove = new LinkedList<>();
        standStillTeleports.forEach((id, teleport) -> {
            if (!teleport.stillValid())
                teleportsToRemove.add(teleport);
            else if (teleport.tick())
                teleportsToExecute.add(teleport);
            if (teleport.ticksLeft % 20 == 0 && teleport.ticksLeft != 0) {
                teleport.player.sendActionBar(Component.text("Teleporting in ").color(GTPlugin.mainThemeColor).append(Component.text(teleport.ticksLeft / 20).color(GTPlugin.lightThemeColor), Component.text(" seconds...").color(GTPlugin.mainThemeColor)));
                teleport.player.playSound(teleport.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
        });

        for (StandStillTeleport teleport : teleportsToRemove)
            standStillTeleports.remove(teleport.player.getUniqueId());

        for (StandStillTeleport teleport : teleportsToExecute) {
            standStillTeleports.remove(teleport.player.getUniqueId());
            Location destination = teleport.getLocation();
            if (canStand(destination)) {
                if (teleport.saveBack)
                    GTPlugin.backTPManager.addBack(teleport.player, teleport.player.getLocation());
                teleport.player.sendActionBar(Component.text("Teleporting...").color(GTPlugin.mainThemeColor));
                coolDowns.put(teleport.player.getUniqueId(), new AtomicInteger(plugin.getConfig().getInt("tp-cooldown")));
                teleport.player.teleport(destination);
                teleport.player.playSound(teleport.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);

            } else {
                teleport.player.sendActionBar(Component.text("Cant teleport! Position obstructed!").color(NamedTextColor.RED));
                teleport.player.playSound(teleport.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            }
        }
    }

    public boolean isOnCoolDown(Player player) {
        return coolDowns.containsKey(player.getUniqueId());
    }

    public void addTeleport(Player player, Location to, int standStill) {
        standStillTeleports.put(player.getUniqueId(), new StandStillTeleport.ToLocation(standStill, player, to, true));
        player.teleport(player.getLocation());
    }

    public void addTeleport(Player player, Player to, int standStill) {
        standStillTeleports.put(player.getUniqueId(), new StandStillTeleport.ToPlayer(standStill, player, to, true));
        player.teleport(player.getLocation());
    }

    public void addTeleportNoBack(Player player, Location to, int standStill) {
        standStillTeleports.put(player.getUniqueId(), new StandStillTeleport.ToLocation(standStill, player, to, false));
        player.teleport(player.getLocation());
    }

    public void moved(Player player) {
        StandStillTeleport teleport = standStillTeleports.get(player.getUniqueId());
        if (teleport != null) {
            standStillTeleports.remove(player.getUniqueId());
            player.sendActionBar(Component.text("You moved! Cancelling teleport...").color(NamedTextColor.RED));
        }
    }

    public static boolean canStand(Location loc) {
        if (loc.y() % 1 > 0) {
            loc = loc.clone();
            loc.setY(Math.ceil(loc.y()));
        }

        World world = loc.getWorld();
        if (world == null) return false;

        Block feet = world.getBlockAt(loc);
        Block head = world.getBlockAt(
                loc.getBlockX(),
                loc.getBlockY() + 1,
                loc.getBlockZ()
        );

        return feet.isPassable()
                && head.isPassable();
    }

    public int getCooldown(Player player) {
        AtomicInteger v = coolDowns.get(player.getUniqueId());
        return v == null ? 0 : v.get();
    }
}
