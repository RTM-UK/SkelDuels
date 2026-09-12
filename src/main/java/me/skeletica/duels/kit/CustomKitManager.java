package me.skeletica.duels.kit;

import me.skeletica.duels.DuelsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CustomKitManager {
    private final DuelsPlugin plugin; private final File folder;
    public CustomKitManager(DuelsPlugin plugin){this.plugin=plugin; folder=new File(plugin.getDataFolder(),"custom-kits"); folder.mkdirs();}
    private File file(UUID uuid,String name){File d=new File(folder,uuid.toString());d.mkdirs();return new File(d,name+".yml");}
    public void save(Player p,String name){save(p,name,p.getInventory().getStorageContents(),p.getInventory().getArmorContents(),p.getInventory().getItemInOffHand());}
    public void save(Player p,String name,ItemStack[] storage,ItemStack[] armor,ItemStack offhand){Kit k=new Kit(name,storage,armor,offhand); YamlConfiguration y=new YamlConfiguration(); y.set("name",name); ItemStack[] c=k.contents(),a=k.armor(); for(int i=0;i<36;i++)if(c[i]!=null)y.set("contents."+i,c[i]); for(int i=0;i<4;i++)if(a[i]!=null)y.set("armor."+i,a[i]); if(k.offhand()!=null)y.set("offhand",k.offhand()); try{y.save(file(p.getUniqueId(),name));}catch(IOException e){plugin.getLogger().warning("Could not save custom kit: "+e.getMessage());}}
    public Kit load(UUID uuid,String name){File f=file(uuid,name); if(!f.exists())return null; YamlConfiguration y=YamlConfiguration.loadConfiguration(f); ItemStack[] c=new ItemStack[36],a=new ItemStack[4]; for(int i=0;i<36;i++)c[i]=y.getItemStack("contents."+i);for(int i=0;i<4;i++)a[i]=y.getItemStack("armor."+i);return new Kit(y.getString("name",name),c,a,y.getItemStack("offhand"));}
    public List<String> list(UUID uuid){File d=new File(folder,uuid.toString());File[] fs=d.listFiles((dir,n)->n.endsWith(".yml"));if(fs==null)return List.of();return Arrays.stream(fs).map(f->f.getName().substring(0,f.getName().length()-4)).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
    public boolean delete(UUID uuid,String name){return file(uuid,name).delete();}
}
