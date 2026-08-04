@echo === 1. Schema ===

@include schema/01_member.sql
@include schema/02_place.sql
@include schema/03_survey.sql
@include schema/04_payment.sql
@include schema/05_course.sql
@include schema/06_chatbot.sql
@include schema/07_review.sql
@include schema/08_indexes.sql
@include schema/09_comments.sql

@echo === 2. Seed data ===

@include seed/01_test_member.sql
@include seed/02_travel_types.sql
@include seed/03_survey_questions.sql
@include seed/places/01_tour.sql
@include seed/places/02_restaurant.sql
@include seed/places/03_cafe.sql
@include seed/places/04_hotel.sql
@include seed/04_type_place_mapping.sql
@include seed/05_demo_reviews.sql

COMMIT;
@echo === Installation completed ===
