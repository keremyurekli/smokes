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

import java.util.Stack;
import java.util.UUID;

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


//    @EventHandler
//    public void onProjectileLaunch(ProjectileLaunchEvent event) {
//        Projectile original = event.getEntity();
//
//        // 1. Prevent infinite recursion: check if this is one of our spawned clones
//        if (original.getScoreboardTags().contains("multishot_clone")) {
//            return;
//        }
//
//        new SmokeProjectileDetector(original);
//
//        if (original.getShooter() != null) {
//            Vector velocity = original.getVelocity();
//            Vector direction = velocity.clone().normalize();
//
//            // 2. Calculate local axes perpendicular to the arrow's flight path
//            Vector globalUp = new Vector(0, 1, 0);
//            Vector right = direction.clone().crossProduct(globalUp);
//
//            // Handle edge case: if the arrow is shot perfectly straight up or down
//            if (right.lengthSquared() < 0.0001) {
//                right = new Vector(1, 0, 0);
//            } else {
//                right.normalize();
//            }
//
//            // The real "up" relative to the arrow's rotation
//            Vector localUp = right.clone().crossProduct(direction).normalize();
//
//            // 3. Define how far apart the arrows should be (in blocks)
//            double spacing = 0.5;
//
//            // 4. Create a 5x5 grid (x from -2 to 2, y from -2 to 2)
//            for (int x = -2; x <= 2; x++) {
//                for (int y = -2; y <= 2; y++) {
//
//                    // Skip (0,0) because the original arrow is already there
//                    if (x == 0 && y == 0) continue;
//
//                    // Calculate the offset location for this specific grid coordinate
//                    Vector offset = right.clone().multiply(x * spacing).add(localUp.clone().multiply(y * spacing));
//                    Location spawnLoc = original.getLocation().add(offset);
//
//                    // Spawn the new arrow
//                    Projectile newArrow = (Projectile) original.getWorld().spawnEntity(spawnLoc, original.getType());
//
//                    // Copy velocity and shooter from the original arrow
//                    newArrow.setVelocity(velocity);
//                    newArrow.setShooter(original.getShooter());
//
//                    // Tag the new arrow so it doesn't trigger this event again
//                    newArrow.addScoreboardTag("multishot_clone");
//                }
//            }
//        }
//    }



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
                        30L,
                        5,
                        30
                );
            }
        });
    }
}
