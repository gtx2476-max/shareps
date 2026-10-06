package ru.spheres;

import org.bukkit.Material;

import java.util.List;

/**
 * Все типы сфер. Крафт любой сферы (кроме EMPTY):
 *   C E C
 *   E S E     S = пустая сфера, C = corner, E = edge
 *   C E C
 * Крафт EMPTY: 9 голов игроков. Лор поддерживает {путь.из.config} — подставится значение из config.yml.
 */
public enum SphereType {
    EMPTY("empty", "&fПустая сфера", "MHF_Question", Material.PLAYER_HEAD, Material.PLAYER_HEAD,
            "&7Основа для всех сфер.", "", "&7Крафт: &f9 голов игроков", "&8Головы выпадают при убийстве игрока"),

    FUGU("fugu", "&aСфера Фугу", "MHF_Slime", Material.SPIDER_EYE, Material.PUFFERFISH,
            "&7В руке: вокруг тебя зелёный круг", "&7радиусом &f{fugu.radius} &7блока.", "",
            "&aВраги в круге получают:", "&c✘ Отравление {fugu.poison-level}", "&c✘ Замедление {fugu.slow-level}"),

    POSEIDON("poseidon", "&bСфера Посейдона", "MHF_Squid", Material.NAUTILUS_SHELL, Material.PRISMARINE_CRYSTALS,
            "&7В руке:", "&a✔ Подводное дыхание", "&a✔ Броня +{poseidon.armor-bonus}",
            "&c✘ Здоровье -{poseidon.health-loss}", "",
            "&7Удар: &f{poseidon.chance}% &7шанс замедлить врага"),

    HADES("hades", "&cСфера Аида", "MHF_LavaSlime", Material.BLAZE_POWDER, Material.MAGMA_BLOCK,
            "&7В руке:", "&a✔ Удары, стрелы и меч поджигают врагов", "&a✔ Скорость {hades.speed-level}",
            "&a✔ Огнеупорность", "&c✘ Броня -{hades.armor-loss}", "",
            "&7Удар: &f{hades.chance}% &7шанс — большой фаербол во врага"),

    ZEUS("zeus", "&eСфера Зевса", "MHF_Ghast", Material.GOLD_INGOT, Material.LIGHTNING_ROD,
            "&7В руке:", "&a✔ Спешка {zeus.haste-level}", "&c✘ Броня -{zeus.armor-loss}", "",
            "&7Удар мечом: &f{zeus.chance}% &7шанс — молния во врага"),

    HERMES("hermes", "&bСфера Гермеса", "MHF_Chicken", Material.SUGAR, Material.FEATHER,
            "&7В руке:", "&a✔ Скорость {hermes.speed-level}", "&a✔ Прыгучесть {hermes.jump-level}",
            "&c✘ Здоровье -{hermes.health-loss}"),

    ATHENA("athena", "&9Сфера Афины", "MHF_Golem", Material.DIAMOND, Material.IRON_BLOCK,
            "&7В руке:", "&a✔ Сопротивление урону {athena.resistance-level}", "&c✘ Урон -{athena.attack-loss}");

    public final String id;
    public final String displayName;
    public final String fallbackHead;
    public final Material corner;
    public final Material edge;
    public final List<String> lore;

    SphereType(String id, String displayName, String fallbackHead, Material corner, Material edge, String... lore) {
        this.id = id;
        this.displayName = displayName;
        this.fallbackHead = fallbackHead;
        this.corner = corner;
        this.edge = edge;
        this.lore = List.of(lore);
    }

    public static SphereType byId(String id) {
        for (SphereType t : values()) if (t.id.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) return t;
        return null;
    }
}
