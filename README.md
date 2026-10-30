# Smart Parking Management System

No Maven needed: just a JDK (17+) and the MySQL JDBC driver jar.

## Setup
1. Create the tables:  `mysql -u root -p < sql/schema.sql`
2. Download the driver jar and put it in `lib/`:
   https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
3. (Optional) credentials: `export DB_USER=root DB_PASSWORD=yourpass`
   (Windows: `set DB_USER=root` and `set DB_PASSWORD=yourpass`)

## Build and run
Linux/macOS: `./build.sh` then `./run.sh` (GUI) or `./run.sh console`
Windows:     `build.bat` then `run.bat` (GUI) or `run.bat console`

Slots are set by `Config.PARK_SLOTS` (8).
