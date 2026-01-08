import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        UUID victimId = victim.getUniqueId();
        UUID killerId = killer.getUniqueId();

        killCounts.putIfAbsent(victimId, new HashMap<>());
        Map<UUID, Integer> victimMap = killCounts.get(victimId);
        int count = victimMap.getOrDefault(killerId, 0) + 1;
        victimMap.put(killerId, count);

        if (count >= maxKills) {
            String pairKey = getPairKey(victimId, killerId);
            mutualCooldowns.put(pairKey, System.currentTimeMillis() + cooldownMillis);

            killer.sendMessage("You can no longer attack " + victim.getName() + " for a while!");
            victim.sendMessage("You can no longer attack " + killer.getName() + " for a while!");

            // Optionally, schedule cleanup
            new BukkitRunnable() {
                @Override
                public void run() {
                    mutualCooldowns.remove(pairKey);
                    // Reset kill counts for this pair
                    killCounts.getOrDefault(victimId, new HashMap<>()).remove(killerId);
                    killCounts.getOrDefault(killerId, new HashMap<>()).remove(victimId);
                }
            }.runTaskLater(this, cooldownMillis / 50); // 20 ticks = 1 sec
        }
    }
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.EventPriority;
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        UUID victimId = victim.getUniqueId();
        UUID attackerId = attacker.getUniqueId();

        String pairKey = getPairKey(victimId, attackerId);
        Long until = mutualCooldowns.get(pairKey);
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
            attacker.sendMessage("You cannot attack " + victim.getName() + " right now!");
            victim.sendMessage("You cannot attack " + attacker.getName() + " right now!");
        }
    }

    private String getPairKey(UUID a, UUID b) {
        // Ensure mutual: order doesn't matter
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
package com.warjorn.club;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class CrazyCombat extends JavaPlugin implements Listener {

    private static final String MODIFIER_NAMESPACE = "crazycombat";

    // Track kills: Map<victim, Map<attacker, count>>
    private final Map<UUID, Map<UUID, Integer>> killCounts = new HashMap<>();
    // Track mutual cooldowns: Map<UUID_pair_string, cooldown_end_time>
    private final Map<String, Long> mutualCooldowns = new HashMap<>();

    private int maxKills = 2; // default
    private long cooldownMillis = 5 * 60 * 1000; // 5 minutes default

    // Configurable sweeping attack
    private boolean sweepingEnabled = true;
    private double sweepingRadius = 1.5;
    private double sweepingDamageMultiplier = 0.5;
    private String sweepingParticle = "SWEEP_ATTACK";

    // Configurable encumbrance
    private double encumbranceLeather = 0.01;
    private double encumbranceChainmail = 0.05;
    private double encumbranceIron = 0.05;
    private double encumbranceDiamond = 0.08;
    private double encumbranceShield = 0.03;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        maxKills = getConfig().getInt("max_kills", 3);
        cooldownMillis = getConfig().getLong("cooldown_ms", 5 * 60 * 1000);
        sweepingEnabled = getConfig().getBoolean("sweeping.enabled", true);
        sweepingRadius = getConfig().getDouble("sweeping.radius", 1.5);
        sweepingDamageMultiplier = getConfig().getDouble("sweeping.damage_multiplier", 0.5);
        sweepingParticle = getConfig().getString("sweeping.particle", "SWEEP_ATTACK");
        encumbranceLeather = getConfig().getDouble("encumbrance.leather", 0.01);
        encumbranceChainmail = getConfig().getDouble("encumbrance.chainmail", 0.05);
        encumbranceIron = getConfig().getDouble("encumbrance.iron", 0.05);
        encumbranceDiamond = getConfig().getDouble("encumbrance.diamond", 0.08);
        encumbranceShield = getConfig().getDouble("encumbrance.shield", 0.03);
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
            // Armor slots: 36 boots, 37 leggings, 38 chest, 39 helmet, 40 off-hand
            if ((slot >= 36 && slot <= 39) || slot == 40) {
                // Schedule update after the event
                getServer().getScheduler().runTask(this, () -> updateSpeed(player));
            }
        }
    }

    @EventHandler
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item != null && item.getType().name().endsWith("_SWORD")) {
                performSweepingAttack(player, item);
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
                    totalSlowdown += encumbranceLeather;
                } else if (mat.name().contains("CHAINMAIL")) {
                    totalSlowdown += encumbranceChainmail;
                } else if (mat.name().contains("IRON")) {
                    totalSlowdown += encumbranceIron;
                } else if (mat.name().contains("DIAMOND")) {
                    totalSlowdown += encumbranceDiamond;
                }
            }
        }

        // Check for shield in off-hand
        ItemStack offHand = inv.getItemInOffHand();
        if (offHand != null && offHand.getType() == Material.SHIELD) {
            totalSlowdown += encumbranceShield;
        }

        if (totalSlowdown > 0) {
            AttributeModifier mod = new AttributeModifier(UUID.nameUUIDFromBytes(MODIFIER_NAMESPACE.getBytes()), MODIFIER_NAMESPACE, -totalSlowdown, AttributeModifier.Operation.ADD_NUMBER);
            attr.addModifier(mod);
        }
    }

    private void performSweepingAttack(Player player, ItemStack sword) {
        if (!sweepingEnabled) return;
        double damage = getSwordDamage(sword) * sweepingDamageMultiplier;
        Location loc = player.getLocation();
        World world = player.getWorld();
        for (Entity entity : player.getNearbyEntities(sweepingRadius, sweepingRadius, sweepingRadius)) {
            if (entity instanceof LivingEntity && entity != player) {
                ((LivingEntity) entity).damage(damage, player);
            }
        }
        try {
            Particle particle = Particle.valueOf(sweepingParticle);
            world.spawnParticle(particle, loc, 1);
        } catch (IllegalArgumentException e) {
            world.spawnParticle(Particle.SWEEP_ATTACK, loc, 1); // fallback
        }
    }

    private double getSwordDamage(ItemStack item) {
        switch (item.getType()) {
            case WOODEN_SWORD: return 4.0;
            case STONE_SWORD: return 5.0;
            case IRON_SWORD: return 6.0;
            case DIAMOND_SWORD: return 7.0;
            case NETHERITE_SWORD: return 8.0;
            case GOLDEN_SWORD: return 4.0;
            default: return 1.0;
        }
    }
}
