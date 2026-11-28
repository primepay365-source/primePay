-- Rollback of 010-create-transactions-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_transactions CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE pp_txn_ref_seq;
