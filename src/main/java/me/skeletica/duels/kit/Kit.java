package me.skeletica.duels.kit;

import org.bukkit.inventory.ItemStack;

public class Kit {
    private final String name;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;

    public Kit(String name, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        this.name = name; this.contents = cloneArray(contents, 36); this.armor = cloneArray(armor, 4); this.offhand = offhand == null ? null : offhand.clone();
    }
    private static ItemStack[] cloneArray(ItemStack[] src, int size) { ItemStack[] out = new ItemStack[size]; if (src != null) for (int i=0;i<Math.min(src.length,size);i++) out[i]=src[i]==null?null:src[i].clone(); return out; }
    public String name() { return name; }
    public ItemStack[] contents() { return cloneArray(contents, 36); }
    public ItemStack[] armor() { return cloneArray(armor, 4); }
    public ItemStack offhand() { return offhand == null ? null : offhand.clone(); }
}
