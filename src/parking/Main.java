package parking;

import parking.ParkingService.ParkingException;
import parking.ParkingService.Receipt;
import parking.ParkingService.Slot;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class Main {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    public static void main(String[] args) throws Exception {
        Db.seedSlots();
        ParkingService svc = new ParkingService();

        // Scheduled task: release expired reservations every EXPIRY_CHECK_INTERVAL.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reservation-expiry");
            t.setDaemon(true);
            return t;
        });
        long period = Config.EXPIRY_CHECK_INTERVAL.toSeconds();
        scheduler.scheduleAtFixedRate(() -> {
            try {   // an uncaught exception would silently cancel future runs
                int n = svc.expireReservations();
                if (n > 0) System.out.println("\n[scheduler] released " + n + " expired reservation(s)");
            } catch (Exception e) {
                System.err.println("[scheduler] " + e.getMessage());
            }
        }, period, period, TimeUnit.SECONDS);

        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.print("""

                        === Smart Parking (%d slots) ===
                        1) Show slots      2) Reserve      3) Vehicle entry
                        4) Vehicle exit    5) Cancel reservation
                        6) Simulate concurrent cars        0) Quit
                        > """.formatted(Config.PARK_SLOTS));
                String choice = in.nextLine().trim();
                try {
                    switch (choice) {
                        case "1" -> showSlots(svc);
                        case "2" -> {
                            int s = svc.reserve(ask(in));
                            System.out.println("Reserved slot " + s + " (held for "
                                    + Config.RESERVATION_HOLD.toMinutes() + " min).");
                        }
                        case "3" -> System.out.println("Parked in slot " + svc.entry(ask(in)) + ".");
                        case "4" -> printReceipt(svc.exit(ask(in)));
                        case "5" -> {
                            svc.cancelReservation(ask(in));
                            System.out.println("Reservation cancelled.");
                        }
                        case "6" -> simulate(svc);
                        case "0" -> { return; }
                        default -> System.out.println("Unknown option.");
                    }
                } catch (ParkingException e) {
                    System.out.println("! " + e.getMessage());
                }
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static String ask(Scanner in) {
        System.out.print("Vehicle number: ");
        return in.nextLine().trim().toUpperCase();
    }

    private static void showSlots(ParkingService svc) throws ParkingException {
        List<Slot> slots = svc.listSlots();
        long free = slots.stream().filter(s -> s.status().equals("FREE")).count();
        for (Slot s : slots) {
            System.out.printf("Slot %d: %-9s %s%n", s.number(), s.status(),
                    s.vehicle() == null ? "" : s.vehicle());
        }
        System.out.println("Available: " + free + "/" + slots.size());
    }

    private static void printReceipt(Receipt r) {
        System.out.printf("Vehicle %s left slot %d%n  In : %s%n  Out: %s%n  Time: %d min%n  Fee : %s%n",
                r.vehicle(), r.slot(), r.entry().format(FMT), r.exit().format(FMT), r.minutes(), r.fee());
    }

    /** More cars than slots, all arriving at once, to show the locking works. */
    private static void simulate(ParkingService svc) throws InterruptedException {
        int cars = Config.PARK_SLOTS + 4;
        ExecutorService pool = Executors.newFixedThreadPool(cars);
        CountDownLatch gate = new CountDownLatch(1);
        for (int i = 1; i <= cars; i++) {
            String v = String.format("SIM-%02d", i);
            pool.submit(() -> {
                try {
                    gate.await();
                    int slot = svc.entry(v);
                    System.out.println(v + " parked in slot " + slot);
                    Thread.sleep(ThreadLocalRandom.current().nextInt(1500, 3000));
                    Receipt r = svc.exit(v);
                    System.out.println(v + " left slot " + r.slot() + ", fee " + r.fee());
                } catch (ParkingException e) {
                    System.out.println(v + " -> " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        gate.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println("Simulation finished.");
    }
}
