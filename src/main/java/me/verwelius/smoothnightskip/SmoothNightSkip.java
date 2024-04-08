package me.verwelius.smoothnightskip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Predicate;

public final class SmoothNightSkip extends JavaPlugin implements Listener {

    private World world;
    private int skippingSpeed;
    private Component onBedEnter;
    private Component onBedLeave;
    private int messagesTimeInMs;

    private long sinceWhen = 0;
    private Component actionbar;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();

        FileConfiguration config = getConfig();
        world = Bukkit.getWorld(config.getString("world-name", "world"));
        skippingSpeed = config.getInt("skipping-speed", 100);

        onBedEnter = MiniMessage.miniMessage().deserialize(
                config.getString("on-bed-enter", "")
        );
        onBedLeave = MiniMessage.miniMessage().deserialize(
                config.getString("on-bed-leave", "")
        );

        messagesTimeInMs = config.getInt("messages-time-in-ms", 1000);

        world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long sleeping = getSleeping(world).size();
            long players = getPlayers(world).size();
            if(players == 0) return;

            world.setTime(world.getTime() + (skippingSpeed * sleeping / players));

        }, 0, 1);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if(System.currentTimeMillis() - sinceWhen > messagesTimeInMs) return;

            getPlayers(world).forEach(p -> p.sendActionBar(actionbar));
        }, 0, 1);

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onBedEnter(PlayerBedEnterEvent event) {
        if(event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Bukkit.getScheduler().runTask(this, () -> {
            setActionbar(apply(onBedEnter, event.getPlayer().getName(),
                    getSleeping(world).size(), getPlayers(world).size()));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onBedLeave(PlayerBedLeaveEvent event) {
        Bukkit.getScheduler().runTask(this, () -> {
            setActionbar(apply(onBedLeave, event.getPlayer().getName(),
                    getSleeping(world).size(), getPlayers(world).size()));
        });
    }

    private List<Player> getSleeping(World world) {
        return getPlayers(world).stream()
                .filter(((Predicate<Player>) LivingEntity::isSleeping)
                .or(Player::isSleepingIgnored)).toList();
    }

    private List<Player> getPlayers(World world) {
        return world.getPlayers().stream().filter(p -> p.getGameMode() != GameMode.SPECTATOR).toList();
    }

    private Component apply(Component text, String name, int sleeping, int players) {
        return text
                .replaceText(config -> config.match("%name%").replacement(name))
                .replaceText(config -> config.match("%sleeping%").replacement(String.valueOf(sleeping)))
                .replaceText(config -> config.match("%players%").replacement(String.valueOf(players)));
    }

    private void setActionbar(Component text) {
        sinceWhen = System.currentTimeMillis();
        actionbar = text;
    }

}
