package com.keremyurekli.smokes;

import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import javax.swing.*;
import java.util.*;

import java.util.stream.Collectors;

public class SmokeManager {

    public static float voxelSize = 0.5f;
    public static int voxelsPerTick = 150;


    private static final Set<BlockDisplay> disappearing =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final Map<BlockDisplay, Transformation> temporarilyHidden =
            new WeakHashMap<>();

    // power = radius
    public static void createSmokeAt(Location loc, float power, boolean colorMode, UUID uuid) {
        World world = loc.getWorld();

        List<Voxel> voxels = generateVoxels(power);
        List<BlockDisplay> smokeVoxels = new ArrayList<>(voxels.size());

        new BukkitRunnable() {

            private int index = 0;

            private final List<Location> failedVoxels = new ArrayList<>();
            private final List<Location> occupiedVoxels = new ArrayList<>();

            @Override
            public void run() {
                int spawned = 0;

                while (index < voxels.size() && spawned < voxelsPerTick) {
                    Voxel voxel = voxels.get(index++);

                    Location center = loc.clone().add(
                            voxel.x * voxelSize,
                            voxel.y * voxelSize,
                            voxel.z * voxelSize
                    );

                    if (!isVoxelValid(center, loc) || !world.getBlockAt(center).isPassable()) {
                        failedVoxels.add(center);
                        continue;
                    }

                    BlockDisplay display = spawnSmokeVoxel(world, center);

                    occupiedVoxels.add(center);
                    smokeVoxels.add(display);
                    spawned++;
                }

                if (index >= voxels.size()) {
                    cancel();

                    if (!failedVoxels.isEmpty()) {
                        List<Location> fillLocations = fill(
                                occupiedVoxels,
                                failedVoxels.size(),
                                loc
                        );

                        spawnFilledVoxels(
                                fillLocations,
                                smokeVoxels,
                                uuid
                        );
                    } else {
                        startSmokeLifecycle(smokeVoxels, uuid);
                    }
                }
            }

        }.runTaskTimer(Smokes.PLUGIN, 0L, 1L);
    }

    public static void temporarilyDisappear(BlockDisplay item, long duration) {
        if (!item.isValid() || disappearing.contains(item)) {
            return;
        }

        // Already temporarily hidden
        if (temporarilyHidden.containsKey(item)) {
            return;
        }

        Transformation original = item.getTransformation();

        Transformation saved = new Transformation(
                new Vector3f(original.getTranslation()),
                original.getLeftRotation(),
                new Vector3f(original.getScale()),
                original.getRightRotation()
        );

        temporarilyHidden.put(item, saved);

        Vector3f scale = original.getScale();

        Vector3f center = new Vector3f(
                original.getTranslation().x + scale.x / 2f,
                original.getTranslation().y + scale.y / 2f,
                original.getTranslation().z + scale.z / 2f
        );

        item.setInterpolationDuration(10);
        item.setInterpolationDelay(0);

        item.setTransformation(
                new Transformation(
                        center,
                        original.getLeftRotation(),
                        new Vector3f(),
                        original.getRightRotation()
                )
        );

        Bukkit.getScheduler().runTaskLater(
                Smokes.PLUGIN,
                () -> {

                    // Permanent fade won.
                    if (disappearing.contains(item)) {
                        temporarilyHidden.remove(item);
                        return;
                    }

                    if (!item.isValid()) {
                        temporarilyHidden.remove(item);
                        return;
                    }

                    Transformation restore = temporarilyHidden.remove(item);

                    if (restore == null) {
                        return;
                    }

                    item.setInterpolationDuration(10);
                    item.setInterpolationDelay(0);
                    item.setTransformation(restore);

                },
                duration
        );
    }

