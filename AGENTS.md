# nostrJsonBench

Which JSON parser, in any language, decodes Nostr events fastest and with the least garbage. One
harness per language (`jvm/`, `rust/`, `go/`, …); `HARNESS.md` is the contract every harness keeps. See `README.md`.

## Build and test

```bash
./run.sh --quick                 # smoke run of every harness whose toolchain is installed
./run.sh --only jvm,rust         # a subset
```

## Remotes and pull requests

The repo is on GitHub, `github.com/vitorpamplona/nostrJsonBench`, which holds the canonical `main`,
and on nostr as a NIP-34 repository,
`nostr://npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z/relay.ngit.dev/nostrjsonbench`:
a push to that remote goes to its GRASP git servers and to GitHub. A PR can go out either way: a
GitHub PR (`gh`), or a nostr proposal (`ngit`, a `pr/…` branch, reviewed on
[gitworkshop.dev](https://gitworkshop.dev/npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z/relay.ngit.dev/nostrjsonbench)).
Remote names vary per clone, so identify them by URL. Open a PR only when the human you work for
asks, and before opening, revising or merging one by either path, use the `ngit-pr` skill.
