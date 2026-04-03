-- テストユーザーデータ
-- パスワードは全て "password" をBCryptでハッシュ化したもの
INSERT INTO users (username, password, name, department, email, created_at, updated_at) VALUES
('yamada', '$2a$10$4.ryeIkQLgmO7z9mlcFjhO0qnD0Y7oUvxp2OH2AMgEtzGFG12UTOu', '山田太郎', '開発部', 'yamada@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('sato', '$2a$10$4.ryeIkQLgmO7z9mlcFjhO0qnD0Y7oUvxp2OH2AMgEtzGFG12UTOu', '佐藤花子', '管理部', 'sato@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('suzuki', '$2a$10$4.ryeIkQLgmO7z9mlcFjhO0qnD0Y7oUvxp2OH2AMgEtzGFG12UTOu', '鈴木一郎', '営業部', 'suzuki@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ロール設定
INSERT INTO user_roles (user_id, role) VALUES
(1, 'ROLE_USER'),
(2, 'ROLE_USER'),
(2, 'ROLE_APPROVER'),
(3, 'ROLE_USER');
