-- Legacy-compatible full reinstall entry point.
-- WARNING: reset/01_drop_tables.sql deletes all existing SeoulLink tables and data.
-- Run this file with DBeaver Alt+X from the database directory.

@echo === SeoulLink full database reinstall ===

@include reset/01_drop_tables.sql
@include install_all.sql
@include verify_all.sql

@echo === SeoulLink database is ready ===
