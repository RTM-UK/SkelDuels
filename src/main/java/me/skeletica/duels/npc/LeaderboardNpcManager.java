package me.skeletica.duels.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.stats.StatsManager.LeaderboardEntry;
import me.skeletica.duels.util.Color;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class LeaderboardNpcManager {
    private static final long DISPLAY_TICKS = 60L * 20L;

    private final DuelsPlugin plugin;
    private final Map<UUID, List<UUID>> spawned = new HashMap<>();
    private final Map<UUID, Long> tokens = new HashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public LeaderboardNpcManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawn(Player viewer) {
        remove(viewer);

        Location base = viewer.getLocation().clone();
        Vector flat = viewer.getLocation().getDirection().setY(0).normalize();
        if (flat.lengthSquared() < 0.001) flat = new Vector(0, 0, 1);
        base.add(flat.multiply(4.0));

        Vector toViewer = viewer.getLocation().toVector().subtract(base.toVector()).normalize();
        base.setDirection(toViewer);

        List<UUID> ids = new ArrayList<>();
        List<LeaderboardEntry> entries = plugin.stats().leaderboard(10);
        OfflinePlayer skin = entries.isEmpty() ? viewer : Bukkit.getOfflinePlayer(entries.get(0).uuid());

        Mannequin npc = (Mannequin) base.getWorld().spawnEntity(base, EntityType.MANNEQUIN);
        npc.setImmovable(true);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setProfile(ResolvableProfile.resolvableProfile(skin.getPlayerProfile()));
        ids.add(npc.getUniqueId());

        TextDisplay title = text(base.clone().add(0, 5.25, 0), "&f&l✦  &bSKELDUELS LEADERBOARD  &f✦");
        style(title, 255, 260, 35, 65, 95, 105);
        ids.add(title.getUniqueId());

        TextDisplay sub = text(base.clone().add(0, 4.78, 0), "&7Top players ranked by duel wins");
        style(sub, 210, 180, 35, 55, 85, 95);
        ids.add(sub.getUniqueId());

        if (entries.isEmpty()) {
            TextDisplay none = text(base.clone().add(0, 4.15, 0), "&7No completed duels yet.");
            style(none, 210, 220, 35, 55, 85, 105);
            ids.add(none.getUniqueId());
        } else {
            for (int i = 0; i < entries.size(); i++) {
                LeaderboardEntry entry = entries.get(i);
                String rank;
                String nameColor;
                if (i == 0) {
                    rank = "&6&l#1";
                    nameColor = "&e";
                } else if (i == 1) {
                    rank = "&f&l#2";
                    nameColor = "&f";
                } else if (i == 2) {
                    rank = "&c&l#3";
                    nameColor = "&c";
                } else {
                    rank = "&8#" + (i + 1);
                    nameColor = "&b";
                }

                String row = rank + " &8• " + nameColor + entry.name()
                        + " &8• &a" + entry.stats().wins() + " wins"
                        + " &8• &7" + String.format(Locale.US, "%.1f%%", entry.stats().winRate());

                TextDisplay display = text(base.clone().add(0, 4.18 - i * 0.40, 0), row);
                style(display, 230, 310, 35, 55, 85, 105);
                ids.add(display.getUniqueId());
            }
        }

        spawned.put(viewer.getUniqueId(), ids);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(viewer.getUniqueId())) show(player, ids);
            else hide(player, ids);
        }

        long token = System.nanoTime();
        tokens.put(viewer.getUniqueId(), token);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (token == tokens.getOrDefault(viewer.getUniqueId(), 0L)) remove(viewer);
        }, DISPLAY_TICKS);
    }

    private TextDisplay text(Location location, String value) {
        TextDisplay display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.text(legacy.deserialize(Color.c(value)));
        display.setBillboard(Display.Billboard.FIXED);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setViewRange(32);
        display.setGravity(false);
        display.setPersistent(false);
        return display;
    }

    private void style(TextDisplay display, int opacity, int width, int r, int g, int b, int alpha) {
        display.setTextOpacity((byte) opacity);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(alpha, r, g, b));
        display.setDefaultBackground(false);
        display.setLineWidth(width);
    }

    private void show(Player player, List<UUID> ids) {
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) player.showEntity(plugin, entity);
        }
    }

    private void hide(Player player, List<UUID> ids) {
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) player.hideEntity(plugin, entity);
        }
    }

    public void hideFrom(Player player) {
        for (List<UUID> ids : spawned.values()) hide(player, ids);
    }

    public boolean isLeaderboardNpc(Entity entity) {
        for (List<UUID> ids : spawned.values()) {
            if (ids.contains(entity.getUniqueId())) return true;
        }
        return false;
    }

    public void remove(Player player) {
        List<UUID> ids = spawned.remove(player.getUniqueId());
        if (ids != null) {
            for (UUID id : ids) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
        }
        tokens.remove(player.getUniqueId());
    }

    public void removeAll() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) remove(player);
    }
}
