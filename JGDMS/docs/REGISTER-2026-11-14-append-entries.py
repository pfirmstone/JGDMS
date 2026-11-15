import subprocess, sys

REG = r"E:\Claude\claim_register\claim_register.py"
BY = "Claude Sonnet 5.5, JGDMS path-rating session 757702ae"
SES = r"C:\Users\peter\.claude\projects\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd.jsonl"
HO = r"JGDMS/docs/HANDOVER-2026-11-01-code-free-proxy-window.md"
ids = []


def run(args):
    r = subprocess.run([sys.executable, "-I", REG] + args, capture_output=True, text=True, encoding="utf-8")
    out = (r.stdout + r.stderr).strip()
    print(out)
    for line in out.splitlines():
        if line.startswith("allocated "):
            ids.append(line.split()[1])
    known = "rulings\JGDMS\LOG.tsv:30: expected 8 tab-separated fields" in out
    if r.returncode != 0 and not known and not any(l.startswith("allocated ") for l in out.splitlines()):
        sys.exit("FAILED: " + " ".join(args)[:120])


def ruling(statement, locator, note, unblock=""):
    a = ["new", "JGDMS", "IN-FORCE", "--kind", "ruling", "--statement", statement, "--locator", locator,
         "--by", BY, "--note", note]
    if unblock:
        a += ["--unblock", unblock]
    run(a)


HYPN = [0]


def hyp(status, statement, locator, note="", unblock=""):
    HYPN[0] += 1
    if HYPN[0] <= 1:
        return  # the first hypothesis is already CL-CODEFREE-0001 (a duplicate 0002 is rejected separately)
    a = ["new", "CODEFREE", status, "--statement", statement, "--locator", locator, "--by", BY]
    if note:
        a += ["--note", note]
    if unblock:
        a += ["--unblock", unblock]
    run(a)


which = sys.argv[1]
if which == "rulings":
    ruling("A name under C:\\Documents and Settings is the Users tree, and a <letter>$ share is that drive on any host, for every path rating; ADMIN$, IPC$ and longer share names are not aliases, and a named directory below an ordinary share stays unrated.",
           "session 757702ae transcript, operator reply 2026-11-07 on the OS-alias options; " + HO + " REVISION 4q section 2 items 1; commit 073333b04",
           "I accept your recommendations.", "the operator says otherwise")
    ruling("The flag set (flagReason, used by ListPromotion) uses the same normaliser and undefined-property rule as the danger rating; a relative .. chain stays MEDIUM read and HIGH write; the relative lib/- write row stays clean.",
           "session 757702ae transcript, operator reply 2026-11-07 on the flag-set and relative-name options; " + HO + " REVISION 4q section 2 item 1; commit 073333b04",
           "I accept your recommendations.", "the operator says otherwise")
    ruling("A recursive absolute file name with an 8.3 short-name-shaped segment among its first two names is rated HIGH on every host, a profile's Local Settings folds to AppData\\Local, PolicyScope reads the same aliases, and fixture n128 is dropped.",
           "session 757702ae transcript, operator reply 2026-11-07 to the recommendations on 8.3 names, PolicyScope and n128; " + HO + " REVISION 4q section 2 items 2 and 3; commit 44505725f",
           "I agree with your recommendations, go.", "a deployment needs a real directory of 8.3 shape in a grant (see the Q3 ruling)")
    ruling("DOCUME~1 folds like Documents and Settings and LOCALS~1 like Local Settings; a profile moved off Users and the Application Data self-junction stay unfolded.",
           "session 757702ae transcript, operator reply before 2026-11-08 00:40 (Q1 of REVIEW-shortnames-opus-2026-11-07.md); " + HO + " REVISION 4q section 2 item 4; commit 9c14ea866",
           "Q1 yes, Q3 none", "the operator rules on a relocated profile or the Application Data self-junction")
    ruling("No allow-list row or PolicyLint option clears a real directory of 8.3 shape such as C:\\PROGRA~1\\MyApp\\-; the rule rates the shape only.",
           "session 757702ae transcript, operator reply before 2026-11-08 00:40 (Q3 of REVIEW-shortnames-opus-2026-11-07.md); " + HO + " REVISION 4q section 2 item 5",
           "Q1 yes, Q3 none", "a deployment needs such a directory in a grant")
    ruling("PolicyScope rates the directory holding every home, one user's home and the root user's home as writable codeBases on every lint host, and --define moves user.home, java.io.tmpdir and user.dir.",
           "session 757702ae transcript, operator reply before 2026-11-08 00:40 (Q2 of REVIEW-shortnames-opus-2026-11-07.md); " + HO + " REVISION 4q section 2 item 6; commit 8800a7a69",
           "Yes, do Q2", "the operator says otherwise")
    ruling("The assistant commits JGDMS changes pathspec-scoped with the Co-Authored-By trailer and never pushes; the operator pushes.",
           "session 757702ae transcript line 6283 (" + SES + ")",
           "You may commit the changes, I'll push them.", "the operator says otherwise")
