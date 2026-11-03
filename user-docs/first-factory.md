# First run and adding a factory

A **factory** is one Paperclip server. On first launch the app shows "Add your first factory".

<img src="images/first-run.png" alt="First-run screen with a factory address field, a name field and a Try the demo factory button" width="260">

1. Type or paste the address, for example `https://paperclip.example.com`. The app checks it as you type and says what to change (it must start with `https://`; user names, query strings and fragments are refused; `http://` works only for `localhost` and `10.0.2.2` in debug builds).
2. Give the factory a name (1 to 40 characters). It is shown in the factory switcher.
3. **Check address** asks the server for its health. Servers in `local_trusted` mode, servers still waiting for their first user and servers that are not ready are refused.
4. **Continue to pairing** approves this phone in your browser; see [pairing](./pairing.md). Then you choose a company and set the app lock ([app lock](./app-lock.md)).

**Try the demo factory** switches to a built-in demo factory with fake data (Acme Labs, PAP-1182, Engineer 1). Use it to look around; it never contacts a server.

**Current state.** A launch without `-demo` runs this flow against the real server; `-demo-first-run` (Debug builds) runs it on fakes. Private servers: see [Tailscale and allowed hostnames](./tailscale-and-hostnames.md). Problems: [troubleshooting](./troubleshooting.md).
