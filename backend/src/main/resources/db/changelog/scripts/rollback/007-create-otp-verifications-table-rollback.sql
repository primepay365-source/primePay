-- Rollback of 007-create-otp-verifications-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_otp_verifications CASCADE CONSTRAINTS PURGE;
