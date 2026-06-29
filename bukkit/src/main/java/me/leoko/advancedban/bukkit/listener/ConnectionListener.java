package me.leoko.advancedban.bukkit.listener;

import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.MessageManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Created by Leoko @ dev.skamps.eu on 16.07.2016.
 */
public class ConnectionListener implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void onConnect(AsyncPlayerPreLoginEvent event) {
        if(event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED){
            UUIDManager.get().supplyInternUUID(event.getName(), event.getUniqueId());
            String result = Universal.get().callConnection(event.getName(), event.getAddress().getHostAddress());
            if (result != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, result);
            }
        }
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent event){
        PunishmentManager.get().discard(event.getPlayer().getName());
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        Universal.get().getMethods().scheduleAsync(() -> {
            if (playerName.equalsIgnoreCase("Leoko")) {
                Universal.get().getMethods().runSync(() -> {
                    if (Universal.get().broadcastLeoko()) {
                        MessageManager.getLayout(Universal.get().getMethods().getMessages(), "Broadcast.CreatorJoin")
                                .forEach(Bukkit::broadcastMessage);
                    } else {
                        MessageManager.sendMessage(event.getPlayer(), "Broadcast.CreatorPrivate", false);
                    }
                });
            }
        }, 20);
    }


}
