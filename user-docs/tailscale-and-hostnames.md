# Tailscale and allowed hostnames

If your Paperclip runs at home it should not be on the open internet. The usual way to reach it from your phone is **Tailscale** (or another VPN): install it on the phone and the server's machine, and use the server's tailnet name, for example `https://paperclip.example.ts.net`.

- The address must be `https://`. The app has no exceptions for plain `http://` (only `localhost` and `10.0.2.2` in debug builds).
- Paperclip may restrict the host names it answers to. Add your tailnet name (and any public name) to the server's allowed hostnames, otherwise the server answers with an error even though the phone can reach it.
- The app does not set up Tailscale. If the phone cannot resolve the name, "Check address" reports the factory as unreachable; see [troubleshooting](./troubleshooting.md).
- Servers in `local_trusted` mode are refused: use `authenticated` mode.
