# Loading a portfolio

`POST /portfolio/import` takes a semicolon-separated CSV and creates whatever the rows refer to and
does not exist yet: accounts, instruments and holdings. `GET /portfolio/import/template` returns
the header to fill in, produced from the same constant the parser reads so the two cannot drift
apart, followed by two example rows. A line starting with `#` is skipped, so the examples can stay
in the file.

```
account;accountType;institution;instrument;isinOrTicker;quantity;averageCost
# Sample Broker;PEA;Sample Bank;Sample S&P 500 ETF;FR0011550185;12;26.65
# Sample Broker;CTO;Sample Bank;Sample Bank Share;GLE.PA;10;
```

`isinOrTicker` is whatever identifies the instrument: an ISIN, a ticker, or a provider id such as
`bitcoin`. The import first looks for an existing instrument with that ISIN or source reference.
Failing that, it asks Yahoo Finance, then Amundi (ISINs only), the only price sources able to look
an instrument up, and keeps the first EUR answer: an Amundi ETF known to both comes in as its Yahoo
listing, with intraday prices, rather than as a fund priced once a day, unless Yahoo's price check
times out during the lookup, in which case Amundi's priced answer wins. A CoinGecko coin, an SG
Sirius fund or a manually priced instrument is not created by the import: add its line in the app first
("Ajouter une ligne"), and the import then finds it by its source reference. When several titles share
one ISIN, the import picks the one the account already holds, else the first EUR Yahoo one, else the
finds it by its source reference. Leave `averageCost` empty for a holding
with no known cost basis.

Two properties worth knowing before running it:

- **All or nothing.** One unreadable or unresolvable row and nothing is written; the 422 lists
  every refused row with its line number, so the file is fixed in one pass rather than one deploy
  at a time. Each error carries a code and the offending token, never a sentence: the frontend
  translates the codes itself.
- **It updates, it never deletes.** A row whose (account, instrument) pair already exists updates
  its quantity and cost basis, which makes replaying a corrected file safe. A holding removed from
  the file stays in the database: deleting is an explicit `DELETE /holdings/{id}`.

`GET /portfolio/export` is the inverse operation and a one-request backup. Take one before
importing over an existing portfolio, since an import overwrites quantities silently.
