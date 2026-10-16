CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Audit logs are append-only';
CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Audit logs are append-only';
CREATE TRIGGER approval_record_no_update BEFORE UPDATE ON approval_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Approval evidence is immutable';
CREATE TRIGGER approval_record_no_delete BEFORE DELETE ON approval_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Approval evidence is immutable';
CREATE TRIGGER proposal_no_update BEFORE UPDATE ON agent_proposal FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Proposal snapshot is immutable';
CREATE TRIGGER proposal_no_delete BEFORE DELETE ON agent_proposal FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Proposal snapshot is immutable';
