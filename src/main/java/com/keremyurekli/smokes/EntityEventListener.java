package com.keremyurekli.smokes;

import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class EntityEventListener implements Listener {


    @EventHandler
    public void entityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Slime slime
                && slime.getPersistentDataContainer().has(Smokes.dropNothingKey)) {
            event.getDrops().clear();
            event.setDroppedExp(0);

            GrenadeManager.entityTaskPairs.get(slime.getUniqueId()).cancel();
            GrenadeManager.entityTaskPairs.remove(slime.getUniqueId());




//            Bukkit.broadcastMessage("Our slime is dead!");
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        new SmokeProjectileDetector(event.getEntity());
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {

        Location center = event.getLocation();

        double radius = 0;

        for (Block block : event.blockList()) {
            double distance = block.getLocation().add(0.5, 0.5, 0.5)
                    .distance(center);

            radius = Math.max(radius, distance);
        }

        double radiusSquared = radius * radius;

        center.getWorld().getNearbyEntities(
                center,
                radius,
                radius,
                radius,
                entity -> entity instanceof BlockDisplay
        ).forEach(entity -> {

            if (entity.getLocation().distanceSquared(center) <= radiusSquared && entity.getPersistentDataContainer().has(Smokes.blockDisplaySmoke)) {
                SmokeManager.temporarilyDisappear(
                        (BlockDisplay) entity,
                        20L
                );
            }
        });
    }
}
