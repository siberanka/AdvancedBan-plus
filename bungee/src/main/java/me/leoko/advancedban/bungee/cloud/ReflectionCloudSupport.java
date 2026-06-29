package me.leoko.advancedban.bungee.cloud;

import me.leoko.advancedban.Universal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

final class ReflectionCloudSupport implements CloudSupport {
    private final boolean cloudNetV3;

    ReflectionCloudSupport(boolean cloudNetV3) {
        this.cloudNetV3 = cloudNetV3;
    }

    @Override
    public boolean kick(UUID uniqueID, String reason) {
        try {
            if (cloudNetV3) {
                return kickV3(uniqueID, reason);
            }
            return kickV2(uniqueID, reason);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Universal.get().debugException(ex);
            return false;
        }
    }

    private boolean kickV3(UUID uniqueID, String reason) throws ReflectiveOperationException {
        Class<?> driverClass = Class.forName("de.dytanic.cloudnet.driver.CloudNetDriver");
        Class<?> playerManagerClass = Class.forName("de.dytanic.cloudnet.ext.bridge.player.IPlayerManager");
        Object driver = driverClass.getMethod("getInstance").invoke(null);
        Object registry = driverClass.getMethod("getServicesRegistry").invoke(driver);
        Object playerManager = registry.getClass().getMethod("getFirstService", Class.class).invoke(registry, playerManagerClass);
        if (playerManager == null) {
            return false;
        }
        Object executor = playerManager.getClass().getMethod("getPlayerExecutor", UUID.class).invoke(playerManager, uniqueID);
        if (executor == null) {
            return false;
        }
        executor.getClass().getMethod("kick", String.class).invoke(executor, reason);
        return true;
    }

    private boolean kickV2(UUID uniqueID, String reason) throws ReflectiveOperationException {
        Class<?> bridgeClass = Class.forName("de.dytanic.cloudnet.api.player.PlayerExecutorBridge");
        Class<?> serverClass = Class.forName("de.dytanic.cloudnet.bridge.CloudServer");
        Field instanceField = bridgeClass.getField("INSTANCE");
        Object bridge = instanceField.get(null);
        Object cloudServer = serverClass.getMethod("getInstance").invoke(null);
        Object players = serverClass.getMethod("getCloudPlayers").invoke(cloudServer);
        Method getMethod = players.getClass().getMethod("get", Object.class);
        Object cloudPlayer = getMethod.invoke(players, uniqueID);
        if (cloudPlayer == null) {
            return false;
        }
        bridgeClass.getMethod("kickPlayer", cloudPlayer.getClass(), String.class).invoke(bridge, cloudPlayer, reason);
        return true;
    }
}
