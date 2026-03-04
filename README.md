# MongoDB Backup & Restore Tool

A desktop application built with Java, Spring Boot, and Swing for backing up 
and restoring MongoDB collections. Features automatic MongoDB version detection, 
BSON-based backup format, and a clean GUI interface.

## What It Does

- Connect to any MongoDB instance (with or without authentication)
- Select specific collections to backup
- Save backup as a portable ZIP file containing BSON data
- Restore selected collections from any backup file
- Two restore modes: Drop & Restore or Replace/Update existing documents

## Features

| Feature | Details |
|---|---|
| Backup format | BSON inside ZIP (portable, compact) |
| Collection selection | Select all or specific collections |
| Restore modes | Drop & Restore / Replace by _id |
| Authentication | Supports username/password + auth source |
| Auto user creation | Creates DB user if not exists (no-auth MongoDB) |
| Batch insert | Inserts in batches of 1000 to avoid memory issues |
| Progress indicator | Live progress dialog during backup/restore |

## Project Structure
```
src/
├── BackUpAndRestoreModelApplication.java  # Spring Boot entry point + Swing launcher
├── model/
│   └── MongoConnectionConfig.java         # Connection config model
├── service/
│   ├── MongoConnectionService.java        # MongoDB connection & auth management
│   └── BackupRestoreService.java          # Core backup & restore logic (BSON/ZIP)
└── ui/
    ├── MainFrame.java                     # Main application window (Backup & Restore tabs)
    ├── CollectionSelectionDialog.java     # Collection picker dialog for restore
    └── ProgressDialog.java               # Progress indicator dialog
```

## Tech Stack

- Java 11+
- Spring Boot
- Swing (Desktop GUI)
- MongoDB Java Driver
- BSON (Binary JSON)
- ZIP compression

## Setup & Usage

### Prerequisites
- Java 11+
- Maven
- MongoDB instance (local or remote)

### Run
```bash
mvn spring-boot:run
```

### How to Backup
1. Enter MongoDB connection details (host, port, database, credentials)
2. Click **Connect & Load Collections**
3. Select collections to backup
4. Click **Backup Selected Collections**
5. Choose save location — file saved as `.zip`

### How to Restore
1. Go to **Restore** tab
2. Enter target MongoDB connection details
3. Choose restore mode:
   - **Drop & Restore** — drops existing collection and reimports
   - **Replace/Update** — updates documents matching by `_id`, inserts new ones
4. Click **Select Backup File & Restore**
5. Select collections from the backup to restore

## Restore Modes Explained

**Drop & Restore** — Completely removes the existing collection and 
recreates it from the backup. Use this when you want a clean restore.

**Replace/Update** — Keeps existing documents not in the backup. 
Only updates documents that match by `_id`. Safe for partial restores.

## Author

Lalit Kiran Nasery — [linkedin.com/in/lalit-nasery-a23b7b267](https://linkedin.com/in/lalit-nasery-a23b7b267)
