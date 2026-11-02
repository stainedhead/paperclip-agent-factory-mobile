# Adding your first factory

A **factory** is one Paperclip server. The app asks for its address first. This is the only screen of the iPhone app that exists today (screen 15, step 1 of First-run setup); everything after the address step is built in the shared core and tested, but has no screen yet.

<img src="images/ios-first-run.png" alt="The built first-run screen with an empty address field" width="260">

1. Type or paste the address, for example `https://paperclip.example.com`.
2. The app checks it as you type and says what to change:
   - **Start the address with https://** : there is no `https://` or `http://` at the start.
   - **Use a secure https:// address** : plain `http://` is refused. In debug builds only, `http://localhost` and `http://10.0.2.2` (the Android emulator's name for the host machine) are accepted.
   - **Add the server name after https://**, **The server name has characters that are not allowed**, **The port must be a number from 1 to 65535** : fix the host or the port.
   - **The part of the address after the server name has characters that are not allowed** : spaces, `..`, a doubled `//` or a broken `%` escape in the path. A server name with a trailing dot, non-English letters (use its `xn--` form) or an invalid IP address gives the server-name message above.
   - **Remove the user name and password from the address**, or **Remove everything after the server address** : keep only the server address and an optional path. Query strings (`?…`) and fragments (`#…`) are refused.
3. When the address is valid the app shows "Will connect to" followed by the cleaned-up address (scheme and host in lower case, no trailing `/`; a port and a path such as `/paperclip` are kept) and enables **Continue to pairing**.

**Continue to pairing does nothing yet.** The rest of the flow (name your factory, check the server, approve this phone in your browser or paste a board key, choose a default company, set the app lock) exists in the shared core as `FirstRunStateHolder` and is tested on fakes, but no screen draws it and no real server is contacted. Storing the access key in the Keychain is Phase 1b.

**Which servers are accepted.** The app only connects to servers in `authenticated` mode that are ready. A server in `local_trusted` mode is always refused, and so is one whose mode the app does not recognise, one that is still waiting for its first user (bootstrap pending) and one that is not ready.

**Private servers.** If your Paperclip runs at home, the phone must be able to reach it, for example through Tailscale. The app does not set that up; it needs a name or address the phone can resolve.

To exercise the whole first-run logic today, see [usage-examples.md](./usage-examples.md#4-add-a-factory-on-fakes).
