CREATE INDEX idx_purchase_requests_status ON purchase_requests(status);
CREATE INDEX idx_purchase_requests_requester_id ON purchase_requests(requester_id);
CREATE INDEX idx_purchase_requests_created_at ON purchase_requests(created_at);
CREATE INDEX idx_purchase_requests_status_requester ON purchase_requests(status, requester_id);
