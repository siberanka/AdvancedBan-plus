package litebans.api;

import litebans.api.exception.MissingImplementationException;

public abstract class RandomID {
    public static final int RESULT_ERROR = -1;
    private static RandomID instance;

    public static void setInstance(RandomID instance) {
        RandomID.instance = instance;
    }

    public static RandomID get() {
        if (instance == null) throw new MissingImplementationException();
        return instance;
    }

    public abstract String convert(long id);
    public abstract long reveal(String randomID);
}
