claude-sonnet-5-5 (Sonnet 5.5). Survey written 2026-11-01. Report only; no other file touched.

NOTE ON METHOD: every "opened" page below was read through WebFetch, which returns a small-model SUMMARY of the page, not the raw text. Quotes are short phrases as relayed by that summary. "Search-only" means I saw only a search-result blurb.

## Summary

- Cloudflare documents (opened): Date.now()/performance.now() advance only after I/O; no multithreading or shared memory; only JS and Wasm accepted; V8 isolates share processes, with "cordons" (separate runtime instances grouped by trust/plan), process isolation for inspected or suspicious Workers (DyPrIs), and an MPK in-process heap boundary since Sept 2025. Cloudflare itself says process isolation "is not a complete defense", Spectre has no official solution, and remote timing over the network cannot be removed.
- Cloudflare's own Aug 2026 post and an Aug 2026 arXiv preprint (Schwarzl et al.) show a remote Spectre attack on production Workers at up to 12 bit/s, 99%+ accuracy, using a remote timer plus amplification. The preprint concludes that freezing/coarsening timers is not adequate by itself. This is a direct counter-example to "coarsen timers + no threads is enough".
- Browsers: performance.now() coarsened to 100 us, or 5 us if cross-origin isolated (COOP same-origin + COEP require-corp); SharedArrayBuffer gated on the same. Chrome's own conclusion (V8, 2019): timer mitigations alone were insufficient; the effective mitigation is site isolation, i.e. data out of the process address space.
- Research (iLeakage, CCS 2023; ShowTime, 2023 per search blurb): low-resolution or absent timers do not stop speculative attacks in browsers.
- Wasmtime/Bytecode Alliance (2022) and the WebAssembly security page say timing attacks are possible and timer blocking is hard; neither claims completeness.

## Table

