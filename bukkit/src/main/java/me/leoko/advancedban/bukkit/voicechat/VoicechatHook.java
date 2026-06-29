package me.leoko.advancedban.bukkit.voicechat;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.bukkit.BukkitMain;
import me.leoko.advancedban.manager.MessageManager;
import org.bukkit.Bukkit;

public final class VoicechatHook {

    private VoicechatHook() {
    }

    public static void register(BukkitMain plugin) {
        if (!Universal.get().getMethods().getBoolean(Universal.get().getMethods().getConfig(), "VoiceChat.MuteIntegration.Enabled", true)) {
            Universal.get().logMessage("Console.VoiceChatDisabled", "&7Simple Voice Chat mute integration is disabled in config.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            return;
        }
        try {
            BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
            if (service == null) {
                Universal.get().debug(MessageManager.getMessageOrDefault("Console.VoiceChatServiceUnavailable", "Simple Voice Chat plugin detected but BukkitVoicechatService is not available yet."));
                return;
            }
            service.registerPlugin(new AdvancedBanVoicechatPlugin());
            Universal.get().logMessage("Console.VoiceChatEnabled", "&aSimple Voice Chat mute integration enabled.");
        } catch (RuntimeException ex) {
            Universal.get().logMessage("Console.VoiceChatHookFailed", "&cFailed to hook Simple Voice Chat safely; voice mute integration disabled.");
            Universal.get().debugException(ex);
        } catch (LinkageError ex) {
            Universal.get().logMessage("Console.VoiceChatHookFailed", "&cFailed to hook Simple Voice Chat safely; voice mute integration disabled.");
            Universal.get().debugThrowable(ex);
        }
    }
}
