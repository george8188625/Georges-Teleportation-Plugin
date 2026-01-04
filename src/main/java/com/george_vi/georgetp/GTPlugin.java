package com.george_vi.georgetp;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.george_vi.georgetp.back.BackTeleportManager;
import com.george_vi.georgetp.tp.StandStillTeleportManager;
import com.george_vi.georgetp.tpa.TpaManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GTPlugin extends JavaPlugin implements Listener {
    public static StandStillTeleportManager teleportManager;
    public static TpaManager tpaManager;
    public static HomeManager homeManager;
    public static WarpManager warpManager;
    public static BackTeleportManager backTPManager;
    public static TextColor mainThemeColor = TextColor.color(0x58F5EB);
    public static TextColor lightThemeColor = TextColor.color(0xD1FFFB);

    @Override
    public void onEnable() {
        saveDefaultConfig();

        teleportManager = new StandStillTeleportManager(this);
        tpaManager = new TpaManager(this);
        homeManager = new HomeManager(this);
        warpManager = new WarpManager(this);
        backTPManager = new BackTeleportManager(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            if (getConfig().getBoolean("spawn-tp-enabled")) {
                LiteralCommandNode<CommandSourceStack> commandNode = Commands.literal("spawn").executes(this::runSpawnCommand).build();
                commands.registrar().register(commandNode);
            }
            if (getConfig().getBoolean("tpa-enabled")) {
                LiteralCommandNode<CommandSourceStack> cancelCommandNode = Commands.literal("tpacancel")
                        .executes(tpaManager::runTpaCancelCommand).build();
                LiteralCommandNode<CommandSourceStack> tpaCommandNode = Commands.literal("tpa")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(tpaManager::runTpaCommand)).build();
                LiteralCommandNode<CommandSourceStack> tpaHereCommandNode = Commands.literal("tpahere")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(tpaManager::runTpaHereCommand)).build();
                LiteralCommandNode<CommandSourceStack> acceptCommandNode = Commands.literal("tpaccept")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> tpaManager.runTpAcceptCommand(ctx, true)))
                        .executes(ctx -> tpaManager.runTpAcceptCommand(ctx, false)).build();
                LiteralCommandNode<CommandSourceStack> denyCommandNode = Commands.literal("tpadeny")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> tpaManager.runTpaDenyCommand(ctx, true)))
                        .executes(ctx -> tpaManager.runTpaDenyCommand(ctx, false)).build();
                commands.registrar().register(cancelCommandNode);
                commands.registrar().register(acceptCommandNode);
                commands.registrar().register(denyCommandNode);
                commands.registrar().register(tpaCommandNode);
                commands.registrar().register(tpaHereCommandNode);
            }
            if (getConfig().getBoolean("homes-enabled")) {
                LiteralCommandNode<CommandSourceStack> setCommandNode = Commands.literal("sethome").executes(homeManager::runSetHomeCommand).build();
                LiteralCommandNode<CommandSourceStack> commandNode = Commands.literal("home").executes(homeManager::runHomeCommand).build();
                commands.registrar().register(setCommandNode);
                commands.registrar().register(commandNode);
            }
            if (getConfig().getBoolean("warps-enabled")) {
                LiteralCommandNode<CommandSourceStack> delCommandNode = Commands.literal("delwarp").then(Commands.argument("warp", StringArgumentType.word()).suggests(warpManager::suggestWarp).executes(warpManager::runDelWarpCommand)).build();
                LiteralCommandNode<CommandSourceStack> setCommandNode = Commands.literal("setwarp").then(Commands.argument("warp", StringArgumentType.word()).suggests(warpManager::suggestWarp).executes(warpManager::runSetWarpCommand)).build();
                LiteralCommandNode<CommandSourceStack> commandNode = Commands.literal("warp").then(Commands.argument("warp", StringArgumentType.word()).suggests(warpManager::suggestWarp).executes(warpManager::runWarpCommand)).build();
                commands.registrar().register(delCommandNode);
                commands.registrar().register(setCommandNode);
                commands.registrar().register(commandNode);
            }
            if (getConfig().getBoolean("back-enabled")) {
                LiteralCommandNode<CommandSourceStack> commandNode = Commands.literal("back").executes(backTPManager::runBackCommand).build();
                commands.registrar().register(commandNode);
            }
        });
    }

    @Override
    public void onDisable() {
        homeManager.saveAllHomes();
        backTPManager.saveAllBacks();
        if (warpManager.dirty)
            warpManager.saveAllWarps();
    }

    @EventHandler
    public void onTickPre(ServerTickStartEvent event) {
        teleportManager.tick();
        tpaManager.tick();
        homeManager.tick();
        warpManager.tick();
        backTPManager.tick();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().distance(event.getTo()) > 0.02125d)
            teleportManager.moved(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (getConfig().getBoolean("back-save-deaths"))
            backTPManager.addBack(event.getPlayer(), event.getPlayer().getLocation());
    }

    private int runSpawnCommand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || GTPlugin.coolDownCheck(player) || !player.hasPermission("georgestp.spawn"))
            return 1;

        String worldId = getConfig().getString("spawn-location.world");
        World world = Bukkit.getWorld(worldId);

        double x = getConfig().getDouble("spawn-location.x");
        double y = getConfig().getDouble("spawn-location.y");
        double z = getConfig().getDouble("spawn-location.z");
        float pitch = (float) getConfig().getDouble("spawn-location.pitch");
        float yaw = (float) getConfig().getDouble("spawn-location.yaw");

        Location spawn = new Location(world, x, y, z, yaw, pitch);
        player.sendActionBar(Component.text("Teleporting to spawn... Don't move!").color(mainThemeColor));
        teleportManager.addTeleport(player, spawn, getConfig().getInt("tp-standstill"));
        return 1;
    }

    public static boolean coolDownCheck(Player player) {
        if (GTPlugin.teleportManager.isOnCoolDown(player)) {
            player.sendActionBar(Component.text("You're still on cooldown for " + (GTPlugin.teleportManager.getCooldown(player) / 20 + 1) + " seconds!").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return true;
        }
        return false;
    }
}
