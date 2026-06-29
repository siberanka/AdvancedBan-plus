package me.leoko.advancedban.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.CommandManager;
import me.leoko.advancedban.utils.tabcompletion.TabCompleter;

import java.util.Collections;
import java.util.List;

final class VelocityCommand implements SimpleCommand {
    private final String permission;
    private final TabCompleter tabCompleter;

    VelocityCommand(String permission, TabCompleter tabCompleter) {
        this.permission = permission;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandManager.get().onCommand(invocation.source(), invocation.alias(), invocation.arguments());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (tabCompleter == null || !hasPermission(invocation)) {
            return Collections.emptyList();
        }
        return tabCompleter.onTabComplete(invocation.source(), invocation.arguments());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return permission == null || Universal.get().hasPerms(invocation.source(), permission);
    }
}
