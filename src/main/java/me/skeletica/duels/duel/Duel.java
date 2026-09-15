package me.skeletica.duels.duel;

import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.arena.Arena;
import me.skeletica.duels.kit.Kit;
import me.skeletica.duels.util.Color;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.UUID;

public class Duel {
    private final DuelsPlugin plugin;
    private final UUID a;
    private final UUID b;
    private final Kit kit;
    private final int rounds;
    private final Arena arena;
    private final PlayerSnapshot snapA;
    private final PlayerSnapshot snapB;

    private int scoreA;
    private int scoreB;
    private DuelState state = DuelState.COUNTDOWN;
    private BukkitTask task;

    public Duel(DuelsPlugin plugin, Player a, Player b, Kit kit, int rounds, Arena arena) {
        this.plugin = plugin;
        this.a = a.getUniqueId();
        this.b = b.getUniqueId();
        this.kit = kit;
        this.rounds = rounds;
        this.arena = arena;
        this.snapA = PlayerSnapshot.capture(a);
        this.snapB = PlayerSnapshot.capture(b);
    }

    public UUID a() { return a; }
    public UUID b() { return b; }
    public Kit kit() { return kit; }
    public int rounds() { return rounds; }
    public DuelState state() { return state; }
    public Arena arena() { return arena; }

    public boolean contains(UUID uuid) {
        return a.equals(uuid) || b.equals(uuid);
    }

    public Player other(Player player) {
        return player.getUniqueId().equals(a) ? Bukkit.getPlayer(b) : Bukkit.getPlayer(a);
    }

    public int score(Player player) {
        return player.getUniqueId().equals(a) ? scoreA : scoreB;
    }

    public void start() {
        Player p1 = Bukkit.getPlayer(a);
        Player p2 = Bukkit.getPlayer(b);
        if (p1 == null || p2 == null) {
            finish(null);
            return;
        }

        p1.getInventory().clear();
        p2.getInventory().clear();
        p1.teleport(arena.pos1());
        p2.teleport(arena.pos2());
        plugin.scoreboard().show(this);
        countdown();
    }

    private void countdown() {
        int seconds = plugin.getConfig().getInt("settings.countdown-seconds", 3);
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                Player p1 = Bukkit.getPlayer(a);
                Player p2 = Bukkit.getPlayer(b);
                if (p1 == null || p2 == null) {
                    if (task != null) task.cancel();
                    finish(null);
                    return;
                }

                if (remaining <= 0) {
                    if (task != null) task.cancel();
                    state = DuelState.IN_PROGRESS;
                    beginRound();
                    return;
                }

                sendCountdown(remaining);
                remaining--;
            }
        }, 0L, 20L);
    }

    private void sendCountdown(int number) {
        String subtitle = "&7" + kit.name() + " &8• &fFirst to " + ((rounds + 1) / 2);
        String title = "&b&l" + number;

        Player p1 = Bukkit.getPlayer(a);
        Player p2 = Bukkit.getPlayer(b);
        if (p1 != null) p1.sendTitle(Color.c(title), Color.c(subtitle), 0, 20, 0);
        if (p2 != null) p2.sendTitle(Color.c(title), Color.c(subtitle), 0, 20, 0);
    }

    private void beginRound() {
        Player p1 = Bukkit.getPlayer(a);
        Player p2 = Bukkit.getPlayer(b);
        if (p1 == null || p2 == null) {
            finish(null);
            return;
        }

        resetPlayer(p1);
        resetPlayer(p2);
        p1.teleport(arena.pos1());
        p2.teleport(arena.pos2());
        sendFight();
    }

    private void sendFight() {
        String score = "&b" + scoreA + " &8- &c" + scoreB + " &8• &7First to &f" + ((rounds + 1) / 2);

        Player p1 = Bukkit.getPlayer(a);
        Player p2 = Bukkit.getPlayer(b);
        if (p1 != null) p1.sendTitle(Color.c("&a&lFIGHT!"), Color.c(score), 0, 25, 8);
        if (p2 != null) p2.sendTitle(Color.c("&a&lFIGHT!"), Color.c(score), 0, 25, 8);
    }

    private void resetPlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setHealth(player.getMaxHealth());
        apply(player);
    }

    private void apply(Player player) {
        player.getInventory().setStorageContents(kit.contents());
        player.getInventory().setArmorContents(kit.armor());
        if (kit.offhand() != null) {
            player.getInventory().setItemInOffHand(kit.offhand());
        }
    }

    public void roundWin(Player winner, Player loser) {
        if (state != DuelState.IN_PROGRESS) return;

        if (winner.getUniqueId().equals(a)) {
            scoreA++;
        } else {
            scoreB++;
        }

        plugin.stats().recordRound(winner.getUniqueId(), loser.getUniqueId());
        plugin.scoreboard().update(this);

        int needed = (rounds + 1) / 2;
        if (scoreA >= needed || scoreB >= needed) {
            finish(winner);
            return;
        }

        String message = "&a&lROUND WON &8• &7Score &f" + scoreA + " &8- &f" + scoreB;
        Player p1 = Bukkit.getPlayer(a);
        Player p2 = Bukkit.getPlayer(b);
        if (p1 != null) Color.send(p1, message);
        if (p2 != null) Color.send(p2, message);

        Bukkit.getScheduler().runTaskLater(plugin, this::beginRound, 40L);
    }

    public void finish(Player winner) {
        if (state == DuelState.FINISHED) return;

        state = DuelState.FINISHED;
        if (task != null) task.cancel();

        Player p1 = Bukkit.getPlayer(a);
        Player p2 = Bukkit.getPlayer(b);

        if (winner != null) {
            Player loser = winner.getUniqueId().equals(a) ? p2 : p1;
            if (loser != null) {
                int winnerScore = winner.getUniqueId().equals(a) ? scoreA : scoreB;
                int loserScore = winner.getUniqueId().equals(a) ? scoreB : scoreA;
                plugin.stats().recordMatch(
                        winner.getUniqueId(),
                        loser.getUniqueId(),
                        kit.name(),
                        winnerScore,
                        loserScore
                );
            }

            String message = "&a&lDUEL COMPLETE &8• &7Winner &f" + winner.getName()
                    + " &8• &7Final &b" + scoreA + " &8- &c" + scoreB;
            if (p1 != null) Color.send(p1, message);
            if (p2 != null) Color.send(p2, message);

            int firstTo = (rounds + 1) / 2;
            if (p1 != null) {
                Color.sendRematchPrompt(p1, p2 == null ? "" : p2.getName(), kit.name(), firstTo);
            }
            if (p2 != null) {
                Color.sendRematchPrompt(p2, p1 == null ? "" : p1.getName(), kit.name(), firstTo);
            }

            plugin.duelManager().rememberFinished(this);
        }

        plugin.scoreboard().hide(this);
        plugin.spectators().removeForDuel(this);

        if (p1 != null) snapA.restore(p1);
        if (p2 != null) snapB.restore(p2);

        plugin.arenaManager().release(arena);
        plugin.duelManager().remove(this);
    }
}
