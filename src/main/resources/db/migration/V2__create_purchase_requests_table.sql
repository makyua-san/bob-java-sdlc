CREATE TABLE purchase_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id),
    item_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10, 2) NOT NULL CHECK (unit_price >= 0),
    purchase_reason VARCHAR(500) NOT NULL,
    desired_delivery_date DATE,
    remarks VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    approver_id BIGINT REFERENCES users(id),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50) NOT NULL
);

COMMENT ON TABLE purchase_requests IS '備品購入申請';
COMMENT ON COLUMN purchase_requests.requester_id IS '申請者ID';
COMMENT ON COLUMN purchase_requests.item_name IS '備品名';
COMMENT ON COLUMN purchase_requests.quantity IS '数量';
COMMENT ON COLUMN purchase_requests.unit_price IS '単価';
COMMENT ON COLUMN purchase_requests.purchase_reason IS '購入理由';
COMMENT ON COLUMN purchase_requests.desired_delivery_date IS '希望納期';
COMMENT ON COLUMN purchase_requests.remarks IS '備考';
COMMENT ON COLUMN purchase_requests.status IS 'ステータス（DRAFT/SUBMITTED/APPROVED/REJECTED）';
COMMENT ON COLUMN purchase_requests.approver_id IS '承認者ID';
COMMENT ON COLUMN purchase_requests.rejection_reason IS '却下理由';
