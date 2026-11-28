-- Rollback of 021-create-beneficiaries-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_beneficiaries CASCADE CONSTRAINTS PURGE;
