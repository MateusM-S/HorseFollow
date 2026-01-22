package com.horsefollow;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.Timer;
import java.util.TimerTask;

public final class HorseFollowPlugin extends JavaPlugin {

    private final FollowService service;
    private Timer timer;

    public HorseFollowPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.service = new FollowService();
    }

    @Override
    public void setup() {
        CommandRegistry reg = getCommandRegistry();
        reg.registerCommand(new com.horsefollow.commands.HorseFollowCommand(service));

        // 10 ticks/s (100ms).
        timer = new Timer("HorseFollow-Tick", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                service.tick();
            }
        }, 250L, 100L);
    }

    @Override
    public void shutdown() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
