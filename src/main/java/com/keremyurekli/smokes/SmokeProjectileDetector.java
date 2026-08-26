package com.keremyurekli.smokes;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class SmokeProjectileDetector {

    private final Set<BlockDisplay> alreadyHit = new HashSet<>();

    private final Projectile projectile;
    private final BukkitTask task;

    private Location previousLocation;

    public SmokeProjectileDetector(Projectile projectile) {
        this.projectile = projectile;
        this.previousLocation = projectile.getLocation().clone();

        this.task = run();
    }

    private BukkitTask run() {
        return Smokes.PLUGIN.getServer().getScheduler().runTaskTimer(
                Smokes.PLUGIN,
                () -> {

                    if (!projectile.isValid() || projectile.isDead()) {
                        stop();
                        return;
                    }

                    Location current = projectile.getLocation();

                    checkTrajectory(previousLocation, current);

                    previousLocation = current.clone();
                },
                0L,
                1L
        );
    }

    private void checkTrajectory(Location start, Location end) {

        Vector movement = end.toVector()
                .subtract(start.toVector());

        double distance = movement.length();

        if (distance <= 0) {
            return;
        }

        double step = 0.25;

        int samples = Math.max(
                1,
                (int) Math.ceil(distance / step)
        );

        for (int i = 0; i <= samples; i++) {

            double t = (double) i / samples;

            Location sample = start.clone().add(
                    movement.clone().multiply(t)
            );

            for (Entity entity : sample.getNearbyEntities(
                    0.5,
                    0.5,
                    0.5
            )) {

                if (!(entity instanceof BlockDisplay display)) {
                    continue;
                }

                if (!display.getPersistentDataContainer().has(Smokes.blockDisplaySmoke)) {
                    continue;
                }

                if (!alreadyHit.add(display)) {
                    continue;
                }

                SmokeManager.temporarilyDisappear(
                        display,
                        20L,
                        5,
                        30
                );
            }
        }
    }

    public void stop() {
        task.cancel();
        alreadyHit.clear();
    }
}