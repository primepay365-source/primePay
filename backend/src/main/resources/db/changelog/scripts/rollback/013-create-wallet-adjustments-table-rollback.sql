-- Rollback of 013-create-wallet-adjustments-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_wallet_adjustments CASCADE CONSTRAINTS PURGE;
