package me.skeletica.duels.command;

import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.kit.Kit;
import me.skeletica.duels.util.Color;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final DuelsPlugin plugin;

    public CommandHandler(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "duel", "duels" -> duel(sender, args);
            case "duelrematch" -> rematch(sender, args);
            case "duelaccept" -> duelAccept(sender, args);
            case "dueldeny" -> duelDeny(sender, args);
            case "stats" -> stats(sender, args);
            case "matches" -> matches(sender, args);
            case "leaderboard" -> {
                if (sender instanceof Player player) {
                    plugin.leaderboard().spawn(player);
                }
            }
            case "challenge" -> challenge(sender, args);
            case "challengeaccept" -> challengeAccept(sender, args);
            case "spectate" -> spectate(sender, args);
            case "kit" -> kit(sender, args);
            case "customkit" -> customKit(sender, args);
            case "kitadmin" -> kitAdmin(sender, args);
            case "arena" -> arena(sender, args);
        }
        return true;
    }

    private void duel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 1) {
            Color.sendInfo(player, "Use &f/duel <player> &7to choose a kit.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Color.sendError(player, "Player not found or is offline.");
            return;
        }

        plugin.gui().openKits(player, target);
    }

    private void rematch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length == 0) {
            plugin.duelManager().rematchLast(player);
            return;
        }
        if (args.length == 1 && !args[0].equalsIgnoreCase("dismiss")) {
            plugin.duelManager().rematch(player, args[0]);
        }
    }

    private void duelAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        Player other = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
        plugin.duelManager().accept(player, other == null ? null : other.getUniqueId());
    }

    private void duelDeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        Player other = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
        plugin.duelManager().deny(player, other == null ? null : other.getUniqueId());
    }

    private void stats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length == 0) {
            plugin.npc().spawn(player, player);
            return;
        }

        OfflinePlayer target = findPlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Color.sendError(player, "That player has not joined this server.");
            return;
        }
        plugin.npc().spawn(player, target);
    }

    private void matches(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;

        if (args.length == 0) {
            plugin.npc().spawnHistory(player, player);
            return;
        }

        OfflinePlayer target = findPlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Color.sendError(player, "That player has not joined this server.");
            return;
        }
        plugin.npc().spawnHistory(player, target);
    }

    private void challenge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 1) {
            Color.sendInfo(player, "Use &f/challenge <kit> &7to open a public challenge.");
            return;
        }

        Kit kit = plugin.kits().get(clean(args[0]));
        if (kit == null) {
            Color.sendError(player, "That admin kit does not exist.");
            return;
        }

        plugin.duelManager().challenge(player, kit);
    }

    private void challengeAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 1) {
            Color.sendInfo(player, "Use the &f[ ACCEPT ] &7button on an open challenge.");
            return;
        }

        Player challenger = Bukkit.getPlayerExact(args[0]);
        if (challenger == null) {
            Color.sendError(player, "That challenge is no longer available.");
            return;
        }

        plugin.duelManager().acceptChallenge(player, challenger.getUniqueId());
    }

    private void spectate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length == 0) {
            plugin.gui().openSpectate(player);
            return;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            plugin.spectators().leave(player);
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Color.sendError(player, "Player not found or is offline.");
            return;
        }
        plugin.spectators().spectate(player, target);
    }

    private void kit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;

        if (args.length == 1 && args[0].equalsIgnoreCase("editor")) {
            plugin.editor().start(player);
            return;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            Color.send(player, "&b&lKIT COMMANDS");
            Color.send(player, "&7/kit editor &8- &fOpen the kit editor");
            Color.send(player, "&7/customkit save <name> &8- &fSave a custom kit");
            Color.send(player, "&7/customkit list &8- &fList your custom kits");
            Color.send(player, "&7/customkit delete <name> &8- &fDelete a custom kit");
            return;
        }

        Color.sendInfo(player, "Use &f/kit editor &7or &f/kit help&7.");
    }

    private void customKit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length == 0) {
            Color.sendInfo(player, "Use &f/customkit save <name> &7or &f/customkit list&7.");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "save" -> {
                String name = args.length >= 2 ? clean(args[1]) : nextName(player);
                if (plugin.editor().get(player) == null) {
                    Color.sendError(player, "Open &f/kit editor &7first.");
                    return;
                }

                ItemStack[] edited = plugin.editor().get(player);
                ItemStack[] storage = Arrays.copyOfRange(edited, 0, 36);
                ItemStack[] armor = {edited[36], edited[37], edited[38], edited[39]};
                plugin.customKits().save(player, name, storage, armor, edited[40]);
                Color.sendSuccess(player, "Saved custom kit &f" + name + "&7.");

                if (plugin.editor().isEditing(player)) {
                    plugin.editor().finish(player);
                }
            }
            case "delete" -> {
                if (args.length < 2) {
                    Color.sendInfo(player, "Use &f/customkit delete <name>&7.");
                    return;
                }
                boolean deleted = plugin.customKits().delete(player.getUniqueId(), clean(args[1]));
                if (deleted) {
                    Color.sendSuccess(player, "Deleted custom kit.");
                } else {
                    Color.sendError(player, "Custom kit not found.");
                }
            }
            case "list" -> {
                List<String> kits = plugin.customKits().list(player.getUniqueId());
                Color.send(player, kits.isEmpty()
                        ? "&7You have no saved custom kits."
                        : "&b&lYOUR KITS &8» &f" + String.join("&7, &f", kits));
            }
            default -> Color.sendInfo(player, "Use &f/customkit save <name> &7or &f/customkit list&7.");
        }
    }

    private void kitAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("duels.admin")) {
            Color.sendError(sender, "You do not have permission to use that command.");
            return;
        }
        if (args.length == 0) {
            Color.sendInfo(sender, "Use &f/kitadmin create|set|delete|list|reload <name>&7.");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) return;
                plugin.kits().saveEmpty(clean(args[1]));
                Color.sendSuccess(sender, "Created admin kit &f" + clean(args[1]) + "&7.");
            }
            case "set" -> {
                if (!(sender instanceof Player player) || args.length < 2) return;
                String name = clean(args[1]);
                plugin.kits().setFromPlayer(name, player);
                Color.sendSuccess(player, "Saved your inventory as admin kit &f" + name + "&7.");
            }
            case "delete" -> {
                if (args.length < 2) return;
                plugin.kits().delete(clean(args[1]));
                Color.sendSuccess(sender, "Deleted admin kit.");
            }
            case "list" -> {
                String kits = String.join("&7, &f", plugin.kits().all().stream()
                        .map(Kit::name)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList());
                Color.send(sender, "&b&lADMIN KITS &8» &f" + kits);
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.kits().load();
                Color.sendSuccess(sender, "Configuration reloaded.");
            }
        }
    }

    private void arena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("duels.admin")) {
            Color.sendError(sender, "You do not have permission to use that command.");
            return;
        }
        if (args.length == 0) {
            Color.sendInfo(sender, "Duels use the &fDuels_1 &7arena world automatically.");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) return;
                plugin.arenaManager().create(args[1]);
                Color.sendSuccess(sender, "Created arena &f" + args[1] + "&7.");
            }
            case "setpos1", "setpos2" -> {
                if (!(sender instanceof Player player) || args.length < 2) return;
                if (plugin.arenaManager().get(args[1]) == null) {
                    Color.sendError(player, "Arena not found.");
                    return;
                }
                if (args[0].equalsIgnoreCase("setpos1")) {
                    plugin.arenaManager().setPos1(args[1], player);
                } else {
                    plugin.arenaManager().setPos2(args[1], player);
                }
                Color.sendSuccess(player, "Updated &f" + args[1] + "&7.");
            }
            case "delete" -> {
                if (args.length < 2) return;
                plugin.arenaManager().delete(args[1]);
                Color.sendSuccess(sender, "Deleted arena.");
            }
            case "list" -> {
                String arenas = String.join("&7, &f", plugin.arenaManager().all().stream()
                        .map(arena -> arena.name() + (arena.complete() ? "" : " &c(incomplete)"))
                        .toList());
                Color.send(sender, "&b&lARENAS &8» &f" + arenas);
            }
        }
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayer(name);
    }

    private String nextName(Player player) {
        int i = 1;
        while (plugin.customKits().list(player.getUniqueId()).contains("CustomKit" + i)) {
            i++;
        }
        return "CustomKit" + i;
    }

    private String clean(String value) {
        String name = value.replaceAll("[^A-Za-z0-9_-]", "");
        if (name.isEmpty()) name = "Kit";
        return name.substring(0, Math.min(16, name.length()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> suggestions;

        switch (command.getName().toLowerCase()) {
            case "kit" -> suggestions = args.length == 1 ? Arrays.asList("editor", "help") : List.of();
            case "customkit" -> {
                if (args.length == 1) suggestions = Arrays.asList("save", "delete", "list");
                else if (args.length == 2 && args[0].equalsIgnoreCase("delete") && sender instanceof Player player) {
                    suggestions = plugin.customKits().list(player.getUniqueId());
                } else suggestions = List.of();
            }
            case "kitadmin" -> {
                if (args.length == 1) suggestions = Arrays.asList("create", "set", "delete", "list", "reload");
                else if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                    suggestions = plugin.kits().all().stream().map(Kit::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
                } else suggestions = List.of();
            }
            case "arena" -> {
                if (args.length == 1) suggestions = Arrays.asList("create", "setpos1", "setpos2", "delete", "list");
                else if (args.length == 2 && (args[0].equalsIgnoreCase("setpos1") || args[0].equalsIgnoreCase("setpos2") || args[0].equalsIgnoreCase("delete"))) {
                    suggestions = plugin.arenaManager().all().stream().map(a -> a.name()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
                } else suggestions = List.of();
            }
            case "duel", "duelrematch" -> suggestions = args.length == 1
                    ? Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList()
                    : List.of();
            case "stats", "matches" -> {
                if (args.length != 1) {
                    suggestions = List.of();
                } else {
                    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName() != null) names.add(player.getName());
                }
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    if (player.hasPlayedBefore() && player.getName() != null) names.add(player.getName());
                }
                    suggestions = new ArrayList<>(names);
                }
            }
            case "challenge" -> suggestions = args.length == 1
                    ? plugin.kits().all().stream().map(Kit::name).sorted(String.CASE_INSENSITIVE_ORDER).toList()
                    : List.of();
            case "spectate" -> {
                if (args.length == 1) {
                    suggestions = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    suggestions.add("leave");
                } else suggestions = List.of();
            }
            default -> suggestions = List.of();
        }

        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        return suggestions.stream()
                .filter(value -> value.toLowerCase().startsWith(prefix))
                .toList();
    }
}
