# core/ai audit fixes — standalone patches

Two fixes from the `core/ai` audit, as standalone patches. They target the
**refactored** `core/ai` package (the WIP currently in your working tree), which
is not yet committed to `main`. Apply them **after** you have committed that
refactor.

## Patches

- `0001-fix-ai-sse-decode-hardening.patch`
  Malformed / schema-mismatched SSE chunks (e.g. an OpenAI-compatible provider
  sending error `code` as a string like `"rate_limit_exceeded"`) threw an
  unhandled `SerializationException` from `parseStreamPiece` that
  `ProviderExecutor.run` does not catch, failing the whole scan instead of
  retrying/falling back.
  - `OpenAiCompatApi.ApiError.code`: `Int?` -> `JsonElement?` + `codeInt` helper.
  - `ProviderClient`: wraps streaming decode in `decodeChunk` -> `TransientAiException`;
    two OpenAI `err.code == 429` -> `err.codeInt == 429`.
  Files: `core/network/OpenAiCompatApi.kt`, `core/ai/ProviderClient.kt`.

- `0002-fix-ai-remove-ratelimited.patch`
  `AiResult.RateLimited` is never constructed anywhere; rate limits already
  surface as `AiResult.Error`. Removes the dead variant and its three dead UI
  branches. A rate-limited translation then falls through to the existing
  `Error` branch (shows "Translation failed" instead of failing silently — this
  is intended).
  Files: `core/ai/AiEvents.kt`, `ui/screens/ResultsScreen.kt`,
  `ui/viewmodel/AiScanViewModel.kt`.

## Apply

From the repo root, on top of your committed refactor:

```sh
git apply --check ai-audit-fixes/0001-fix-ai-sse-decode-hardening.patch \
                  ai-audit-fixes/0002-fix-ai-remove-ratelimited.patch   # dry run
git apply ai-audit-fixes/0001-fix-ai-sse-decode-hardening.patch \
          ai-audit-fixes/0002-fix-ai-remove-ratelimited.patch
```

Both were verified with `git apply --check` against the current refactor tree.
If the refactor changed any of the touched regions after these were generated,
`--check` will flag it; re-generate or resolve by hand.

## Notes

- These are `git diff -R HEAD` exports, so the `diff --git` header prefixes read
  `b/… a/…`. `git apply -p1` handles them fine.
- Delete this `ai-audit-fixes/` directory once applied.
- Findings #3–#10 from the audit are untouched.
