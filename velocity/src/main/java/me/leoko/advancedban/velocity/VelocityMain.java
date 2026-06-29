package me.leoko.advancedban.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.leoko.advancedban.Universal;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "advancedban",
        name = "AdvancedBan",
        version = "2026.06.29.4",
        description = "Modernized AdvancedBan with Bukkit, BungeeCord and Velocity support",
        authors = {"Leoko", "siberanka"}
)
public class VelocityMain {
    private static VelocityMain instance;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public VelocityMain(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    public static VelocityMain get() {
        return instance;
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Universal.get().setup(new VelocityMethods(this));
        proxy.getEventManager().register(this, new VelocityListener(this));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        Universal.get().shutdown();
    }
}
