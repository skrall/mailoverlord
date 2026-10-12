#!/usr/bin/env python3
"""Seed a Mailoverlord instance with test messages over SMTP.

Mailoverlord captures mail instead of delivering it, so seeding it needs nothing but a
socket: point this at the SMTP port and send. Standard library only, so it runs
anywhere Python does and needs nothing installed.

    python3 scripts/seed-messages.py                       # localhost:2025
    python3 scripts/seed-messages.py --host mail.example  # somewhere else
    python3 scripts/seed-messages.py --list               # what the fixtures cover
    python3 scripts/seed-messages.py --only attachments   # one fixture

The fixtures are chosen to hit the shapes Mailoverlord actually treats differently
rather than to be a pile of lookalike mail. Notes on what each one is for are in the
FIXTURES table below; the interesting ones are the envelope-versus-header cases, since
Mailoverlord records envelope recipients at capture and only keeps Cc/Bcc inside the
stored MIME.
"""

from __future__ import annotations

import argparse
import base64
import smtplib
import sys
import textwrap
from dataclasses import dataclass
from email.message import EmailMessage

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 2025


@dataclass(frozen=True)
class Fixture:
    """One canned message: an envelope, a set of headers, and a body."""

    name: str
    subject: str | None
    sender: str
    to: tuple[str, ...]
    body: str | None = None
    html: str | None = None
    cc: tuple[str, ...] = ()
    bcc: tuple[str, ...] = ()
    attachment: tuple[str, bytes, str] | None = None
    extra_headers: tuple[tuple[str, str], ...] = ()
    note: str = ""


def _pdf_stub(size_kb: int = 48) -> bytes:
    """A byte blob that is not a real PDF.

    The size and the declared content type are what the UI shows; nothing decodes the
    body, so a real document would only cost bytes and attention.
    """
    return b"%PDF-1.4\n" + b"seed attachment, not a real pdf. " * (size_kb * 48)


