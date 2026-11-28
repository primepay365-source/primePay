-- Rollback of 011-create-wallet-ledger-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_wallet_ledger CASCADE CONSTRAINTS PURGE;
