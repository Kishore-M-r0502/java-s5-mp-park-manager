package parking;

import parking.ParkingService.ParkingException;
import parking.ParkingService.Receipt;
import parking.ParkingService.Slot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Simple Swing front end. All database work runs off the UI thread in SwingWorkers. */
public class ParkingApp extends JFrame {
    private static final Color FREE = new Color(0xA5D6A7);
    private static final Color RESERVED = new Color(0xFFE082);
    private static final Color OCCUPIED = new Color(0xEF9A9A);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    private final ParkingService svc = new ParkingService();
    private final List<JLabel> slotLabels = new ArrayList<>();
    private final JTextField vehicleField = new JTextField(12);
    private final JLabel summary = new JLabel(" ");
    private final JLabel status = new JLabel("Ready.");

    public ParkingApp() {
        super("Smart Parking Management");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Slot grid
        JPanel grid = new JPanel(new GridLayout(0, 4, 8, 8));
        grid.setBorder(new EmptyBorder(12, 12, 6, 12));
        for (int i = 0; i < Config.PARK_SLOTS; i++) {
            JLabel l = new JLabel("", SwingConstants.CENTER);
            l.setOpaque(true);
            l.setPreferredSize(new Dimension(130, 80));
            l.setBorder(new LineBorder(Color.GRAY));
            slotLabels.add(l);
            grid.add(l);
        }

        // Controls
        JButton reserve = new JButton("Reserve");
        JButton enter = new JButton("Entry");
        JButton exit = new JButton("Exit");
        JButton cancel = new JButton("Cancel reservation");
        JButton simulate = new JButton("Simulate cars");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        controls.add(new JLabel("Vehicle no:"));
        controls.add(vehicleField);
        controls.add(reserve);
        controls.add(enter);
        controls.add(exit);
        controls.add(cancel);
        controls.add(simulate);

        JPanel south = new JPanel(new GridLayout(0, 1));
        south.setBorder(new EmptyBorder(0, 12, 8, 12));
        south.add(controls);
        south.add(summary);
        south.add(status);

        add(grid, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        reserve.addActionListener(e -> withVehicle(v -> run(() ->
                "Reserved slot " + svc.reserve(v) + " for " + v + " ("
                        + Config.RESERVATION_HOLD.toMinutes() + " min hold).", false)));
        enter.addActionListener(e -> withVehicle(v -> run(() ->
                v + " parked in slot " + svc.entry(v) + ".", false)));
        exit.addActionListener(e -> withVehicle(v -> run(() -> receipt(svc.exit(v)), true)));
        cancel.addActionListener(e -> withVehicle(v -> run(() -> {
            svc.cancelReservation(v);
            return "Reservation cancelled for " + v + ".";
        }, false)));
        simulate.addActionListener(e -> run(this::simulate, false));

        // Scheduled task: release expired reservations.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reservation-expiry");
            t.setDaemon(true);
            return t;
        });
        long period = Config.EXPIRY_CHECK_INTERVAL.toSeconds();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                svc.expireReservations();
            } catch (Exception ex) {
                System.err.println("[scheduler] " + ex.getMessage());
            }
        }, period, period, TimeUnit.SECONDS);

        // Auto-refresh so changes from the scheduler or simulation show up live.
        new javax.swing.Timer(2000, e -> refresh()).start();

        pack();
        setLocationRelativeTo(null);
        refresh();
    }

    private void withVehicle(java.util.function.Consumer<String> action) {
        String v = vehicleField.getText().trim().toUpperCase();
        if (v.isEmpty()) {
            status.setText("! Enter a vehicle number first.");
            return;
        }
        action.accept(v);
    }

    private void run(Callable<String> task, boolean showDialog) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return task.call();
            }
            @Override protected void done() {
                try {
                    String msg = get();
                    status.setText(msg.lines().findFirst().orElse(msg));
                    if (showDialog) {
                        JOptionPane.showMessageDialog(ParkingApp.this, msg, "Receipt",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (ExecutionException ex) {
                    status.setText("! " + ex.getCause().getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                refresh();
            }
        }.execute();
    }

    private void refresh() {
        new SwingWorker<List<Slot>, Void>() {
            @Override protected List<Slot> doInBackground() throws Exception {
                return svc.listSlots();
            }
            @Override protected void done() {
                try {
                    List<Slot> slots = get();
                    long free = 0;
                    for (int i = 0; i < slots.size() && i < slotLabels.size(); i++) {
                        Slot s = slots.get(i);
                        JLabel l = slotLabels.get(i);
                        l.setBackground(switch (s.status()) {
                            case "FREE" -> FREE;
                            case "RESERVED" -> RESERVED;
                            default -> OCCUPIED;
                        });
                        l.setText("<html><center>Slot " + s.number() + "<br><b>" + s.status()
                                + "</b><br>" + (s.vehicle() == null ? "&nbsp;" : s.vehicle())
                                + "</center></html>");
                        if (s.status().equals("FREE")) free++;
                    }
                    summary.setText("Available: " + free + " / " + slots.size()
                            + "   (green = free, yellow = reserved, red = occupied)");
                } catch (Exception ex) {
                    status.setText("! Could not refresh: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private static String receipt(Receipt r) {
        return "Vehicle " + r.vehicle() + " left slot " + r.slot()
                + "\nIn:   " + r.entry().format(FMT)
                + "\nOut:  " + r.exit().format(FMT)
                + "\nTime: " + r.minutes() + " min"
                + "\nFee:  " + r.fee();
    }

    /** More cars than slots arrive at once; runs in a background worker. */
    private String simulate() throws InterruptedException {
        int cars = Config.PARK_SLOTS + 4;
        AtomicInteger parked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(cars);
        CountDownLatch gate = new CountDownLatch(1);
        for (int i = 1; i <= cars; i++) {
            String v = String.format("SIM-%02d", i);
            pool.submit(() -> {
                try {
                    gate.await();
                    svc.entry(v);
                    parked.incrementAndGet();
                    Thread.sleep(ThreadLocalRandom.current().nextInt(3000, 6000));
                    svc.exit(v);
                } catch (ParkingException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        gate.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        return "Simulation done: " + parked.get() + " parked, " + rejected.get() + " turned away (full).";
    }

    public static void main(String[] args) {
        try {
            Db.seedSlots();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Cannot reach the database:\n" + e.getMessage(),
                    "Database error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> new ParkingApp().setVisible(true));
    }
}