elif which == "hyps":
    sup = "TESTED-SUPPORTED"
    hyp(sup, "On a Windows host, Files.isSameFile is true for C:\\Documents and Settings\\<user> and C:\\Users\\<user>, and for \\\\localhost\\C$\\Users\\<user> and C:\\Users\\<user>.",
        "scratchpad regv\\V.java run 2026-11-14 on the DirtyChai 27-internal image build/windows-x86_64-server-release/images/jdk; JGDMS/docs/REVIEW-aliases-opus-2026-11-07.md",
        "Re-run by the main session on 2026-11-14: both isSameFile lines printed true for user peter.")
    hyp(sup, "The JDK does not resolve 8.3 names: FilePermission C:\\DOCUME~1\\- (read) implies C:\\DOCUME~1\\<user>\\x and does not imply C:\\Users\\<user>\\x.",
        "scratchpad regv\\V.java run 2026-11-14 on the DirtyChai 27-internal image; JGDMS/docs/REVIEW-shortnames-opus-2026-11-07.md section (a)",
        "Re-run by the main session on 2026-11-14: implies true, then false.")
    hyp(sup, "On a Windows host the PermissionSets file-name ratings score 0 of 15,322 JDK-covered rows below the ruled rating against FilePermission.implies (population 8,878 spellings x 2 actions = 17,756 rows; narrow controls 0 of 992); deep probes only, direct children are unrated by ruling.",
        "JGDMS/jgdms-platform/src/test/java/au/net/zeus/jgdms/api/policy/PermissionSetsFilePermissionDifferentialTest.java; JGDMS/docs/REVIEW-normaliser-r2-opus-2026-11-07.md",
        "Opus reproduced the counts before the round-3 edits; the test passes (3 run, 0 failed) in the main session's build of 8800a7a69. It is skipped on Linux.")
    hyp("TESTED-PARTIAL", "au.zeus.jdk.net.Uri (RFC 3986) cannot normalise FilePermission names: its normalize() keeps a root .. and it has no Windows file-name semantics.",
        "DirtyChai src/java.base/share/classes/au/zeus/jdk/net/Uri.java (class header and normalize()); session 757702ae transcript, operator question 2026-11-06",
        "Code reading only, nothing run.", "a test that rates names through Uri and compares with FilePermission.implies")
    hyp("FALSIFIED", "A short, fixed SVID lifetime is the real control that sets the worst case of an undetected board compromise.",
        "JGDMS/docs/REVIEW-memo-s15-handover-4n-fable-2026-11-06.md finding 1 (Fable 5.1); struck in place in JGDMS/docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md section 15",
        "By code and document reading: the board renews the SVID automatically and the re-attestation evidence is boot-time, so the lifetime bounds only the renewal tail. Nothing was run.")
    hyp("TESTED-PARTIAL", "PermissionSets.fileReach compares the JDK home and the temp directory lexically with the spelling in java.home and java.io.tmpdir, so a JVM launched through an 8.3 path rates the long spelling of conf\\security\\java.security write 0.",
        "JGDMS/docs/REVIEW-shortnames-opus-2026-11-07.md finding F1 (RateProbe, env XP and the 8.3 launch)",
        "Measured by Opus; the main session did not re-run it. Present before this work. No ruling yet.",
        "the operator rules on F1 or runs java -XshowSettings:properties on a deployment host launched as the lint is")
    hyp("TESTED-PARTIAL", "No tracked JGDMS policy file contains an 8.3 token, a Local Settings name or a Documents and Settings name other than fixture n126, so the corpus and deploy tests cannot see the short-name and Local Settings rules.",
        "JGDMS/docs/REVIEW-shortnames-opus-2026-11-07.md section (b) (git grep over 580 tracked files)",
        "Reported by Opus; not re-run by the main session.", "re-run the git grep named in the review")
    hyp(sup, "PermissionSetsTest (34 tests) passes on Ubuntu 24.04 with the DirtyChai 27-internal Linux image of 2026-10-28, at JGDMS HEAD 34fa219c2.",
        "10.1.1.22:~/verify-linux-20261111.log; " + HO + " REVISION 4r section 2",
        "Run by the main session with mvn -o -B -pl jgdms-platform,tools/policy-condenser clean test on the Linux host.")
    hyp("PROPOSED", "The 8 Linux failures other than CombinerDebugRecursionTest (UnresolvedPrincipalImpliesTest 2, PolicyLintCorpusTest 3, PolicyLintGateTest.wildcardPrincipalsOfIssuedClassesFail, PolicyLintTest 2) are caused by the Linux DirtyChai image predating issue 236 (DirtyChai 4c71278f1c5).",
        HO + " REVISION 4r section 2 (git merge-base --is-ancestor 4c71278f1c5 HEAD printed 236-NOT-in-HEAD on the Linux checkout)",
        "The corpus tests name a stale JDK themselves; the other failure texts were read only as far as their stack traces.",
        "rebuild the Linux image from a DirtyChai trunk containing 4c71278f1c5 and rerun the same mvn command: expect only CombinerDebugRecursionTest to fail")
    hyp("TESTED-PARTIAL", "CombinerDebugRecursionTest fails in every run of jgdms-platform on Windows and on Linux (StackOverflowError in the JDK's Subject.toString) and does not involve PermissionSets.",
        "main-session runs of 2026-11-07 and 2026-11-11 (logs verify-q2.log in the session scratchpad and ~/verify-linux-20261111.log); " + HO + " REVISION 4p section 1",
        "The producer reports an identical failure on a scratch copy of 0eb9f0cc7; the main session did not run it on base.",
        "run the test on commit 0eb9f0cc7")
    hyp(sup, "The tests of the 8.3 shape rule, the Local Settings, DOCUME~1 and LOCALS~1 folds, the PolicyScope home-tree call, its env read, its short-name-before-fold order, the one-user's-home rule and the second-name rule each fail when their rule is disabled (9 of 9 mutants killed at 8800a7a69).",
        "main-session mutant runs 2026-11-08 (logs verify-shortnames*.log and verify-q2.log in the session scratchpad); " + HO + " REVISION 4q section 1",
        "Each mutant was applied to a copy of the final bytes and the files restored and md5-checked afterwards.")
print("IDS", ids)
