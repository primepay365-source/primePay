# Reference platform research

**Status: not done yet.** None of the 12 reference URLs has been inspected in this project so far, so this file records no observations. Nothing below should be read as a finding.

When the research is done, only the **public** pages (login pages, marketing pages) will be inspected. Screens behind a login won't be described unless you provide authorized access, and they'll be marked as such. The references are for functional inspiration only: no branding, logos, code or exact layouts are copied.

The existing repo shows the earlier frontend was modelled on one reference (the old `src/data.js` said "copied from the Payswitch screenshots"). That data has been removed because it contained real people's personal details.

| # | URL | Public pages seen | Verified functionality | Screen patterns | Missing / needs confirmation | PrimePay opportunity |
|---|---|---|---|---|---|---|
| 1 | https://payswitch.in/retailer/ | — | — | — | — | — |
| 2 | https://portal.flipypay.com/login | — | — | — | — | — |
| 3 | https://paytravel.co.in/MemberLogin.aspx | — | — | — | — | — |
| 4 | https://n1pay.in/ | — | — | — | — | — |
| 5 | https://masterpay.co.in/ | — | — | — | — | — |
| 6 | https://greenpay.co.in/retailer/walletLoadReport | — | — | — | — | — |
| 7 | https://retailer.payswitch.co.in/wallet | — | — | — | — | — |
| 8 | https://dashboard.flipypay.in/ | — | — | — | — | — |
| 9 | https://member.paytravel.in/member/login | — | — | — | — | — |
| 10 | https://partner.n1pay.in/login.aspx | — | — | — | — | — |
| 11 | https://m.masterpay.pro/login_page | — | — | — | — | — |
| 12 | https://vendor.greenpaynetwork.co.in/retailer/WalletLoadReportPGT10 | — | — | — | — | — |

## Assumptions made without the research

These came from the spec, not from the reference sites, and should be checked against them:

- Wallet loads are bank transfers (NEFT/RTGS/IMPS/UPI) that an admin matches and approves, not instant gateway top-ups.
- The main Retailer service is a bank payout (IMPS/NEFT) that earns commission.
- Commission is credited to the wallet when the transaction succeeds, not settled in batches.
