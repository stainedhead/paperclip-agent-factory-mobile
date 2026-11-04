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

Developers: see [getting-started](./getting-started.md).
