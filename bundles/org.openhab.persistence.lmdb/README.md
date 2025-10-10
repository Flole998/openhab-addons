# LMDB Persistence

The [LMDB](http://www.lmdb.tech/doc/) persistence service is based on a simple key-value store that only saves the last value.
LMDB is useful for restoring items that have the `restoreOnStartup` strategy because other persistence options have some drawbacks if only the last value is needed on restarts.

Some advantages of LMDB compared to other persistence services:

- Lightning fast read/write performance
- Very small memory footprint
- Zero-copy architecture for efficiency
- ACID transactions
- No complex installation required

Some disadvantages of LMDB persistence compared to other services:

- It can only store one value per item (no historical data)
- It is only possible to query the last value and not other historic values

## Supported Platforms

The LMDB persistence service includes native libraries for the following platforms:

- Linux x86_64
- Linux ARM64/aarch64 (including Raspberry Pi 4/5 with 64-bit OS)
- macOS x86_64
- Windows x86_64

## Configuration

This service requires no configuration and is ready to use after installation.

## Features

- Stores the last known state of items
- Supports all openHAB item types
- Automatic database management
- Thread-safe operations
- ACID transactions for data integrity
