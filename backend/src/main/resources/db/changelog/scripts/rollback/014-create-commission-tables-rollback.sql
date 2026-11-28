-- Rollback of 014-create-commission-tables.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_commission_transactions CASCADE CONSTRAINTS PURGE;
DROP TABLE pp_commission_distribution CASCADE CONSTRAINTS PURGE;
DROP TABLE pp_commission_rules CASCADE CONSTRAINTS PURGE;
