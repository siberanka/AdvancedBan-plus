package me.leoko.advancedban.utils;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.MessageManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.utils.litebans.LiteBansCompatibility;

public class CommandUtils {
    public static Punishment getPunishment(String target, PunishmentType type) {
        return type == PunishmentType.MUTE
                ? PunishmentManager.get().getMute(target)
                : PunishmentManager.get().getBan(target);
    }

    // Removes name argument and returns uuid (null if failed)
    public static String processName(Command.CommandInput input) {
        String name = LiteBansCompatibility.providePlayerTarget(input.getPrimary());
        input.next();
        if (Security.isValidUuid(name)) {
            return Security.normalizeUuid(name);
        }
        if (!Security.isValidPlayerName(name)) {
            MessageManager.sendMessage(input.getSender(), "General.InvalidArguments",
                    true, "NAME", String.valueOf(name));
            return null;
        }
        String uuid = UUIDManager.get().getUUID(name.toLowerCase());

        if (uuid == null)
            MessageManager.sendMessage(input.getSender(), "General.FailedFetch",
                    true, "NAME", name);

        return uuid;
    }

    // Removes name/ip argument and returns ip (null if failed)
    public static String processIP(Command.CommandInput input) {
        String name = LiteBansCompatibility.providePlayerTarget(input.getPrimaryData());
        input.next();
        if (Security.isValidIpV4(name)) {
            return name;
        }
        if (!Security.isValidPlayerName(name)) {
            MessageManager.sendMessage(input.getSender(), "General.InvalidArguments",
                    true, "NAME", String.valueOf(name));
            return null;
        }
		String ip = Universal.get().getIps().get(name);

		if (ip == null)
		    MessageManager.sendMessage(input.getSender(), "Ipban.IpNotCashed",
		            true, "NAME", name);

		return ip;
    }

    // Builds reason from remaining arguments (null if failed)
    public static String processReason(Command.CommandInput input) {
        MethodInterface mi = Universal.get().getMethods();
        String reason = Security.sanitizeReason(String.join(" ", input.getArgs()));

        if (reason.matches("[~@].+") && !mi.contains(mi.getLayouts(), "Message." + reason.split(" ")[0].substring(1))) {
            MessageManager.sendMessage(input.getSender(), "General.LayoutNotFound",
                    true, "NAME", reason.split(" ")[0].substring(1));
            return null;
        }

        return reason;
    }
}
