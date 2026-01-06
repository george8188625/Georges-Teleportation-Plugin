George's Teleportation Plugin
A simple paper plugin for teleportation-related features.

Features:
- /spawn
- tpa
- homes
- /back
- warps

Each feature is configurable

Config:

```yaml
# SPAWN (/spawn)
# /spawn uses the warp system. Make sure warp has a 'spawn' warp with the correct position
# to set spawn, enable warps and type '/setwarp spawn align' when standing on a block
# you can disable warps afterward
spawn-tp-enabled: true

# WARPS
# There really is no reason to disable it because there are no warps by default.
# Warps are server-wide and defined by admins
warps-enabled: true

# TPA
tpa-enabled: true
# TPA request expiration time [in ticks]
tpa-request-expiration-time: 600
# Ping the player when receiving a TPA request
tpa-request-ping: true

# HOMES
homes-enabled: true
home-limit: 3

# BACK (/back)
back-enabled: true
# /back works for death locations
back-save-deaths: true
# Previous locations expiration time [in ticks]
back-expiration: 6000

# Teleport stand-still [in ticks]
tp-standstill: 60
# Teleport cooldown [in ticks]
tp-cooldown: 120
```
