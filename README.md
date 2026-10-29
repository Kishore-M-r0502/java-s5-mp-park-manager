# Smart Parking Management System

No Maven needed: just a JDK (17+) and the MySQL JDBC driver jar.

## Setup
Create the tables:  `mysql -u root -p < sql/schema.sql`

## Build and run
Linux/macOS: `./build.sh` then `./run.sh` (GUI) or `./run.sh console`
Windows:     `build.bat` then `run.bat` (GUI) or `run.bat console`

Slots are set by `Config.PARK_SLOTS` (8).

## Database configuration
Configure environment with your shell's commands so Java can read them at runtime via System.getEnv()

- The default configured user is `root` (set using `DB_USER`)
- The password is to be set with `DB_PASSWORD`(the password is set during installation.Use the password for the user the database resides in)

## Components used
- Swing for GUI
- MySQL 8.0.46(Windows x86_64)

