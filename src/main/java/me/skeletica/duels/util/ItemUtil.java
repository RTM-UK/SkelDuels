package me.skeletica.duels.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemUtil {
    private ItemUtil() {}

    public static ItemStack item(Material material, String name, List<String> lore) {
        return item(material, name, lore, false);
    }

    public static ItemStack item(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Color.c(name));
            if (lore != null) meta.setLore(lore.stream().map(Color::c).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (glow) meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, "&7 ", List.of());
    }

    public static ItemStack border() {
        return item(Material.BLACK_STAINED_GLASS_PANE, "&7 ", List.of());
    }
}
