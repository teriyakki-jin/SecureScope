-- prev_hash 에 UNIQUE 제약 추가.
-- AuditService.append() 에서 동시 호출 시 SELECT findLatest() 가 같은 prevHash 를 반환해도
-- INSERT 시점에 DB 가 중복을 거부하므로 체인 분기(fork)를 방지.
-- (genesis 레코드의 prevHash = '000...0' 도 유일해야 하므로 NULL 허용 없음)
ALTER TABLE audit_logs
    ADD CONSTRAINT uq_audit_logs_prev_hash UNIQUE (prev_hash);
