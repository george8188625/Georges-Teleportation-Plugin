package com.george_vi.georgetp.tpa;

import com.george_vi.georgetp.GTPlugin;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class TpaManager {
    // K -> sender UUID
    Map<UUID, TpaRequest> outgoingRequests = new HashMap<>();

    final GTPlugin plugin;
    public TpaManager(GTPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        List<TpaRequest> requestsToRemove = new LinkedList<>();
        outgoingRequests.forEach((id, request) -> {
            if (request.tick() || !request.stillValid())
                requestsToRemove.add(request);
        });

        for (TpaRequest request : requestsToRemove) {
            outgoingRequests.remove(request.sender.getUniqueId());
            request.sender.sendActionBar(Component.text("Teleportation request expired!").color(NamedTextColor.RED));
            request.sender.playSound(request.sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
        }
    }


    public int runTpaCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || !player.hasPermission("georgestp.tpa"))
            return 1;
        final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        Player otherPlayer = targetResolver.resolve(ctx.getSource()).getFirst();
        if (otherPlayer == null)
            return 1;
        if (outgoingRequests.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You already have an outgoing teleportation request! ").color(NamedTextColor.RED).append(Component.text("[CANCEL]").decorate(TextDecoration.BOLD).color(NamedTextColor.RED).clickEvent(ClickEvent.suggestCommand("/tpacancel"))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return 1;
        }

        if (plugin.getConfig().getBoolean("tpa-request-ping"))
            otherPlayer.playSound(otherPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        player.sendActionBar(Component.text("Sending a teleportation request to ").color(GTPlugin.mainThemeColor).append(Component.text(otherPlayer.getName()).color(GTPlugin.lightThemeColor), Component.text(" ...").color(GTPlugin.mainThemeColor)));
        otherPlayer.sendMessage(Component.text(player.getName()).color(GTPlugin.lightThemeColor).append(
                Component.text(" wants to teleport to you! ").color(GTPlugin.mainThemeColor),
                Component.text("[ACCEPT] ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.suggestCommand("/tpaccept " + player.getName())),
                Component.text("[DENY]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.suggestCommand("/tpadeny " + player.getName()))));
        outgoingRequests.put(player.getUniqueId(), new TpaRequest(plugin.getConfig().getInt("tpa-request-expiration-time"), player, otherPlayer, false));
        return 1;
    }

    public int runTpAcceptCommand(CommandContext<CommandSourceStack> ctx, boolean specifyPlayer) throws CommandSyntaxException {
        if (!(ctx.getSource().getExecutor() instanceof Player receivingPlayer) || !receivingPlayer.hasPermission("georgestp.tpa"))
            return 1;
        final PlayerSelectorArgumentResolver targetResolver = specifyPlayer ? ctx.getArgument("player", PlayerSelectorArgumentResolver.class) : null;
        Player senderPlayer = specifyPlayer ? targetResolver.resolve(ctx.getSource()).getFirst() : null;
        if (senderPlayer == null) {
            List<TpaRequest> requests = new LinkedList<>();
            outgoingRequests.forEach((uuid, request) -> {
                if (request.receiver == receivingPlayer && request.stillValid())
                    requests.add(request);
            });
            if (requests.isEmpty()) {
                receivingPlayer.playSound(receivingPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(Component.text("You do not have any incoming teleportation requests!").color(NamedTextColor.RED));
            } else if (requests.size() == 1)
                acceptRequest(requests.get(0));
            else {
                receivingPlayer.playSound(receivingPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(Component.text("You have more than one teleportation request!").color(NamedTextColor.RED));
                for (TpaRequest request : requests) {
                    receivingPlayer.sendMessage(Component.text(request.sender.getName()).color(GTPlugin.lightThemeColor).append(
                            Component.text(request.reverse ? " wants you to teleport to them! " : " wants to teleport to you! ").color(GTPlugin.mainThemeColor),
                            Component.text("[ACCEPT] ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.suggestCommand("/tpaccept " + request.sender.getName())),
                            Component.text("[DENY]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.suggestCommand("/tpadeny " + request.sender.getName()))));
                }
            }
        } else {
            TpaRequest request = outgoingRequests.get(senderPlayer.getUniqueId());
            if (request == null || request.receiver != receivingPlayer || !request.stillValid()) {
                receivingPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(Component.text("You do not have an incoming teleportation request from " + senderPlayer.getName() + "!").color(NamedTextColor.RED));
            } else
                acceptRequest(request);

        }
        return 1;
    }

    private void acceptRequest(TpaRequest request) {
        Player receivingPlayer = request.receiver;
        Player senderPlayer = request.sender;
        if (plugin.getConfig().getBoolean("tpa-request-ping"))
            senderPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        outgoingRequests.remove(senderPlayer.getUniqueId());
        if (request.reverse) {
            receivingPlayer.sendActionBar(Component.text("Teleporting to ").color(GTPlugin.mainThemeColor).append(Component.text(senderPlayer.getName()).color(GTPlugin.lightThemeColor), Component.text("... Don't move!").color(GTPlugin.mainThemeColor)));
            senderPlayer.sendActionBar(Component.text("Teleporting ").color(GTPlugin.mainThemeColor).append(Component.text(receivingPlayer.getName()).color(GTPlugin.lightThemeColor), Component.text(" to you...").color(GTPlugin.mainThemeColor)));
            GTPlugin.teleportManager.addTeleport(receivingPlayer, senderPlayer, plugin.getConfig().getInt("tp-standstill"));
        } else {
            senderPlayer.sendActionBar(Component.text("Teleporting to ").color(GTPlugin.mainThemeColor).append(Component.text(receivingPlayer.getName()).color(GTPlugin.lightThemeColor), Component.text("... Don't move!").color(GTPlugin.mainThemeColor)));
            receivingPlayer.sendActionBar(Component.text("Teleporting ").color(GTPlugin.mainThemeColor).append(Component.text(senderPlayer.getName()).color(GTPlugin.lightThemeColor), Component.text(" to you...").color(GTPlugin.mainThemeColor)));
            GTPlugin.teleportManager.addTeleport(senderPlayer, receivingPlayer, plugin.getConfig().getInt("tp-standstill"));
        }
    }

    private void denyRequest(TpaRequest request) {
        Player receivingPlayer = request.receiver;
        Player senderPlayer = request.sender;
        if (plugin.getConfig().getBoolean("tpa-request-ping"))
            senderPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
        outgoingRequests.remove(senderPlayer.getUniqueId());
        senderPlayer.sendMessage(Component.text("Your teleportation request was denied by " + receivingPlayer.getName() + "!").color(NamedTextColor.RED));
    }

    public int runTpaDenyCommand(CommandContext<CommandSourceStack> ctx, boolean specifyPlayer) throws CommandSyntaxException {
        if (!(ctx.getSource().getExecutor() instanceof Player receivingPlayer) || !receivingPlayer.hasPermission("georgestp.tpa"))
            return 1;
        final PlayerSelectorArgumentResolver targetResolver = specifyPlayer ? ctx.getArgument("player", PlayerSelectorArgumentResolver.class) : null;
        Player senderPlayer = specifyPlayer ? targetResolver.resolve(ctx.getSource()).getFirst() : null;
        if (senderPlayer == null) {
            List<TpaRequest> requests = new LinkedList<>();
            outgoingRequests.forEach((uuid, request) -> {
                if (request.receiver == receivingPlayer && request.stillValid())
                    requests.add(request);
            });
            if (requests.isEmpty()) {
                receivingPlayer.playSound(receivingPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(Component.text("You do not have any incoming teleportation requests!").color(NamedTextColor.RED));
            } else if (requests.size() == 1)
                denyRequest(requests.get(0));
            else {
                receivingPlayer.playSound(receivingPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(Component.text("You have more than one teleportation request!").color(NamedTextColor.RED));
                for (TpaRequest request : requests) {
                    receivingPlayer.sendMessage(Component.text(request.sender.getName()).color(GTPlugin.lightThemeColor).append(
                            Component.text(request.reverse ? " wants you to teleport to them! " : " wants to teleport to you! ").color(GTPlugin.mainThemeColor),
                            Component.text("[ACCEPT] ").color(NamedTextColor.GREEN).clickEvent(ClickEvent.suggestCommand("/tpaccept " + request.sender.getName())),
                            Component.text("[DENY]").color(NamedTextColor.RED).clickEvent(ClickEvent.suggestCommand("/tpadeny " + request.sender.getName()))));
                }
            }
        } else {
            TpaRequest request = outgoingRequests.get(senderPlayer.getUniqueId());
            if (request == null || request.receiver != receivingPlayer || !request.stillValid()) {
                receivingPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(Component.text("You do not have an incoming teleportation request from " + senderPlayer.getName() + "!").color(NamedTextColor.RED));
            } else
                denyRequest(request);
        }
        return 1;
    }

    public int runTpaCancelCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getExecutor() instanceof Player senderPlayer) || !senderPlayer.hasPermission("georgestp.tpa"))
            return 1;

        TpaRequest request = outgoingRequests.get(senderPlayer.getUniqueId());
        if (request == null || !request.stillValid()) {
            senderPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            senderPlayer.sendMessage(Component.text("You do not have an outgoing teleportation requests!").color(NamedTextColor.RED));
        } else {
            outgoingRequests.remove(senderPlayer.getUniqueId());
            request.receiver.sendMessage(Component.text(senderPlayer.getName()).color(GTPlugin.lightThemeColor).append(Component.text(" has cancelled his teleportation request.").color(GTPlugin.mainThemeColor)));
            senderPlayer.sendMessage(Component.text("Cancelled all outgoing teleportation requests!").color(NamedTextColor.RED));
        }
        return 1;
    }

    public int runTpaHereCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getExecutor() instanceof Player player) || !player.hasPermission("georgestp.tpa"))
            return 1;
        final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        Player otherPlayer = targetResolver.resolve(ctx.getSource()).getFirst();
        if (otherPlayer == null)
            return 1;
        if (outgoingRequests.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You already have an outgoing teleportation request! ").color(NamedTextColor.RED).append(Component.text("[CANCEL]").decorate(TextDecoration.BOLD).color(NamedTextColor.RED).clickEvent(ClickEvent.suggestCommand("/tpacancel"))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return 1;
        }

        if (plugin.getConfig().getBoolean("tpa-request-ping"))
            otherPlayer.playSound(otherPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        player.sendActionBar(Component.text("Sending a teleportation request to ").color(GTPlugin.mainThemeColor).append(Component.text(otherPlayer.getName()).color(GTPlugin.lightThemeColor), Component.text(" ...").color(GTPlugin.mainThemeColor)));
        otherPlayer.sendMessage(Component.text(player.getName()).color(GTPlugin.lightThemeColor).append(
                Component.text(" wants you to teleport to them! ").color(GTPlugin.mainThemeColor),
                Component.text("[ACCEPT] ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.suggestCommand("/tpaccept " + player.getName())),
                Component.text("[DENY]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.suggestCommand("/tpadeny " + player.getName()))));
        outgoingRequests.put(player.getUniqueId(), new TpaRequest(plugin.getConfig().getInt("tpa-request-expiration-time"), player, otherPlayer, true));
        return 1;
    }
}
