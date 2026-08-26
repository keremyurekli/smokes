package com.keremyurekli.smokes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Smokes extends JavaPlugin {

    public static NamespacedKey grenadePowerKey;
    public static NamespacedKey dropNothingKey;
    public static NamespacedKey grenadeColorKey;
    public static NamespacedKey blockDisplaySmoke;


    public static Plugin PLUGIN;

    private static Logger logger;

    public static void log(Level level, String s) {
        logger.log(level, s);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        PLUGIN = this;
        logger = getLogger();
        //§
        log(Level.INFO,"Starting registeries!");

        grenadePowerKey = new NamespacedKey(this, "grenade_power");
        dropNothingKey = new NamespacedKey(this, "drop_nothing");
        grenadeColorKey = new NamespacedKey(this, "grenade_color");
        blockDisplaySmoke = new NamespacedKey(this, "smoke_display");

        getServer().getPluginManager().registerEvents(new ItemActionListener(), this);
        getServer().getPluginManager().registerEvents(new EntityEventListener(), this);

        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("smokes")
                .then(Commands.literal("get")
                        .then(Commands.argument("power", FloatArgumentType.floatArg())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) {
                                        sender.sendPlainMessage("This command is player only!");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    float power = FloatArgumentType.getFloat(ctx, "power");
                                    player.give(createGrenadeItem(power));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );

        LiteralCommandNode<CommandSourceStack> buildCommand = command.build();
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(buildCommand);
        });


    }
    public ItemStack createGrenadeItem(float power) {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Smoke");

            meta.setLore(List.of(
                    "§7A smoke grenade with power of "+Math.abs(power)
            ));

            meta.addEnchant(Enchantment.LOYALTY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(grenadePowerKey, PersistentDataType.FLOAT, Math.abs(power));
            data.set(grenadeColorKey, PersistentDataType.BOOLEAN, power > 0);

            item.setItemMeta(meta);
        }

        return item;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
