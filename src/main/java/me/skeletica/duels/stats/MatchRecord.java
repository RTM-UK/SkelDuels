package me.skeletica.duels.stats;

import java.util.UUID;

public record MatchRecord(UUID opponent, String opponentName, String kit, int yourScore, int opponentScore,
                          boolean win, long timestamp) {}
