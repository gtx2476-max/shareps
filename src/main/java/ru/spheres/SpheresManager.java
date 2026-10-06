package ru.spheres;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Пассивные эффекты сфер (бафы/дебафы, аура Фугу). Тик = 5 игровых тиков. */
public final class SpheresManager {

    private record Mod(Attribute attribute, String name, double amount) {}

    private final SpheresPlugin plugin;
    private final SphereItems items;
    private BukkitTask task;
    private int counter = 0;

    public SpheresManager(SpheresPlugin plugin, SphereItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) clearPlayer(p);
    }

    public void clearPlayer(Player p) {
        for (SphereType t : SphereType.values()) applyMods(p, t, false);
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private int amp(String path) { return Math.max(0, cfg().getInt(path, 1) - 1); }

    /** Какие сферы сейчас "активны" у игрока (лежат в руках). */
    public Set<SphereType> active(Player p) {
        Map<SphereType, Integer> counts = new EnumMap<>(SphereType.class);
        ItemStack[] hands = {p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()};
        for (ItemStack it : hands) {
            SphereType t = items.typeOf(it);
            if (t != null && t != SphereType.EMPTY) counts.merge(t, 1, Integer::sum);
        }
        Set<SphereType> result = EnumSet.noneOf(SphereType.class);
        for (Map.Entry<SphereType, Integer> en : counts.entrySet()) {
            if (en.getKey() == SphereType.HADES && cfg().getBoolean("hades.require-both-hands", false)
                    && en.getValue() < 2) continue;
            result.add(en.getKey());
        }
        return result;
    }

    private void tick() {
        counter++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Set<SphereType> act = active(p);
            for (SphereType t : SphereType.values()) {
                if (t == SphereType.EMPTY) continue;
                applyMods(p, t, act.contains(t));
            }
            if (counter % 2 == 0) {
                for (SphereType t : act) applyPassive(p, t);
            }
            if (act.contains(SphereType.FUGU)) fuguAura(p);
        }
    }

    private void applyPassive(Player p, SphereType t) {
        switch (t) {
            case POSEIDON -> eff(p, PotionEffectType.WATER_BREATHING, 0);
            case HADES -> {
                eff(p, PotionEffectType.SPEED, amp("hades.speed-level"));
                eff(p, PotionEffectType.FIRE_RESISTANCE, 0);
            }
            case ZEUS -> eff(p, PotionEffectType.HASTE, amp("zeus.haste-level"));
            case HERMES -> {
                eff(p, PotionEffectType.SPEED, amp("hermes.speed-level"));
                eff(p, PotionEffectType.JUMP_BOOST, amp("hermes.jump-level"));
            }
            case ATHENA -> eff(p, PotionEffectType.RESISTANCE, amp("athena.resistance-level"));
            default -> {}
        }
    }

    private void eff(LivingEntity e, PotionEffectType type, int amplifier) {
        e.addPotionEffect(new PotionEffect(type, 45, amplifier, true, false, true));
    }

    private void fuguAura(Player holder) {
        double r = cfg().getDouble("fugu.radius", 2.0);
        Location base = holder.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.2f);
        int points = 24;
        for (int i = 0; i < points; i++) {
            double ang = 2 * Math.PI * i / points;
            double dx = Math.cos(ang) * r;
            double dz = Math.sin(ang) * r;
            holder.getWorld().spawnParticle(Particle.DUST, base.clone().add(dx, 0.15, dz), 1, 0, 0, 0, 0, dust);
            holder.getWorld().spawnParticle(Particle.DUST, base.clone().add(dx, 1.0, dz), 1, 0, 0, 0, 0, dust);
        }
        List<Entity> near = new ArrayList<>(holder.getNearbyEntities(r, 2.0, r));
        for (Entity en : near) {
            if (!(en instanceof LivingEntity le) || en instanceof ArmorStand || en.equals(holder)) continue;
            if (le instanceof Player other
                    && (other.getGameMode() == GameMode.CREATIVE || other.getGameMode() == GameMode.SPECTATOR)) continue;
            double dx = en.getLocation().getX() - base.getX();
            double dz = en.getLocation().getZ() - base.getZ();
            if (dx * dx + dz * dz > r * r) continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, amp("fugu.poison-level")));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, amp("fugu.slow-level")));
        }
    }

    private List<Mod> mods(SphereType t) {
        return switch (t) {
            case POSEIDON -> List.of(
                    new Mod(Attribute.ARMOR, "armor", cfg().getDouble("poseidon.armor-bonus", 4)),
                    new Mod(Attribute.MAX_HEALTH, "health", -cfg().getDouble("poseidon.health-loss", 4)));
            case HADES -> List.of(new Mod(Attribute.ARMOR, "armor", -cfg().getDouble("hades.armor-loss", 3)));
            case ZEUS -> List.of(new Mod(Attribute.ARMOR, "armor", -cfg().getDouble("zeus.armor-loss", 2)));
            case HERMES -> List.of(new Mod(Attribute.MAX_HEALTH, "health", -cfg().getDouble("hermes.health-loss", 6)));
            case ATHENA -> List.of(new Mod(Attribute.ATTACK_DAMAGE, "attack", -cfg().getDouble("athena.attack-loss", 2)));
            default -> List.of();
        };
    }

    private void applyMods(Player p, SphereType t, boolean active) {
        for (Mod m : mods(t)) {
            AttributeInstance inst = p.getAttribute(m.attribute());
            if (inst == null) continue;
            NamespacedKey k = new NamespacedKey(plugin, "sphere_" + t.id + "_" + m.name());
            AttributeModifier current = null;
            for (AttributeModifier am : inst.getModifiers()) {
                if (k.equals(am.getKey())) { current = am; break; }
            }
            if (active) {
                if (current != null && current.getAmount() == m.amount()) continue;
                if (current != null) inst.removeModifier(current);
                inst.addTransientModifier(new AttributeModifier(k, m.amount(),
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            } else if (current != null) {
                inst.removeModifier(current);
            }
        }
    }
}
