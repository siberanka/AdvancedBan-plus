package me.leoko.advancedban.bungee.listener;

import com.imaginarycode.minecraft.redisbungee.events.PubSubMessageEvent;
import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.Security;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Locale;

/**
 *
 * @author Beelzebu
 */
public class PubSubMessageListener implements Listener {
    
    private static MethodInterface methods() {
        return Universal.get().getMethods();
    }

    @SuppressWarnings("deprecation")
	@EventHandler
    public void onMessageReceive(PubSubMessageEvent e) {
        String channel = e.getChannel();
        String message = e.getMessage();
        if (channel == null || message == null || message.length() > Security.DEFAULT_MAX_TOTAL_COMMAND_LENGTH) {
            return;
        }
        if (channel.equals("advancedban:main")) {
            String[] msg = message.split(" ", 3);
            if (message.startsWith("kick ")) {
                if (msg.length < 3 || !Security.isSafePlayerName(msg[1])) {
                    return;
                }
                ProxiedPlayer player = ProxyServer.getInstance().getPlayer(msg[1]);
                if (player != null) {
                    player.disconnect(msg[2]);
                }
            } else if (message.startsWith("notification ")) {
                if (msg.length < 3 || !msg[1].matches("[A-Za-z0-9_.-]{1,128}")) {
                    return;
                }
                for (ProxiedPlayer pp : ProxyServer.getInstance().getPlayers()) {
                    if (methods().hasPerms(pp, msg[1])) {
                        methods().sendMessage(pp, msg[2]);
                    }
                }
            } else if (message.startsWith("message ")) {
                if (msg.length < 3
                        || (!Security.isSafePlayerName(msg[1]) && !msg[1].equalsIgnoreCase("CONSOLE"))) {
                    return;
                }
                ProxiedPlayer player = ProxyServer.getInstance().getPlayer(msg[1]);
                if (player != null) {
                    player.sendMessage(msg[2]);
                }
                if (msg[1].equalsIgnoreCase("CONSOLE")) {
                    ProxyServer.getInstance().getConsole().sendMessage(msg[2]);
                }
            }
        } else if (channel.equals("advancedban:connection")) {
            String[] msg = message.split(",", 2);
            if (msg.length < 2 || !Security.isSafePlayerName(msg[0])
                    || !Security.isValidIpAddress(msg[1])) {
                return;
            }
            Universal.get().getIps().put(msg[0].toLowerCase(Locale.ROOT), msg[1]);
        }
    }
}
