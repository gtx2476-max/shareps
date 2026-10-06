package ru.spheres;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.Set;

public final class SphereListener implements Listener {

    private final SpheresPlugin plugin;
    private final SphereItems items;
    private final SpheresManager manager;
    private final Random random = new Random();
    private final NamespacedKey fireballKey;

    public SphereListener(SpheresPlugin plugin, SphereItems items, SpheresManager manager) {
        this.plugin = plugin;
        this.items = items;
        this.manager = manager;
        this.fireballKey = new NamespacedKey(plugin, "sphere_fireball");
    }

    private boolean roll(String path, double def) {
        return random.nextDouble() * 100.0 < plugin.getConfig().getDouble(path, def);
    }

    private void actionBar(Player p, String key) {
        p.sendActionBar(SphereItems.c(plugin.getConfig().getString("messages." + key, "")));
    }

    // ---------- бой ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();

        // Свой фаербол не бьёт владельца
        if (damager.getPersistentDataContainer().has(fireballKey, PersistentDataType.BYTE)) {
            if (damager instanceof Projectile pr && e.getEntity().equals(pr.getShooter())) e.setCancelled(true);
            return; // от фаербола сферы эффекты не запускаем (без цепной реакции)
        }
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        Player attacker = null;
        boolean melee = false;
        if (damager instanceof Player p) {
            attacker = p;
            melee = true;
        } else if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) return;

        Set<SphereType> act = manager.active(attacker);
        if (act.isEmpty()) return;

        if (act.contains(SphereType.POSEIDON) && roll("poseidon.chance", 40)) {
            int secs = plugin.getConfig().getInt("poseidon.slow-seconds", 4);
            int amp = Math.max(0, plugin.getConfig().getInt("poseidon.slow-level", 2) - 1);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secs * 20, amp));
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 1f);
            actionBar(attacker, "poseidon-proc");
        }

        if (act.contains(SphereType.HADES)) {
            if (melee) victim.setFireTicks(plugin.getConfig().getInt("hades.fire-seconds", 6) * 20);
            if (roll("hades.chance", 18)) {
                launchFireball(attacker, victim);
                actionBar(attacker, "hades-proc");
            }
        }

        if (act.contains(SphereType.ZEUS) && melee
                && attacker.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")
                && roll("zeus.chance", 4)) {
            victim.getWorld().strikeLightning(victim.getLocation());
            actionBar(attacker, "zeus-proc");
        }
    }

    private void launchFireball(Player attacker, LivingEntity victim) {
        Location from = attacker.getEyeLocation().add(attacker.getLocation().getDirection().multiply(1.0));
        Vector dir = victim.getEyeLocation().toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        float power = (float) plugin.getConfig().getDouble("hades.fireball-power", 2.0);
        attacker.getWorld().spawn(from, LargeFireball.class, fb -> {
            fb.setShooter(attacker);
            fb.setDirection(dir);
            fb.setYield(power);
            fb.setIsIncendiary(false);
            fb.getPersistentDataContainer().set(fireballKey, PersistentDataType.BYTE, (byte) 1);
        });
        attacker.getWorld().playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(fireballKey, PersistentDataType.BYTE)
                && !plugin.getConfig().getBoolean("hades.fireball-break-blocks", false)) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && manager.active(p).contains(SphereType.HADES)) {
            e.getProjectile().setFireTicks(400);
        }
    }

    // ---------- головы ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (victim.getKiller() == null || victim.getKiller().equals(victim)) return;
        if (random.nextDouble() * 100.0 < plugin.getConfig().getDouble("head-drop-chance", 100)) {
            e.getDrops().add(items.playerHead(victim));
        }
    }

    // ---------- крафт ----------

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().getNamespace().equals(plugin.getName().toLowerCase())) return;

        ItemStack[] m = e.getInventory().getMatrix();
        if (m.length != 9) { e.getInventory().setResult(null); return; }

        boolean isEmptyRecipe = sr.getKey().getKey().equals("recipe_" + SphereType.EMPTY.id);
        if (isEmptyRecipe) {
            // Нужны 9 ОБЫЧНЫХ голов (сферы использовать нельзя)
            for (ItemStack it : m) {
                if (it == null || it.getType() != Material.PLAYER_HEAD || items.typeOf(it) != null) {
                    e.getInventory().setResult(null);
                    return;
                }
            }
        } else {
            // В центре обязательно ПУСТАЯ СФЕРА
            if (items.typeOf(m[4]) != SphereType.EMPTY) e.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (items.typeOf(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(plugin.getRecipeKeys());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.clearPlayer(e.getPlayer());
    }
}
