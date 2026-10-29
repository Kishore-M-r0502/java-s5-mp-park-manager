CREATE DATABASE IF NOT EXISTS parking_db;
USE parking_db;

-- One row per physical slot. The app seeds rows 1..PARK_SLOTS on startup.
-- UNIQUE(vehicle_no): a vehicle can hold at most one slot (NULLs are allowed many times).
CREATE TABLE IF NOT EXISTS slots (
    slot_no    INT PRIMARY KEY,
    status     ENUM('FREE','RESERVED','OCCUPIED') NOT NULL DEFAULT 'FREE',
    vehicle_no VARCHAR(20) NULL,
    UNIQUE KEY uq_slots_vehicle (vehicle_no)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reservations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_no     INT NOT NULL,
    vehicle_no  VARCHAR(20) NOT NULL,
    reserved_at DATETIME NOT NULL,
    expires_at  DATETIME NOT NULL,
    status      ENUM('ACTIVE','USED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    FOREIGN KEY (slot_no) REFERENCES slots(slot_no),
    INDEX idx_res_status_exp (status, expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS parking_records (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_no    INT NOT NULL,
    vehicle_no VARCHAR(20) NOT NULL,
    entry_time DATETIME NOT NULL,
    exit_time  DATETIME NULL,
    fee        DECIMAL(10,2) NULL,
    FOREIGN KEY (slot_no) REFERENCES slots(slot_no),
    INDEX idx_rec_vehicle_open (vehicle_no, exit_time)
) ENGINE=InnoDB;
