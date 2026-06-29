package litebans.api.exception;

public class MissingImplementationException extends UnsupportedOperationException {
    public MissingImplementationException() {
        super("LiteBans API implementation is not available! Enable litebans-api-support in AdvancedBan config.yml.");
    }
}
