package me.skeletica.duels.util;

import me.skeletica.duels.duel.DuelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Color {
    private static final String[] GRADIENT = {
            "#35D6FF", "#3BC9FF", "#42BCFF", "#4AAFFF", "#52A2FF",
            "#5A95FF", "#6288FF", "#6A7BFF", "#726EFF"
    };

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Color() {
    }

    public static String c(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static String prefix() {
        return gradient("SkelDuels") + c(" &8» &7");
    }

    public static String gradient(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int index = Math.min(GRADIENT.length - 1, i * GRADIENT.length / text.length());
            out.append(hex(GRADIENT[index])).append(text.charAt(i));
        }
        return out.toString();
    }

    private static String hex(String value) {
        StringBuilder out = new StringBuilder("§x");
        for (char c : value.substring(1).toCharArray()) {
            out.append('§').append(c);
        }
        return out.toString();
    }

    public static String message(String text) {
        return prefix() + c(text);
    }

    public static void send(CommandSender sender, String text) {
        sender.sendMessage(message(text));
    }

    public static void sendSuccess(CommandSender sender, String text) {
        send(sender, "&a✓ &7" + text);
    }

    public static void sendError(CommandSender sender, String text) {
        send(sender, "&c✕ &7" + text);
    }

    public static void sendInfo(CommandSender sender, String text) {
        send(sender, "&b• &7" + text);
    }

    public static void sendRematchPrompt(Player player, String opponentName, String kitName, int firstTo) {
        Component message = header("REMATCH")
                .append(Component.newline())
                .append(Component.text("Your last opponent: ").color(NamedTextColor.GRAY))
                .append(Component.text(opponentName).color(TextColor.fromHexString("#FFFFFF")).decorate(TextDecoration.BOLD))
                .append(Component.text("  •  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(kitName).color(TextColor.fromHexString("#5FD8FF")).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("First to ").color(NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(firstTo)).color(TextColor.fromHexString("#FFFFFF")).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(button("[ REMATCH ]", "Click to instantly send a rematch request", "/duelrematch " + opponentName, NamedTextColor.GREEN))
                .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
                .append(button("[ DISMISS ]", "Hide this prompt", "/duelrematch dismiss", NamedTextColor.GRAY));

        player.sendMessage(message);
    }

    public static void sendChallenge(DuelManager.Challenge challenge, String senderName) {
        int firstTo = (challenge.rounds() + 1) / 2;

        Component message = header("OPEN CHALLENGE")
                .append(Component.newline())
                .append(Component.text(senderName).color(TextColor.fromHexString("#FFFFFF")).decorate(TextDecoration.BOLD))
                .append(Component.text(" is looking for an opponent.").color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Kit  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(challenge.kit().name()).color(TextColor.fromHexString("#5FD8FF")).decorate(TextDecoration.BOLD))
                .append(Component.text("  •  First to ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(firstTo)).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(button("[ ACCEPT ]", "Accept and enter the duel arena", "/challengeaccept " + senderName, NamedTextColor.GREEN));

        Bukkit.broadcast(message);
    }

    public static void sendDuelRequest(Player target, String senderName, String kitName, int rounds) {
        Component message = header("DUEL REQUEST")
                .append(Component.newline())
                .append(Component.text(senderName).color(TextColor.fromHexString("#FFFFFF")).decorate(TextDecoration.BOLD))
                .append(Component.text(" challenged you.").color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Kit  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(kitName).color(TextColor.fromHexString("#5FD8FF")).decorate(TextDecoration.BOLD))
                .append(Component.text("  •  Best of ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(rounds)).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(button("[ ACCEPT ]", "Accept this duel", "/duelaccept " + senderName, NamedTextColor.GREEN))
                .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
                .append(button("[ DENY ]", "Decline this duel", "/dueldeny " + senderName, NamedTextColor.RED));

        target.sendMessage(message);
    }

    private static Component header(String text) {
        return Component.text("✦ ").color(TextColor.fromHexString("#8A9BFF"))
                .append(gradientComponent("SKELDUELS"))
                .append(Component.text("  •  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(text).color(TextColor.fromHexString("#5FD8FF")).decorate(TextDecoration.BOLD));
    }

    private static Component button(String text, String hover, String command, NamedTextColor color) {
        return Component.text(text)
                .color(color)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover).color(color)));
    }

    private static Component gradientComponent(String text) {
        Component result = Component.empty();
        if (text == null || text.isEmpty()) {
            return result;
        }

        for (int i = 0; i < text.length(); i++) {
            int index = Math.min(GRADIENT.length - 1, i * GRADIENT.length / text.length());
            result = result.append(Component.text(String.valueOf(text.charAt(i)))
                    .color(TextColor.fromHexString(GRADIENT[index]))
                    .decorate(TextDecoration.BOLD));
        }
        return result;
    }

    public static Component component(String legacyText) {
        return LEGACY.deserialize(c(legacyText));
    }
}
