@echo === Existing DB upgrade ===

@include migrations/004_add_survey_travel_fields.sql
@include migrations/005_member_social_login.sql
@include migrations/006_drop_travel_survey_people_count.sql
@include migrations/007_chatbot_conversation_id.sql
@include migrations/008_normalize_review_companions.sql
@include migrations/009_payment_integrity.sql
@include migrations/010_add_theme_course_source_key.sql

COMMIT;
@echo === Upgrade completed ===
