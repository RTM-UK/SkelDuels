package me.skeletica.duels.listener;

import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.duel.Duel;
import me.skeletica.duels.gui.GuiManager;
import me.skeletica.duels.kit.Kit;
import me.skeletica.duels.util.Color;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class DuelsListener implements Listener {
    private final DuelsPlugin plugin;
    private final Map<UUID, PendingSign> pendingRoundSigns = new HashMap<>();

    private record PendingSign(Block block, BlockState original) {}

    public DuelsListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void hideStatsFromJoiningPlayer(PlayerJoinEvent e) {
        plugin.npc().hideFrom(e.getPlayer());
        plugin.leaderboard().hideFrom(e.getPlayer());
    }

    @EventHandler
    public void npcClick(PlayerInteractEntityEvent e) {
        if (plugin.npc().handleInteraction(e.getPlayer(), e.getRightClicked())) { e.setCancelled(true); return; }
        if (plugin.npc().isStatsNpc(e.getRightClicked()) || plugin.leaderboard().isLeaderboardNpc(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();

        if (title.equals(GuiManager.SPECTATE.replace('&', '§'))) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;

            int index = spectateIndex(e.getRawSlot());
            if (index < 0) return;

            UUID targetId = plugin.gui().spectateTarget(p, index);
            if (targetId == null) return;
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                Color.sendError(p, "That duel is no longer available.");
                plugin.gui().openSpectate(p);
                return;
            }
            p.closeInventory();
            plugin.spectators().spectate(p, target);
            return;
        }

        if (plugin.editor().isArmorMenu(title)) {
            handleArmorMenuClick(e, p);
            return;
        }

        if (plugin.editor().isEnchantMenu(title)) {
            handleEnchantMenuClick(e, p);
            return;
        }

        if (plugin.editor().isEditing(p) && plugin.editor().isEditorMenu(title)) {
            e.setCancelled(true);
            int raw = e.getRawSlot();
            if (raw == 4) {
                plugin.editor().openCreative(p);
                return;
            }
            if (raw >= 46 && raw <= 49) {
                ItemStack current = switch (raw) {
                    case 46 -> p.getInventory().getBoots();
                    case 47 -> p.getInventory().getLeggings();
                    case 48 -> p.getInventory().getChestplate();
                    case 49 -> p.getInventory().getHelmet();
                    default -> null;
                };
                if (e.isRightClick() && current != null && !current.getType().isAir()) {
                    plugin.editor().openEnchantMenu(p, raw - 10);
                    return;
                }
                int armorType = raw - 10;
                plugin.editor().rememberArmorType(p, armorType);
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.editor().isEditing(p)) p.openInventory(plugin.editor().createArmorMenu(armorType));
                });
                return;
            }
            if (raw == 51) {
                ItemStack current = p.getInventory().getItemInOffHand();
                if (e.isRightClick() && current != null && !current.getType().isAir()) {
                    plugin.editor().openEnchantMenu(p, 40);
                }
                return;
            }

            if (e.isRightClick()) {
                int actualSlot = displayToActualSlot(raw);
                if (actualSlot >= 0) plugin.editor().openEnchantMenu(p, actualSlot);
                return;
            }

            if (raw == 53) {
                plugin.editor().saveFromMenu(p);
                return;
            }
            if (raw == 52) {
                plugin.editor().abort(p);
                return;
            }
            return;
        }

        if (plugin.editor().isEditing(p)) return;

        if (title.equals(GuiManager.KITS.replace('&', '§'))) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            if (e.getRawSlot() == 49) {
                plugin.gui().openCustom(p);
                return;
            }
            String name = e.getCurrentItem().getItemMeta() != null
                    ? org.bukkit.ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()) : "";
            Kit k = plugin.kits().get(name);
            if (k != null) plugin.gui().selectKit(p, k);
            return;
        }

        if (title.equals(GuiManager.CUSTOM.replace('&', '§'))) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) {
                UUID t = plugin.gui().target(p);
                Player target = t == null ? null : Bukkit.getPlayer(t);
                if (target != null) plugin.gui().openKits(p, target);
                return;
            }
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.NETHER_STAR) return;
            String name = org.bukkit.ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            Kit k = plugin.customKits().load(p.getUniqueId(), name);
            if (k != null) plugin.gui().selectKit(p, k);
            return;
        }

        if (title.equals(GuiManager.ROUNDS.replace('&', '§'))) {
            e.setCancelled(true);
            if (e.getRawSlot() == 22) {
                UUID t = plugin.gui().target(p);
                Player target = t == null ? null : Bukkit.getPlayer(t);
                if (target != null) plugin.gui().openKits(p, target);
                return;
            }
            if (e.getRawSlot() == 18) {
                openCustomRoundSign(p);
                return;
            }
            int rounds = switch (e.getRawSlot()) {
                case 10 -> 1;
                case 12 -> 3;
                case 14 -> 5;
                case 16 -> 7;
                default -> 0;
            };
            if (rounds == 0) return;
            UUID t = plugin.gui().target(p);
            Player target = t == null ? null : Bukkit.getPlayer(t);
            Kit kit = plugin.gui().selected(p);
            if (target != null && kit != null) plugin.duelManager().request(p, target, kit, rounds);
            p.closeInventory();
            return;
        }

        if (title.equals(GuiManager.STATS.replace('&', '§'))) e.setCancelled(true);
    }

    private int spectateIndex(int raw) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31, 32, 33, 34, 35};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == raw) return i;
        }
        return -1;
    }

    private void handleArmorMenuClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        int raw = e.getRawSlot();
        Integer armorType = plugin.editor().armorType(p);
        if (armorType == null) {
            p.closeInventory();
            return;
        }

        if (raw == 27) {
            plugin.editor().clearArmor(p, armorType);
            plugin.editor().clearArmorType(p);
            p.closeInventory();
            return;
        }
        if (raw == 35) {
            plugin.editor().clearArmorType(p);
            p.closeInventory();
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        Material material = clicked.getType();
        if (!plugin.editor().isValidArmorChoice(armorType, material)) return;

        plugin.editor().applyArmorChoice(p, armorType, material);
        plugin.editor().clearArmorType(p);
        p.closeInventory();
        Color.sendSuccess(p, "Equipped &f" + pretty(material) + " &7in the selected armour slot.");
    }

    private void handleEnchantMenuClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        int raw = e.getRawSlot();

        if (raw == 45) {
            plugin.editor().clearEnchantSlot(p);
            p.closeInventory();
            return;
        }
        if (raw == 49) {
            plugin.editor().removeAllEnchants(p);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.editor().isEditing(p)) {
                    Integer slot = plugin.editor().enchantSlot(p);
                    if (slot != null) plugin.editor().openEnchantMenu(p, slot);
                }
            });
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        String rawName = clicked.getItemMeta() == null ? "" : clicked.getItemMeta().getDisplayName();
        String plain = org.bukkit.ChatColor.stripColor(rawName).replaceFirst("\\s+\\[.*$", "").trim();
        org.bukkit.enchantments.Enchantment selected = java.util.Arrays.stream(org.bukkit.enchantments.Enchantment.values())
                .filter(en -> prettyEnchantment(en.getKey().getKey()).equalsIgnoreCase(plain))
                .findFirst().orElse(null);
        if (selected == null) return;

        plugin.editor().changeEnchantment(p, selected, e.isLeftClick());
        Integer slot = plugin.editor().enchantSlot(p);
        if (slot != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.editor().isEditing(p)) plugin.editor().openEnchantMenu(p, slot);
            });
        }
    }

    private int displayToActualSlot(int raw) {
        int[] displaySlots = {
                10,11,12,13,14,15,16,17,18,
                19,20,21,22,23,24,25,26,27,
                28,29,30,31,32,33,34,35,36,
                37,38,39,40,41,42,43,44,45
        };
        for (int i = 0; i < displaySlots.length; i++) {
            if (displaySlots[i] == raw) return i;
        }
        return -1;
    }

    private String prettyEnchantment(String key) {
        String[] parts = key.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(' ');
        }
        return out.toString().trim();
    }

    private String pretty(Material material) {
        String[] parts = material.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return out.toString().trim();
    }

    private void openCustomRoundSign(Player player) {
        player.closeInventory();
        Block block = player.getLocation().getBlock().getRelative(0, 2, 0);
        if (!block.getType().isAir()) {
            Color.send(player, "&cThere is not enough space above you to open the custom round selector.");
            return;
        }
        BlockState original = block.getState();
        pendingRoundSigns.put(player.getUniqueId(), new PendingSign(block, original));
        block.setType(Material.OAK_SIGN, false);
        if (block.getState() instanceof Sign sign) {
            sign.setLine(0, "First to:");
            sign.setLine(1, "1");
            sign.setLine(2, "Enter a number");
            sign.setLine(3, "");
            sign.update(true, false);
            player.openSign(sign);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                PendingSign current = pendingRoundSigns.remove(player.getUniqueId());
                if (current != null && current.block().equals(block)) {
                    current.original().update(true, false);
                }
            }, 200L);
        } else {
            pendingRoundSigns.remove(player.getUniqueId());
            original.update(true, false);
            Color.send(player, "&cCould not open the custom round selector.");
        }
    }

    @EventHandler
    public void editorInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!plugin.editor().isEditing(p)) return;

        String title = e.getView().getTitle();
        if (plugin.editor().isEditorMenu(title)) return;

        if (plugin.editor().isCreativeOpening(p)) {
            plugin.editor().clearCreativeOpening(p);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.editor().isEditing(p)) plugin.editor().openEditorMenu(p);
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.editor().isEditing(p)) return;
            plugin.editor().openEditorMenu(p);
        });
    }

    @EventHandler
    public void signChange(SignChangeEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        PendingSign pending = pendingRoundSigns.remove(p.getUniqueId());
        if (pending == null || !pending.block().equals(e.getBlock())) return;
        int firstTo;
        try {
            StringBuilder digits = new StringBuilder();
            for (int line = 0; line < 4; line++) {
                String value = e.getLine(line);
                if (value == null) continue;
                String found = value.replaceAll("[^0-9]", "");
                if (!found.isEmpty()) { digits.append(found); break; }
            }
            if (digits.isEmpty()) throw new NumberFormatException();
            firstTo = Integer.parseInt(digits.toString());
        } catch (NumberFormatException ex) {
            pending.original().update(true, false);
            Color.send(p, "&cPlease enter a valid whole number from &f1 &cto &f100&c.");
            return;
        }
        if (firstTo < 1 || firstTo > 100) {
            pending.original().update(true, false);
            Color.send(p, "&cThe custom first-to score must be between &f1 &cand &f100&c.");
            return;
        }
        int rounds = firstTo * 2 - 1;
        UUID t = plugin.gui().target(p);
        Player target = t == null ? null : Bukkit.getPlayer(t);
        Kit kit = plugin.gui().selected(p);
        pending.original().update(true, false);
        if (target == null || kit == null) {
            Color.send(p, "&cYour duel selection expired. Please start again.");
            return;
        }
        plugin.duelManager().request(p, target, kit, rounds);
    }

    @EventHandler
    public void damageStatsNpc(org.bukkit.event.entity.EntityDamageEvent e) {
        if (plugin.npc().isStatsNpc(e.getEntity()) || plugin.leaderboard().isLeaderboardNpc(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void freezeEditor(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.editor().isEditing(p)) return;
        if (e.getTo() == null) return;
        if (e.getFrom().getX() != e.getTo().getX() ||
                e.getFrom().getY() != e.getTo().getY() ||
                e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom().clone().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler
    public void editorInteract(PlayerInteractEvent e) {
        if (plugin.editor().isEditing(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void spectatorInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && plugin.spectators().isSpectating(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void death(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Duel d = plugin.duelManager().get(victim.getUniqueId());
        if (d == null) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player winner = d.other(victim);
        if (winner == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isDead()) victim.spigot().respawn();
            d.roundWin(winner, victim);
        });
    }

    @EventHandler
    public void damage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Duel d = plugin.duelManager().get(victim.getUniqueId());
        if (d == null) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) attacker = p;
        if (d.state() != me.skeletica.duels.duel.DuelState.IN_PROGRESS || attacker == null || d != plugin.duelManager().get(attacker.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void food(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && plugin.duelManager().isInDuel(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void breakBlock(BlockBreakEvent e) {
        if (plugin.duelManager().isInDuel(e.getPlayer().getUniqueId()) || plugin.editor().isEditing(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void place(BlockPlaceEvent e) {
        if (plugin.duelManager().isInDuel(e.getPlayer().getUniqueId()) || plugin.editor().isEditing(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void drop(PlayerDropItemEvent e) {
        if (plugin.duelManager().isInDuel(e.getPlayer().getUniqueId()) || plugin.editor().isEditing(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        plugin.spectators().quit(e.getPlayer());
        plugin.leaderboard().remove(e.getPlayer());
        PendingSign pending = pendingRoundSigns.remove(e.getPlayer().getUniqueId());
        if (pending != null) pending.original().update(true, false);
        plugin.editor().abort(e.getPlayer());
        plugin.npc().remove(e.getPlayer());
        plugin.duelManager().quit(e.getPlayer());
        plugin.stats().save(e.getPlayer().getUniqueId());
    }

}
