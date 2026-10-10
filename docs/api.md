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