    private static List<Voxel> generateVoxels(float power) {
        double voxelRadius = power / voxelSize;
        double radiusSquared = voxelRadius * voxelRadius;
        int max = (int) Math.ceil(voxelRadius);

        List<Voxel> voxels = new ArrayList<>();

        for (int y = -max; y <= max; y++) {
            for (int x = -max; x <= max; x++) {
                for (int z = -max; z <= max; z++) {

                    double distanceSquared =
                            x * x +
                                    y * y +
                                    z * z;

                    if (distanceSquared > radiusSquared) {
                        continue;
                    }

                    voxels.add(new Voxel(
                            x,
                            y,
                            z,
                            distanceSquared
                    ));
                }
            }
        }

        // Spawn from the center outward.
        voxels.sort(Comparator.comparingDouble(v -> v.distanceSquared));

        return voxels;
    }


    private static BlockDisplay spawnSmokeVoxel(World world, Location location) {
        return world.spawn(
                location,
                BlockDisplay.class,
                entity -> {
                    entity.setBlock(
                            Material.LIGHT_GRAY_STAINED_GLASS.createBlockData()
                    );

                    entity.setPersistent(false);
                    entity.setRotation(0, 0);

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
                    PersistentDataContainer pers = entity.getPersistentDataContainer();
                    pers.set(Smokes.blockDisplaySmoke, PersistentDataType.BOOLEAN, true);
                }
        );
    }


    private static void startSmokeLifecycle(List<BlockDisplay> smokeVoxels, UUID uuid) {
        List<BlockDisplay> outerShell = calculateOuterShell(smokeVoxels);

        Set<BlockDisplay> disappearing = new HashSet<>();

        startIdleAnimation(outerShell, disappearing);

        List<BlockDisplay> firstFades = new ArrayList<>();

        // First half starts disappearing.
        Bukkit.getScheduler().runTaskLater(
                Smokes.PLUGIN,
                () -> {
                    List<BlockDisplay> fading = getRandomHalf(smokeVoxels);
//
//                    fading.forEach(entity -> {
//                        entity.setGlowColorOverride(Color.AQUA);
//                        entity.setGlowing(true);
//                    });
                    makeDisappear(fading);

                    firstFades.addAll(fading);
                    smokeVoxels.removeAll(fading);
                },
                300L
        );

        // First half is now gone, remaining half starts disappearing.
        Bukkit.getScheduler().runTaskLater(
                Smokes.PLUGIN,
                () -> {
                    firstFades.forEach(Entity::remove);
                    firstFades.clear();

//                    smokeVoxels.forEach(entity -> {
//                        entity.setGlowColorOverride(Color.RED);
//                        entity.setGlowing(true);
//                    });
                    makeDisappear(smokeVoxels);
                },
                360L
        );

        // Remaining half is gone.
        Bukkit.getScheduler().runTaskLater(
                Smokes.PLUGIN,
                () -> {
                    smokeVoxels.forEach(Entity::remove);
                    smokeVoxels.clear();

                    GrenadeManager.getRidOfSlime(uuid);
                    //HERE
                },
                420L
        );
    }

    private static void startIdleAnimation(
            List<BlockDisplay> outerShell,
            Set<BlockDisplay> disappearing
    ) {

        BukkitTask idleTask = Bukkit.getScheduler().runTaskTimer(
                Smokes.PLUGIN,
                () -> {
                    List<BlockDisplay> randomItems = getRandomHalf(outerShell);
                    idleAnimation(randomItems, disappearing);
                },
                0L,
                40L
        );

        // Stop the repeating animation before the fade begins.
        Bukkit.getScheduler().runTaskLater(
                Smokes.PLUGIN,
                idleTask::cancel,
                300L
        );
    }


    private static List<BlockDisplay> getRandomHalf(List<BlockDisplay> source) {
        List<BlockDisplay> result = new ArrayList<>(source);

        Collections.shuffle(result);

        return result.subList(0, result.size() / 2);
    }


