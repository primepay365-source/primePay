-- Rollback of 009-create-wallets-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_wallets CASCADE CONSTRAINTS PURGE;
