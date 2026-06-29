package me.leoko.advancedban.bungee.utils;

import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.Permissionable;

import java.lang.reflect.Method;
import java.util.List;

public class CloudNetCloudPermsOfflineUser implements Permissionable {
    private Object permissionUser;

    public CloudNetCloudPermsOfflineUser(String name) {
        try {
            Class<?> driverClass = Class.forName("de.dytanic.cloudnet.driver.CloudNetDriver");
            Object driver = driverClass.getMethod("getInstance").invoke(null);
            Object permissionManagement = driverClass.getMethod("getPermissionManagement").invoke(driver);
            Object usersObject = permissionManagement.getClass().getMethod("getUsers", String.class).invoke(permissionManagement, name);
            if (!(usersObject instanceof List)) {
                return;
            }

            List<?> users = (List<?>) usersObject;
            if (!users.isEmpty()) {
                this.permissionUser = users.get(0);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Universal.get().debugException(ex);
        }
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permissionUser == null) {
            return false;
        }
        try {
            Object permissionResult = permissionUser.getClass().getMethod("hasPermission", String.class).invoke(permissionUser, permission);
            Method asBoolean = permissionResult.getClass().getMethod("asBoolean");
            Object result = asBoolean.invoke(permissionResult);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Universal.get().debugException(ex);
            return false;
        }
    }
}
