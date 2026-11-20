# Candidate: a device with an isolated processor for untrusted proxies and service interface code

- **Status:** CANDIDATE, listed as entry 1 of `docs/SUGGESTIONS-requiring-further-review.md`. Not a ruling, not a design, nothing built or checked. Recorded so the idea is not lost.
- **Date:** 2026-11-21. **Recorded by:** Claude Fable 5.2, proxy-board review session, after the operator said of the outline below "Yes, I think that would work."
- **Belongs with:** the proxy-board design (`docs/SUMMARY-proxy-board-design-2026-11-15.md`, the lookup design memo's section 16, `docs/relay-isolation/`).

## The operator's idea (verbatim, 2026-11-21 00:47 +1000)

"So if we designed a laptop, desktop or device that had a separate isolated processor and memory, then that space could be used for a new ServiceUI with an untrusted proxy."

It followed his remark at 00:46 on entry classes that are not trusted: "It could for instance be unmarshalled in an isolated host."

## What it would change

The lookup design memo's decision D10 says the trusted host ships standard interfaces and the service supplies no interface code. With an isolated processor and memory inside the device, a service's own interface code could run there, beside its untrusted proxy, and never in the user's main system. It is the proxy board built into the machine: a small untrusted place inside a trusted one.

## The outline the operator agreed would work (the session's proposal; UNREVIEWED)

| Concern | Rule proposed |
|---|---|
| Drawing | The isolated side runs the service's interface logic and sends a description of what to show. The trusted side draws it. The description is data and is checked and bounded like everything else that crosses the boundary. Raw pixels are not sent. |
| Spoofing | The trusted side owns the screen and marks the untrusted region in a way that region cannot draw over. |
| Input | Keystrokes and pointer events go to the isolated side only while its region has focus, and never during a trusted prompt. |
| Graphics hardware | Not shared with the isolated side. |
| Clipboard, files, camera, microphone | Mediated by the trusted side, or absent. |
| Hardware | As for the proxy board: its own processor of a class without speculative execution, its own memory, nothing shared with the main system, an identity tied to the hardware class. |

## Related use

An entry class or other application code that is not trusted could be loaded only on the isolated side, with the trusted side receiving data back (RL-JGDMS-0155 says a loaded entry class must be trusted and download is off by default; the operator offered isolation as one way, which is not registered).

## Open

Everything: the format of the interface description, the width of the link, who authenticates whom, what the isolated side may persist, and whether any existing `net.jini.lookup.ui` code could run there unchanged.
