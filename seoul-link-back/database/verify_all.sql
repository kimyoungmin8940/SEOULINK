@echo === Verification ===

@include verify/01_verify_tables.sql
@include verify/02_verify_seed_counts.sql
@include verify/03_find_duplicate_places.sql
@include verify/04_verify_foreign_keys.sql
@include verify/05_verify_backend52_columns.sql
@include verify/06_verify_member_social_login.sql
@include verify/07_verify_travel_survey_people_count_removed.sql
@include verify/08_verify_latest_integrity.sql
@include verify/09_verify_theme_course.sql

@echo === Verification completed ===
