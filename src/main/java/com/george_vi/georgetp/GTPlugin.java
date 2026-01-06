package com.george_vi.georgetp;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.george_vi.georgetp.back.BackTeleportManager;
import com.george_vi.georgetp.tp.StandStillTeleportManager;
import com.george_vi.georgetp.tpa.TpaManager;
import com.george_vi.georgetp.util.LangUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class GTPlugin extends JavaPlugin implements Listener {
    public static StandStillTeleportManager teleportManager;
    public static TpaManager tpaManager;
    public static HomeManager homeManager;
    public static WarpManager warpManager;
    public static BackTeleportManager backTPManager;
    public static LangUtil langUtil;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        teleportManager = new StandStillTeleportManager(this);
        tpaManager = new TpaManager(this);
        homeManager = new HomeManager(this);
        warpManager = new WarpManager(this);
        backTPManager = new BackTeleportManager(this);
        langUtil = new LangUtil(this);
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
                LiteralCommandNode<CommandSourceStack> setCommandNode = Commands.literal("sethome").executes(ctx -> homeManager.runSetHomeCommand(ctx, false))
                        .then(Commands.argument("home", StringArgumentType.word()).suggests(homeManager::suggestHome).executes(ctx -> homeManager.runSetHomeCommand(ctx, true))).build();
                LiteralCommandNode<CommandSourceStack> delCommandNode = Commands.literal("delhome").executes(ctx -> homeManager.runDelHomeCommand(ctx, false))
                        .then(Commands.argument("home", StringArgumentType.word()).suggests(homeManager::suggestHome).executes(ctx -> homeManager.runDelHomeCommand(ctx, true))).build();
                LiteralCommandNode<CommandSourceStack> commandNode = Commands.literal("home").executes(ctx -> homeManager.runHomeCommand(ctx, false))
                        .then(Commands.argument("home", StringArgumentType.word()).suggests(homeManager::suggestHome).executes(ctx -> homeManager.runHomeCommand(ctx, true))).build();
                commands.registrar().register(setCommandNode);
                commands.registrar().register(delCommandNode);
                commands.registrar().register(commandNode);
            }
            if (getConfig().getBoolean("warps-enabled")) {
                LiteralCommandNode<CommandSourceStack> delCommandNode = Commands.literal("delwarp").then(Commands.argument("warp", StringArgumentType.word()).suggests(warpManager::suggestWarp).executes(warpManager::runDelWarpCommand)).build();
                LiteralCommandNode<CommandSourceStack> setCommandNode = Commands.literal("setwarp").then(Commands.argument("warp", StringArgumentType.word()).suggests(warpManager::suggestWarp).executes(warpManager::runSetWarpCommand)
                        .then(Commands.literal("align").executes(warpManager::runSetAlignedWarpCommand))).build();
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

        Location loc = warpManager.getWarp(player, "spawn");
        if (loc == null) {
            player.sendMessage(langUtil.getMessage("spawn-missing"));
            return 1;
        }

        player.sendActionBar(langUtil.getMessage("spawn-teleporting"));
        teleportManager.addTeleport(player, loc, getConfig().getInt("tp-standstill"));
        return 1;
    }

    public static boolean coolDownCheck(Player player) {
        if (GTPlugin.teleportManager.isOnCoolDown(player)) {
            player.sendActionBar(langUtil.getMessage("teleporting-cooldown", Map.of("time", String.valueOf(GTPlugin.teleportManager.getCooldown(player) / 20 + 1))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return true;
        }
        return false;
    }
}
