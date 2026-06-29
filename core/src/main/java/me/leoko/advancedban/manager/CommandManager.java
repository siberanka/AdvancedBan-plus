package me.leoko.advancedban.manager;

import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.Command;
import me.leoko.advancedban.utils.CommandRateLimiter;
import me.leoko.advancedban.utils.Security;

/**
 * The Command Manager is used to handle commands based on the sender, command-name and arguments.
 */
public class CommandManager {

    private static CommandManager instance = null;
    private final CommandRateLimiter rateLimiter = new CommandRateLimiter();

    /**
     * Get the instance of the command manager
     *
     * @return the command manager instance
     */
    public static synchronized CommandManager get() {
        return instance == null ? instance = new CommandManager() : instance;
    }

    /**
     * Handle/Perform a command.
     *
     * @param sender the sender which executes the command
     * @param cmd    the command name
     * @param args   the arguments for this command
     */
    public void onCommand(final Object sender, final String cmd, final String[] args) {
        if (!isSafeCommand(cmd, args)) {
            MessageManager.sendMessage(sender, "General.InvalidArguments", true);
            return;
        }
        if (!rateLimiter.allow(sender, cmd + " " + String.join(" ", args))) {
            MessageManager.sendMessage(sender, "General.RateLimited", true);
            return;
        }
        Universal.get().getMethods().runAsync(() -> {
            Command command = Command.getByName(cmd);
            if (command == null)
                return;

            String permission = command.getPermission();
            if (permission != null && !Universal.get().hasPerms(sender, permission)) {
                MessageManager.sendMessage(sender, "General.NoPerms", true);
                return;
            }

            if (!command.validateArguments(args)) {
                MessageManager.sendMessage(sender, command.getUsagePath(), true);
                return;
            }

            command.execute(sender, args);
        });
    }

    private boolean isSafeCommand(String cmd, String[] args) {
        if (cmd == null || cmd.length() > 64 || args == null) {
            return false;
        }
        int total = cmd.length();
        int maxArg = Security.getInt("Security.MaxArgumentLength", Security.DEFAULT_MAX_ARGUMENT_LENGTH);
        int maxTotal = Security.getInt("Security.MaxTotalCommandLength", Security.DEFAULT_MAX_TOTAL_COMMAND_LENGTH);
        for (String arg : args) {
            if (arg == null || arg.length() > maxArg) {
                return false;
            }
            total += arg.length() + 1;
            if (total > maxTotal) {
                return false;
            }
        }
        return true;
    }
}
