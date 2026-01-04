package com.george_vi.georgetp.tp;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public abstract class StandStillTeleport {
    public int ticksLeft;
    public Player player;
    public final boolean saveBack;

    public StandStillTeleport(int ticksLeft, Player player, boolean saveBack) {
        if (player == null)
            throw new IllegalArgumentException("player can not be null!");
        this.ticksLeft = ticksLeft;
        this.player = player;
        this.saveBack = saveBack;
    }

    public boolean tick() {
        ticksLeft--;
        return ticksLeft <= 0;
    }

    public boolean stillValid() {
        return player.isConnected();
    }

    public abstract Location getLocation();

    public static class ToLocation extends StandStillTeleport {
        public final Location location;

        public ToLocation(int ticksLeft, Player player, Location location, boolean saveBack) {
            super(ticksLeft, player, saveBack);
            this.location = location;
        }

        @Override
        public Location getLocation() {
            return location;
        }
    }

    public static class ToPlayer extends StandStillTeleport {
        public final Player destinationPlayer;

        public ToPlayer(int ticksLeft, Player player, Player destinationPlayer, boolean saveBack) {
            super(ticksLeft, player, saveBack);
            this.destinationPlayer = destinationPlayer;
        }

        @Override
        public Location getLocation() {
            return destinationPlayer.getLocation();
        }

        @Override
        public boolean stillValid() {
            return super.stillValid() && destinationPlayer.isConnected();
        }
    }
}
