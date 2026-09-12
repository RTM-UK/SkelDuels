package me.skeletica.duels.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.skeletica.duels.DuelsPlugin;
import me.skeletica.duels.stats.MatchRecord;
import me.skeletica.duels.stats.PlayerStats;
import me.skeletica.duels.util.Color;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class StatsNpcManager {
    private static final long DISPLAY_TICKS = 30L * 20L;
    private static final int HISTORY_PER_PAGE = 4;

    private final DuelsPlugin plugin;
    private final Map<UUID, List<UUID>> spawned = new HashMap<>();
    private final Map<UUID, List<UUID>> dynamic = new HashMap<>();
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, Boolean> historyView = new HashMap<>();
    private final Map<UUID, Integer> historyPage = new HashMap<>();
    private final Map<UUID, Location> bases = new HashMap<>();
    private final Map<UUID, Long> expiryToken = new HashMap<>();
    private final Map<UUID, UUID> interactionOwner = new HashMap<>();
    private final Map<UUID, InteractionAction> interactionActions = new HashMap<>();
    private final Map<UUID, UUID> statsTabs = new HashMap<>();
    private final Map<UUID, UUID> historyTabs = new HashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    private enum Tab { STATS, HISTORY }
    private enum InteractionAction { STATS, HISTORY, PREVIOUS, NEXT }

    public StatsNpcManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawn(Player viewer, OfflinePlayer target) {
        spawn(viewer, target, false);
    }

    public void spawnHistory(Player viewer, OfflinePlayer target) {
        spawn(viewer, target, true);
    }

    private void spawn(Player viewer, OfflinePlayer target, boolean history) {
        remove(viewer);
        UUID viewerId = viewer.getUniqueId();
        targets.put(viewerId, target.getUniqueId());
        historyView.put(viewerId, history);
        historyPage.put(viewerId, 0);
        bases.put(viewerId, createBase(viewer));
        createStatic(viewer, target);
        render(viewer, target, history ? Tab.HISTORY : Tab.STATS);
    }

    public boolean toggle(Player viewer) {
        UUID targetId = targets.get(viewer.getUniqueId());
        if (targetId == null) return false;

        boolean next = !historyView.getOrDefault(viewer.getUniqueId(), false);
        historyView.put(viewer.getUniqueId(), next);
        historyPage.put(viewer.getUniqueId(), 0);
        render(viewer, Bukkit.getOfflinePlayer(targetId), next ? Tab.HISTORY : Tab.STATS);
        return true;
    }

    private void createStatic(Player viewer, OfflinePlayer target) {
        UUID viewerId = viewer.getUniqueId();
        Location base = bases.get(viewerId).clone();
        Vector toViewer = base.getDirection();
        if (toViewer.lengthSquared() < 0.001) toViewer = new Vector(0, 0, -1);
        Vector right = new Vector(-toViewer.getZ(), 0, toViewer.getX()).normalize();
        List<UUID> ids = new ArrayList<>();

        Mannequin npc = (Mannequin) base.getWorld().spawnEntity(base, EntityType.MANNEQUIN);
        npc.setImmovable(true);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setProfile(ResolvableProfile.resolvableProfile(target.getPlayerProfile()));
        npc.setCustomNameVisible(false);
        ids.add(npc.getUniqueId());

        TextDisplay title = text(base.clone().add(0, 4.90, 0), "&f&l✦  &bDUELS PROFILE  &f✦");
        style(title, 255, 300, 35, 42, 55, 180);
        ids.add(title.getUniqueId());

        String name = target.getName() == null ? "Unknown Player" : target.getName();
        TextDisplay player = text(base.clone().add(0, 4.52, 0), Color.gradient(name));
        style(player, 255, 260, 0, 0, 0, 0);
        ids.add(player.getUniqueId());

        TextDisplay statsTab = text(offset(base, right, -0.92, 4.08), "&b&l[ STATS ]");
        style(statsTab, 255, 180, 0, 0, 0, 0);
        ids.add(statsTab.getUniqueId());
        statsTabs.put(viewerId, statsTab.getUniqueId());

        TextDisplay historyTab = text(offset(base, right, 0.98, 4.08), "&7[ MATCH HISTORY ]");
        style(historyTab, 255, 250, 0, 0, 0, 0);
        ids.add(historyTab.getUniqueId());
        historyTabs.put(viewerId, historyTab.getUniqueId());

        Interaction statsHit = hit(offset(base, right, -0.92, 4.08), toViewer, 1.55f, 0.55f);
        Interaction historyHit = hit(offset(base, right, 0.98, 4.08), toViewer, 2.45f, 0.55f);
        ids.add(statsHit.getUniqueId());
        ids.add(historyHit.getUniqueId());
        action(statsHit, viewerId, InteractionAction.STATS);
        action(historyHit, viewerId, InteractionAction.HISTORY);

        spawned.put(viewerId, ids);
        dynamic.put(viewerId, new ArrayList<>());

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewerId)) showAll(online, ids);
            else hideAll(online, ids);
        }
    }

    private Location createBase(Player viewer) {
        Location base = viewer.getLocation().clone();
        Vector flat = viewer.getLocation().getDirection().setY(0).normalize();
        if (flat.lengthSquared() < 0.001) flat = new Vector(0, 0, 1);
        base.add(flat.multiply(3.5));

        Vector toViewer = viewer.getLocation().toVector().subtract(base.toVector()).normalize();
        base.setDirection(toViewer);
        return base;
    }

    private void render(Player viewer, OfflinePlayer target, Tab tab) {
        UUID viewerId = viewer.getUniqueId();
        removeDynamic(viewerId);

        TextDisplay statsTab = entity(statsTabs.get(viewerId), TextDisplay.class);
        TextDisplay historyTab = entity(historyTabs.get(viewerId), TextDisplay.class);
        if (statsTab != null) statsTab.text(legacy.deserialize(Color.c(tab == Tab.STATS ? "&b&l[ STATS ]" : "&7[ STATS ]")));
        if (historyTab != null) historyTab.text(legacy.deserialize(Color.c(tab == Tab.HISTORY ? "&d&l[ MATCH HISTORY ]" : "&7[ MATCH HISTORY ]")));

        List<UUID> ids = dynamic.computeIfAbsent(viewerId, key -> new ArrayList<>());
        Location base = bases.get(viewerId);
        if (base == null) return;

        if (tab == Tab.STATS) renderStats(base, ids, target);
        else renderHistory(viewer, base, ids, target);

        List<UUID> all = spawned.get(viewerId);
        if (all == null) return;
        all.addAll(ids);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewerId)) showAll(online, ids);
            else hideAll(online, ids);
        }

        long token = System.nanoTime();
        expiryToken.put(viewerId, token);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (token == expiryToken.getOrDefault(viewerId, 0L)) remove(viewer);
        }, DISPLAY_TICKS);
    }

    private void renderStats(Location base, List<UUID> ids, OfflinePlayer target) {
        PlayerStats s = plugin.stats().get(target.getUniqueId());
        String rate = String.format(Locale.US, "%.1f%%", s.winRate());

        line(ids, base, 3.58, "&b&lWINS &f" + s.wins() + "    &8•    &c&lLOSSES &f" + s.losses(), 340);
        line(ids, base, 3.08, "&e&lWIN RATE &8» &f" + rate, 260);
        line(ids, base, 2.58, "&b&lKILLS &f" + s.kills() + "    &8•    &c&lDEATHS &f" + s.deaths(), 340);
        line(ids, base, 2.08, "&6&lBEST STREAK &8» &f" + s.bestStreak(), 260);
    }

    private void renderHistory(Player viewer, Location base, List<UUID> ids, OfflinePlayer target) {
        List<MatchRecord> matches = plugin.stats().matches(target.getUniqueId());
        if (matches.isEmpty()) {
            line(ids, base, 3.58, "&7No completed matches yet.", 300);
            return;
        }

        UUID viewerId = viewer.getUniqueId();
        int pages = (matches.size() + HISTORY_PER_PAGE - 1) / HISTORY_PER_PAGE;
        int page = Math.max(0, Math.min(historyPage.getOrDefault(viewerId, 0), pages - 1));
        historyPage.put(viewerId, page);

        int start = page * HISTORY_PER_PAGE;
        int end = Math.min(start + HISTORY_PER_PAGE, matches.size());
        for (int i = start; i < end; i++) {
            MatchRecord m = matches.get(i);
            String result = m.win() ? "&a&lW" : "&c&lL";
            String score = "&f" + m.yourScore() + " &8- &f" + m.opponentScore();
            String date = new SimpleDateFormat("dd/MM/yy", Locale.UK).format(new Date(m.timestamp()));
            String row = result + "  &f" + truncate(m.opponentName(), 16)
                    + " &8• &b" + truncate(m.kit(), 12)
                    + " &8• " + score + " &8• &7" + date;
            line(ids, base, 3.55 - (i - start) * 0.45, row, 460);
        }

        if (pages > 1) {
            Vector right = new Vector(-base.getDirection().getZ(), 0, base.getDirection().getX()).normalize();
            double buttonY = 1.78;

            if (page > 0) {
                TextDisplay previous = text(offset(base, right, -0.82, buttonY), "&b‹");
                style(previous, 255, 70, 0, 0, 0, 0);
                scale(previous, 0.80f);
                ids.add(previous.getUniqueId());
                Interaction hit = hit(offset(base, right, -0.82, buttonY), base.getDirection(), 0.65f, 0.40f);
                ids.add(hit.getUniqueId());
                action(hit, viewerId, InteractionAction.PREVIOUS);
            }

            TextDisplay pageText = text(base.clone().add(0, buttonY, 0), "&7Page &f" + (page + 1) + " &8/ &f" + pages);
            style(pageText, 255, 100, 0, 0, 0, 0);
            scale(pageText, 0.75f);
            ids.add(pageText.getUniqueId());

            TextDisplay next = text(offset(base, right, 0.82, buttonY), "&b›");
            style(next, 255, 70, 0, 0, 0, 0);
            scale(next, 0.80f);
            ids.add(next.getUniqueId());
            Interaction hit = hit(offset(base, right, 0.82, buttonY), base.getDirection(), 0.65f, 0.40f);
            ids.add(hit.getUniqueId());
            action(hit, viewerId, InteractionAction.NEXT);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return "Unknown";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private void line(List<UUID> ids, Location base, double y, String value, int width) {
        TextDisplay d = text(base.clone().add(0, y, 0), value);
        style(d, 255, width, 0, 0, 0, 0);
        ids.add(d.getUniqueId());
    }

    private Location offset(Location base, Vector right, double x, double y) {
        return base.clone().add(right.clone().multiply(x)).add(0, y, 0);
    }

    private void action(Entity entity, UUID owner, InteractionAction action) {
        interactionOwner.put(entity.getUniqueId(), owner);
        interactionActions.put(entity.getUniqueId(), action);
    }

    public boolean handleInteraction(Player player, Entity entity) {
        UUID owner = interactionOwner.get(entity.getUniqueId());
        if (owner == null || !owner.equals(player.getUniqueId())) return false;

        InteractionAction action = interactionActions.get(entity.getUniqueId());
        if (action == null) return false;

        UUID viewerId = player.getUniqueId();
        if (action == InteractionAction.STATS || action == InteractionAction.HISTORY) {
            boolean history = action == InteractionAction.HISTORY;
            if (historyView.getOrDefault(viewerId, false) != history) {
                historyView.put(viewerId, history);
                historyPage.put(viewerId, 0);
                UUID targetId = targets.get(viewerId);
                if (targetId != null) render(player, Bukkit.getOfflinePlayer(targetId), history ? Tab.HISTORY : Tab.STATS);
            }
            return true;
        }

        UUID targetId = targets.get(viewerId);
        if (targetId == null) return true;
        List<MatchRecord> matches = plugin.stats().matches(targetId);
        int pages = Math.max(1, (matches.size() + HISTORY_PER_PAGE - 1) / HISTORY_PER_PAGE);
        int page = historyPage.getOrDefault(viewerId, 0);
        if (action == InteractionAction.NEXT && page < pages - 1) page++;
        if (action == InteractionAction.PREVIOUS && page > 0) page--;
        historyPage.put(viewerId, page);
        render(player, Bukkit.getOfflinePlayer(targetId), Tab.HISTORY);
        return true;
    }

    private Interaction hit(Location loc, Vector facing, float width, float height) {
        Location hitLocation = loc.clone().add(facing.clone().multiply(0.10));
        hitLocation.setDirection(facing);
        Interaction i = (Interaction) hitLocation.getWorld().spawnEntity(hitLocation, EntityType.INTERACTION);
        i.setInteractionWidth(width);
        i.setInteractionHeight(height);
        i.setResponsive(true);
        return i;
    }

    private TextDisplay text(Location loc, String legacyText) {
        TextDisplay d = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        d.text(legacy.deserialize(Color.c(legacyText)));
        d.setBillboard(Display.Billboard.FIXED);
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setShadowed(true);
        d.setSeeThrough(false);
        d.setViewRange(32);
        d.setGravity(false);
        d.setPersistent(false);
        return d;
    }

    private void scale(TextDisplay d, float scale) {
        d.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        ));
    }

    private void style(TextDisplay d, int opacity, int width, int r, int g, int b, int alpha) {
        d.setTextOpacity((byte) Math.max(0, Math.min(255, opacity)));
        d.setBackgroundColor(org.bukkit.Color.fromARGB(alpha, r, g, b));
        d.setDefaultBackground(false);
        d.setLineWidth(width);
    }

    private void showAll(Player p, List<UUID> ids) {
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) p.showEntity(plugin, e);
        }
    }

    private void hideAll(Player p, List<UUID> ids) {
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) p.hideEntity(plugin, e);
        }
    }

    private void removeDynamic(UUID viewerId) {
        List<UUID> ids = dynamic.get(viewerId);
        if (ids == null) return;
        List<UUID> spawnedIds = spawned.get(viewerId);
        if (spawnedIds != null) spawnedIds.removeAll(ids);
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
            interactionOwner.remove(id);
            interactionActions.remove(id);
        }
        ids.clear();
    }

    private <T extends Entity> T entity(UUID id, Class<T> type) {
        if (id == null) return null;
        Entity entity = Bukkit.getEntity(id);
        return type.isInstance(entity) ? type.cast(entity) : null;
    }

    public void hideFrom(Player player) {
        for (List<UUID> ids : spawned.values()) hideAll(player, ids);
    }

    public boolean isStatsNpc(Entity entity) {
        for (List<UUID> ids : spawned.values()) if (ids.contains(entity.getUniqueId())) return true;
        return false;
    }

    public void remove(Player p) {
        UUID viewerId = p.getUniqueId();
        removeDynamic(viewerId);
        List<UUID> ids = spawned.remove(viewerId);
        if (ids != null) {
            for (UUID id : ids) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
                interactionOwner.remove(id);
                interactionActions.remove(id);
            }
        }
        targets.remove(viewerId);
        historyView.remove(viewerId);
        historyPage.remove(viewerId);
        bases.remove(viewerId);
        expiryToken.remove(viewerId);
        statsTabs.remove(viewerId);
        historyTabs.remove(viewerId);
        dynamic.remove(viewerId);
    }

    public void removeAll() {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) remove(p);
        interactionOwner.clear();
        interactionActions.clear();
    }
}
