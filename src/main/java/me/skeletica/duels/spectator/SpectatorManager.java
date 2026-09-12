package me.skeletica.duels.spectator;

import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.duel.Duel;
import me.skeletica.duels.duel.PlayerSnapshot;
import me.skeletica.duels.util.Color;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpectatorManager {
    private final DuelsPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private record Session(PlayerSnapshot snapshot, UUID duelA, UUID duelB) {
    }

    public SpectatorManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSpectating(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public boolean spectate(Player viewer, Player target) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            Color.sendError(viewer, "You cannot spectate yourself.");
            return false;
        }

        Duel duel = plugin.duelManager().get(target.getUniqueId());
        if (duel == null) {
            Color.sendError(viewer, "That player is not in a duel.");
            return false;
        }
        if (plugin.editor().isEditing(viewer)) {
            Color.sendError(viewer, "Leave the kit editor before spectating.");
            return false;
        }
        if (plugin.duelManager().isInDuel(viewer.getUniqueId())) {
            Color.sendError(viewer, "You cannot spectate while you are in a duel.");
            return false;
        }

        leave(viewer, false);
        sessions.put(viewer.getUniqueId(), new Session(PlayerSnapshot.capture(viewer), duel.a(), duel.b()));
        viewer.closeInventory();
        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.setSpectatorTarget(target);

        Color.sendSuccess(viewer, "Spectating &f" + target.getName() + "&7. Use &f/spectate leave &7to exit.");
        return true;
    }

    public void leave(Player viewer) {
        leave(viewer, true);
    }

    private void leave(Player viewer, boolean message) {
        Session session = sessions.remove(viewer.getUniqueId());
        if (session == null) return;

        viewer.setSpectatorTarget(null);
        session.snapshot().restore(viewer);

        if (message) {
            Color.sendSuccess(viewer, "You left spectator mode.");
        }
    }

    public void removeForDuel(Duel duel) {
        for (Map.Entry<UUID, Session> entry : new HashSet<>(sessions.entrySet())) {
            Session session = entry.getValue();
            if (!session.duelA().equals(duel.a()) || !session.duelB().equals(duel.b())) {
                continue;
            }

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                leave(player, true);
            } else {
                sessions.remove(entry.getKey());
            }
        }
    }

    public void quit(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public void removeAll() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            leave(player, false);
        }
        sessions.clear();
    }
}
