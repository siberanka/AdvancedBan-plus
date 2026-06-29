package me.leoko.advancedban.bukkit.voicechat;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.bukkit.BukkitMain;
import org.bukkit.Bukkit;

public final class VoicechatHook {

    private VoicechatHook() {
    }

    public static void register(BukkitMain plugin) {
        if (!Universal.get().getMethods().getBoolean(Universal.get().getMethods().getConfig(), "VoiceChat.MuteIntegration.Enabled", true)) {
            Universal.get().log("&7Simple Voice Chat mute integration is disabled in config.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            return;
        }
        try {
            BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
            if (service == null) {
                Universal.get().debug("Simple Voice Chat plugin detected but BukkitVoicechatService is not available yet.");
                return;
            }
            service.registerPlugin(new AdvancedBanVoicechatPlugin());
            Universal.get().log("&aSimple Voice Chat mute integration enabled.");
        } catch (RuntimeException ex) {
            Universal.get().log("&cFailed to hook Simple Voice Chat safely; voice mute integration disabled.");
            Universal.get().debugException(ex);
        } catch (LinkageError ex) {
            Universal.get().log("&cFailed to hook Simple Voice Chat safely; voice mute integration disabled.");
            Universal.get().debug(ex.getMessage());
        }
    }
}