    private static void idleAnimation(
            List<BlockDisplay> voxels,
            Set<BlockDisplay> disappearing
    ) {
        voxels.forEach(item -> {

            if (!item.isValid()
                    || disappearing.contains(item)
                    || temporarilyHidden.containsKey(item)) {
                return;
            }
            item.setInterpolationDuration(20);
            item.setInterpolationDelay(0);

            Transformation transformation = item.getTransformation();

            Vector3f oldScale = transformation.getScale();

            // Preserve the current center.
            Vector3f center = new Vector3f(
                    transformation.getTranslation().x + oldScale.x / 2f,
                    transformation.getTranslation().y + oldScale.y / 2f,
                    transformation.getTranslation().z + oldScale.z / 2f
            );

            float factor = 0.8f;

            Vector3f newScale = new Vector3f(
                    voxelSize * factor,
                    voxelSize * factor,
                    voxelSize * factor
            );

            Vector3f newTranslation = new Vector3f(
                    center.x - newScale.x / 2f,
                    center.y - newScale.y / 2f,
                    center.z - newScale.z / 2f
            );

            item.setTransformation(
                    new Transformation(
                            newTranslation,
                            transformation.getLeftRotation(),
                            newScale,
                            transformation.getRightRotation()
                    )
            );

            // Return to normal size after 20 ticks.
            Bukkit.getScheduler().runTaskLater(
                    Smokes.PLUGIN,
                    () -> restoreVoxelSize(item, disappearing),
                    20L
            );
        });
    }


    private static void restoreVoxelSize(
            BlockDisplay item,
            Set<BlockDisplay> disappearing
    ) {
        if (!item.isValid()
                || disappearing.contains(item)
                || temporarilyHidden.containsKey(item)) {
            return;
        }


        item.setInterpolationDuration(20);
        item.setInterpolationDelay(0);

        Transformation transformation = item.getTransformation();

        Vector3f oldScale = transformation.getScale();

        Vector3f center = new Vector3f(
                transformation.getTranslation().x + oldScale.x / 2f,
                transformation.getTranslation().y + oldScale.y / 2f,
                transformation.getTranslation().z + oldScale.z / 2f
        );

        Vector3f newScale = new Vector3f(
                voxelSize,
                voxelSize,
                voxelSize
        );

        Vector3f newTranslation = new Vector3f(
                center.x - newScale.x / 2f,
                center.y - newScale.y / 2f,
                center.z - newScale.z / 2f
        );

        item.setTransformation(
                new Transformation(
                        newTranslation,
                        transformation.getLeftRotation(),
                        newScale,
                        transformation.getRightRotation()
                )
        );
    }

    private static void makeDisappear(List<BlockDisplay> voxels) {
        voxels.forEach(item -> {

            if (!item.isValid()) {
                return;
            }

            // Permanent fade has highest priority.
            disappearing.add(item);

            // Cancel any temporary-hide state.
            temporarilyHidden.remove(item);

            item.setInterpolationDuration(60);
            item.setInterpolationDelay(0);

            Transformation transformation = item.getTransformation();

            Vector3f scale = transformation.getScale();

            Vector3f center = new Vector3f(
                    transformation.getTranslation().x + scale.x / 2f,
                    transformation.getTranslation().y + scale.y / 2f,
                    transformation.getTranslation().z + scale.z / 2f
            );

            item.setTransformation(
                    new Transformation(
                            center,
                            transformation.getLeftRotation(),
                            new Vector3f(),
                            transformation.getRightRotation()
                    )
            );
        });
    }


    private static List<BlockDisplay> calculateOuterShell(List<BlockDisplay> smokeVoxels) {
        Set<Location> locations = smokeVoxels.stream()
                .map(BlockDisplay::getLocation)
                .collect(Collectors.toSet());

        List<BlockDisplay> outerShell = new ArrayList<>();

        for (BlockDisplay voxel : smokeVoxels) {
            for (Location neighbor : neighborsWithUp(voxel.getLocation())) {
                if (!locations.contains(neighbor)) {
                    outerShell.add(voxel);
                    break;
                }
            }
        }

        return outerShell;
    }



