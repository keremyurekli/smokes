package com.keremyurekli.smokes;

import net.kyori.adventure.sound.Sound;
import org.bukkit.*;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GrenadeManager {

    public static HashMap<UUID, BukkitTask> entityTaskPairs = new HashMap<>();

    public static HashMap<UUID, List<Display>> entityDisplayPairs = new HashMap<>();

    public static void throwGrenade(Player player, float power, boolean rightClick, boolean colorMode) {
        World world = player.getWorld();

        world.playSound(
                player.getLocation(),
                "minecraft:item.flintandsteel.use",
                SoundCategory.BLOCKS,
                1.0f,
                0f
        );

        world.playSound(
                player.getLocation(),
                "minecraft:item.armor.equip_leather",
                SoundCategory.BLOCKS,
                1.0f,
                1f
        );

        Slime slime = world.spawn(player.getEyeLocation(), Slime.class, entity -> {
            entity.setSize(0);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setInvisible(true);

            PersistentDataContainer data = entity.getPersistentDataContainer();
            data.set(Smokes.dropNothingKey, PersistentDataType.BOOLEAN, true);

//            entity.setCollidable(false);
//
//            Bukkit.broadcastMessage(entity.getCollidableExemptions().contains(player.getUniqueId())+" ahb");
            entity.getCollidableExemptions().add(player.getUniqueId());
        });

        List<Display> displays = new ArrayList<>();


        Display test = world.spawn(slime.getLocation(), BlockDisplay.class, entity -> {
            entity.setBlock(Material.LIGHT_GRAY_WOOL.createBlockData());
            entity.setTransformation(
                    new Transformation(
                            new Vector3f(-0.125f,-0.5f,-0.125f), // no translation
                            new AxisAngle4f(), // no left rotation
                            new Vector3f(0.25f, 0.5f, 0.25f), // scale up by a factor of 2 on all axes
                            new AxisAngle4f() // no right rotation
                    )
            );

            entity.setRotation(entity.getYaw(),0);
            // or set a raw transformation matrix from JOML
            // entity.setTransformationMatrix(
            //         new Matrix4f()
            //                 .scale(2) // scale up by a factor of 2 on all axes
            // );
        });

        slime.addPassenger(test);
        displays.add(test);



        Vector velocity = player.getLocation().getDirection().multiply(rightClick ? 0.25f : 4f);

        velocity.setX(velocity.getX() + player.getVelocity().getX()*2);

        velocity.setY(velocity.getY() + player.getVelocity().getY());

        velocity.setZ(velocity.getZ() + player.getVelocity().getZ()*2);

        slime.setVelocity(velocity);

//        slime.getPathfinder().setCanFloat(true);

        float yaw = player.getYaw();
        float pitch = 0;
        BukkitTask tt = Bukkit.getServer().getScheduler().runTaskTimer(Smokes.PLUGIN, () -> {
            if (slime.isDead()) return;
            slime.setRotation(yaw, pitch);

            Vector v = slime.getVelocity();
//            slime.getPathfinder().stopPathfinding();
//            Bukkit.broadcastMessage("Speed is "+ velocity.getX() +" / "+velocity.getY() + " / " + velocity.getZ());
            if (v.getX() == 0 && v.getZ() == 0 && v.getY() == -0.0784000015258789) {

                float offset = grenadeRotationOnLand(entityDisplayPairs.get(slime.getUniqueId()),slime);

                Location smokePos = slime.getLocation().add(slime.getLocation().getDirection().multiply(offset));

                slime.teleportAsync(new Location(slime.getWorld(), 0, -100, 0)).thenAccept(success -> { // loads chunks asynchronously and teleports the entity
                    // this code is ran when the teleport completes
                    // the Future is completed on the main thread, so it is safe to use the API here

                    if (success) {
                        // the entity was teleported successfully!
                        slime.kill();
                    }
                });
                //maybe wait for a sec before popping
                Bukkit.getScheduler().runTaskLater(Smokes.PLUGIN, () -> {
                    SmokeManager.createSmokeAt(smokePos, power, colorMode, slime.getUniqueId());
                    world.playSound(
                            smokePos,
                            "minecraft:event.mob_effect.bad_omen",
                            SoundCategory.BLOCKS,
                            1.0f,
                            2.0f
                    );
                }, ThreadLocalRandom.current().nextInt(20, 30));



            } else {
                grenadeRotationOnAir(entityDisplayPairs.get(slime.getUniqueId()),slime);
            }

        },0,0);

        entityTaskPairs.put(slime.getUniqueId(),tt);
        entityDisplayPairs.put(slime.getUniqueId(),displays);

    }

    public static void getRidOfSlime(UUID id) {
        entityDisplayPairs.get(id).forEach(Entity::remove);
        entityDisplayPairs.remove(id);
    }

    private static void grenadeRotationOnAir(List<Display> displays, Entity parent) {
        displays.forEach(display -> {
            Transformation transformation = display.getTransformation();

            Quaternionf rotation = transformation.getLeftRotation();

            rotation.rotateX((float) Math.toRadians(10));

            display.setTransformation(
                    new Transformation(
                            transformation.getTranslation(),
                            rotation,
                            transformation.getScale(),
                            transformation.getRightRotation()
                    )
            );
        });
    }

    private static float grenadeRotationOnLand(List<Display> displays, Entity parent) {
        float[] offset = {0.125f};
        displays.forEach(display -> {


            Transformation transformation = display.getTransformation();

            Quaternionf current = new Quaternionf(transformation.getLeftRotation());

            Quaternionf positive90 = new Quaternionf()
                    .rotateX((float) Math.toRadians(90));

            Quaternionf negative90 = new Quaternionf()
                    .rotateX((float) Math.toRadians(-90));

// Quaternion dot product tells us how close the rotations are.
// Higher absolute dot = more similar rotation.
            float positiveDot = Math.abs(current.dot(positive90));
            float negativeDot = Math.abs(current.dot(negative90));

            Quaternionf target = positiveDot > negativeDot
                    ? positive90
                    : negative90;

//            Vector3f translation = positiveDot > negativeDot
//                    ? new Vector3f(0,0.25f,0)
//                    : transformation.getTranslation();

            if (positiveDot <= negativeDot) offset[0] = -0.375f;


            display.setInterpolationDuration(2);
            display.setInterpolationDelay(0);

            display.setTransformation(new Transformation(
                    transformation.getTranslation(),
                    target,
                    transformation.getScale(),
                    transformation.getRightRotation()
            ));



            parent.removePassenger(display);
            if (positiveDot > negativeDot) display.teleport(display.getLocation().add(0,0.25f,0));

//            display.teleport(display.getLocation().add(display.getLocation().getDirection().multiply(0.375f)));


        });

        return offset[0];
    }


//    private static void grenadeRotations(List<Display> displays, Entity parent)
//    {
//        displays.forEach(display -> {
//
//            float yawToAdd = 0;
//            float pitchToAdd = 0;
//
//            Vector parentSpeed = parent.getVelocity();
//            double horizontalMagnitude = new Vector(parentSpeed.getX(), 0, parentSpeed.getZ()).lengthSquared();
//            double verticalMagnitude = new Vector(0, parentSpeed.getY(), 0).lengthSquared();
//
//
////            yawToAdd = (float) (verticalMagnitude * 30);
//            pitchToAdd = (float) (horizontalMagnitude * 30);
//
//            float spin = (float) parent.getVelocity().length() * 10f;
//
//            display.setRotation(
//                    display.getYaw(),
//                    display.getPitch() + spin
//            );
//
////            display.setRotation(display.getYaw() + yawToAdd, display.getPitch() + pitchToAdd);
//
//
//        });
//    }
}
