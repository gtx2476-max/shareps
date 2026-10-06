package ru.spheres;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class SpheresPlugin extends JavaPlugin {

    private static SpheresPlugin instance;
    private SphereItems items;
    private SpheresManager manager;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        items = new SphereItems(this);
        registerRecipes();
        manager = new SpheresManager(this, items);
        manager.start();

        getServer().getPluginManager().registerEvents(new SphereListener(this, items, manager), this);
        SphereMenu menu = new SphereMenu(this, items);
        getServer().getPluginManager().registerEvents(menu, this);

        PluginCommand cmd = getCommand("spheres");
        if (cmd != null) {
            SpheresCommand h = new SpheresCommand(this, items, menu);
            cmd.setExecutor(h);
            cmd.setTabCompleter(h);
        }
        getLogger().info("Spheres включен.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
    }

    private void registerRecipes() {
        for (SphereType t : SphereType.values()) {
            NamespacedKey k = new NamespacedKey(this, "recipe_" + t.id);
            ShapedRecipe r = new ShapedRecipe(k, items.create(t));
            if (t == SphereType.EMPTY) {
                r.shape("HHH", "HHH", "HHH");
                r.setIngredient('H', new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
            } else {
                r.shape("CEC", "ESE", "CEC");
                r.setIngredient('C', new RecipeChoice.MaterialChoice(t.corner));
                r.setIngredient('E', new RecipeChoice.MaterialChoice(t.edge));
                r.setIngredient('S', new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
            }
            Bukkit.addRecipe(r);
            recipeKeys.add(k);
        }
    }

    public List<NamespacedKey> getRecipeKeys() { return recipeKeys; }

    public static SpheresPlugin getInstance() { return instance; }
    public SphereItems getItems() { return items; }
    public SpheresManager getManager() { return manager; }
}