| Claim | Source URL | Date | Status | What it says / does not cover |
|---|---|---|---|---|
| Workers clock frozen during execution | https://blog.cloudflare.com/mitigating-spectre-and-other-security-threats-the-cloudflare-workers-security-model/ | published 2020-07-29 (page metadata; modified 2026-07-15) | opened (fetch summary) | Date.now() is "the time at which the network message was received"; time fixed while code runs; implemented mid-2017. Timer fuzzing "only makes them slower"; remote timing over the network still possible. |
| Timers: advance only after I/O | https://developers.cloudflare.com/workers/runtime-apis/performance/ | last updated 2026-10-18 | opened | performance.now()/Date.now() "only advance or increment after I/O occurs"; CPU-only code measures zero. Local workerd advances normally. Page states NO resolution figure. timeOrigin returns 0. |
| No threads / shared memory | same blog + https://developers.cloudflare.com/workers/reference/security-model/ | 2020-07-29 / page 2026 (no exact date relayed) | opened | "we also do not permit multi-threading or shared memory"; one event on one thread; no concurrency access. |
| Dynamic code (eval, Wasm compile) | both Cloudflare pages above | as above | opened | Neither page discusses eval/runtime codegen. Only JS and Wasm accepted; native binaries refused. I found no Cloudflare statement on eval from these pages. |
| Spectre: no complete fix | blog and security-model page | as above | opened | "V8 itself cannot defend against Spectre"; "no one has fixed Spectre"; 2020 post says no working remote-timer production attack developed (since contradicted by 2026 work below). |
| Process isolation, dynamic (DyPrIs) | blog (2020) | 2020-07-29 | opened | Workers with suspicious performance metrics rescheduled into own process; "isn't a complete defense" (known vulnerabilities only); false positives acknowledged; metrics not named in this page (the security-model page says "suspicious performance metrics"). |
| Cordons / risk-based placement | blog (2020) and security-model page | 2020 / 2026 | opened | Multiple runtime instances per machine ("cordons"); workers assigned a trust level; free plan not co-scheduled with enterprise. Aimed at V8 zero-days (patch gap "under 24 hours"). Periodic rescheduling/memory shuffling described as planned in 2020. The 2026 post does NOT mention cordons. |
| Hardware performance counters | blog (2020); https://blog.cloudflare.com/revisiting-spectre-attacks-on-workers | 2020-07-29; 2026-08-19 | opened | 2020 post: attacks show in "CPU performance counters", "metrics which reliably detect attacks". 2026 post: DyPrIs uses iTLB-normalised counters; it missed the new attack (isolated only after invocation ended; WebSocket traffic skewed normalisation); improved since. Counters named in news coverage only (search-only: esecurityplanet, gigazine). |
| Remote Spectre on production Workers | https://blog.cloudflare.com/revisiting-spectre-attacks-on-workers | 2026-08-19 | opened | Type-confusion gadget + PLRU amplifier + remote timer (WebSocket to external server, sub-ms median resolution); up to 12 bit/s, >99% accuracy, vs ~120 bit/h earlier; attacker and victim in same process. Says "mitigated already", no sign of exploitation in 3 yrs. Mitigations: improved DyPrIs, V8 sandbox ("not a complete Spectre mitigation"), MPK in-process isolation (deployed Sept 2025; finite hardware domains). Other variants may still allow OOB access; MPK "strictly reduces" leakage. Remote timing "cannot" be eliminated. Deno not mentioned. |
| Independent evaluation of the above | https://arxiv.org/abs/2608.17043 (Schwarzl, Xiao, Pedersen, Ainsworth, Topham) | submitted 2026-08-17, v1 | opened (abstract page) | Preprint, no venue listed. Concludes freezing/coarsening timers do not adequately protect Workers; amplification + remote timers leak a JWT from a co-located worker; production DyPrIs was insufficient. Does not mention Deno. |
| Browser timer limits | https://developer.mozilla.org/en-US/docs/Web/API/Performance/now ; https://developer.mozilla.org/en-US/docs/Web/API/Performance_API/High_precision_timing | modified 2025-08-25; 2025-02-25 | opened | Coarsened by cross-origin isolation status: 5 us isolated, 100 us not. High_precision_timing page notes browsers may fall back to 1 ms. Neither page gives Firefox pref values or jitter. |
| W3C High Resolution Time L3 | https://www.w3.org/TR/hr-time-3/ | Working Draft 2026-09-01 | opened | 100 us default, 5 us for cross-origin isolated; implementations may be coarser; suggests resolution reduction, jitter, API throttling; recommended values and COI detail still under discussion. |
| COOP/COEP, SharedArrayBuffer | https://web.dev/articles/why-coop-coep | 2020-05-04 | opened | Attackers speed attacks with explicit (performance.now) and implicit (SharedArrayBuffer) timers; SAB, high-res timers allowed only when COOP same-origin + COEP require-corp. Gives no resolution numbers or site-isolation discussion. Opaque-resource protections do not stop Spectre. |
| Site isolation | https://www.chromium.org/Home/chromium-security/site-isolation/ | undated (latest item June 2025) | opened | Protects against compromised renderers, UXSS and Spectre-class attacks; desktop since Chrome 67. Limits: CORB best-effort; site not origin granularity; partial on Android, absent in WebView and <2 GB devices. |
| Chrome/V8 view of timers | https://v8.dev/blog/spectre | 2019-04-23 | opened | "timer mitigations alone would not be sufficient"; "only effective mitigation is to move sensitive data out of the process's address space"; variant 4 "infeasible" in software; safe languages can leak on current hardware. |
| Timerless/low-res browser attack | https://ileakage.com/ (iLeakage, Kim, Sun, Guo, Yarom; ACM CCS 2023) | CCS 2023; Safari fix 17.2 | opened (project page); paper PDFs search-only | Works on Safari with low-resolution timers (and a race-based timerless variant); Apple had already added low-res timers and per-tab process isolation. Authors say other browsers' engines make it unlikely to work there; no real-world abuse known. |
| Timer-less attack (Zhang et al., USENIX Security 2023) | https://www.usenix.org/system/files/usenixsecurity23-zhang-ruiyi.pdf | 2023 | opened but UNREADABLE (PDF streams not decoded) | Only the search blurb: timing-less measurement primitive, strengthened by a coarse timer. Unconfirmed by me. |
| ShowTime (amplifying CPU timing side channels) | https://dl.acm.org/doi/fullHtml/10.1145/3579856.3590332 | 2023 (ACM AsiaCCS-era DOI) | fetch returned HTTP 403; search-only | Blurb: amplification bypasses imposed timer restrictions. Venue/year not confirmed by me. |
| SoK on JS timers | https://oaklandsok.github.io/papers/rokicki2021.pdf (Rokicki et al.) | 2021 | search-only | Blurb: SharedArrayBuffer-based timers are the most powerful in browsers. |
| Wasmtime Spectre mitigations | https://bytecodealliance.org/articles/security-and-correctness-in-wasmtime | 2022-09-13 | opened | Spectre needs a timer, but "surprisingly easy" to build timers from widgets, so blocking timer APIs is not a full answer. Mitigates speculative call_indirect, br_table, linear-memory access. BTI/CFI support "in progress". Not claimed complete; no statement on other side channels or on Wasmtime timers. |
| WebAssembly security page | https://webassembly.org/docs/security/ | undated | opened | "timing attacks against modules" possible; races/TOCTOU possible; no Spectre or timer mitigation described. |
| Wasm threads proposal | https://github.com/WebAssembly/spec/blob/main/proposals/threads/Overview.md | n/a | HTTP 404 | Could not open. |
| Fastly Compute | https://www.fastly.com/blog/how-compute-edge-is-tackling-the-most-frustrating-aspects-of-serverless (and docs.fastly.com/products/compute) | undated | search-only | Per-request sandbox created and destroyed (vendor claim, marketing). No Fastly timer/Spectre statement found. |
| Deno sandbox | https://docs.deno.com/runtime/fundamentals/security/ | updated 2026-06-17 | opened | No mention of timers, Spectre or side channels. Sandbox does not limit code at the same privilege (eval, dynamic import, workers); recommends OS sandbox/VM/MicroVM for untrusted code. |
| Process isolation across cloud tenants | https://gruss.cc/files/scalableisolation.pdf | unknown | search-only | Title only: "Robust and Scalable Process Isolation against Spectre in the Cloud". Not read. |

