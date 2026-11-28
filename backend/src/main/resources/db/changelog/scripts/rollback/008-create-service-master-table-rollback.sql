-- Rollback of 008-create-service-master-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE pp_service_master CASCADE CONSTRAINTS PURGE;
