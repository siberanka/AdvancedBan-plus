package me.leoko.advancedban.velocity;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.manager.MessageManager;

final class VelocityListener {
    private final VelocityMain plugin;

    VelocityListener(VelocityMain plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        try {
            UUIDManager.get().supplyInternUUID(event.getPlayer().getUsername(), event.getPlayer().getUniqueId());
            String result = Universal.get().callConnection(
                    event.getPlayer().getUsername(),
                    event.getPlayer().getRemoteAddress().getAddress().getHostAddress());
            if (result != null) {
                event.setResult(ResultedEvent.ComponentResult.denied(VelocityMethods.component(result)));
            }
        } catch (RuntimeException | LinkageError ex) {
            Universal.get().debugThrowable(ex);
            if (Universal.get().isLockdownOnError()) {
                event.setResult(ResultedEvent.ComponentResult.denied(VelocityMethods.component(
                        MessageManager.getMessageOrDefault("Connection.FailedDataLoad",
                                "[AdvancedBan] Failed to load player data!"))));
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        PunishmentManager.get().discard(event.getPlayer().getUsername());
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        try {
            if (Universal.get().getMethods().callChat(event.getPlayer())) {
                event.setResult(PlayerChatEvent.ChatResult.denied());
            }
        } catch (RuntimeException | LinkageError ex) {
            Universal.get().debugThrowable(ex);
            if (Universal.get().isLockdownOnError()) {
                event.setResult(PlayerChatEvent.ChatResult.denied());
            }
        }
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        try {
            if (Universal.get().getMethods().callCMD(event.getCommandSource(), "/" + event.getCommand())) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
            }
        } catch (RuntimeException | LinkageError ex) {
            Universal.get().debugThrowable(ex);
            if (Universal.get().isLockdownOnError()) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
            }
        }
    }
}
