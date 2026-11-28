-- Rollback of 015-insert-commission-rules.sql (run by Liquibase only: liquibase rollback).
DELETE FROM pp_commission_distribution WHERE rule_id IN (SELECT id FROM pp_commission_rules WHERE rule_code IN ('CR-PAYOUT-01', 'CR-BILL-01', 'CR-ADDF-01'));
DELETE FROM pp_commission_rules WHERE rule_code IN ('CR-PAYOUT-01', 'CR-BILL-01', 'CR-ADDF-01');
