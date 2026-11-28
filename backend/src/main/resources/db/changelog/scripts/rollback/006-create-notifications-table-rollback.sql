-- Rollback of 006-create-notifications-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_notifications CASCADE CONSTRAINTS PURGE;
