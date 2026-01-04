package com.george_vi.georgetp.back;

import org.bukkit.Location;

public class BackTeleportEntry {
    public Location location;
    public int expiration;

    public BackTeleportEntry(Location location, int expiration) {
        this.location = location;
        this.expiration = expiration;
    }

    public boolean tick() {
        expiration--;
        return expiration <= 0;
    }
}
