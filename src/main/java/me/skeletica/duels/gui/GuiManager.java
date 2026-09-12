package me.skeletica.duels.gui;

import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.kit.Kit;
import me.skeletica.duels.stats.PlayerStats;
import me.skeletica.duels.util.Color;
import me.skeletica.duels.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class GuiManager {
    public static final String KITS = "&0&lDUELS &8» &bKit Selection";
    public static final String CUSTOM = "&0&lDUELS &8» &dCustom Kits";
    public static final String ROUNDS = "&0&lDUELS &8» &bMatch Format";
    public static final String STATS = "&0&lDUELS &8» &bStatistics";
    public static final String SPECTATE = "&0&lDUELS &8» &bSpectate";

    private final DuelsPlugin plugin;
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, Kit> selected = new HashMap<>();
    private final Map<UUID, List<UUID>> spectateTargets = new HashMap<>();

    public GuiManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openKits(Player viewer, Player target) {
        targets.put(viewer.getUniqueId(), target.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, Color.c(KITS));
        fill(inv);

        int slot = 10;
        for (Kit kit : plugin.kits().all()) {
            if (slot >= 44) break;
            inv.setItem(slot, ItemUtil.item(Material.DIAMOND_SWORD,
                    "&b&l" + kit.name(), List.of(), true));
            slot = nextGridSlot(slot);
        }

        inv.setItem(4, ItemUtil.item(Material.COMPASS, "&b&lDUEL LOADOUT", List.of(), true));
        inv.setItem(49, ItemUtil.item(Material.NETHER_STAR, "&d&lCUSTOM KITS", List.of(), true));
        viewer.openInventory(inv);
    }

    public void openCustom(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, 54, Color.c(CUSTOM));
        fill(inv);

        List<String> kits = plugin.customKits().list(viewer.getUniqueId());
        if (kits.isEmpty()) {
            inv.setItem(22, ItemUtil.item(Material.BARRIER, "&c&lNO CUSTOM KITS", List.of()));
        } else {
            int slot = 10;
            for (String name : kits) {
                if (slot >= 44) break;
                inv.setItem(slot, ItemUtil.item(Material.NETHER_STAR,
                        "&d&l" + name, List.of(), true));
                slot = nextGridSlot(slot);
            }
        }

        inv.setItem(4, ItemUtil.item(Material.NETHER_STAR, "&d&lYOUR CUSTOM LOADOUTS", List.of()));
        inv.setItem(49, ItemUtil.item(Material.ARROW, "&7&lBACK", List.of()));
        viewer.openInventory(inv);
    }

    public void openSpectate(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, 54, Color.c(SPECTATE));
        List<me.skeletica.duels.duel.Duel> games = new ArrayList<>(plugin.duelManager().active());
        games.sort(Comparator.comparing(d -> d.a().toString()));

        if (games.isEmpty()) {
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, ItemUtil.item(Material.RED_STAINED_GLASS_PANE, "&c&lNO GAMES", List.of()));
            }
            inv.setItem(22, ItemUtil.item(Material.BARRIER, "&c&lNo games are available to spectate", List.of()));
            spectateTargets.remove(viewer.getUniqueId());
            viewer.openInventory(inv);
            return;
        }

        fill(inv);
        inv.setItem(4, ItemUtil.item(Material.ENDER_EYE, "&b&lLIVE DUELS", List.of("&7Select a duel to spectate."), true));

        List<UUID> targetsForViewer = new ArrayList<>();
        int slot = 10;
        for (me.skeletica.duels.duel.Duel duel : games) {
            Player a = Bukkit.getPlayer(duel.a());
            Player b = Bukkit.getPlayer(duel.b());
            if (a == null || b == null) continue;

            inv.setItem(slot, ItemUtil.item(Material.PLAYER_HEAD,
                    "&b&l" + a.getName() + " &8vs &c&l" + b.getName(),
                    List.of("&7Kit: &f" + duel.kit().name(),
                            "&7Score: &b" + duel.score(a) + " &8- &c" + duel.score(b),
                            "", "&aClick to spectate"), true));
            targetsForViewer.add(a.getUniqueId());
            slot = nextGridSlot(slot);
            if (slot >= 44) break;
        }

        spectateTargets.put(viewer.getUniqueId(), targetsForViewer);
        viewer.openInventory(inv);
    }

    public UUID spectateTarget(Player viewer, int index) {
        List<UUID> targets = spectateTargets.get(viewer.getUniqueId());
        if (targets == null || index < 0 || index >= targets.size()) return null;
        return targets.get(index);
    }

    public void selectKit(Player viewer, Kit kit) {
        selected.put(viewer.getUniqueId(), kit);
        openRounds(viewer);
    }

    public void openRounds(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, 27, Color.c(ROUNDS));
        fillSmall(inv);

        int[] slots = {10, 12, 14, 16};
        int[] rounds = {1, 3, 5, 7};
        for (int i = 0; i < rounds.length; i++) {
            inv.setItem(slots[i], ItemUtil.item(Material.NETHER_STAR,
                    "&b&lBEST OF " + rounds[i], List.of(), true));
        }

        inv.setItem(4, ItemUtil.item(Material.CLOCK, "&b&lMATCH FORMAT", List.of(), true));
        inv.setItem(18, ItemUtil.item(Material.OAK_SIGN, "&6&lCUSTOM FORMAT", List.of()));
        inv.setItem(22, ItemUtil.item(Material.ARROW, "&7&lBACK", List.of()));
        viewer.openInventory(inv);
    }

    public void openStats(Player viewer, Player target) {
        Inventory inv = Bukkit.createInventory(null, 27, Color.c(STATS));
        fillSmall(inv);

        PlayerStats s = plugin.stats().get(target.getUniqueId());
        inv.setItem(4, ItemUtil.item(Material.PLAYER_HEAD, "&b&l" + target.getName(), List.of(), true));
        inv.setItem(10, stat(Material.LIME_DYE, "&aWins", s.wins()));
        inv.setItem(11, stat(Material.RED_DYE, "&cLosses", s.losses()));
        inv.setItem(12, ItemUtil.item(Material.GOLD_INGOT,
                "&eWin Rate &f" + String.format(Locale.US, "%.1f%%", s.winRate()), List.of()));
        inv.setItem(14, stat(Material.IRON_SWORD, "&bKills", s.kills()));
        inv.setItem(15, stat(Material.SKELETON_SKULL, "&cDeaths", s.deaths()));
        inv.setItem(16, stat(Material.FIREWORK_STAR, "&6Best Streak", s.bestStreak()));
        viewer.openInventory(inv);
    }

    private ItemStack stat(Material material, String label, int value) {
        return ItemUtil.item(material, label + " &f" + value, List.of());
    }

    private void fill(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ItemUtil.filler());
        for (int slot : new int[]{0, 8, 45, 53}) inv.setItem(slot, ItemUtil.border());
    }

    private void fillSmall(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ItemUtil.filler());
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, ItemUtil.border());
            inv.setItem(18 + i, ItemUtil.border());
        }
    }

    private int nextGridSlot(int current) {
        return switch (current) {
            case 16 -> 19;
            case 25 -> 28;
            case 34 -> 37;
            default -> current + 1;
        };
    }

    public UUID target(Player player) {
        return targets.get(player.getUniqueId());
    }

    public Kit selected(Player player) {
        return selected.get(player.getUniqueId());
    }

    public void clear(Player player) {
        targets.remove(player.getUniqueId());
        selected.remove(player.getUniqueId());
        spectateTargets.remove(player.getUniqueId());
    }
}
