package parking;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * All parking operations. Thread safety comes from two layers:
 *  1. a fair ReentrantLock serialises operations inside this JVM
 *     (user thread, scheduled expiry task, simulation threads);
 *  2. every operation is one DB transaction using SELECT ... FOR UPDATE,
 *     so it stays correct even if a second process shares the database.
 */
public class ParkingService {

    public record Slot(int number, String status, String vehicle) {}

    public record Receipt(String vehicle, int slot, LocalDateTime entry,
                          LocalDateTime exit, long minutes, BigDecimal fee) {}

    public static class ParkingException extends Exception {
        public ParkingException(String msg) { super(msg); }
    }

    private final ReentrantLock lock = new ReentrantLock(true);

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection c) throws SQLException, ParkingException;
    }

    private <T> T inTx(Work<T> work) throws ParkingException {
        lock.lock();
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (ParkingException | SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ParkingException("Database error: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    // ---------- public operations ----------

    public List<Slot> listSlots() throws ParkingException {
        return inTx(c -> {
            List<Slot> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT slot_no, status, vehicle_no FROM slots ORDER BY slot_no")) {
                while (rs.next()) {
                    out.add(new Slot(rs.getInt(1), rs.getString(2), rs.getString(3)));
                }
            }
            return out;
        });
    }

    /** Holds the lowest free slot for Config.RESERVATION_HOLD. Returns the slot number. */
    public int reserve(String vehicle) throws ParkingException {
        return inTx(c -> {
            if (slotOf(c, vehicle).isPresent()) {
                throw new ParkingException(vehicle + " already has a slot.");
            }
            int slot = findFreeSlot(c);
            LocalDateTime now = LocalDateTime.now();
            setSlot(c, slot, "RESERVED", vehicle);
            exec(c, "INSERT INTO reservations (slot_no, vehicle_no, reserved_at, expires_at, status) "
                            + "VALUES (?,?,?,?, 'ACTIVE')",
                    slot, vehicle, ts(now), ts(now.plus(Config.RESERVATION_HOLD)));
            return slot;
        });
    }

    public void cancelReservation(String vehicle) throws ParkingException {
        inTx(c -> {
            Optional<Slot> s = slotOf(c, vehicle);
            if (s.isEmpty() || !s.get().status().equals("RESERVED")) {
                throw new ParkingException("No active reservation for " + vehicle + ".");
            }
            exec(c, "UPDATE reservations SET status='CANCELLED' "
                    + "WHERE slot_no=? AND vehicle_no=? AND status='ACTIVE'", s.get().number(), vehicle);
            setSlot(c, s.get().number(), "FREE", null);
            return null;
        });
    }

    /** Vehicle enters. Uses its reserved slot if it has one, otherwise the lowest free slot. */
    public int entry(String vehicle) throws ParkingException {
        return inTx(c -> {
            Optional<Slot> existing = slotOf(c, vehicle);
            int slot;
            if (existing.isPresent()) {
                if (existing.get().status().equals("OCCUPIED")) {
                    throw new ParkingException(vehicle + " is already parked.");
                }
                slot = existing.get().number();
                exec(c, "UPDATE reservations SET status='USED' "
                        + "WHERE slot_no=? AND vehicle_no=? AND status='ACTIVE'", slot, vehicle);
            } else {
                slot = findFreeSlot(c);
            }
            setSlot(c, slot, "OCCUPIED", vehicle);
            exec(c, "INSERT INTO parking_records (slot_no, vehicle_no, entry_time) VALUES (?,?,?)",
                    slot, vehicle, ts(LocalDateTime.now()));
            return slot;
        });
    }

    /** Vehicle leaves: stamps exit time, computes the fee, frees the slot. */
    public Receipt exit(String vehicle) throws ParkingException {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, slot_no, entry_time FROM parking_records "
                            + "WHERE vehicle_no=? AND exit_time IS NULL FOR UPDATE")) {
                ps.setString(1, vehicle);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ParkingException(vehicle + " is not currently parked.");
                    }
                    long id = rs.getLong("id");
                    int slot = rs.getInt("slot_no");
                    LocalDateTime entry = rs.getTimestamp("entry_time").toLocalDateTime();
                    LocalDateTime exit = LocalDateTime.now();
                    BigDecimal fee = FeeCalculator.fee(entry, exit);
                    exec(c, "UPDATE parking_records SET exit_time=?, fee=? WHERE id=?",
                            ts(exit), fee, id);
                    setSlot(c, slot, "FREE", null);
                    return new Receipt(vehicle, slot, entry, exit,
                            FeeCalculator.minutes(entry, exit), fee);
                }
            }
        });
    }

    /** Called by the scheduled task: frees slots whose reservation ran out. */
    public int expireReservations() throws ParkingException {
        return inTx(c -> {
            int count = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, slot_no, vehicle_no FROM reservations "
                            + "WHERE status='ACTIVE' AND expires_at < ? FOR UPDATE")) {
                ps.setTimestamp(1, ts(LocalDateTime.now()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        exec(c, "UPDATE reservations SET status='EXPIRED' WHERE id=?", rs.getLong("id"));
                        exec(c, "UPDATE slots SET status='FREE', vehicle_no=NULL "
                                        + "WHERE slot_no=? AND vehicle_no=? AND status='RESERVED'",
                                rs.getInt("slot_no"), rs.getString("vehicle_no"));
                        count++;
                    }
                }
            }
            return count;
        });
    }

    // ---------- helpers ----------

    private static Optional<Slot> slotOf(Connection c, String vehicle) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT slot_no, status, vehicle_no FROM slots WHERE vehicle_no=? FOR UPDATE")) {
            ps.setString(1, vehicle);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Slot(rs.getInt(1), rs.getString(2), rs.getString(3)))
                        : Optional.empty();
            }
        }
    }

    private static int findFreeSlot(Connection c) throws SQLException, ParkingException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT slot_no FROM slots WHERE status='FREE' ORDER BY slot_no LIMIT 1 FOR UPDATE")) {
            if (!rs.next()) throw new ParkingException("Parking is full.");
            return rs.getInt(1);
        }
    }

    private static void setSlot(Connection c, int slot, String status, String vehicle) throws SQLException {
        exec(c, "UPDATE slots SET status=?, vehicle_no=? WHERE slot_no=?", status, vehicle, slot);
    }

    private static int exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }
}
