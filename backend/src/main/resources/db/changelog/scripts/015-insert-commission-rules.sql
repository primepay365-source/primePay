-- 015: starting commission rules (illustrative rates; the Platform Admin changes them under Commission rules).
-- Withdrawals and bill payments pay the transacting account and every level above it.
-- Add funds pays only the levels ABOVE the account that adds funds: on 1,000 with a 2% gateway charge,
-- a Super Distributor is credited 975, a Master 970, a Distributor 965 and a Retailer 960.
INSERT INTO pp_commission_rules (rule_code, service_code, calc_type, effective_from, status, created_at)
VALUES ('CR-PAYOUT-01', 'PAYOUT', 'PERCENT', TIMESTAMP '2026-01-01 00:00:00', 'ACTIVE', SYSTIMESTAMP);
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'RETAILER', 0.10, 0.01, 500 FROM pp_commission_rules WHERE rule_code = 'CR-PAYOUT-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'DISTRIBUTOR', 0.20, 0.01, 500 FROM pp_commission_rules WHERE rule_code = 'CR-PAYOUT-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'MASTER_DISTRIBUTOR', 0.15, 0.01, 500 FROM pp_commission_rules WHERE rule_code = 'CR-PAYOUT-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'SUPER_DISTRIBUTOR', 0.10, 0.01, 500 FROM pp_commission_rules WHERE rule_code = 'CR-PAYOUT-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'PLATFORM', 0.05, 0.01, 500 FROM pp_commission_rules WHERE rule_code = 'CR-PAYOUT-01';

INSERT INTO pp_commission_rules (rule_code, service_code, calc_type, effective_from, status, created_at)
VALUES ('CR-BILL-01', 'BILL_PAYMENT', 'PERCENT', TIMESTAMP '2026-01-01 00:00:00', 'ACTIVE', SYSTIMESTAMP);
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'RETAILER', 0.10, 0.01, 200 FROM pp_commission_rules WHERE rule_code = 'CR-BILL-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'DISTRIBUTOR', 0.05, 0.01, 100 FROM pp_commission_rules WHERE rule_code = 'CR-BILL-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'MASTER_DISTRIBUTOR', 0.03, 0.01, 60 FROM pp_commission_rules WHERE rule_code = 'CR-BILL-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'SUPER_DISTRIBUTOR', 0.02, 0.01, 40 FROM pp_commission_rules WHERE rule_code = 'CR-BILL-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate, min_amount, max_amount) SELECT id, 'PLATFORM', 0.02, 0.01, 40 FROM pp_commission_rules WHERE rule_code = 'CR-BILL-01';

INSERT INTO pp_commission_rules (rule_code, service_code, calc_type, effective_from, status, created_at)
VALUES ('CR-ADDF-01', 'ADD_FUNDS', 'PERCENT', TIMESTAMP '2026-01-01 00:00:00', 'ACTIVE', SYSTIMESTAMP);
INSERT INTO pp_commission_distribution (rule_id, level_role, rate) SELECT id, 'PLATFORM', 0.5 FROM pp_commission_rules WHERE rule_code = 'CR-ADDF-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate) SELECT id, 'SUPER_DISTRIBUTOR', 0.5 FROM pp_commission_rules WHERE rule_code = 'CR-ADDF-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate) SELECT id, 'MASTER_DISTRIBUTOR', 0.5 FROM pp_commission_rules WHERE rule_code = 'CR-ADDF-01';
INSERT INTO pp_commission_distribution (rule_id, level_role, rate) SELECT id, 'DISTRIBUTOR', 0.5 FROM pp_commission_rules WHERE rule_code = 'CR-ADDF-01';
