package me.skeletica.duels.kit;

import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.util.Color;
import me.skeletica.duels.util.ItemUtil;
import me.skeletica.duels.duel.PlayerSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class KitEditorManager {
    public static final String EDITOR_TITLE = "&0&lDUELS &8» &bKit Editor";
    public static final String ARMOR_TITLE = "&0&lDUELS &8» &bKit Editor • Armour";
    public static final String ENCHANT_TITLE = "&0&lDUELS &8» &bKit Editor • Enchants";

    private static final int[] DISPLAY_SLOTS = {
            10,11,12,13,14,15,16,17,18,
            19,20,21,22,23,24,25,26,27,
            28,29,30,31,32,33,34,35,36,
            37,38,39,40,41,42,43,44,45
    };

    private final DuelsPlugin plugin;
    private final Set<UUID> editing = new HashSet<>();
    private final Map<UUID, Integer> armorType = new HashMap<>();
    private final Map<UUID, Integer> enchantSlot = new HashMap<>();
    private final Set<UUID> openingCreative = new HashSet<>();
    private final Map<UUID, PlayerSnapshot> originalState = new HashMap<>();

    public KitEditorManager(DuelsPlugin plugin) { this.plugin = plugin; }

    public void start(Player player) {
        if (plugin.duelManager().isInDuel(player.getUniqueId())) {
            Color.sendError(player, "You cannot open the kit editor while you are in a duel.");
            return;
        }
        if (editing.contains(player.getUniqueId())) {
            Color.sendInfo(player, "You are already editing a kit. Finish it from the editor menu.");
            return;
        }

        originalState.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        editing.add(player.getUniqueId());
        openingCreative.add(player.getUniqueId());
        player.closeInventory();
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(false);
        player.setFlying(false);
        applyBlindness(player);

        Color.sendInfo(player, "&d&lKIT EDITOR &8• &7Creative mode ready.");
        Color.send(player, "&7Choose your items, then close the Creative menu.");
    }

    public boolean isEditing(Player player) { return editing.contains(player.getUniqueId()); }
    public boolean isEditing(UUID uuid) { return editing.contains(uuid); }
    public boolean isEditorMenu(String title) { return title.equals(Color.c(EDITOR_TITLE)); }
    public boolean isArmorMenu(String title) { return title.equals(Color.c(ARMOR_TITLE)); }
    public boolean isEnchantMenu(String title) { return title.equals(Color.c(ENCHANT_TITLE)); }

    public boolean isCreativeOpening(Player player) { return openingCreative.contains(player.getUniqueId()); }
    public void clearCreativeOpening(Player player) { openingCreative.remove(player.getUniqueId()); }

    public void openCreative(Player player) {
        if (!isEditing(player)) return;
        openingCreative.add(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isEditing(player)) return;
            Color.sendInfo(player, "&bCreative inventory ready. Press &fE &bto import/change your items. Close it when finished to return here.");
        });
    }

    
    public void openEditorMenu(Player player) {
        if (!isEditing(player)) return;
        armorType.remove(player.getUniqueId());
        enchantSlot.remove(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, Color.c(EDITOR_TITLE));
        ItemStack filler = ItemUtil.item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&7 ", List.of());
        ItemStack border = ItemUtil.item(Material.BLACK_STAINED_GLASS_PANE, "&7 ", List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, filler.clone());
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border.clone());
            inv.setItem(45 + i, border.clone());
        }

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < 36; i++) {
            if (storage[i] != null && !storage[i].getType().isAir()) {
                inv.setItem(DISPLAY_SLOTS[i], storage[i].clone());
            }
        }

        inv.setItem(4, ItemUtil.item(Material.CHEST, "&b&lIMPORT INVENTORY", List.of(), true));

        inv.setItem(46, armourPreview(player.getInventory().getBoots(), "&b&lBOOTS"));
        inv.setItem(47, armourPreview(player.getInventory().getLeggings(), "&b&lLEGGINGS"));
        inv.setItem(48, armourPreview(player.getInventory().getChestplate(), "&b&lCHESTPLATE"));
        inv.setItem(49, armourPreview(player.getInventory().getHelmet(), "&b&lHELMET"));

        ItemStack offhand = player.getInventory().getItemInOffHand();
        inv.setItem(51, offhand != null && !offhand.getType().isAir()
                ? offhand.clone()
                : ItemUtil.item(Material.SHIELD, "&7&lOFFHAND", List.of()));

        inv.setItem(52, ItemUtil.item(Material.BARRIER, "&c&lCANCEL", List.of()));
        inv.setItem(53, ItemUtil.item(Material.LIME_DYE, "&a&lSAVE KIT", List.of(), true));
        player.openInventory(inv);
    }

    private ItemStack armourPreview(ItemStack current, String label) {
        if (current != null && !current.getType().isAir()) {
            ItemStack copy = current.clone();
            return copy;
        }
        return ItemUtil.item(Material.ARMOR_STAND, label, List.of());
    }

    public void rememberArmorType(Player player, int type) { armorType.put(player.getUniqueId(), type); }
    public Integer armorType(Player player) { return armorType.get(player.getUniqueId()); }
    public void clearArmorType(Player player) { armorType.remove(player.getUniqueId()); }

    
    public boolean isValidArmorChoice(int type, Material material) {
        return switch (type) {
            case 36 -> material == Material.LEATHER_BOOTS || material == Material.CHAINMAIL_BOOTS || material == Material.IRON_BOOTS || material == Material.GOLDEN_BOOTS || material == Material.DIAMOND_BOOTS || material == Material.NETHERITE_BOOTS;
            case 37 -> material == Material.LEATHER_LEGGINGS || material == Material.CHAINMAIL_LEGGINGS || material == Material.IRON_LEGGINGS || material == Material.GOLDEN_LEGGINGS || material == Material.DIAMOND_LEGGINGS || material == Material.NETHERITE_LEGGINGS;
            case 38 -> material == Material.LEATHER_CHESTPLATE || material == Material.CHAINMAIL_CHESTPLATE || material == Material.IRON_CHESTPLATE || material == Material.GOLDEN_CHESTPLATE || material == Material.DIAMOND_CHESTPLATE || material == Material.NETHERITE_CHESTPLATE;
            case 39 -> material == Material.LEATHER_HELMET || material == Material.CHAINMAIL_HELMET || material == Material.IRON_HELMET || material == Material.GOLDEN_HELMET || material == Material.DIAMOND_HELMET || material == Material.NETHERITE_HELMET || material == Material.TURTLE_HELMET;
            default -> false;
        };
    }

    public Inventory createArmorMenu(int type) {
        Inventory inv = Bukkit.createInventory(null, 36, Color.c(ARMOR_TITLE));

        ItemStack filler = ItemUtil.item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&7 ", List.of());
        ItemStack border = ItemUtil.item(Material.BLACK_STAINED_GLASS_PANE, "&7 ", List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, filler.clone());
        for (int i = 0; i < 9; i++) inv.setItem(i, border.clone());

        Material[] choices;
        String piece;
        switch (type) {
            case 36 -> {
                piece = "Boots";
                choices = new Material[]{
                        Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
                        Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
                        Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
                };
            }
            case 37 -> {
                piece = "Leggings";
                choices = new Material[]{
                        Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS,
                        Material.IRON_LEGGINGS, Material.GOLDEN_LEGGINGS,
                        Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS
                };
            }
            case 38 -> {
                piece = "Chestplate";
                choices = new Material[]{
                        Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
                        Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE,
                        Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE
                };
            }
            case 39 -> {
                piece = "Helmet";
                choices = new Material[]{
                        Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
                        Material.IRON_HELMET, Material.GOLDEN_HELMET,
                        Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
                        Material.TURTLE_HELMET
                };
            }
            default -> {
                piece = "Armour";
                choices = new Material[0];
            }
        }

        ItemStack heading = ItemUtil.item(Material.ARMOR_STAND,
                "&b&lSELECT " + piece.toUpperCase(Locale.ROOT), List.of(), true);
        inv.setItem(4, heading);

        
        int[] choiceSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < choices.length; i++) {
            Material material = choices[i];
            ItemStack display = new ItemStack(material, 1);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Color.c("&b&l" + pretty(material)));
                meta.setLore(null);
                display.setItemMeta(meta);
            }
            inv.setItem(choiceSlots[i], display);
        }

        inv.setItem(27, ItemUtil.item(Material.BARRIER, "&c&lREMOVE ARMOUR", List.of()));
        inv.setItem(35, ItemUtil.item(Material.ARROW, "&7&lBACK TO EDITOR", List.of()));
        return inv;
    }

    public void applyArmorChoice(Player player, int type, Material material) {
        if (type == 36) player.getInventory().setBoots(material == Material.AIR ? null : new ItemStack(material));
        else if (type == 37) player.getInventory().setLeggings(material == Material.AIR ? null : new ItemStack(material));
        else if (type == 38) player.getInventory().setChestplate(material == Material.AIR ? null : new ItemStack(material));
        else if (type == 39) player.getInventory().setHelmet(material == Material.AIR ? null : new ItemStack(material));
    }

    public void clearArmor(Player player, int type) { applyArmorChoice(player, type, Material.AIR); }

    
    public void openEnchantMenu(Player player, int actualSlot) {
        ItemStack item = getItemAtSlot(player, actualSlot);
        if (item == null || item.getType().isAir()) {
            Color.sendError(player, "There is no item in that slot to enchant.");
            return;
        }
        enchantSlot.put(player.getUniqueId(), actualSlot);
        Inventory inv = Bukkit.createInventory(null, 54, Color.c(ENCHANT_TITLE));
        ItemStack filler = ItemUtil.item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&7 ", List.of());
        ItemStack border = ItemUtil.item(Material.BLACK_STAINED_GLASS_PANE, "&7 ", List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, filler.clone());
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border.clone());
            inv.setItem(45 + i, border.clone());
        }

        ItemStack preview = item.clone();
        ItemMeta pm = preview.getItemMeta();
        if (pm != null) {
            pm.setLore(null);
            preview.setItemMeta(pm);
        }
        inv.setItem(4, preview);

        List<Enchantment> enchants = Arrays.stream(Enchantment.values())
                .filter(Objects::nonNull)
                .filter(e -> e.canEnchantItem(item))
                .sorted(Comparator.comparing(e -> prettyEnchantment(e.getKey().getKey())))
                .toList();

        int[] slots = {10,11,12,13,14,15,16,17,19,20,21,22,23,24,25,26,28,29,30,31,32,33,34,35,37,38,39,40,41,42,43,44};
        for (int i = 0; i < Math.min(slots.length, enchants.size()); i++) {
            Enchantment enchant = enchants.get(i);
            int level = item.getEnchantmentLevel(enchant);
            String name = prettyEnchantment(enchant.getKey().getKey());
            String levelText = level > 0
                    ? " &8[&a" + level + "&7/" + enchant.getMaxLevel() + "&8]"
                    : " &8[&7- &8]";
            inv.setItem(slots[i], ItemUtil.item(level > 0 ? Material.ENCHANTED_BOOK : Material.BOOK,
                    "&b&l" + name + levelText, List.of(), level > 0));
        }

        inv.setItem(45, ItemUtil.item(Material.ARROW, "&7&lBACK", List.of()));
        inv.setItem(49, ItemUtil.item(Material.BARRIER, "&c&lREMOVE ALL ENCHANTS", List.of()));
        player.openInventory(inv);
    }

    public Integer enchantSlot(Player player) { return enchantSlot.get(player.getUniqueId()); }
    public void clearEnchantSlot(Player player) { enchantSlot.remove(player.getUniqueId()); }

    public void changeEnchantment(Player player, Enchantment enchantment, boolean increase) {
        Integer actual = enchantSlot(player);
        if (actual == null) return;
        ItemStack item = getItemAtSlot(player, actual);
        if (item == null || item.getType().isAir()) return;

        int current = item.getEnchantmentLevel(enchantment);
        if (increase) {
            int next = Math.min(enchantment.getMaxLevel(), current + 1);
            if (next == current) return;
            item.addUnsafeEnchantment(enchantment, next);
        } else {
            int next = current - 1;
            if (next <= 0) item.removeEnchantment(enchantment);
            else item.addUnsafeEnchantment(enchantment, next);
        }
        setItemAtSlot(player, actual, item);
    }

    public void removeAllEnchants(Player player) {
        Integer actual = enchantSlot(player);
        if (actual == null) return;
        ItemStack item = getItemAtSlot(player, actual);
        if (item == null) return;
        for (Enchantment enchantment : new HashSet<>(item.getEnchantments().keySet())) item.removeEnchantment(enchantment);
        setItemAtSlot(player, actual, item);
    }

    private ItemStack getItemAtSlot(Player player, int slot) {
        if (slot < 36) return player.getInventory().getItem(slot);
        return switch (slot) {
            case 36 -> player.getInventory().getBoots();
            case 37 -> player.getInventory().getLeggings();
            case 38 -> player.getInventory().getChestplate();
            case 39 -> player.getInventory().getHelmet();
            case 40 -> player.getInventory().getItemInOffHand();
            default -> null;
        };
    }

    private void setItemAtSlot(Player player, int slot, ItemStack item) {
        if (slot < 36) player.getInventory().setItem(slot, item);
        else switch (slot) {
            case 36 -> player.getInventory().setBoots(item);
            case 37 -> player.getInventory().setLeggings(item);
            case 38 -> player.getInventory().setChestplate(item);
            case 39 -> player.getInventory().setHelmet(item);
            case 40 -> player.getInventory().setItemInOffHand(item);
        }
    }

    public void saveFromMenu(Player player) {
        if (!isEditing(player)) return;
        ItemStack[] data = get(player);
        if (data == null) return;
        String name = nextName(player);
        ItemStack[] storage = Arrays.copyOfRange(data, 0, 36);
        ItemStack[] armor = new ItemStack[]{data[36], data[37], data[38], data[39]};
        plugin.customKits().save(player, name, storage, armor, data[40]);
        Color.sendSuccess(player, "Saved custom kit &f" + name + "&7.");
        finish(player);
    }

    private String nextName(Player player) {
        int i = 1;
        while (plugin.customKits().list(player.getUniqueId()).contains("CustomKit" + i)) i++;
        return "CustomKit" + i;
    }

    public ItemStack[] get(Player player) {
        if (!isEditing(player)) return null;
        ItemStack[] data = new ItemStack[41];
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < 36; i++) data[i] = cloneItem(storage[i]);
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < 4; i++) data[36 + i] = cloneItem(armor[i]);
        data[40] = cloneItem(player.getInventory().getItemInOffHand());
        return data;
    }

    public void finish(Player player) {
        if (!editing.remove(player.getUniqueId())) return;
        restoreOriginalState(player);
        cleanup(player);
        player.closeInventory();
    }

    public void abort(Player player) {
        if (!editing.remove(player.getUniqueId())) return;
        restoreOriginalState(player);
        cleanup(player);
        player.closeInventory();
    }

    private void restoreOriginalState(Player player) {
        PlayerSnapshot snapshot = originalState.remove(player.getUniqueId());
        if (snapshot != null) {
            snapshot.restore(player);
        } else {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void cleanup(Player player) {
        armorType.remove(player.getUniqueId());
        enchantSlot.remove(player.getUniqueId());
        openingCreative.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public void abortAll() {
        for (UUID uuid : new HashSet<>(editing)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) abort(player);
            else { editing.remove(uuid); armorType.remove(uuid); enchantSlot.remove(uuid); openingCreative.remove(uuid); originalState.remove(uuid); }
        }
    }

    private void applyBlindness(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 60 * 60, 0, false, false, false));
    }

    private static ItemStack cloneItem(ItemStack item) { return item == null || item.getType().isAir() ? null : item.clone(); }

    private String pretty(Material material) {
        return prettyKey(material.name());
    }

    private String prettyEnchantment(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "vanishing_curse" -> "Curse of Vanishing";
            case "binding_curse" -> "Curse of Binding";
            case "bane_of_arthropods" -> "Bane of Arthropods";
            case "fire_aspect" -> "Fire Aspect";
            case "knockback" -> "Knockback";
            case "looting" -> "Looting";
            case "mending" -> "Mending";
            case "silk_touch" -> "Silk Touch";
            case "sweeping_edge" -> "Sweeping Edge";
            default -> prettyKey(key);
        };
    }

    private String prettyKey(String key) {
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                out.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1)).append(' ');
            }
        }
        return out.toString().trim();
    }
}