    private static final int MAX_SEARCH_STEPS = 32; // cap how far we'll wander to find an open spot

    private static List<Location> fill(
            List<Location> occupiedVoxels,
            int count,
            Location center
    ) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        Set<Location> occupiedSet = new HashSet<>(occupiedVoxels);

        Queue<Location> frontier = new ArrayDeque<>(occupiedVoxels);
        Set<Location> visited = new HashSet<>(occupiedVoxels);

        List<Location> candidates = new ArrayList<>();

        int steps = 0;

        while (!frontier.isEmpty()
                && candidates.size() < count
                && steps < MAX_SEARCH_STEPS) {

            int levelSize = frontier.size();

            for (int i = 0; i < levelSize && candidates.size() < count; i++) {
                Location current = frontier.poll();

                for (Location neighbor : neighbors(current)) {

                    if (visited.contains(neighbor)) {
                        continue;
                    }

                    visited.add(neighbor);

                    if (!occupiedSet.contains(neighbor)
                            && neighbor.getWorld().getBlockAt(neighbor).isPassable()
                            && isVoxelValid(neighbor, center)) {

                        occupiedSet.add(neighbor);
                        frontier.add(neighbor);

                        candidates.add(neighbor);

                        if (candidates.size() >= count) {
                            break;
                        }

                    } else if (occupiedSet.contains(neighbor)) {
                        frontier.add(neighbor);
                    }
                }
            }

            steps++;
        }

        candidates.sort(Comparator.comparingDouble(
                location -> location.distanceSquared(center)
        ));

        return candidates;
    }

    private static void spawnFilledVoxels(
            List<Location> locations,
            List<BlockDisplay> smokeVoxels,
            UUID uuid
    ) {
        new BukkitRunnable() {

            private int index = 0;

            @Override
            public void run() {
                int spawned = 0;

                while (index < locations.size()
                        && spawned < voxelsPerTick) {

                    Location location = locations.get(index++);

                    BlockDisplay display = spawnSmokeVoxel(
                            location.getWorld(),
                            location
                    );

                    smokeVoxels.add(display);
                    spawned++;
                }

                if (index >= locations.size()) {
                    cancel();

                    startSmokeLifecycle(smokeVoxels, uuid);
                }
            }

        }.runTaskTimer(Smokes.PLUGIN, 0L, 1L);
    }


    private static List<Location> neighbors(Location loc) {
        return List.of(
                loc.clone().add(0, -voxelSize, 0),
                loc.clone().add(0, 0, voxelSize),
                loc.clone().add(0, 0, -voxelSize),
                loc.clone().add(voxelSize, 0, 0),
                loc.clone().add(-voxelSize, 0, 0)
//                loc.clone().add(0, voxelSize, 0),
        );
    }
    private static List<Location> neighborsWithUp(Location loc) {
        return List.of(
                loc.clone().add(0, -voxelSize, 0),
                loc.clone().add(0, 0, voxelSize),
                loc.clone().add(0, 0, -voxelSize),
                loc.clone().add(voxelSize, 0, 0),
                loc.clone().add(-voxelSize, 0, 0),
                loc.clone().add(0, voxelSize, 0)
        );
    }

    private static boolean isVoxelValid(Location voxelCenter, Location loc) {
        Vector direction = voxelCenter.toVector().subtract(loc.toVector());
        double distanceSquared = direction.lengthSquared();

        if (distanceSquared > 0) {
            double distance = Math.sqrt(distanceSquared);
            direction.normalize();

            RayTraceResult result = loc.getWorld().rayTraceBlocks(
                    loc,
                    direction,
                    distance,
                    FluidCollisionMode.NEVER,
                    true
            );


            if (result != null) {
                return false;
            }
        }

        return true;
    }

}
class Voxel {

    final int x;
    final int y;
    final int z;
    final double distanceSquared;
    public BlockDisplay display;

    Voxel(int x, int y, int z, double distanceSquared) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.distanceSquared = distanceSquared;
    }
}