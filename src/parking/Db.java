package parking;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class Db {
    private Db() {}

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(Config.DB_URL, Config.DB_USER, Config.DB_PASSWORD);
    }

    /** Makes sure slot rows 1..PARK_SLOTS exist. Existing rows are left untouched. */
    public static void seedSlots() throws SQLException {
        try (Connection c = get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO slots (slot_no, status) VALUES (?, 'FREE')")) {
            for (int i = 1; i <= Config.PARK_SLOTS; i++) {
                ps.setInt(1, i);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
