#!/usr/bin/env python3
"""Seeds AnkiDroid (emulator, adb root) with rendering fixtures via the content provider and dumps the raw
provider answers (question/answer/css) to a directory. Usage: seed-fixtures.py <outdir>"""
import base64, os, re, shlex, subprocess, sys

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
URI = "content://com.ichi2.anki.flashcards"
SEP = "\x1f"


def emulator_serial():
    """Only ever an emulator (this seeds AnkiDroid): ANDROID_SERIAL from emu-env.sh, else the first attached emulator."""
    s = os.environ.get("ANDROID_SERIAL", "")
    if not s.startswith("emulator-"):
        out = subprocess.run([ADB, "devices"], capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout
        s = next((l.split()[0] for l in out.splitlines() if l.startswith("emulator-") and l.split()[1:2] == ["device"]), "")
    if not s:
        sys.exit("no emulator attached: refusing (never seeds a phone)")
    return s


SERIAL = emulator_serial()


def sh(cmd):
    return subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout.replace("\r", "")


models = {}
for line in sh(f"content query --uri {URI}/models --projection _id:name").splitlines():
    m = re.match(r"Row: \d+ _id=(\d+), name=(.*)$", line)
    if m:
        models[m.group(2)] = m.group(1)

FIX = {
    "cloze": ("Cloze", ["Die {{c1::Hauptstadt}} von Frankreich ist {{c2::Paris}}.", "Extra-Hinweis"]),
    "type": ("Basic (type in the answer)", ["Hauptstadt von Frankreich?", "Paris"]),
    "image": ("Basic", ['Was zeigt das Bild? <img src="bild ä 1.png">', "Ein rotes Rechteck"]),
    "audio": ("Basic", ["Hoere zu [sound:klang.mp3]", "Ein Ton"]),
    "html": ("Basic", ["<b>Fett</b> &amp; <i>kursiv</i><br>Zeile 2 &lt;tag&gt;", "<ul><li>eins</li><li>zwei</li></ul>"]),
    "broken": ("Basic", ["{{c1::kein cloze in basic}} {{Unbekannt}}", "Antwort"]),
}
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for name, (model, fields) in FIX.items():
    flds = SEP.join(fields)
    b64 = base64.b64encode(flds.encode()).decode()
    sh("logcat -c")
    sh(f"am broadcast -n dev.tinow.brainrotstopper/.debug.SpikeReceiver --es cmd addnote --es mid {models[model]} --es flds_b64 {b64} --es tags bsfixture")
    import time; time.sleep(1.5)
    print(name, "insert:", [l.split("cmd=")[1] for l in sh("logcat -d -s BSSpike").splitlines() if "cmd=addnote" in l])
# dump every fixture note: notes query takes an Anki search as selection
notes = sh(f"content query --uri {URI}/notes --where {shlex.quote('tag:bsfixture')} --projection _id:flds")
ids = re.findall(r"Row: \d+ _id=(\d+)", notes)
print("fixture notes:", len(ids))
for n, nid in enumerate(ids):
    raw = sh(f"content query --uri {URI}/notes/{nid}/cards/0 --projection question:answer:answer_pure:question_simple:answer_simple:card_name")
    open(os.path.join(out, f"card-{n:02d}.txt"), "w").write(raw)
    print(f"--- note {nid}\n{raw.strip()[:600]}")
css = sh(f"content query --uri {URI}/models --projection name:css")
open(os.path.join(out, "models-css.txt"), "w").write(css)
