package com.george_vi.georgetp.tpa;

import org.bukkit.entity.Player;

public class TpaRequest {
    public int ticksLeft;
    public Player sender;
    public Player receiver;
    public boolean reverse;

    // When reverse = false, sender teleports to receiver, otherwise receiver teleports to sender.
    public TpaRequest(int ticksLeft, Player sender, Player receiver, boolean reverse) {
        this.ticksLeft = ticksLeft;
        this.sender = sender;
        this.receiver = receiver;
        this.reverse = reverse;
    }

    public boolean tick() {
        ticksLeft--;
        return ticksLeft <= 0;
    }

    public boolean stillValid() {
        return sender.isConnected() && receiver.isConnected();
    }
}
