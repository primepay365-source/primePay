-- Rollback of 005-create-audit-logs-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_audit_logs CASCADE CONSTRAINTS PURGE;
