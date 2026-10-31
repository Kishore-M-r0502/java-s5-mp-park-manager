# Configuration and Capacity Changes

## Parking Slot Configuration

The default parking capacity is defined in `src/parking/Config.java`:

```java
public static final int PARK_SLOTS = 8;
```

To change the configured capacity:

1. Modify the value of `PARK_SLOTS`.
2. Rebuild the project.
3. Restart the application.
4. Verify that the required slots have been seeded into the MySQL database.

## Important Warnings

- Increasing the configured capacity allows the existing database seeding mechanism to add new slots.
- Decreasing the configured capacity does not automatically remove existing database entries.
- Do not manually delete slots that have active reservations, occupied vehicles, or associated parking records.
- The current implementation does not support changing the parking capacity dynamically while the application is running.

**Note:** Configuration changes require rebuilding and restarting the application.

## Steps
## Changing the number of slots

`PARK_SLOTS` is set in `src/parking/Config.java`.

- **Increasing it:** change the constant, run `./build.sh` and restart the app.
  The new slots are added automatically and existing data is kept.
- **Decreasing it:** do not just change the constant. The extra slot rows stay
  in MySQL and are still used by the app, so the counts will be wrong and cars
  can be parked in slots the GUI doesn't show. Before restarting:
  1. Exit or cancel every car in the slots being removed.
  2. Delete the extra rows (this also needs the history rows that point at them
     to be removed, so back up the data first if you need it):
     DELETE FROM parking_records WHERE slot_no > N;
     DELETE FROM reservations WHERE slot_no > N;
     DELETE FROM slots WHERE slot_no > N;
  (N is the new number of slots.)

### Charged fees
In `src/parking/Config.java` change the value of `RATE_PER_HOUR`
