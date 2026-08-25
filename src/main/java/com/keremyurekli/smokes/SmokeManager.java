package com.keremyurekli.smokes;

import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.*;

public class SmokeManager {

    public static float voxelSize = 0.5f;
    public static int voxelsPerTick = 150;

    public static void createSmokeAt(Location loc, float power, boolean colorMode) {

        World world = loc.getWorld();

        double voxelRadius = power / voxelSize;
        double radiusSquared = voxelRadius * voxelRadius;

        int max = (int) Math.ceil(voxelRadius);

        List<Voxel> voxels = new ArrayList<>();

        // Generate voxel offsets
        for (int y = -max; y <= max; y++) {
            for (int x = -max; x <= max; x++) {
                for (int z = -max; z <= max; z++) {

                    double distanceSquared =
                            x * x +
                                    y * y +
                                    z * z;

                    if (distanceSquared > radiusSquared)
                        continue;

                    voxels.add(new Voxel(
                            x,
                            y,
                            z,
                            distanceSquared
                    ));
                }
            }
        }

        // Center → outside
        voxels.sort(Comparator.comparingDouble(v -> v.distanceSquared));

        List<BlockDisplay> smokeVoxels = new ArrayList<>(voxels.size());


        new BukkitRunnable() {

            private int index = 0;

            private int extraVoxels;

            @Override
            public void run() {

                int spawned = 0;

                while (index < voxels.size() && spawned < voxelsPerTick) {

                    Voxel voxel = voxels.get(index++);

                    Location voxelCenter = loc.clone().add(
                            voxel.x * voxelSize,
                            voxel.y * voxelSize,
                            voxel.z * voxelSize
                    );
                    Vector direction = voxelCenter.toVector().subtract(loc.toVector());
                    double distanceSquared = direction.lengthSquared();

                    if (distanceSquared > 0) {
                        double distance = Math.sqrt(distanceSquared);
                        direction.normalize();

                        RayTraceResult result = world.rayTraceBlocks(
                                loc,
                                direction,
                                distance,
                                FluidCollisionMode.NEVER,
                                true
                        );


                        if (result != null) {
                            extraVoxels++;
                            continue;
                        }
                    }

                    BlockDisplay display = world.spawn(
                            voxelCenter,
                            BlockDisplay.class,
                            entity -> {
                                entity.setBlock(
                                        Material.LIGHT_GRAY_STAINED_GLASS.createBlockData()
                                );

                                entity.setPersistent(false);

                                entity.setTransformation(
                                        new Transformation(
                                                new Vector3f(),
                                                new AxisAngle4f(),
                                                new Vector3f(
                                                        voxelSize,
                                                        voxelSize,
                                                        voxelSize
                                                ),
                                                new AxisAngle4f()
                                        )
                                );
                                entity.setRotation(0,0);
                            }
                    );

                    smokeVoxels.add(display);
                    spawned++;
                }

                if (index >= voxels.size()) {

                    cancel();

                    Bukkit.getScheduler().runTaskLater(
                            Smokes.PLUGIN,
                            () -> smokeVoxels.forEach(Entity::remove),
                            300
                    );
                }
            }
        }.runTaskTimer(Smokes.PLUGIN, 0L, 1L);

    }

}
class Voxel {

    final int x;
    final int y;
    final int z;
    final double distanceSquared;

    Voxel(int x, int y, int z, double distanceSquared) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.distanceSquared = distanceSquared;
    }
}