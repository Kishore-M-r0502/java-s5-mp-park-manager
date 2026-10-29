package parking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public final class FeeCalculator {
    private FeeCalculator() {}

    /** Whole minutes parked, rounded up. */
    public static long minutes(LocalDateTime entry, LocalDateTime exit) {
        long seconds = Math.max(0, Duration.between(entry, exit).getSeconds());
        return (seconds + 59) / 60;
    }

    /** Charged per started hour, minimum one hour. */
    public static BigDecimal fee(LocalDateTime entry, LocalDateTime exit) {
        long hours = Math.max(1, (minutes(entry, exit) + 59) / 60);
        return Config.RATE_PER_HOUR.multiply(BigDecimal.valueOf(hours));
    }
}
