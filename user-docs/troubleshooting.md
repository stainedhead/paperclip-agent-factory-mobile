# Troubleshooting

| Symptom | What to check |
|---|---|
| "Check address" says unreachable | Is Tailscale or the VPN on? Can the phone open the address in Safari? See [Tailscale](./tailscale-and-hostnames.md). |
| "Use a secure https:// address" | The app only accepts `https://`. |
| Server refused | `local_trusted` mode, first-user bootstrap pending or not ready: fix on the server. |
| Server answers with an error although reachable | Add the host name to Paperclip's allowed hostnames. |
| "Signed out" or "Key expired" | [Re-pair](./key-expiry-and-repairing.md). |
| Buttons are disabled with a reason | The phone is offline, or your role may not do it (a Viewer changes nothing; only an Owner raises budgets). |
| The pairing sheet closed and nothing happened | It was cancelled or could not be shown; start pairing again. |
| Face ID prompt cancelled | Nothing was sent; repeat the action. |
| A change shows "outcome unknown" | The app never retries a change; it reloads and shows the server's truth. |

## Android

| Symptom | What to check |
|---|---|
| The app says "A fingerprint, face or screen lock isn't set up on this phone." | Set a PIN, pattern or password in Android settings. Without one the Keystore cannot protect a key. On an emulator: `adb shell locksettings set-pin 1234`. |
| "Paperclip can't open its data" with Try again | The factory database or the Keystore could not be opened. Try again; nothing was changed and the app never falls back to demo data. Free storage space or restart the phone if it keeps happening. |
| The pairing tab closed and nothing happened | Same as above: it was cancelled or could not be shown. Check that Chrome (or another browser that supports Custom Tabs) is installed and enabled, then pair again. |
| A factory says "Needs re-pairing" after you changed the phone's lock or fingerprints | Android deleted the key that protected the board key. [Re-pair](./key-expiry-and-repairing.md). |
| The fingerprint prompt never appears for a destructive action | No fingerprint or screen lock is set, the prompt is locked out after too many failures (wait, or use the screen lock), or the app was not in the foreground. Nothing was sent. |
| Recents shows a blank card for Paperclip | Intended: the lock screen and the pairing page hide themselves there. |
| "Actions unavailable" on the Inbox | The phone reports no validated internet. The data shown is the last loaded, with its time. |
| Files on an issue cannot be opened | Not built yet on either platform. Open the issue in the Paperclip web app. |
| Comments show raw `*` and `#` characters | Android does not render markdown yet; the text is shown as written. |
| Diagnostics | Settings, Diagnostics shows a redacted report you can copy into a bug report. |

Developers: see [getting-started](./getting-started.md).
