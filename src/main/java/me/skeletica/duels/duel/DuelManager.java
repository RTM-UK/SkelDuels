package me.skeletica.duels.duel;

import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.arena.Arena;
import me.skeletica.duels.kit.Kit;
import me.skeletica.duels.util.Color;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DuelManager {
    private final DuelsPlugin plugin;
    private final Map<UUID, DuelRequest> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, Duel> active = new ConcurrentHashMap<>();
    private final Map<UUID, LastDuel> lastDuels = new ConcurrentHashMap<>();
    private final Map<UUID, Challenge> challenges = new ConcurrentHashMap<>();

    public record LastDuel(UUID opponent, Kit kit, int rounds) {
    }

    public record Challenge(UUID sender, Kit kit, int rounds, long expiresAt) {
    }

    public DuelManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInDuel(UUID uuid) {
        return active.containsKey(uuid);
    }

    public Duel get(UUID uuid) {
        return active.get(uuid);
    }

    public void challenge(Player player, Kit kit) {
        if (isInDuel(player.getUniqueId())) {
            Color.sendError(player, "You are already in a duel.");
            return;
        }

        int rounds = plugin.getConfig().getInt("settings.default-rounds", 1);
        long timeout = plugin.getConfig().getLong("settings.request-timeout-seconds", 30);
        Challenge challenge = new Challenge(
                player.getUniqueId(),
                kit,
                rounds,
                System.currentTimeMillis() + timeout * 1000L
        );

        challenges.put(player.getUniqueId(), challenge);
        Color.sendChallenge(challenge, player.getName());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (challenges.get(player.getUniqueId()) != challenge) return;

            challenges.remove(player.getUniqueId());
            Player current = Bukkit.getPlayer(player.getUniqueId());
            if (current != null) {
                Color.sendInfo(current, "Your &f" + kit.name() + " &7challenge has expired.");
            }
        }, timeout * 20L);
    }

    public void acceptChallenge(Player target, UUID senderId) {
        if (target.getUniqueId().equals(senderId)) {
            Color.sendError(target, "You cannot accept your own challenge.");
            return;
        }
        if (isInDuel(target.getUniqueId())) {
            Color.sendError(target, "You are already in a duel.");
            return;
        }

        Challenge challenge = challenges.remove(senderId);
        if (challenge == null || challenge.expiresAt() < System.currentTimeMillis()) {
            Color.sendError(target, "That challenge is no longer available.");
            return;
        }

        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null) {
            Color.sendError(target, "The challenger is no longer online.");
            return;
        }
        if (isInDuel(senderId)) {
            Color.sendError(target, "The challenger is already in a duel.");
            return;
        }

        Color.sendSuccess(sender, "&f" + target.getName() + " &7accepted your &f" + challenge.kit().name() + " &7challenge.");
        Color.sendSuccess(target, "You accepted &f" + sender.getName() + "&7's challenge.");
        start(sender, target, challenge.kit(), challenge.rounds());
    }

    public void request(Player from, Player to, Kit kit, int rounds) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            Color.sendError(from, "You cannot duel yourself.");
            return;
        }
        if (isInDuel(from.getUniqueId()) || isInDuel(to.getUniqueId())) {
            Color.sendError(from, "One of you is already in a duel.");
            return;
        }

        long expires = System.currentTimeMillis()
                + plugin.getConfig().getLong("settings.request-timeout-seconds", 30) * 1000L;
        DuelRequest request = new DuelRequest(from.getUniqueId(), to.getUniqueId(), kit, rounds, expires);
        incoming.put(to.getUniqueId(), request);

        Color.sendSuccess(from, "Duel request sent to &f" + to.getName() + " &7using &f" + kit.name() + " &7(best of " + rounds + ").");
        Color.sendDuelRequest(to, from.getName(), kit.name(), rounds);

        long timeout = plugin.getConfig().getLong("settings.request-timeout-seconds", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (incoming.get(to.getUniqueId()) != request) return;

            incoming.remove(to.getUniqueId());
            Color.sendError(from, "Your duel request expired.");
            Color.sendError(to, "The duel request from &f" + from.getName() + " &7expired.");
        }, timeout * 20L);
    }

    public void accept(Player target, UUID optionalSender) {
        DuelRequest request = incoming.get(target.getUniqueId());
        if (request == null) {
            Color.sendError(target, "You have no pending duel request.");
            return;
        }
        if (optionalSender != null && !request.sender().equals(optionalSender)) {
            Color.sendError(target, "That player did not send your current request.");
            return;
        }

        incoming.remove(target.getUniqueId());
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null) {
            Color.sendError(target, "The requester is offline.");
            return;
        }

        start(sender, target, request.kit(), request.rounds());
    }

    public void deny(Player target, UUID optionalSender) {
        DuelRequest request = incoming.get(target.getUniqueId());
        if (request == null) {
            Color.sendError(target, "You have no pending duel request.");
            return;
        }
        if (optionalSender != null && !request.sender().equals(optionalSender)) {
            Color.sendError(target, "That player did not send your current request.");
            return;
        }

        incoming.remove(target.getUniqueId());
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            Color.sendError(sender, "&f" + target.getName() + " &7declined your duel request.");
        }
        Color.sendInfo(target, "Duel request declined.");
    }

    public void start(Player a, Player b, Kit kit, int rounds) {
        Arena arena = plugin.arenaManager().acquire();
        if (arena == null) {
            Color.sendError(a, "The duel arena is busy. Try again in a moment.");
            Color.sendError(b, "The duel arena is busy. Try again in a moment.");
            return;
        }

        Duel duel = new Duel(plugin, a, b, kit, rounds, arena);
        active.put(a.getUniqueId(), duel);
        active.put(b.getUniqueId(), duel);
        duel.start();
    }

    public void remove(Duel duel) {
        active.remove(duel.a());
        active.remove(duel.b());
    }

    public void rememberFinished(Duel duel) {
        lastDuels.put(duel.a(), new LastDuel(duel.b(), duel.kit(), duel.rounds()));
        lastDuels.put(duel.b(), new LastDuel(duel.a(), duel.kit(), duel.rounds()));
    }

    public void rematchLast(Player player) {
        if (isInDuel(player.getUniqueId())) {
            Color.sendError(player, "You cannot rematch while you are in a duel.");
            return;
        }

        LastDuel last = lastDuels.get(player.getUniqueId());
        if (last == null) {
            Color.sendError(player, "You do not have a recent duel to rematch.");
            return;
        }

        Player opponent = Bukkit.getPlayer(last.opponent());
        if (opponent == null) {
            Color.sendError(player, "Your previous opponent is not online.");
            return;
        }
        if (isInDuel(opponent.getUniqueId())) {
            Color.sendError(player, "Your previous opponent is already in a duel.");
            return;
        }

        request(player, opponent, last.kit(), last.rounds());
    }

    public void rematch(Player player, String opponentName) {
        if (isInDuel(player.getUniqueId())) {
            Color.sendError(player, "You cannot rematch while you are in a duel.");
            return;
        }

        LastDuel last = lastDuels.get(player.getUniqueId());
        if (last == null) {
            Color.sendError(player, "You do not have a recent duel to rematch.");
            return;
        }

        Player opponent = Bukkit.getPlayerExact(opponentName);
        if (opponent == null || !opponent.getUniqueId().equals(last.opponent())) {
            Color.sendError(player, "Your previous opponent is not online.");
            return;
        }
        if (isInDuel(opponent.getUniqueId())) {
            Color.sendError(player, "Your previous opponent is already in a duel.");
            return;
        }

        request(player, opponent, last.kit(), last.rounds());
    }

    public void quit(Player player) {
        Duel duel = active.get(player.getUniqueId());
        if (duel == null) return;

        Player other = duel.other(player);
        if (other != null) {
            duel.finish(other);
        } else {
            duel.finish(null);
        }
    }

    public Collection<Duel> active() {
        return new HashSet<>(active.values());
    }
}
