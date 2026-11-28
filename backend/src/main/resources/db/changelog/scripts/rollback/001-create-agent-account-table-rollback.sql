-- Rollback of 001-create-agent-account-table.sql (run by Liquibase only: liquibase rollback).
DROP TABLE agent_account CASCADE CONSTRAINTS PURGE;
