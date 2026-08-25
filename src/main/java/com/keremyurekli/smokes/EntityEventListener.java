package com.keremyurekli.smokes;

import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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


}
