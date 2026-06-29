package me.leoko.advancedban.bungee.cloud;

import net.md_5.bungee.api.ProxyServer;

public class CloudSupportHandler {

    public static CloudSupport getCloudSystem(){
        if (ProxyServer.getInstance().getPluginManager().getPlugin("CloudNet-Bridge") != null)  {
            return new ReflectionCloudSupport(true);
        }
        if (ProxyServer.getInstance().getPluginManager().getPlugin("CloudNetAPI") != null) {
            return new ReflectionCloudSupport(false);
        }
        return null;
    }
}
