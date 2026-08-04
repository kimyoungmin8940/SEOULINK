SELECT api_provider, api_place_id, COUNT(*) AS duplicate_count FROM PLACES GROUP BY api_provider, api_place_id HAVING COUNT(*) > 1;

