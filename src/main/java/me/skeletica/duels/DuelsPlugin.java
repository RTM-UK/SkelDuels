package me.skeletica.duels;

import me.skeletica.duels.arena.ArenaManager;
import me.skeletica.duels.command.CommandHandler;
import me.skeletica.duels.duel.DuelManager;
import me.skeletica.duels.gui.GuiManager;
import me.skeletica.duels.kit.CustomKitManager;
import me.skeletica.duels.kit.KitEditorManager;
import me.skeletica.duels.kit.KitManager;
import me.skeletica.duels.listener.DuelsListener;
import me.skeletica.duels.npc.StatsNpcManager;
import me.skeletica.duels.stats.StatsManager;
import me.skeletica.duels.spectator.SpectatorManager;
import me.skeletica.duels.npc.LeaderboardNpcManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

public class DuelsPlugin extends JavaPlugin {
    private KitManager kits; private NamespacedKey enchantKey; private CustomKitManager customKits; private KitEditorManager editor; private ArenaManager arenas; private StatsManager stats; private DuelManager duels; private GuiManager gui; private StatsNpcManager npc; private LeaderboardNpcManager leaderboard; private SpectatorManager spectators;
    @Override public void onEnable(){saveDefaultConfig(); enchantKey=new NamespacedKey(this,"editor_enchantment");getDataFolder().mkdirs();kits=new KitManager(this);customKits=new CustomKitManager(this);editor=new KitEditorManager(this);arenas=new ArenaManager(this);stats=new StatsManager(this);duels=new DuelManager(this);gui=new GuiManager(this);npc=new StatsNpcManager(this);leaderboard=new LeaderboardNpcManager(this);spectators=new SpectatorManager(this);CommandHandler handler=new CommandHandler(this);for(String n:new String[]{"duel","duels","duelaccept","duelrematch","dueldeny","stats","matches","leaderboard","challenge","challengeaccept","spectate","kit","customkit","kitadmin","arena"}){var cmd=getCommand(n);if(cmd!=null){cmd.setExecutor(handler);cmd.setTabCompleter(handler);}}getServer().getPluginManager().registerEvents(new DuelsListener(this),this);getLogger().info("Duels enabled.");}
    @Override public void onDisable(){for(var d:duels.active())d.finish(null);for(var p:getServer().getOnlinePlayers())editor.abort(p);spectators.removeAll();stats.saveAll();npc.removeAll();leaderboard.removeAll();}
    public NamespacedKey enchantKey(){return enchantKey;} public KitManager kits(){return kits;} public CustomKitManager customKits(){return customKits;} public KitEditorManager editor(){return editor;} public ArenaManager arenaManager(){return arenas;} public StatsManager stats(){return stats;} public DuelManager duelManager(){return duels;} public GuiManager gui(){return gui;} public StatsNpcManager npc(){return npc;} public LeaderboardNpcManager leaderboard(){return leaderboard;} public SpectatorManager spectators(){return spectators;}
}
