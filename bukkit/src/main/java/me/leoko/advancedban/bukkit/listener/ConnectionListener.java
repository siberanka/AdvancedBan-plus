package me.leoko.advancedban.bukkit.listener;

import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.manager.MessageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Created by Leoko @ dev.skamps.eu on 16.07.2016.
 */
public class ConnectionListener implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void onConnect(AsyncPlayerPreLoginEvent event) {
        if(event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED){
            try {
                UUIDManager.get().supplyInternUUID(event.getName(), event.getUniqueId());
                String result = Universal.get().callConnection(event.getName(), event.getAddress().getHostAddress());
                if (result != null) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, result);
                }
            } catch (RuntimeException | LinkageError ex) {
                Universal.get().debugThrowable(ex);
                if (Universal.get().isLockdownOnError()) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                            MessageManager.getMessageOrDefault("Connection.FailedDataLoad",
                                    "[AdvancedBan] Failed to load player data!"));
                }
            }
        }
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent event){
        PunishmentManager.get().discard(event.getPlayer().getName());
    }

}
