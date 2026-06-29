package me.leoko.advancedban.bungee.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.bungee.event.PunishmentEvent;
import me.leoko.advancedban.bungee.event.RevokePunishmentEvent;
import me.leoko.advancedban.manager.TimeManager;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.PunishmentType;
import me.leoko.advancedban.utils.Security;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Beelzebu
 */
public class InternalListener implements Listener {

    private final Universal universal = Universal.get();

    @EventHandler
    public void onPunish(PunishmentEvent e) {
        sendToBukkit("Punish", Arrays.asList(e.getPunishment().toString()));
    }

    @EventHandler
    public void onUnPunish(RevokePunishmentEvent e) {
        sendToBukkit("Unpunish", Arrays.asList(e.getPunishment().toString()));
    }

    @EventHandler
    public void onPluginMessageEvent(PluginMessageEvent e) {
        if (!"advancedban:main".equals(e.getTag())) {
            return;
        }
        if (e.getSender() instanceof ProxiedPlayer) {
            return;
        }
        if (e.getData() == null || e.getData().length > Security.DEFAULT_MAX_TOTAL_COMMAND_LENGTH) {
            return;
        }
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(e.getData());
            String channel = in.readUTF();
            switch (channel) {
                case "Punish":
                    String message = in.readUTF();
                    if (message == null || message.length() > Security.DEFAULT_MAX_TOTAL_COMMAND_LENGTH) {
                        return;
                    }
                    JsonObject punishment = universal.getGson().fromJson(message, JsonObject.class);
                    if (!isValidPunishmentPayload(punishment)) {
                        universal.debug("Rejected malformed punishment plugin message.");
                        return;
                    }
                    new Punishment(
                            punishment.get("name").getAsString(),
                            Security.normalizeUuid(punishment.get("uuid").getAsString()),
                            punishment.get("reason").getAsString(),
                            punishment.get("operator") != null ? punishment.get("operator").getAsString() : "CONSOLE",
                            PunishmentType.valueOf(punishment.get("punishmenttype").getAsString().toUpperCase()),
                            punishment.get("start") != null ? punishment.get("start").getAsLong() : TimeManager.getTime(),
                            TimeManager.getTime() + punishment.get("end").getAsLong(),
                            punishment.get("calculation") != null ? punishment.get("calculation").getAsString() : null,
                            -1
                    ).create(punishment.get("silent") != null && punishment.get("silent").getAsBoolean());
                    universal.logMessage("Console.PluginMessagePunishmentCreated", "A punishment was created using PluginMessaging listener.");
                    universal.debug(punishment.toString());
                    break;
                default:
                    universal.debug("Unknown channel for tag \"AdvancedBan\"");
                    break;
            }
        } catch (JsonSyntaxException | IllegalArgumentException | NullPointerException | IllegalStateException ex) {
            universal.logMessage("Console.PluginMessagePunishmentFailed", "An exception occurred while reading a punishment from plugin messaging channel.");
            universal.debugException(ex);
        }
    }

    private boolean isValidPunishmentPayload(JsonObject punishment) {
        if (punishment == null
                || !punishment.has("name")
                || !punishment.has("uuid")
                || !punishment.has("reason")
                || !punishment.has("punishmenttype")
                || !punishment.has("end")) {
            return false;
        }
        String name = punishment.get("name").getAsString();
        String uuid = punishment.get("uuid").getAsString();
        String reason = punishment.get("reason").getAsString();
        return Security.isSafePlayerName(name)
                && Security.isValidUuid(uuid)
                && Security.isReasonSafe(reason)
                && Security.isReasonSafe(punishment.get("punishmenttype").getAsString());
    }

    public void sendToBukkit(String channel, List<String> messages) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(channel);
        messages.forEach(out::writeUTF);
        ProxyServer.getInstance().getServers().keySet().forEach(server -> ProxyServer.getInstance().getServerInfo(server).sendData("advancedban:main", out.toByteArray(), true));
    }
}
