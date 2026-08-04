SELECT constraint_name, table_name, r_constraint_name, status FROM user_constraints WHERE constraint_type = 'R' ORDER BY table_name, constraint_name;

