package parking;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.regex.Pattern;

public final class Config {
    private Config() {}

    /** Number of parking slots (v1: fixed at 8). */
    public static final int PARK_SLOTS = 8;

    /** Valid vehicle numbers: 3-15 chars of A-Z, 0-9, space or dash (after upper-casing). */
    public static final Pattern VEHICLE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9 -]{2,14}");

    /** Fee per started hour (minimum one hour is charged). */
    public static final BigDecimal RATE_PER_HOUR = new BigDecimal("20.00");

    /** How long a reservation is held before it expires. */
    public static final Duration RESERVATION_HOLD = Duration.ofMinutes(15);

    /** How often the scheduled task looks for expired reservations. */
    public static final Duration EXPIRY_CHECK_INTERVAL = Duration.ofSeconds(30);

    public static final String DB_URL = System.getenv().getOrDefault("DB_URL",
            "jdbc:mysql://localhost:3306/parking_db?useSSL=false&allowPublicKeyRetrieval=true");
    public static final String DB_USER = System.getenv().getOrDefault("DB_USER", "root");
    public static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");
}
