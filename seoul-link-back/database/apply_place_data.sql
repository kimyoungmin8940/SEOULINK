@echo === Apply latest place data ===

@include seed/places/01_tour.sql
@include seed/places/02_restaurant.sql
@include seed/places/03_cafe.sql
@include seed/places/04_hotel.sql

COMMIT;
@echo === Place data applied ===