FIXTURES: tuple[Fixture, ...] = (
    Fixture(
        name="plain-text",
        subject="Your order #10482 has shipped",
        sender="orders@shop.example",
        to=("alice@example.com",),
        body=(
            "Hi Alice,\n\n"
            "Your order #10482 shipped this morning and should arrive Thursday.\n\n"
            "Track it: https://shop.example/orders/10482\n\n"
            "-- Shop"
        ),
        note="The baseline. Text only, which is all the UI renders.",
    ),
    Fixture(
        name="html-only",
        subject="Welcome to Shop",
        sender="hello@shop.example",
        to=("bob@example.com",),
        html=(
            "<html><body style='font-family:sans-serif'>"
            "<h1>Welcome</h1>"
            "<p>Thanks for signing up. <a href='https://shop.example/start'>Get started</a>.</p>"
            "</body></html>"
        ),
        note=(
            "No text/plain part at all. Mailoverlord's detail pane has nothing to render "
            "and shows the raw MIME, so this is the fixture that makes that visible."
        ),
    ),
    Fixture(
        name="multipart",
        subject="Your receipt",
        sender="billing@shop.example",
        to=("carol@example.com",),
        body="Receipt for order #10493. Total: 48.20",
        html=(
            "<html><body><h1>Receipt</h1>"
            "<p>Receipt for order <b>#10493</b>. Total: <b>48.20</b></p>"
            "</body></html>"
        ),
        note="multipart/alternative with both parts, which is what real mail looks like.",
    ),
    Fixture(
        name="attachment",
        subject="Invoice INV-2210",
        sender="billing@shop.example",
        to=("dave@example.com",),
        body="Invoice attached.",
        attachment=("invoice.pdf", _pdf_stub(), "application/pdf"),
        note="A named attachment, so the parts list in the UI has something to show.",
    ),
    Fixture(
        name="multiple-recipients",
        subject="Team standup notes",
        sender="sarah@example.com",
        to=("erin@example.com", "frank@example.com", "grace@example.com"),
        body="Notes from today:\n\n- release blocked on the migration\n- docs updated",
        note=(
            "Three RCPT TO commands on one message. Mailoverlord appends each envelope "
            "recipient, so this is what exercises the comma-joined To column."
        ),
    ),
    Fixture(
        name="cc",
        subject="Contract review",
        sender="legal@example.com",
        to=("heidi@example.com",),
        cc=("ivan@example.com", "judy@example.com"),
        body="Please review the attached contract before Friday.",
        note=(
            "Cc recipients are envelope recipients, the same as To, so all three appear in "
            "the To column. The To column does not tell you who was To and who was Cc; "
            "find that in the raw source."
        ),
    ),
    Fixture(
        name="bcc",
        subject="Weekly digest",
        sender="newsletter@example.com",
        to=("kate@example.com",),
        bcc=("archive@example.com",),
        body="This week's digest.",
        note=(
            "The surprising one. Bcc is absent from the stored headers entirely, as it "
            "should be, yet it is still an envelope recipient, so archive@example.com "
            "appears in the To column. Correct MTA behaviour, but it means a captured "
            "message discloses its Bcc recipients where a delivered one would not."
        ),
    ),
    Fixture(
        name="unicode-subject",
        subject="Beställningsbekräftelse — コーヒー £42.00",
        sender="intl@example.com",
        to=("leo@example.com",),
        body="Din beställning är bekräftad. ありがとう.",
        note=(
            "A non-ASCII subject across three scripts. Stored RFC 2047-encoded and decoded "
            "at capture, which is what the subject column has to get right."
        ),
    ),
    Fixture(
        name="no-subject",
        subject=None,
        sender="alerts@example.com",
        to=("mallory@example.com",),
        body="Your verification code is 4471. It expires in 10 minutes.",
        note=(
            "No Subject header at all, which senders do and which the UI has to render as "
            "blank rather than crash on."
        ),
    ),
    Fixture(
        name="long-subject",
        subject=("Refund request: " + "order 10482, damaged on arrival, replacement requested " * 12),
        sender="support@shop.example",
        to=("niaud@example.com",),
        body="Details below.",
        note="A very long subject, near the 4000-character column it is truncated to.",
    ),
    Fixture(
        name="reply-chain",
        subject="Re: Re: Re: Invoice INV-2210 is wrong",
        sender="dave@example.com",
        to=("billing@shop.example",),
        body="Line three of a three-deep reply.",
        extra_headers=(
            ("Message-ID", "<chain-3@example.com>"),
            ("In-Reply-To", "<chain-2@example.com>"),
            ("References", "<chain-1@example.com> <chain-2@example.com>"),
        ),
        note=(
            "Message-ID, In-Reply-To and References, which is what any future thread "
            "grouping would key on and what is currently invisible in the UI."
        ),
    ),
    Fixture(
        name="custom-headers",
        subject="Deployment finished",
        sender="ci@example.com",
        to=("ops@example.com",),
        body="main @ a1b2c3d is live.",
        extra_headers=(
            ("X-Environment", "production"),
            ("X-Commit", "a1b2c3d"),
            ("X-Mailer", "seed-messages"),
        ),
        note="Extra headers survive capture and are visible in the raw source only.",
    ),
    Fixture(
        name="large-attachment",
        subject="Log bundle",
        sender="ops@example.com",
        to=("ops@example.com",),
        body="Bundle attached; 900 KB to stay under the 10 MB capture limit.",
        attachment=("bundle.log", b"log line\n" * 60_000, "text/plain"),
        note="A ~0.5 MB attachment, to check a page of messages stays a sane size.",
    ),
)


def build(fixture: Fixture) -> EmailMessage:
    """Turn a fixture into a MIME message.

    Bcc is deliberately not written as a header. That is what a real MTA does, and it is
    what makes the `bcc` fixture worth sending: the recipient is visible in the envelope
    Mailoverlord captures and absent from the message it stores.
    """
    message = EmailMessage()
    message["From"] = fixture.sender
    message["To"] = ", ".join(fixture.to)
    if fixture.cc:
        message["Cc"] = ", ".join(fixture.cc)
    if fixture.subject is not None:
        message["Subject"] = fixture.subject
    for name, value in fixture.extra_headers:
        message[name] = value

    body = fixture.body
    if body is not None and fixture.html is not None:
        message.set_content(body)
        message.add_alternative(fixture.html, subtype="html")
    elif fixture.html is not None:
        message.set_content(fixture.html, subtype="html")
    else:
        message.set_content(body or "")

    if fixture.attachment is not None:
        filename, payload, content_type = fixture.attachment
        maintype, _, subtype = content_type.partition("/")
        message.add_attachment(
            payload, maintype=maintype, subtype=subtype, filename=filename
        )
    return message


