package arinside.output;

/** Java port of output/RootPath - the "../" prefix needed to reach the output root from a given depth. */
public final class RootPath {
    private RootPath() {}

    public static String of(int rootLevel) {
        return switch (rootLevel) {
            case 1 -> "../";
            case 2 -> "../../";
            case 3 -> "../../../";
            default -> "";
        };
    }
}
