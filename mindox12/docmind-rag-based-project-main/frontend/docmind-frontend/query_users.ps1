docker exec doc-mind-postgres psql -U postgres -d docmind -c "SELECT id, username, password FROM users;"
