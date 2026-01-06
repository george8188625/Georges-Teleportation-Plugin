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
            request.sender.sendActionBar(GTPlugin.langUtil.getMessage("tpa-expired"));
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
            player.sendMessage(GTPlugin.langUtil.getMessage("tpa-already-outgoing", Map.of("target", outgoingRequests.get(player.getUniqueId()).receiver.getName())).append(GTPlugin.langUtil.getMessage("button-cancel").clickEvent(ClickEvent.suggestCommand("/tpacancel"))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return 1;
        }

        if (plugin.getConfig().getBoolean("tpa-request-ping"))
            otherPlayer.playSound(otherPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        player.sendActionBar(GTPlugin.langUtil.getMessage("tpa-sending", Map.of("target", otherPlayer.getName())));
        otherPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-receive-forward", Map.of("sender", player.getName())).append(
                GTPlugin.langUtil.getMessage("button-accept").clickEvent(ClickEvent.suggestCommand("/tpaccept " + player.getName())),
                GTPlugin.langUtil.getMessage("button-deny").clickEvent(ClickEvent.suggestCommand("/tpadeny " + player.getName()))));
        outgoingRequests.put(player.getUniqueId(), new TpaRequest(plugin.getConfig().getInt("tpa-request-expiration-time"), player, otherPlayer, false));
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
            player.sendMessage(GTPlugin.langUtil.getMessage("tpa-already-outgoing", Map.of("target", outgoingRequests.get(player.getUniqueId()).receiver.getName())).append(GTPlugin.langUtil.getMessage("button-cancel").clickEvent(ClickEvent.suggestCommand("/tpacancel"))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return 1;
        }

        if (plugin.getConfig().getBoolean("tpa-request-ping"))
            otherPlayer.playSound(otherPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        player.sendActionBar(GTPlugin.langUtil.getMessage("tpa-sending", Map.of("target", otherPlayer.getName())));
        otherPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-receive-backward", Map.of("sender", player.getName())).append(
                GTPlugin.langUtil.getMessage("button-accept").clickEvent(ClickEvent.suggestCommand("/tpaccept " + player.getName())),
                GTPlugin.langUtil.getMessage("button-deny").clickEvent(ClickEvent.suggestCommand("/tpadeny " + player.getName()))));

        outgoingRequests.put(player.getUniqueId(), new TpaRequest(plugin.getConfig().getInt("tpa-request-expiration-time"), player, otherPlayer, true));
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
                receivingPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-no-incoming"));
            } else if (requests.size() == 1)
                acceptRequest(requests.get(0));
            else {
                receivingPlayer.playSound(receivingPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-more-than-one"));
                for (TpaRequest request : requests) {
                    receivingPlayer.sendMessage(GTPlugin.langUtil.getMessage(request.reverse ? "tpa-receive-backward" : "tpa-receive-forward", Map.of("sender", request.sender.getName())).append(
                            GTPlugin.langUtil.getMessage("button-accept").clickEvent(ClickEvent.suggestCommand("/tpaccept " + request.sender.getName())),
                            GTPlugin.langUtil.getMessage("button-deny").clickEvent(ClickEvent.suggestCommand("/tpadeny " + request.sender.getName()))));
                }
            }
        } else {
            TpaRequest request = outgoingRequests.get(senderPlayer.getUniqueId());
            if (request == null || request.receiver != receivingPlayer || !request.stillValid()) {
                receivingPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-no-incoming-targeted", Map.of("sender", senderPlayer.getName())));
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
            receivingPlayer.sendActionBar(GTPlugin.langUtil.getMessage("tpa-teleporting-to", Map.of("player", senderPlayer.getName())));
            senderPlayer.sendActionBar(GTPlugin.langUtil.getMessage("tpa-teleporting-them", Map.of("player", receivingPlayer.getName())));
            GTPlugin.teleportManager.addTeleport(receivingPlayer, senderPlayer, plugin.getConfig().getInt("tp-standstill"));
        } else {
            receivingPlayer.sendActionBar(GTPlugin.langUtil.getMessage("tpa-teleporting-them", Map.of("player", senderPlayer.getName())));
            senderPlayer.sendActionBar(GTPlugin.langUtil.getMessage("tpa-teleporting-to", Map.of("player", receivingPlayer.getName())));
            GTPlugin.teleportManager.addTeleport(senderPlayer, receivingPlayer, plugin.getConfig().getInt("tp-standstill"));
        }
    }

    private void denyRequest(TpaRequest request) {
        Player receivingPlayer = request.receiver;
        Player senderPlayer = request.sender;
        if (plugin.getConfig().getBoolean("tpa-request-ping"))
            senderPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
        outgoingRequests.remove(senderPlayer.getUniqueId());
        senderPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-denied", Map.of("denier", receivingPlayer.getName())));
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
                receivingPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-no-incoming"));
            } else if (requests.size() == 1)
                denyRequest(requests.get(0));
            else {
                receivingPlayer.playSound(receivingPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-more-than-one"));
                for (TpaRequest request : requests) {
                    receivingPlayer.sendMessage(GTPlugin.langUtil.getMessage(request.reverse ? "tpa-receive-backward" : "tpa-receive-forward", Map.of("sender", request.sender.getName())).append(
                            GTPlugin.langUtil.getMessage("button-accept").clickEvent(ClickEvent.suggestCommand("/tpaccept " + request.sender.getName())),
                            GTPlugin.langUtil.getMessage("button-deny").clickEvent(ClickEvent.suggestCommand("/tpadeny " + request.sender.getName()))));
                }
            }
        } else {
            TpaRequest request = outgoingRequests.get(senderPlayer.getUniqueId());
            if (request == null || request.receiver != receivingPlayer || !request.stillValid()) {
                receivingPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
                receivingPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-no-incoming-targeted", Map.of("sender", senderPlayer.getName())));
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
            senderPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-no-outgoing"));
        } else {
            outgoingRequests.remove(senderPlayer.getUniqueId());
            request.receiver.sendMessage(GTPlugin.langUtil.getMessage("tpa-cancelled-receiver", Map.of("sender", request.sender.getName())));
            senderPlayer.sendMessage(GTPlugin.langUtil.getMessage("tpa-cancelled"));
        }
        return 1;
    }
}
