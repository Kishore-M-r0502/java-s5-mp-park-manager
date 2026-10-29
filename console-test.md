kishoremr@DESKTOP-HFQVPFE MINGW64 /d/java-s5-park-manager/java-mp-park-manager
$ ./run.sh console

=== Smart Parking (8 slots) ===
1) Show slots      2) Reserve      3) Vehicle entry
4) Vehicle exit    5) Cancel reservation
6) Simulate concurrent cars        0) Quit
>1
Slot 1: FREE      
Slot 2: FREE      
Slot 3: FREE      
Slot 4: FREE      
Slot 5: FREE      
Slot 6: FREE      
Slot 7: FREE      
Slot 8: FREE      
Available: 8/8

=== Smart Parking (8 slots) ===
1) Show slots      2) Reserve      3) Vehicle entry
4) Vehicle exit    5) Cancel reservation
6) Simulate concurrent cars        0) Quit
>3
Vehicle number: 12
Parked in slot 1.

=== Smart Parking (8 slots) ===
1) Show slots      2) Reserve      3) Vehicle entry
4) Vehicle exit    5) Cancel reservation
6) Simulate concurrent cars        0) Quit
>4
Vehicle number: 12
Vehicle 12 left slot 1
  In : 29 Oct 2026 20:30:12
  Out: 29 Oct 2026 20:32:04
  Time: 2 min
  Fee : 20.00

=== Smart Parking (8 slots) ===
1) Show slots      2) Reserve      3) Vehicle entry
4) Vehicle exit    5) Cancel reservation
6) Simulate concurrent cars        0) Quit
>2
Vehicle number: 13
Reserved slot 1 (held for 15 min).

=== Smart Parking (8 slots) ===
1) Show slots      2) Reserve      3) Vehicle entry
4) Vehicle exit    5) Cancel reservation
6) Simulate concurrent cars        0) Quit
>5
Vehicle number: 13
Reservation cancelled.

=== Smart Parking (8 slots) ===
1) Show slots      2) Reserve      3) Vehicle entry
4) Vehicle exit    5) Cancel reservation
6) Simulate concurrent cars        0) Quit
>6
SIM-11 parked in slot 1
SIM-01 parked in slot 2
SIM-02 parked in slot 3
SIM-03 parked in slot 4
SIM-04 parked in slot 5
SIM-05 parked in slot 6
SIM-06 parked in slot 7
SIM-12 parked in slot 8
SIM-07 -> Parking is full.
SIM-08 -> Parking is full.
SIM-09 -> Parking is full.
SIM-10 -> Parking is full.
SIM-05 left slot 6, fee 20.00
SIM-03 left slot 4, fee 20.00
SIM-06 left slot 7, fee 20.00
SIM-11 left slot 1, fee 20.00
SIM-04 left slot 5, fee 20.00
SIM-02 left slot 3, fee 20.00
SIM-01 left slot 2, fee 20.00
SIM-12 left slot 8, fee 20.00
Simulation finished.

=== Smart Parking (8 slots) ===
1) Show slots      2) Reserve      3) Vehicle entry
4) Vehicle exit    5) Cancel reservation
6) Simulate concurrent cars        0) Quit
>0



kishoremr@DESKTOP-HFQVPFE MINGW64 /d/java-s5-park-manager/java-mp-park-manager
$ mysql -u root -p -D parking_db

mysql> SELECT * FROM slots;
+---------+--------+------------+
| slot_no | status | vehicle_no |
+---------+--------+------------+
|       1 | FREE   | NULL       |
|       2 | FREE   | NULL       |
|       3 | FREE   | NULL       |
|       4 | FREE   | NULL       |
|       5 | FREE   | NULL       |
|       6 | FREE   | NULL       |
|       7 | FREE   | NULL       |
|       8 | FREE   | NULL       |
+---------+--------+------------+
8 rows in set (0.00 sec)

mysql> SELECT * FROM parking_records;
+----+---------+------------+---------------------+---------------------+-------+
| id | slot_no | vehicle_no | entry_time          | exit_time           | fee   |
+----+---------+------------+---------------------+---------------------+-------+
|  1 |       1 | 12         | 2026-10-29 20:30:12 | 2026-10-29 20:32:05 | 20.00 |
|  2 |       1 | SIM-11     | 2026-10-29 20:33:02 | 2026-10-29 20:33:04 | 20.00 |
|  3 |       2 | SIM-01     | 2026-10-29 20:33:02 | 2026-10-29 20:33:05 | 20.00 |
|  4 |       3 | SIM-02     | 2026-10-29 20:33:02 | 2026-10-29 20:33:05 | 20.00 |
|  5 |       4 | SIM-03     | 2026-10-29 20:33:02 | 2026-10-29 20:33:04 | 20.00 |
|  6 |       5 | SIM-04     | 2026-10-29 20:33:02 | 2026-10-29 20:33:05 | 20.00 |
|  7 |       6 | SIM-05     | 2026-10-29 20:33:02 | 2026-10-29 20:33:04 | 20.00 |
|  8 |       7 | SIM-06     | 2026-10-29 20:33:02 | 2026-10-29 20:33:04 | 20.00 |
|  9 |       8 | SIM-12     | 2026-10-29 20:33:02 | 2026-10-29 20:33:05 | 20.00 |
+----+---------+------------+---------------------+---------------------+-------+
9 rows in set (0.00 sec)

mysql> SELECT * FROM reservations;
+----+---------+------------+---------------------+---------------------+-----------+
| id | slot_no | vehicle_no | reserved_at         | expires_at          | status    |
+----+---------+------------+---------------------+---------------------+-----------+
|  1 |       1 | 13         | 2026-10-29 20:32:35 | 2026-10-29 20:47:35 | CANCELLED |
+----+---------+------------+---------------------+---------------------+-----------+
1 row in set (0.00 sec)

mysql> exit;
Bye