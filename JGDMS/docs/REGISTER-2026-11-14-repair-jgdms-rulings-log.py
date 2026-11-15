"""Insert the one missing newline in rulings/JGDMS/LOG.tsv line 30 (RL-JGDMS-0028 glued to RL-JGDMS-0029).

No field text is changed: the line was written without its trailing newline by an earlier writer and the next
append (claim_register.new, whose append() has no newline guard) started on the same physical line.
"""
import os, shutil, sys, time

HERE = r"E:\Claude\claim_register"
LOCK = os.path.join(HERE, "CLAIM_LOCK")
P = os.path.join(HERE, "rulings", "JGDMS", "LOG.tsv")
BAK = r"C:\Users\peter\AppData\Local\Temp\claude\1e1178e5113a78e2\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\regv\JGDMS-LOG.tsv.before-repair"

fd = os.open(LOCK, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
try:
    os.write(fd, b"repair.py")
    os.close(fd)
    b = open(P, "rb").read()
    shutil.copyfile(P, BAK)
    lines = b.decode("utf-8").split("\n")
    assert lines[-1] == "", "log does not end with a newline"
    i = 29
    f = lines[i].split("\t")
    assert len(f) == 15, len(f)
    assert f[1] == "RL-JGDMS-0028" and f[8] == "RL-JGDMS-0029", (f[1], f[8])
    assert f[7].endswith("2026-11-14"), f[7][-20:]
    first = "\t".join(f[:7] + [f[7][:-10]])
    second = f[7][-10:] + "\t" + "\t".join(f[8:])
    assert len(first.split("\t")) == 8 and len(second.split("\t")) == 8
    new = lines[:i] + [first, second] + lines[i + 1:]
    tmp = P + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as o:
        o.write("\n".join(new))
    os.replace(tmp, P)
    print("repaired; lines now", len(new) - 1, "backup", BAK)
finally:
    try:
        os.remove(LOCK)
    except OSError:
        pass
