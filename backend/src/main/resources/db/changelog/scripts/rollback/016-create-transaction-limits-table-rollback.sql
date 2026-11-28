-- Rollback of 016-create-transaction-limits-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_transaction_limits CASCADE CONSTRAINTS PURGE;
