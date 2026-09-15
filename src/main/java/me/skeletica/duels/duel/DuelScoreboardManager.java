package me.skeletica.duels.duel;

import me.skeletica.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DuelScoreboardManager {
    private final DuelsPlugin plugin;
    private final Map<UUID, Scoreboard> oldBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> duelBoards = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public DuelScoreboardManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Duel duel) {
        showPlayer(duel, Bukkit.getPlayer(duel.a()));
        showPlayer(duel, Bukkit.getPlayer(duel.b()));

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (duel.state() == DuelState.FINISHED) {
                hide(duel);
                return;
            }

            update(duel);
        }, 0L, 10L);

        tasks.put(duel.a(), task);
        tasks.put(duel.b(), task);
    }

    private void showPlayer(Duel duel, Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        oldBoards.put(uuid, player.getScoreboard());

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        duelBoards.put(uuid, board);
        player.setScoreboard(board);
    }

    public void update(Duel duel) {
        updatePlayer(duel, Bukkit.getPlayer(duel.a()));
        updatePlayer(duel, Bukkit.getPlayer(duel.b()));
    }

    private void updatePlayer(Duel duel, Player player) {
        if (player == null) return;

        Scoreboard board = duelBoards.get(player.getUniqueId());
        if (board == null) return;

        Objective old = board.getObjective("duel");
        if (old != null) old.unregister();

        Objective objective = board.registerNewObjective(
                "duel",
                "dummy",
                ChatColor.AQUA + "§lDUELS"
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Player opponent = duel.other(player);
        String name = opponent == null ? "Offline" : opponent.getName();
        String ping = opponent == null || opponent.getPing() < 0 ? "?" : opponent.getPing() + "ms";

        int ownScore = duel.score(player);
        int otherScore = opponent == null ? 0 : duel.score(opponent);

        objective.getScore(ChatColor.DARK_GRAY + " ").setScore(7);
        objective.getScore(ChatColor.GRAY + "Opponent").setScore(6);
        objective.getScore(ChatColor.WHITE + name).setScore(5);
        objective.getScore(ChatColor.GRAY + "Ping: " + ChatColor.GREEN + ping).setScore(4);
        objective.getScore(ChatColor.GRAY + "Score").setScore(3);
        objective.getScore(ChatColor.AQUA + String.valueOf(ownScore)
                + ChatColor.DARK_GRAY + " - "
                + ChatColor.RED + otherScore).setScore(2);
        objective.getScore(ChatColor.DARK_GRAY + "§r").setScore(1);
    }

    public void hide(Duel duel) {
        cancel(duel.a());
        cancel(duel.b());
        restore(duel.a());
        restore(duel.b());
    }

    private void cancel(UUID uuid) {
        BukkitTask task = tasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void restore(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        Scoreboard old = oldBoards.remove(uuid);
        duelBoards.remove(uuid);

        if (player != null && old != null) {
            player.setScoreboard(old);
        }
    }

    public void removeAll() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();

        for (Map.Entry<UUID, Scoreboard> entry : oldBoards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.setScoreboard(entry.getValue());
            }
        }

        oldBoards.clear();
        duelBoards.clear();
    }
}
