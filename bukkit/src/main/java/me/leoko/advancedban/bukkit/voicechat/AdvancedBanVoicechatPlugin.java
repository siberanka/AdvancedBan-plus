package me.leoko.advancedban.bukkit.voicechat;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.utils.Security;

import java.util.Locale;
import java.util.UUID;

public class AdvancedBanVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "advancedban";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null || senderConnection.getPlayer() == null) {
            return;
        }
        UUID uniqueId = senderConnection.getPlayer().getUuid();
        if (uniqueId == null) {
            return;
        }
        String uuid = Security.normalizeUuid(uniqueId.toString());
        if (uuid == null || !PunishmentManager.get().isCached(uuid.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (PunishmentManager.get().isMuted(uuid)) {
            event.cancel();
        }
    }
}
