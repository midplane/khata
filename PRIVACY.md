# Khata privacy policy

_Effective 16 November 2026_

Khata is a ledger app for keeping track of money that clients owe you. It is built to keep your
data on your phone. This policy explains what the app handles and where that data goes.

## The short version

- **No account, no server, no tracking.** Khata does not have internet permission, so it cannot
  send your data anywhere by itself.
- **No ads, analytics or third-party tracking SDKs.**
- **Your ledger lives on your phone**, in the app's private storage.
- **SMS access is optional** and only used on your phone to suggest which dues have been paid.

## What Khata stores on your phone

Everything you enter in the app is saved in a database in the app's private storage on your device:

- **Clients:** names and, if you add them, phone numbers.
- **Dues:** what the work was, amounts, billed and due dates, notes, and the organisation it's billed under.
- **Payments:** amounts, dates and notes. When you record a payment from an SMS suggestion, the
  note includes the payer's name from that message (for example "Via SMS from Priya Nair"), and the
  app keeps a short identifier of that message so it isn't suggested again.
- **Organisations** you create, and which one you last had selected.
- **Dismissed SMS suggestions:** a short identifier per message, so they aren't suggested again.

This data is not sent to the developers or to anyone else by the app.

## SMS messages (optional)

If you open **Payments from SMS** and grant the SMS permission, Khata reads messages in your SMS
inbox from the last 90 days, on your phone, to find bank and UPI credit alerts. For each credit
alert it works out the amount and the payer, and compares them with your open dues to suggest
matches.

- Messages are processed in memory. Khata does **not** copy or store your messages; it stores only
  what is described above, and only for messages you act on (record or dismiss).
- Nothing from your messages leaves your phone.
- You can deny the permission and use every other part of the app, or revoke it at any time in
  Android Settings → Apps → Khata → Permissions.

## Contacts

When you tap the contacts button, Khata uses Android's built-in contact picker. Only the name and
phone number of the single contact you choose are shared with the app. Khata does not request
permission to read your contact list.

## Sharing you choose to do

Some features hand information to another app **only when you tap them**:

- **Reminders** open WhatsApp, your SMS app or Android's share sheet with a pre-filled message
  (client name, amount and work description). Nothing is sent until you send it in that app.
- **Export** creates a spreadsheet file of your ledger (or of the selected organisation) and shares
  it with the app you pick, or saves it where you choose.
- **Call** opens your phone's dialler with the client's number.

Once information is in another app, that app's privacy policy applies.

## Backups

Khata allows Android's standard app backup. If backup is turned on for your device (for example
Google's backup to your Google account), Android may include Khata's data in that backup, under
your device and account settings. Khata itself does not upload anything.

## Deleting your data

- Delete individual payments, dues, clients or organisations inside the app.
- Clear everything with Android Settings → Apps → Khata → Storage → Clear storage, or by
  uninstalling the app.
- If your device backup includes Khata, manage or delete that backup in your device or account settings.

## Children

Khata is a tool for professionals and is not directed at children.

## Open source

Khata is open source under the GPL-3.0 licence. You can read exactly what the app does at
<https://github.com/midplane/khata>. The app requests one runtime permission, `READ_SMS`, which is
used only as described above.

## Changes to this policy

If this policy changes, the updated version will be published in this file, with its history
visible in the repository.

## Contact

Questions or concerns: open an issue at <https://github.com/midplane/khata/issues>.
