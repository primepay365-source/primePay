-- Rollback of 002-create-account-user-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE account_user CASCADE CONSTRAINTS PURGE;
