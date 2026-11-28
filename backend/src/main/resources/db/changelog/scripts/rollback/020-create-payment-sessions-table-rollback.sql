-- Rollback of 020-create-payment-sessions-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_payment_sessions CASCADE CONSTRAINTS PURGE;
