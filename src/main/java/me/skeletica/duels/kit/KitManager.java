package me.skeletica.duels.kit;

import me.skeletica.duels.DuelsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KitManager {
    private final DuelsPlugin plugin;
    private final File folder;
    private final Map<String, Kit> kits = new HashMap<>();
    public KitManager(DuelsPlugin plugin) { this.plugin=plugin; folder=new File(plugin.getDataFolder(),"kits"); folder.mkdirs(); load(); if(kits.isEmpty()) createDefaults(); }
    public void load(){ kits.clear(); File[] fs=folder.listFiles((d,n)->n.endsWith(".yml")); if(fs==null)return; for(File f:fs){ Kit k=read(f); if(k!=null) kits.put(k.name().toLowerCase(),k); } }
    private Kit read(File f){ YamlConfiguration y=YamlConfiguration.loadConfiguration(f); String name=y.getString("name",f.getName().replace(".yml","")); ItemStack[] c=new ItemStack[36],a=new ItemStack[4]; for(int i=0;i<36;i++) c[i]=y.getItemStack("contents."+i); for(int i=0;i<4;i++) a[i]=y.getItemStack("armor."+i); return new Kit(name,c,a,y.getItemStack("offhand")); }
    public void save(Kit k){ YamlConfiguration y=new YamlConfiguration(); y.set("name",k.name()); ItemStack[] c=k.contents(),a=k.armor(); for(int i=0;i<36;i++) if(c[i]!=null)y.set("contents."+i,c[i]); for(int i=0;i<4;i++)if(a[i]!=null)y.set("armor."+i,a[i]); if(k.offhand()!=null)y.set("offhand",k.offhand()); try{y.save(new File(folder,k.name()+".yml"));}catch(IOException e){plugin.getLogger().severe("Could not save kit: "+e.getMessage());} kits.put(k.name().toLowerCase(),k); }
    public void setFromPlayer(String name, Player p){ save(new Kit(name,p.getInventory().getStorageContents(),p.getInventory().getArmorContents(),p.getInventory().getItemInOffHand())); }
    public void saveEmpty(String name){ save(new Kit(name,new ItemStack[36],new ItemStack[4],null)); }
    public Kit get(String name){return kits.get(name.toLowerCase());}
    public Collection<Kit> all(){return Collections.unmodifiableCollection(kits.values());}
    public void delete(String name){ Kit k=kits.remove(name.toLowerCase()); if(k!=null)new File(folder,k.name()+".yml").delete(); }
    private void createDefaults(){
        ItemStack[] no=new ItemStack[36]; no[0]=new ItemStack(Material.DIAMOND_SWORD); no[1]=new ItemStack(Material.ENDER_PEARL,16); no[2]=new ItemStack(Material.SPLASH_POTION,16); no[3]=new ItemStack(Material.GOLDEN_APPLE,4);
        save(new Kit("NoDebuff",no,new ItemStack[]{new ItemStack(Material.DIAMOND_BOOTS),new ItemStack(Material.DIAMOND_LEGGINGS),new ItemStack(Material.DIAMOND_CHESTPLATE),new ItemStack(Material.DIAMOND_HELMET)},null));
        ItemStack[] boxing=new ItemStack[36]; boxing[0]=new ItemStack(Material.STICK); save(new Kit("Boxing",boxing,new ItemStack[4],null));
        ItemStack[] sumo=new ItemStack[36]; sumo[0]=new ItemStack(Material.STICK); save(new Kit("Sumo",sumo,new ItemStack[4],null));
    }
}
