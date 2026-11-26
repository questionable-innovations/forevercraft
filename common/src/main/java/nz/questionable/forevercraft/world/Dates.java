package nz.questionable.forevercraft.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Dates {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private Dates() {}

    /** Server-local calendar date, e.g. "22 Nov 2026". */
    public static String day(long epochMillis) {
        return DAY.format(Instant.ofEpochMilli(epochMillis));
    }
}
