package litebans.api;

import litebans.api.exception.MissingImplementationException;

public abstract class Events {
    private static Events instance;

    public static void setInstance(Events instance) {
        Events.instance = instance;
    }

    public static Events get() {
        if (instance == null) throw new MissingImplementationException();
        return instance;
    }

    public abstract void register(Listener listener);
    public abstract void unregister(Listener listener);

    public static class Listener {
        public void broadcastSent(String message, String type) {}
        public void entryAdded(Entry entry) {}
        public void entryRemoved(Entry entry) {}
    }
}
