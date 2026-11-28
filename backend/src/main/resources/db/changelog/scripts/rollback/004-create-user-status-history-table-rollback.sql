-- Rollback of 004-create-user-status-history-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_user_status_history CASCADE CONSTRAINTS PURGE;
