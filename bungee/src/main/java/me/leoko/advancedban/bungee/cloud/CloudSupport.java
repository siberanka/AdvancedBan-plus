package me.leoko.advancedban.bungee.cloud;

import java.util.UUID;

public interface CloudSupport {
    boolean kick(UUID uniqueID, String reason);
}
