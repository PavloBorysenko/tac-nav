# TacNav Devices

## What this is

TacNav Devices lets authorized TacNav staff define field applications, hand one application to one Android phone, revoke lost or replaced phones, publish the current Tac Nav Mortar APK, and deliver durable messages between application-owned map objects.

## Who owns what

Staff own application names, allowed maps and teams, default selections, reference settings, activation, revocation, and release metadata. The phone owns later PIN-authorized gameplay customizations. TacNav Maps owns maps, teams, icons, and geo objects. Firebase only wakes a phone; WordPress owns the mailbox record.

## How work usually happens

An Organizer creates an application and generates a short-lived handoff. The operator scans its QR code or enters the same server URL and manual code. Activation replaces any prior phone and returns a fresh private credential and the current reference settings. During play, an application can select only an allowed map/team, browse the complete icon catalog, create or update its own map objects through TacNav Maps, and catch up on retained mailbox messages after sleep or network loss.

## What to know before changing it

- TacNav Maps must be active.
- Only Organizer/Administrator staff capabilities may expose definitions, activation codes, revocation, or handoff controls.
- Activation codes and bearer credentials must never be committed, logged, or placed in URLs.
- Firebase service-account credentials and Android signing keys stay outside Git and the public WordPress tree.
- Reactivation intentionally revokes the old phone and resets local gameplay settings from the server reference.
