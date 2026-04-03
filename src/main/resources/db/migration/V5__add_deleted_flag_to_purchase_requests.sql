-- 削除フラグ関連カラムの追加
ALTER TABLE purchase_requests
ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN deleted_at TIMESTAMP,
ADD COLUMN deleted_by VARCHAR(50);

-- 削除フラグのインデックス追加（検索性能向上）
CREATE INDEX idx_deleted ON purchase_requests(deleted);

-- 既存データの削除フラグをfalseに設定（デフォルト値で自動設定されるが明示的に実行）
UPDATE purchase_requests SET deleted = FALSE WHERE deleted IS NULL;

-- コメント追加（ドキュメント目的）
COMMENT ON COLUMN purchase_requests.deleted IS '論理削除フラグ（true: 削除済み、false: 未削除）';
COMMENT ON COLUMN purchase_requests.deleted_at IS '削除日時（削除時に設定）';
COMMENT ON COLUMN purchase_requests.deleted_by IS '削除者のユーザー名（削除時に設定）';