## The Cloudflare/Deno claim from the earlier survey: settled or not

I do not know the exact wording of the earlier claim. What I can settle:
- Cloudflare side: SETTLED (primary pages above) - frozen/I/O-only timers, no threads, process isolation, cordons.
- Deno side: NOT SETTLED. Deno's security doc (opened, updated 2026-06-17) says nothing on timers or Spectre. Search results (search-only, not opened) show Deno once had an `--allow-hrtime` permission with a timing-attack warning and that deno_web source rounds to 2 ms when not granted; an OpenReplay blog (unverified, third-party) says it was dropped in Deno 2.0. Deno Deploy timer behaviour: nothing found. Any claim that Deno Deploy freezes or coarsens timers like Workers remains uncorroborated. No page I opened pairs Cloudflare and Deno.

## What would and would not suit a chassis of per-identity ARM boards (my reading, not the sources')

- The sources agree on the direction: they converge on moving tenants out of shared address space and shared microarchitecture (V8 team: site isolation; Cloudflare: processes, cordons, MPK) because timer coarsening and thread limits only slow attacks. This supports the operator's plan; it does not prove it.
- Would suit: a tenant per physical board removes shared caches, branch predictors and memory controller with other tenants, which is the channel the 2026 work exploits (same-process co-location). Risk-based placement (trust level, never co-locate low and high trust) maps directly to which identities share a board.
- Would not suit / not covered: the sources say nothing about physical-board separation, shared network fabric, or shared power/thermal. Network remote timing is explicitly unremovable (Cloudflare), so boards still leak timing to anyone who can time responses; this only matters if the attacker and victim share a board or a switch-level resource. Within one board, JVM/SecurityManager rules remain language-level, which the Schwarzl preprint and V8 team say is insufficient against Spectre. DirtyChai's timer coarsening and early thread initialisation are the same class of control Cloudflare uses and should be treated as slowing, not preventing.
- ARM-specific: I read nothing ARM-specific beyond iLeakage (Apple silicon, Safari) and Wasmtime's BTI note. No source here assesses small ARM boards.

## Could not confirm

- Cloudflare timer resolution (not stated on opened pages).
- Current Chrome and Firefox documented timer values beyond MDN/W3C 100 us / 5 us (Firefox privacy.reduceTimerPrecision defaults not found; Chrome's own page not opened).
- Cloudflare statements on eval/dynamic code and on named performance counters (only in news coverage).
- Zhang et al. USENIX 2023 conclusions; ShowTime venue; gruss.cc paper; Okapi (arXiv 2312.08156, search-only).
- Anything on Deno Deploy timers; Fastly timer or Spectre policy; WASI/component-model security documents (not found); Wasm threads proposal (404).
- Newsworthy secondary claims (shattered.io, techtimes, gigazine) are unreliable and not used as facts.

## What I did not search

- Firefox/Mozilla Spectre blog posts and Safari/WebKit documents directly; Chrome's SharedArrayBuffer enablement pages.
- Cloudflare DyPrIs original paper (2021) and the TU Graz papers beyond this arXiv preprint.
- Hardware-level work on ARM (cache partitioning, MTE, Spectre on Cortex-A) and cross-board/network timing research.
- Deno blog or Deploy docs beyond one search, V8 flags, and Node.js `vm`/isolate-based comparisons.
