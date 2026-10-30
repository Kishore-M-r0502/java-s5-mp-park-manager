package parking;

import parking.ParkingService.HistoryRow;
import parking.ParkingService.ParkingException;
import parking.ParkingService.Receipt;
import parking.ParkingService.Slot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Swing front end. All database work runs off the UI thread in SwingWorkers. */
public class ParkingApp extends JFrame {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("EEE dd MMM yyyy  HH:mm:ss");
    private static final Pattern VEHICLE = Config.VEHICLE_PATTERN;
    private static final Color OK = new Color(0x2E7D32);
    private static final Color ERR = new Color(0xC62828);

    private final ParkingService svc = new ParkingService();
    private final List<SlotCard> cards = new ArrayList<>();
    private final List<JButton> buttons = new ArrayList<>();
    private final JTextField vehicleField = new JTextField(12);
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JLabel availLabel = new JLabel(" ");
    private final JLabel clock = new JLabel(" ");
    private final JProgressBar usage = new JProgressBar(0, Config.PARK_SLOTS);
    private final JTabbedPane tabs = new JTabbedPane();
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new String[]{"Vehicle", "Slot", "Entry", "Exit", "Fee"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    public ParkingApp() {
        super("Smart Parking Management");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // ---- header ----
        JLabel title = new JLabel("Smart Parking Management");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel rate = new JLabel("Rate: " + Config.RATE_PER_HOUR + " per started hour (minimum 1 hour)");
        JPanel left = new JPanel(new GridLayout(0, 1));
        left.add(title);
        left.add(rate);
        clock.setFont(clock.getFont().deriveFont(Font.BOLD, 14f));
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(12, 16, 4, 16));
        header.add(left, BorderLayout.WEST);
        header.add(clock, BorderLayout.EAST);

        // ---- slots tab ----
        JPanel grid = new JPanel(new GridLayout(0, 4, 12, 12));
        grid.setBorder(new EmptyBorder(12, 12, 6, 12));
        for (int i = 0; i < Config.PARK_SLOTS; i++) {
            SlotCard card = new SlotCard(i + 1);
            cards.add(card);
            grid.add(card);
        }
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        legend.add(swatch(colorFor("FREE"), "Free"));
        legend.add(swatch(colorFor("RESERVED"), "Reserved"));
        legend.add(swatch(colorFor("OCCUPIED"), "Occupied"));
        JLabel hint = new JLabel("   (click a taken slot to fill in its vehicle number)");
        legend.add(hint);
        usage.setStringPainted(false);
        JPanel usageRow = new JPanel(new BorderLayout(10, 0));
        usageRow.add(availLabel, BorderLayout.WEST);
        usageRow.add(usage, BorderLayout.CENTER);
        JPanel slotsBottom = new JPanel(new GridLayout(0, 1, 0, 4));
        slotsBottom.setBorder(new EmptyBorder(0, 12, 8, 12));
        slotsBottom.add(legend);
        slotsBottom.add(usageRow);
        JPanel slotsTab = new JPanel(new BorderLayout());
        slotsTab.add(grid, BorderLayout.CENTER);
        slotsTab.add(slotsBottom, BorderLayout.SOUTH);

        // ---- history tab ----
        JTable table = new JTable(historyModel);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(false);
        table.setFillsViewportHeight(true);
        JPanel historyTab = new JPanel(new BorderLayout());
        historyTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        historyTab.add(new JScrollPane(table), BorderLayout.CENTER);

        tabs.addTab("Slots", slotsTab);
        tabs.addTab("History", historyTab);
        tabs.addChangeListener(e -> refresh());

        // ---- controls ----
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        controls.add(new JLabel("Vehicle no:"));
        controls.add(vehicleField);
        controls.add(btn("Reserve", e -> doReserve()));
        controls.add(btn("Entry", e -> doEntry()));
        controls.add(btn("Exit", e -> doExit()));
        controls.add(btn("Cancel reservation", e -> doCancel()));
        controls.add(btn("Simulate cars", e -> run(this::simulate, false)));
        vehicleField.addActionListener(e -> doEntry());   // Enter key = Entry

        statusLabel.setBorder(new EmptyBorder(0, 14, 8, 14));
        JPanel south = new JPanel(new BorderLayout());
        south.add(controls, BorderLayout.CENTER);
        south.add(statusLabel, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        // ---- background activity ----
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

        new javax.swing.Timer(1000, e -> clock.setText(LocalDateTime.now().format(CLOCK))).start();
        new javax.swing.Timer(2000, e -> refresh()).start();
        clock.setText(LocalDateTime.now().format(CLOCK));

        pack();
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(null);
        refresh();
    }

    // ---------- actions ----------

    private void doReserve() {
        withVehicle(v -> run(() -> "Reserved slot " + svc.reserve(v) + " for " + v + " ("
                + Config.RESERVATION_HOLD.toMinutes() + " min hold).", false));
    }

    private void doEntry() {
        withVehicle(v -> run(() -> v + " parked in slot " + svc.entry(v) + ".", false));
    }

    private void doExit() {
        withVehicle(v -> run(() -> receipt(svc.exit(v)), true));
    }

    private void doCancel() {
        withVehicle(v -> run(() -> {
            svc.cancelReservation(v);
            return "Reservation cancelled for " + v + ".";
        }, false));
    }

    private void withVehicle(Consumer<String> action) {
        String v = vehicleField.getText().trim().toUpperCase();
        if (!VEHICLE.matcher(v).matches()) {
            setStatus("! Enter a vehicle number (3-15 letters, digits, space or dash).", false);
            return;
        }
        action.accept(v);
    }

    private void run(Callable<String> task, boolean showDialog) {
        setBusy(true);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return task.call();
            }
            @Override protected void done() {
                try {
                    String msg = get();
                    setStatus(msg.lines().findFirst().orElse(msg), true);
                    if (showDialog) {
                        JOptionPane.showMessageDialog(ParkingApp.this, msg, "Receipt",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (ExecutionException ex) {
                    setStatus("! " + ex.getCause().getMessage(), false);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    setBusy(false);
                    refresh();
                }
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
                    int used = 0;
                    for (int i = 0; i < slots.size() && i < cards.size(); i++) {
                        cards.get(i).update(slots.get(i));
                        if (!slots.get(i).status().equals("FREE")) used++;
                    }
                    usage.setValue(used);
                    availLabel.setText("Available: " + (slots.size() - used) + " / " + slots.size());
                } catch (Exception ex) {
                    setStatus("! Could not refresh: " + ex.getMessage(), false);
                }
            }
        }.execute();
        if (tabs.getSelectedIndex() == 1) refreshHistory();
    }

    private void refreshHistory() {
        new SwingWorker<List<HistoryRow>, Void>() {
            @Override protected List<HistoryRow> doInBackground() throws Exception {
                return svc.history(100);
            }
            @Override protected void done() {
                try {
                    historyModel.setRowCount(0);
                    for (HistoryRow r : get()) {
                        historyModel.addRow(new Object[]{
                                r.vehicle(), r.slot(), r.entry().format(FMT),
                                r.exit() == null ? "still parked" : r.exit().format(FMT),
                                r.fee() == null ? "" : r.fee()});
                    }
                } catch (Exception ex) {
                    setStatus("! Could not load history: " + ex.getMessage(), false);
                }
            }
        }.execute();
    }

    // ---------- helpers ----------

    private JButton btn(String text, ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        buttons.add(b);
        return b;
    }

    private void setBusy(boolean busy) {
        for (JButton b : buttons) b.setEnabled(!busy);
        vehicleField.setEnabled(!busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void setStatus(String msg, boolean ok) {
        statusLabel.setText(msg);
        statusLabel.setForeground(ok ? OK : ERR);
    }

    private static JPanel swatch(Color c, String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JPanel box = new JPanel();
        box.setBackground(c);
        box.setPreferredSize(new Dimension(14, 14));
        box.setBorder(new LineBorder(c.darker()));
        p.add(box);
        p.add(new JLabel(text));
        return p;
    }

    private static Color colorFor(String status) {
        return switch (status) {
            case "FREE" -> new Color(0xC8E6C9);
            case "RESERVED" -> new Color(0xFFF0B3);
            default -> new Color(0xFFCDD2);
        };
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

    /** One parking bay, painted as a rounded card. */
    private class SlotCard extends JPanel {
        private final int number;
        private String status = "FREE";
        private String vehicle;

        SlotCard(int number) {
            this.number = number;
            setOpaque(false);
            setPreferredSize(new Dimension(150, 95));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (vehicle != null) vehicleField.setText(vehicle);
                }
            });
        }

        void update(Slot s) {
            status = s.status();
            vehicle = s.vehicle();
            setCursor(vehicle == null ? Cursor.getDefaultCursor()
                    : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(vehicle == null ? "Free slot" : "Click to select " + vehicle);
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = colorFor(status);
            g.setColor(c);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g.setColor(c.darker());
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g.setColor(new Color(0x263238));
            int cx = getWidth() / 2;
            g.setFont(getFont().deriveFont(Font.BOLD, 20f));
            centered(g, "Slot " + number, cx, 30);
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            centered(g, status, cx, 54);
            if (vehicle != null) {
                g.setFont(getFont().deriveFont(Font.PLAIN, 14f));
                centered(g, vehicle, cx, 76);
            }
            g.dispose();
        }

        private void centered(Graphics2D g, String s, int cx, int y) {
            g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
        }
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
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
