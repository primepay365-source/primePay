-- Rollback of 003-create-distributor-onboarding-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE distributor_onboarding CASCADE CONSTRAINTS PURGE;
