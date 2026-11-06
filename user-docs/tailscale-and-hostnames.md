# Tailscale and allowed hostnames

If your Paperclip runs at home it should not be on the open internet. The usual way to reach it from your phone is **Tailscale** (or another VPN): install it on the phone and the server's machine, and use the server's tailnet name, for example `https://paperclip.example.ts.net`.

- The address must be `https://`. The app has no exceptions for plain `http://` (only `localhost` and `10.0.2.2` in debug builds).
- Paperclip may restrict the host names it answers to. Add your tailnet name (and any public name) to the server's allowed hostnames, otherwise the server answers with an error even though the phone can reach it.
- The app does not set up Tailscale. If the phone cannot resolve the name, "Check address" reports the factory as unreachable; see [troubleshooting](./troubleshooting.md).
- Servers in `local_trusted` mode are refused: use `authenticated` mode.

## On Android

- Install the **Tailscale** app from Google Play on the phone, sign in to the same tailnet and leave it connected (the key icon shows in the status bar). Android may pause a VPN to save battery: if the factory suddenly looks unreachable, open Tailscale first. Some phones need the app excluded from battery optimisation for it to stay on.
- Use the tailnet name, for example `https://paperclip.example.ts.net`. The app accepts only `https`; the release app never allows plain `http`. The debug build also allows `http://localhost` and `http://10.0.2.2` (an emulator's name for the computer it runs on) for development.
- **Private DNS**: if you use a custom Private DNS provider in Android settings, it must be able to resolve the tailnet name (Tailscale's own DNS normally does that while it is connected).
- Chrome Custom Tabs, used for pairing, also go through the VPN, so a tailnet-only approval page opens in them.
- A VPN that needs a captive login, or a Wi-Fi that blocks it, shows up as "unreachable" under **Check address**; see [troubleshooting](./troubleshooting.md).
