package me.leoko.advancedban.bukkit;

import me.leoko.advancedban.Universal;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class FoliaSchedulerBridge {
    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaSchedulerBridge() {
    }

    static boolean isFolia() {
        return FOLIA;
    }

    static boolean runAsync(Plugin plugin, Runnable task) {
        return invokeAsync(plugin, "runNow", new Class<?>[]{Plugin.class, Consumer.class},
                new Object[]{plugin, consumer(task)});
    }

    static boolean runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        return invokeAsync(plugin, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class, TimeUnit.class},
                new Object[]{plugin, consumer(task), ticksToMillis(delayTicks), TimeUnit.MILLISECONDS});
    }

    static boolean runAsyncRepeating(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return invokeAsync(plugin, "runAtFixedRate", new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class},
                new Object[]{plugin, consumer(task), ticksToMillis(delayTicks), Math.max(50L, ticksToMillis(periodTicks)), TimeUnit.MILLISECONDS});
    }

    static boolean runGlobal(Plugin plugin, Runnable task) {
        return invokeGlobal(plugin, "run", new Class<?>[]{Plugin.class, Consumer.class}, new Object[]{plugin, consumer(task)});
    }

    static boolean runEntity(Player player, Plugin plugin, Runnable task) {
        if (player == null) {
            return false;
        }
        try {
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object scheduler = getScheduler.invoke(player);
            Method run = schedulerMethod("io.papermc.paper.threadedregions.scheduler.EntityScheduler",
                    "run", Plugin.class, Consumer.class, Runnable.class);
            run.invoke(scheduler, plugin, consumer(task), (Runnable) () -> {
            });
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logBridgeFailure("entity scheduler", ex);
            return false;
        }
    }

    private static boolean invokeAsync(Plugin plugin, String method, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method getScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            Object scheduler = getScheduler.invoke(null);
            schedulerMethod("io.papermc.paper.threadedregions.scheduler.AsyncScheduler", method, parameterTypes)
                    .invoke(scheduler, args);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logBridgeFailure("async scheduler", ex);
            return false;
        }
    }

    private static boolean invokeGlobal(Plugin plugin, String method, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method getScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object scheduler = getScheduler.invoke(null);
            schedulerMethod("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler", method, parameterTypes)
                    .invoke(scheduler, args);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logBridgeFailure("global scheduler", ex);
            return false;
        }
    }

    private static Consumer<Object> consumer(Runnable task) {
        return ignored -> safeRun(task);
    }

    private static void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Throwable ex) {
            Universal.get().debugThrowable(ex);
        }
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(0L, ticks) * 50L;
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, FoliaSchedulerBridge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private static Method schedulerMethod(String className, String method, Class<?>... parameterTypes)
            throws ReflectiveOperationException {
        return Class.forName(className, false, FoliaSchedulerBridge.class.getClassLoader())
                .getMethod(method, parameterTypes);
    }

    private static void logBridgeFailure(String scheduler, Throwable ex) {
        Throwable cause = ex instanceof InvocationTargetException && ((InvocationTargetException) ex).getCause() != null
                ? ((InvocationTargetException) ex).getCause()
                : ex;
        Universal.get().debugThrowable(new IllegalStateException("Failed to use Folia " + scheduler + ".", cause));
    }
}
