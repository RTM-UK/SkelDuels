package me.skeletica.duels.arena;

import me.skeletica.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class ArenaManager {
    public static final String WORLD_NAME = "Duels_1";
    private static final int PLATFORM_Y = 64;
    private static final int PLATFORM_MIN = -25;
    private static final int PLATFORM_MAX = 24;

    private final DuelsPlugin plugin;
    private final Set<String> used = new HashSet<>();
    private World world;

    public ArenaManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        ensureWorld();
    }

    public void ensureWorld() {
        world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) {
            WorldCreator creator = new WorldCreator(WORLD_NAME);
            creator.generator(new VoidGenerator());
            creator.generateStructures(false);
            world = creator.createWorld();
        }
        if (world == null) {
            throw new IllegalStateException("Could not create duel world " + WORLD_NAME);
        }

        buildPlatform();
        world.setSpawnLocation(0, PLATFORM_Y + 1, 0);
    }

    private void buildPlatform() {
        for (int x = PLATFORM_MIN; x <= PLATFORM_MAX; x += 16) {
            for (int z = PLATFORM_MIN; z <= PLATFORM_MAX; z += 16) {
                Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                if (!chunk.isLoaded()) chunk.load(true);
            }
        }

        for (int x = PLATFORM_MIN; x <= PLATFORM_MAX; x++) {
            for (int z = PLATFORM_MIN; z <= PLATFORM_MAX; z++) {
                world.getBlockAt(x, PLATFORM_Y, z).setType(Material.COBBLESTONE, false);
                for (int y = PLATFORM_Y + 1; y <= PLATFORM_Y + 5; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    public Arena acquire() {
        String key = WORLD_NAME.toLowerCase();
        if (used.contains(key)) return null;
        used.add(key);

        Location pos1 = new Location(world, -10.0, PLATFORM_Y + 1.0, 0.0, -90.0f, 0.0f);
        Location pos2 = new Location(world, 10.0, PLATFORM_Y + 1.0, 0.0, 90.0f, 0.0f);
        return new Arena(WORLD_NAME, pos1, pos2);
    }

    public void release(Arena arena) {
        if (arena != null) used.remove(WORLD_NAME.toLowerCase());
    }

    public World world() {
        return world;
    }

    public void load() { ensureWorld(); }
    public void save() {}
    public void create(String name) {}
    public void delete(String name) {}
    public Arena get(String name) { return WORLD_NAME.equalsIgnoreCase(name) ? new Arena(WORLD_NAME,
            new Location(world, -10, PLATFORM_Y + 1, 0, -90, 0),
            new Location(world, 10, PLATFORM_Y + 1, 0, 90, 0)) : null; }
    public Collection<Arena> all() { return Collections.singleton(get(WORLD_NAME)); }
    public void setPos1(String name, Player p) {}
    public void setPos2(String name, Player p) {}

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}
