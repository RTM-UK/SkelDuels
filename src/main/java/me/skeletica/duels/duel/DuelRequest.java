package me.skeletica.duels.duel;
import me.skeletica.duels.kit.Kit;
import java.util.UUID;
public record DuelRequest(UUID sender, UUID target, Kit kit, int rounds, long expiresAt) {}
