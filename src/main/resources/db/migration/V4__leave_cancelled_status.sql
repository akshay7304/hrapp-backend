-- Distinguish cancelled leave from rejected leave.
ALTER TABLE `leave_requests`
  MODIFY COLUMN `status` enum('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING';
