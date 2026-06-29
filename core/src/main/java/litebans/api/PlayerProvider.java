package litebans.api;

import litebans.api.exception.MissingImplementationException;

public abstract class PlayerProvider {
    private static PlayerProvider instance;

    public static void setInstance(PlayerProvider instance) {
        PlayerProvider.instance = instance;
    }

    public static PlayerProvider get() {
        if (instance == null) throw new MissingImplementationException();
        return instance;
    }

    public abstract String provide(String target);
}
