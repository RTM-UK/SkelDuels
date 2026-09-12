package me.skeletica.duels.duel;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;

public class PlayerSnapshot {
    private final ItemStack[] contents, armor; private final ItemStack offhand; private final Location location; private final double health; private final int food, level; private final float exp; private final GameMode mode; private final Collection<PotionEffect> effects;
    private PlayerSnapshot(Player p){
        ItemStack[] originalContents = p.getInventory().getStorageContents();
        ItemStack[] originalArmor = p.getInventory().getArmorContents();
        contents = cloneArray(originalContents);
        armor = cloneArray(originalArmor);
        offhand = clone(p.getInventory().getItemInOffHand());
        location=p.getLocation().clone();health=p.getHealth();food=p.getFoodLevel();level=p.getLevel();exp=p.getExp();mode=p.getGameMode();effects=new ArrayList<>(p.getActivePotionEffects());}
    public static PlayerSnapshot capture(Player p){return new PlayerSnapshot(p);}
    private static ItemStack[] cloneArray(ItemStack[] items){
        ItemStack[] copy = new ItemStack[items.length];
        for(int i=0;i<items.length;i++) copy[i] = clone(items[i]);
        return copy;
    }
    private static ItemStack clone(ItemStack item){return item == null ? null : item.clone();}
    public void restore(Player p){p.getInventory().setStorageContents(contents);p.getInventory().setArmorContents(armor);p.getInventory().setItemInOffHand(offhand);p.setGameMode(mode);p.teleport(location);p.setFoodLevel(food);p.setLevel(level);p.setExp(exp);for(PotionEffect e:new ArrayList<>(p.getActivePotionEffects()))p.removePotionEffect(e.getType());for(PotionEffect e:effects)p.addPotionEffect(e);p.setHealth(Math.min(health,p.getMaxHealth()));}
}
