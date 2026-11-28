-- Rollback of 012-create-transaction-status-history-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_transaction_status_history CASCADE CONSTRAINTS PURGE;
