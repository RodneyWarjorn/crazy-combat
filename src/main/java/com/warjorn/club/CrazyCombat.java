package com.warjorn.club;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class CrazyCombat extends JavaPlugin implements Listener {

    private static final String MODIFIER_NAMESPACE = "crazycombat";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CrazyCombat enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CrazyCombat disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateSpeed(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        updateSpeed(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            int slot = event.getSlot();
            // Armor slots: 36 boots, 37 leggings, 38 chest, 39 helmet
            if (slot >= 36 && slot <= 39) {
                // Schedule update after the event
                getServer().getScheduler().runTask(this, () -> updateSpeed(player));
            }
        }
    }

    private void updateSpeed(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;

        // Remove existing modifiers
        attr.getModifiers().removeIf(mod -> MODIFIER_NAMESPACE.equals(mod.getName()));

        PlayerInventory inv = player.getInventory();
        double totalSlowdown = 0.0;

        // Check each armor piece
        ItemStack[] armor = {inv.getBoots(), inv.getLeggings(), inv.getChestplate(), inv.getHelmet()};
        for (ItemStack item : armor) {
            if (item != null) {
                Material mat = item.getType();
                if (mat.name().contains("LEATHER")) {
                    totalSlowdown += 0.01; // Minimal slowdown
                } else if (mat.name().contains("CHAINMAIL")) {
                    totalSlowdown += 0.05; // Considerable
                } else if (mat.name().contains("IRON")) {
                    totalSlowdown += 0.05; // Considerable
                } else if (mat.name().contains("DIAMOND")) {
                    totalSlowdown += 0.08; // Full slowdown
                }
            }
        }

        if (totalSlowdown > 0) {
            AttributeModifier mod = new AttributeModifier(MODIFIER_NAMESPACE, -totalSlowdown, AttributeModifier.Operation.ADD_NUMBER);
            attr.addModifier(mod);
        }
    }
}