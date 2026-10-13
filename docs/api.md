# HTTP API

Five endpoints. `/v3/api-docs` is the OpenAPI document, and it is the contract the UI's
TypeScript types are generated from — see [ui.md](ui.md#generated-api-types).

## Endpoints

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `GET` | `/messages/list` | VIEWER | One page of message summaries |
| `GET` | `/messages/{id}` | VIEWER | One message in full, including its body |
| `POST` | `/messages/delete` | OPERATOR | Permanently delete messages |
| `POST` | `/messages/release` | OPERATOR | Send messages onward to a real SMTP server |
| `GET` | `/v3/api-docs` | — | The OpenAPI document |

A VIEWER is refused the two mutating endpoints with 403. Everything else under
`/actuator/**` is OPERATOR-only, except `health`, `info` and `metrics`, which are open so a
probe can reach them.

### `GET /messages/list`

Returns a `PageResponse` with `content`, `number`, `size`, `totalElements`, `totalPages`,
`first` and `last`.

Paging and sorting use the standard Spring Data parameters: `?size=50&page=2` (page is
0-based), `?sort=from,asc`. Defaults to 25 per page, newest first. The sortable fields are
`receivedTimestamp`, `from`, `to` and `subject`; anything else is a 400 rather than a 500,
because the sort name is handed to Hibernate and an unknown one would otherwise surface as an
unresolved identifier. Every ordering gets a `receivedTimestamp` tiebreaker appended, so tied
rows cannot swap places between two polls and move across a page boundary.

Filtering is optional and combined with AND; every criterion is a case-insensitive substring
match.

| Parameter | Meaning |
| --- | --- |
| `subject` | Substring of the subject |
| `from` | Substring of the sender address |
| `to` | Substring of any envelope recipient |
| `receivedFrom` | Inclusive ISO-8601 lower bound on arrival |
| `receivedTo` | Inclusive ISO-8601 upper bound on arrival |

`receivedFrom` and `receivedTo` are inclusive at both ends, so setting them to the same
instant matches a message that arrived exactly then. That suits the UI, which fills the fields
in to the resolution the user picked rather than to a whole day. `totalElements` reflects the
filter, not the whole mailbox.

A malformed timestamp is a 400 from the converter rather than a silent no-match, which is the
honest answer for a request the server could not understand.

### `GET /messages/{id}`

Returns the message in full, including its `body` and the metadata of every part of its MIME
structure. 404 if no message has that id — it may have been deleted, or the id may never have
existed.

Attachments are **described, not served**: a part reports its filename, content type, decoded
size and disposition, and size is absent when the message did not declare one. A non-numeric id
is a 400.

### `POST /messages/delete`

```json
{"messageIds": [1, 2]}
```

Permanently removes the messages. A hard delete, not a soft one: the rows are gone.

Answers `{"successful": true}` or `{"successful": false, "errorMessage": "..."}`. A rejected
body is a 400 instead — `messageIds` absent, misspelled, null or empty, or naming more than
2000 messages — because deleting nothing while claiming success reads as though the messages
are gone. The limit is the most ids one page of the table can hold, which is the most a
selection can contain.

### `POST /messages/release`

Forwards the given messages to the SMTP server named by `spring.mail.host` and
`spring.mail.port`. All fields are optional; with only `messageIds` the original addresses are
used.

| Field | Meaning |
| --- | --- |
| `messageIds` | The messages to release |
| `overrideTo` | Whether to replace the recipients |
| `overrideToAddresses` | Comma-separated replacement recipients |
| `overrideFrom` | Whether to replace the sender |
| `overrideFromAddress` | The replacement sender |

An override asked for without naming addresses that parse is a 400, as is an override naming
a recipient the release allowlist refuses. Both are rejected up front, before anything is
attempted, since nothing was tried and nothing needs releasing again.

The response carries an outcome per requested id, in the order requested, and `successful` is
true only when every id was released. Delivery is not reversible, so a batch can partly
succeed: a partial failure leaves the successful ids released and the rest captured. The
per-id outcomes are what to consult before retrying, because retrying the whole batch re-sends
what already went out.

An override of the recipients replaces `To`, `Cc` and `Bcc` together: all three are cleared
before the override addresses are added. Every other header — `Reply-To`, `Message-ID`, `Date`
— survives from the captured message untouched.

Releasing mail that Mailoverlord cannot reach returns HTTP 200 with `successful: false` and
leaves the messages captured, so you can fix the target and try again.

### Recipient allowlist

`mailoverlord.release.allowed-destinations` restricts who mail may be released to. It is a
list of glob patterns; `*` matches any run of characters, and an entry with no `@` is shorthand
for `*@pattern`. The local and domain halves are matched separately, fully anchored and
case-insensitively, so `*@example.com` does not also match `user@example.com.evil.test`.

Every resulting recipient is checked, `Cc` and `Bcc` included. Unset means unrestricted, and
the app logs a warning at startup saying so.

## Asserting on mail from a test

The list endpoint is the whole mechanism; there is no wait-for-message call, and none is
needed. Two properties of the existing API make polling safe, and both are worth knowing
because they are what a test author would otherwise try to work around.

`receivedFrom` is an inclusive ISO-8601 lower bound. Capture the current instant *before*
triggering the action under test, then poll with that value: everything your action produced
arrives at or after it, and mail from an earlier test run cannot leak into the result.

Every ordering gets a `receivedTimestamp` tiebreaker appended, so rows cannot swap places
between two polls. Without that, a message arriving mid-poll could move across a page
boundary and a test could read it twice, or miss it.

The pattern, then, with `Welcome to Shop` as a subject that
[`scripts/seed-messages.py`](../scripts/seed-messages.py) actually sends:

```bash
# 1. Everything from here on is what the action under test produces.
BEFORE=$(date -u +%Y-%m-%dT%H:%M:%SZ)

# 2. Trigger the thing that should send mail — here, the application's own code path.
python3 scripts/seed-messages.py

# 3. Poll with a deadline. Do not loop forever: a timeout has to fail the test.
until [ "$(curl -s -u operator:$PASSWORD \
    "http://localhost:8080/messages/list?subject=Welcome&receivedFrom=$BEFORE" \
    | jq '.totalElements')" -gt 0 ]; do
  sleep 0.5
  [ "$(date +%s)" -gt "$(( $(date +%s) + 30 ))" ] && { echo "no mail within 30s"; exit 1; }
done
```

Then fetch the body once the message exists:

```bash
ID=$(curl -s -u operator:$PASSWORD \
  "http://localhost:8080/messages/list?subject=Welcome&receivedFrom=$BEFORE" | jq -r '.content[0].id')
curl -s -u operator:$PASSWORD "http://localhost:8080/messages/$ID" | jq -r '.body'
```

`totalElements` reflects the filter rather than the whole mailbox, so it is the number to
assert on; `content` holds the matching summaries.

### Authenticating

The default security mode is `basic`, not `none` — the test suite sets `none`, so a packaged
jar behaves differently from the suite and an unauthenticated request gets a 401. Pass
credentials per request with `-u`, or set `MAILOVERLORD_PASSWORD`, whose default is
`change-me-on-deploy`.

Neither mutating endpoint demands a CSRF token, so a `curl` or test client needs no cookie
handling at all. A browser client does, the way sessions
[do for logout](authentication.md#browser-login-against-a-provider-oidc).

### Two limits worth knowing

**`sizeBytes` loads the blob.** Listing a page reads each row's LOB even though nothing else
in a summary needs it, so a poll of a large page is not free. Polling a narrow filter is
cheap; polling a 100-message page in a tight loop is not.

**Only headers are searchable.** `subject`, `from` and `to` are columns; the message body is
a blob and is never queried. Filtering matches a substring of a header, never the content.

Two things the API cannot express yet, both visible while writing the example above. There is
no filter on `releasedTimestamp`, so "did my release actually go out?" means fetching the
message and checking that field rather than filtering for it. And there is no way to delete or
release by a filter, only by explicit ids, so cleanup has to collect ids from a list first.

## Response shapes

Read endpoints answer with RFC 9457 problem details on error (`spring.mvc.problemdetails.enabled`).
The mutating endpoints instead return the `{successful, errorMessage}` envelope above, and
report per-id failures in the body rather than with a status code, so a partially successful
request is still a 200.

## Two implementation notes

**Subjects are a column.** `subject` is a database column rather than a header parsed out of
the stored content on each request. The database cannot read a header out of a MIME blob, so
ordering by subject has to become an `ORDER BY`, and it has to happen in SQL rather than in
Java after the rows come back: a page of messages cannot be sorted by subject otherwise. It is
decoded from RFC 2047 and truncated once, when the message is captured.

**`sizeBytes` loads the blob.** `sizeBytes` is the length of the stored content, so listing a
page still reads each row's LOB even though nothing else in a summary needs it. The summaries
themselves do omit the body, which is what keeps a page of large messages small over the wire.

## Versioning

The paths are unversioned. `springdoc` publishes the contract at `/v3/api-docs` and the UI's
types are generated from it, so treat a field rename as a breaking change for any UI built
before it.