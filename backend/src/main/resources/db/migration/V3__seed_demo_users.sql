INSERT INTO users (id, demo_key, display_name, created_at)
VALUES
    ('a1111111-1111-4111-8111-111111111111', 'alice', 'Alice', '2026-10-30 00:00:00.000000'),
    ('b2222222-2222-4222-8222-222222222222', 'bob', 'Bob', '2026-10-30 00:00:00.000000')
ON DUPLICATE KEY UPDATE id = id;
