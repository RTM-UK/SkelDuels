package me.skeletica.duels.stats;

import me.skeletica.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class StatsManager {
    private final DuelsPlugin plugin;
    private final File folder;
    private final File eventFile;
    private final Map<UUID, PlayerStats> cache = new HashMap<>();
    private final Map<UUID, List<MatchRecord>> history = new HashMap<>();
    private final Map<UUID, Long> lastEvent = new HashMap<>();
    private long nextEvent;

    public StatsManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "stats");
        folder.mkdirs();
        eventFile = new File(plugin.getDataFolder(), "duel-events.log");
        nextEvent = readLastEvent();
    }

    public synchronized PlayerStats get(UUID uuid) {
        PlayerStats stats = cache.computeIfAbsent(uuid, this::load);
        if (applyPending(uuid, stats)) save(uuid);
        return stats;
    }

    private PlayerStats load(UUID uuid) {
        File file = new File(folder, uuid + ".yml");
        PlayerStats stats = new PlayerStats();
        if (!file.exists()) return stats;

        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        stats.set(y.getInt("wins"), y.getInt("losses"), y.getInt("kills"), y.getInt("deaths"),
                y.getInt("streak"), y.getInt("best-streak"));
        lastEvent.put(uuid, y.getLong("last-event"));
        loadHistory(uuid, y);
        return stats;
    }

    private void loadHistory(UUID uuid, YamlConfiguration y) {
        List<MatchRecord> list = new ArrayList<>();
        ConfigurationSection section = y.getConfigurationSection("matches");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection match = section.getConfigurationSection(key);
                if (match == null) continue;
                try {
                    list.add(new MatchRecord(
                            UUID.fromString(match.getString("opponent", "00000000-0000-0000-0000-000000000000")),
                            match.getString("opponent-name", "Unknown"),
                            match.getString("kit", "Unknown"),
                            match.getInt("your-score"),
                            match.getInt("opponent-score"),
                            match.getBoolean("win"),
                            match.getLong("timestamp")
                    ));
                } catch (Exception ignored) {
                }
            }
        }
        list.sort(Comparator.comparingLong(MatchRecord::timestamp).reversed());
        history.put(uuid, list);
    }

    public synchronized List<MatchRecord> matches(UUID uuid) {
        get(uuid);
        return Collections.unmodifiableList(new ArrayList<>(history.getOrDefault(uuid, List.of())));
    }

    public synchronized void recordRound(UUID winner, UUID loser) {
        get(winner);
        get(loser);
        long id = writeEvent("ROUND", winner, loser);
        PlayerStats winStats = getWithoutReplay(winner);
        PlayerStats loseStats = getWithoutReplay(loser);
        winStats.kill();
        loseStats.death();
        lastEvent.put(winner, id);
        lastEvent.put(loser, id);
        save(winner);
        save(loser);
        plugin.getLogger().info("Round logged: " + shortName(winner) + " killed " + shortName(loser));
    }

    public synchronized void recordMatch(UUID winner, UUID loser, String kit, int winnerScore, int loserScore) {
        get(winner);
        get(loser);
        long now = System.currentTimeMillis();
        long id = writeEvent("MATCH", winner, loser, kit, winnerScore, loserScore, now);

        PlayerStats winnerStats = getWithoutReplay(winner);
        PlayerStats loserStats = getWithoutReplay(loser);
        winnerStats.win();
        loserStats.loss();

        String winnerName = name(winner);
        String loserName = name(loser);
        addMatch(winner, new MatchRecord(loser, loserName, kit, winnerScore, loserScore, true, now));
        addMatch(loser, new MatchRecord(winner, winnerName, kit, loserScore, winnerScore, false, now));

        lastEvent.put(winner, id);
        lastEvent.put(loser, id);
        save(winner);
        save(loser);
        plugin.getLogger().info("Match logged: " + winnerName + " beat " + loserName + " " + winnerScore + "-" + loserScore + " (" + kit + ")");
    }

    public synchronized void recordMatch(UUID player, UUID opponent, String opponentName, String kit,
                                         int yourScore, int opponentScore, boolean win) {
        List<MatchRecord> list = history.computeIfAbsent(player, k -> new ArrayList<>());
        list.add(0, new MatchRecord(opponent, opponentName, kit, yourScore, opponentScore, win, System.currentTimeMillis()));
        trim(list);
        save(player);
    }

    private void addMatch(UUID uuid, MatchRecord record) {
        List<MatchRecord> list = history.computeIfAbsent(uuid, k -> new ArrayList<>());
        list.add(0, record);
        trim(list);
    }

    private void trim(List<MatchRecord> list) {
        if (list.size() > 100) list.subList(100, list.size()).clear();
    }

    public synchronized List<LeaderboardEntry> leaderboard(int limit) {
        List<LeaderboardEntry> out = new ArrayList<>();
        Map<String, LeaderboardEntry> byName = new HashMap<>();
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return out;

        for (File file : files) {
            try {
                UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                PlayerStats stats = get(uuid);
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                String name = player.getName();
                if (name == null || name.isBlank()) continue;

                LeaderboardEntry entry = new LeaderboardEntry(uuid, name, stats);
                String key = name.toLowerCase(Locale.ROOT);
                LeaderboardEntry old = byName.get(key);
                if (old == null || better(entry, old)) byName.put(key, entry);
            } catch (Exception ignored) {
            }
        }

        out.addAll(byName.values());
        out.sort(Comparator.comparingInt((LeaderboardEntry e) -> e.stats().wins()).reversed()
                .thenComparingInt(e -> e.stats().losses())
                .thenComparing(e -> e.name(), String.CASE_INSENSITIVE_ORDER));
        return out.subList(0, Math.min(limit, out.size()));
    }

    private boolean better(LeaderboardEntry a, LeaderboardEntry b) {
        if (a.stats().wins() != b.stats().wins()) return a.stats().wins() > b.stats().wins();
        if (a.stats().losses() != b.stats().losses()) return a.stats().losses() < b.stats().losses();
        return a.stats().kills() > b.stats().kills();
    }

    public record LeaderboardEntry(UUID uuid, String name, PlayerStats stats) {}

    public synchronized void save(UUID uuid) {
        PlayerStats stats = getWithoutReplay(uuid);
        YamlConfiguration y = new YamlConfiguration();
        y.set("wins", stats.wins());
        y.set("losses", stats.losses());
        y.set("kills", stats.kills());
        y.set("deaths", stats.deaths());
        y.set("streak", stats.streak());
        y.set("best-streak", stats.bestStreak());
        y.set("last-event", lastEvent.getOrDefault(uuid, 0L));

        List<MatchRecord> list = history.computeIfAbsent(uuid, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            MatchRecord m = list.get(i);
            String path = "matches." + i;
            y.set(path + ".opponent", m.opponent().toString());
            y.set(path + ".opponent-name", m.opponentName());
            y.set(path + ".kit", m.kit());
            y.set(path + ".your-score", m.yourScore());
            y.set(path + ".opponent-score", m.opponentScore());
            y.set(path + ".win", m.win());
            y.set(path + ".timestamp", m.timestamp());
        }

        File file = new File(folder, uuid + ".yml");
        File temp = new File(folder, uuid + ".yml.tmp");
        try {
            y.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save stats for " + uuid + ": " + e.getMessage());
        }
    }

    public synchronized void saveAll() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) save(uuid);
    }

    private PlayerStats getWithoutReplay(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    private boolean applyPending(UUID uuid, PlayerStats stats) {
        long seen = lastEvent.getOrDefault(uuid, 0L);
        boolean changed = false;

        if (!eventFile.exists()) return false;
        try (BufferedReader reader = Files.newBufferedReader(eventFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                if (p.length < 4) continue;
                long id;
                try {
                    id = Long.parseLong(p[0]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (id <= seen) continue;

                if ("ROUND".equals(p[1]) && p.length >= 4) {
                    UUID winner = UUID.fromString(p[2]);
                    UUID loser = UUID.fromString(p[3]);
                    if (uuid.equals(winner)) stats.kill();
                    if (uuid.equals(loser)) stats.death();
                } else if ("MATCH".equals(p[1]) && p.length >= 8) {
                    UUID winner = UUID.fromString(p[2]);
                    UUID loser = UUID.fromString(p[3]);
                    String kit = p[4];
                    int winnerScore = Integer.parseInt(p[5]);
                    int loserScore = Integer.parseInt(p[6]);
                    long timestamp = Long.parseLong(p[7]);

                    if (uuid.equals(winner)) {
                        stats.win();
                        addMatch(uuid, new MatchRecord(loser, name(loser), kit, winnerScore, loserScore, true, timestamp));
                    } else if (uuid.equals(loser)) {
                        stats.loss();
                        addMatch(uuid, new MatchRecord(winner, name(winner), kit, loserScore, winnerScore, false, timestamp));
                    }
                }

                if (uuid.equals(UUID.fromString(p[2])) || uuid.equals(UUID.fromString(p[3]))) {
                    lastEvent.put(uuid, id);
                    changed = true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read duel event log: " + e.getMessage());
        }
        return changed;
    }

    private long writeEvent(String type, UUID a, UUID b, Object... values) {
        long id = ++nextEvent;
        StringBuilder line = new StringBuilder().append(id).append('|').append(type)
                .append('|').append(a).append('|').append(b);
        for (Object value : values) line.append('|').append(clean(String.valueOf(value)));
        line.append('\n');

        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                eventFile.toPath(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.write(StandardCharsets.UTF_8.encode(line.toString()));
            channel.force(true);
            return id;
        } catch (IOException e) {
            throw new IllegalStateException("Could not write duel event log", e);
        }
    }

    private long readLastEvent() {
        if (!eventFile.exists()) return 0L;
        long last = 0L;
        try (BufferedReader reader = Files.newBufferedReader(eventFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", 2);
                if (p.length == 0) continue;
                try { last = Math.max(last, Long.parseLong(p[0])); } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read duel event log: " + e.getMessage());
        }
        return last;
    }

    private String clean(String value) {
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    private String name(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? "Unknown" : player.getName();
    }

    private String shortName(UUID uuid) {
        String name = name(uuid);
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
