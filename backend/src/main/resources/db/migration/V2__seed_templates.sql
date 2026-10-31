INSERT INTO templates (id, title, category, description, cover_path, required_image_count, accepted_mime_types, max_file_size_bytes, created_at)
VALUES
    ('c1111111-1111-4111-8111-111111111111', '山海明信片', '旅行影像', '上传两张旅途照片，组合成一张旅行明信片。', 'demo/covers/postcard.svg', 2, JSON_ARRAY('image/jpeg', 'image/png'), 5242880, '2026-10-30 00:00:00.000000'),
    ('c2222222-2222-4222-8222-222222222222', '节日问候卡', '节日创意', '上传一张照片，制作一张简洁的节日卡片。', 'demo/covers/greeting.svg', 1, JSON_ARRAY('image/jpeg', 'image/png'), 5242880, '2026-10-30 00:00:00.000000'),
    ('c3333333-3333-4333-8333-333333333333', '周末拼贴', '日常记录', '上传三张日常照片，制作一张拼贴记录。', 'demo/covers/collage.svg', 3, JSON_ARRAY('image/jpeg', 'image/png'), 5242880, '2026-10-30 00:00:00.000000')
ON DUPLICATE KEY UPDATE id = id;
