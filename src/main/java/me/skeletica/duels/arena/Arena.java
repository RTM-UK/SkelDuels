package me.skeletica.duels.arena;

import org.bukkit.Location;

public record Arena(String name, Location pos1, Location pos2) {
    public boolean complete() { return pos1 != null && pos2 != null && pos1.getWorld() != null && pos2.getWorld() != null; }
}