def connect(host: str, port: int, timeout: int) -> smtplib.SMTP:
    """Open one SMTP connection for the whole run.

    Two things here are not about SMTP etiquette, they are about not wasting five seconds
    per message:

    - ``local_hostname`` is passed explicitly so smtplib never calls ``socket.getfqdn()``.
      It does so in its constructor to build the EHLO name, which is a reverse DNS lookup
      of this machine's own hostname. Where that does not resolve promptly it blocks until
      the resolver gives up — 5.0s per message here, for 13 messages that together weigh
      well under a megabyte. Nothing about Mailoverlord is slow; the resolver was.
    - One connection is reused rather than dialled per fixture, which is also just correct
      SMTP: the greeting, EHLO and QUIT round trips were being paid 13 times over.
    """
    return smtplib.SMTP(host, port, timeout=timeout, local_hostname="localhost")


def send(
    smtp: smtplib.SMTP, fixture: Fixture
) -> tuple[bool, str]:
    """Send one fixture over an existing connection.

    Takes the connection rather than making one so a whole run costs a single handshake.
    Returns (ok, detail) instead of raising: a seeder that dies on the first rejection is a
    bad seeder, and the reason belongs in the summary rather than in a traceback.
    """
    message = build(fixture)
    recipients = fixture.to + fixture.cc + fixture.bcc
    try:
        smtp.send_message(message, from_addr=fixture.sender, to_addrs=list(recipients))
    except smtplib.SMTPResponseException as error:
        # smtp_error is bytes on 3.x and str on some paths; normalise rather than guess.
        detail = error.smtp_error
        if isinstance(detail, bytes):
            detail = detail.decode(errors="replace")
        return False, f"rejected {error.smtp_code}: {detail}"
    except (OSError, smtplib.SMTPException) as error:
        return False, f"connection lost: {error}"
    return True, f"{len(recipients)} recipient(s)"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Seed a Mailoverlord instance with test messages.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--host", default=DEFAULT_HOST, help=f"default: {DEFAULT_HOST}")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"default: {DEFAULT_PORT}")
    parser.add_argument(
        "--only",
        metavar="NAME",
        help="send just this fixture; repeatable",
        action="append",
        choices=[f.name for f in FIXTURES],
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="list the fixtures and what each one covers",
    )
    parser.add_argument("--timeout", type=int, default=10, help="per-message timeout, seconds")
    parser.add_argument(
        "--repeat",
        type=int,
        default=1,
        help="send each fixture N times, for filling a page",
    )
    parser.add_argument("--quiet", action="store_true", help="only report failures")
    args = parser.parse_args(argv)

    if args.list:
        width = max(len(f.name) for f in FIXTURES)
        for fixture in FIXTURES:
            print(f"{fixture.name:<{width}}  {textwrap.shorten(fixture.note, 90)}")
        return 0

    selected = FIXTURES
    if args.only:
        wanted = set(args.only)
        selected = tuple(f for f in FIXTURES if f.name in wanted)
    # A fixture repeated is the easiest way to fill a page.
    failures = 0
    sent = 0

    try:
        smtp = connect(args.host, args.port, args.timeout)
    except (OSError, smtplib.SMTPException) as error:
        print(f"Could not connect to {args.host}:{args.port}: {error}", file=sys.stderr)
        print(
            "Is the instance up, and is the port right?\n"
            "  docker compose ps                    # is the app running\n"
            "  docker compose logs mailoverlord-postgres | tail",
            file=sys.stderr,
        )
        return 1

    with smtp:
        for round_number in range(1, args.repeat + 1):
            for fixture in selected:
                ok, detail = send(smtp, fixture)
                if ok:
                    sent += 1
                    if not args.quiet:
                        suffix = f" (round {round_number})" if args.repeat > 1 else ""
                        print(f"  sent {fixture.name:<18} {detail}{suffix}")
                else:
                    failures += 1
                    print(f"  FAILED {fixture.name:<17} {detail}", file=sys.stderr)

    print(f"\n{sent} sent, {failures} failed, to {args.host}:{args.port}")
    if failures:
        print(
            "Check the instance is still up:\n"
            "  docker compose ps                    # is the app running\n"
            "  docker compose logs mailoverlord-postgres | tail",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())