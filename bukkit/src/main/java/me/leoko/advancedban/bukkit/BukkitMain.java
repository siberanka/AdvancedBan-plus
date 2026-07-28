package me.leoko.advancedban.bukkit;

import me.leoko.advancedban.Universal;
import me.leoko.advancedban.bukkit.listener.ChatListener;
import me.leoko.advancedban.bukkit.listener.CommandListener;
import me.leoko.advancedban.bukkit.listener.ConnectionListener;
import me.leoko.advancedban.bukkit.listener.InternalListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public class BukkitMain extends JavaPlugin {
    private static BukkitMain instance;

    public static BukkitMain get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Universal.get().setup(new BukkitMethods());

        ConnectionListener connListener = new ConnectionListener();
        this.getServer().getPluginManager().registerEvents(connListener, this);
        this.getServer().getPluginManager().registerEvents(new ChatListener(), this);
        this.getServer().getPluginManager().registerEvents(new CommandListener(), this);
        this.getServer().getPluginManager().registerEvents(new InternalListener(), this);
        registerVoicechatHook();

    }

    @Override
    public void onDisable() {
        if (FoliaSchedulerBridge.isFolia()) {
            FoliaSchedulerBridge.cancelTasks(this);
        } else {
            Bukkit.getScheduler().cancelTasks(this);
        }
        Universal.get().shutdown();
        instance = null;
    }

    private void registerVoicechatHook() {
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            return;
        }
        try {
            Class<?> hookClass = Class.forName("me.leoko.advancedban.bukkit.voicechat.VoicechatHook", true, getClassLoader());
            Method register = hookClass.getMethod("register", BukkitMain.class);
            register.invoke(null, this);
        } catch (ReflectiveOperationException ex) {
            Universal.get().logMessage("Console.VoiceChatHookFailed", "&cFailed to hook Simple Voice Chat safely; voice mute integration disabled.");
            Universal.get().debugException(ex);
        } catch (LinkageError ex) {
            Universal.get().logMessage("Console.VoiceChatHookFailed", "&cFailed to hook Simple Voice Chat safely; voice mute integration disabled.");
            Universal.get().debugThrowable(ex);
        }
    }
}
