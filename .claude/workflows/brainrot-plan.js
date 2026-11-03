export const meta = {
  name: 'brainrot-plan',
  description: 'Brainrot Stopper: Preflight (git, SDK, Emulator), verifizierte Recherche und Architekturentwurf (Opus), Doku (Sonnet)',
  phases: [
    { title: 'Preflight', detail: 'git init, Android-SDK per CLI, AVD, Emulator-Boot-Test' },
    { title: 'Research', detail: '5 Themen: Recherche, 2 Skeptiker, Auflösung offener Punkte' },
    { title: 'Design', detail: '3 Architekturentwürfe, 3 Judges, Synthese, Kritiker, Revision' },
    { title: 'Docs', detail: 'Ergebnisse in .agents/ schreiben, lokal committen' },
  ],
}

const OPUS = { model: 'opus', effort: 'high' }
const SONNET = { model: 'sonnet', effort: 'high' }

const CTX = 'Projekt: Brainrot Stopper (Android-App, Pixel 9, Ziel: App-Sperre mit Anki-Karten). Arbeitsverzeichnis: <projektordner>. ' +
  'Lies zuerst CLAUDE.md und .agents/INDEX.md, danach nur die fuer deine Aufgabe relevanten Dateien (.agents/decisions.md, architecture.md, gotchas.md, mockups.md, workflow-prompt.md; Mockup-Quellen in design/mockups/). ' +
  'Ausgaben auf Deutsch (Code und Bezeichner Englisch). Behaupte keine API-Details aus dem Gedaechtnis: belege sie (URL, Datei, Zeile) oder markiere sie als unverifiziert. ' +
  'Web-Tools (WebSearch, WebFetch) sind verzoegert geladen: lade sie ueber ToolSearch mit "select:WebSearch,WebFetch". Heutiges Datum: 2026-11-02. ' +
  'Umgebung: Arch Linux, Standard-java ist JDK 25 (fuer Android-Builds zu neu), JDK 17 liegt in /usr/lib/jvm/java-17-openjdk, zusaetzlich nur eine JRE 21. Kein sudo. Kein git push.'

// ---------- Schemas ----------
const STR = { type: 'string' }
const STRS = { type: 'array', items: { type: 'string' } }

const PREFLIGHT = {
  type: 'object',
  properties: {
    ok: { type: 'boolean' },
    gitReady: { type: 'boolean' },
    sdkPath: STR,
    installedPackages: STRS,
    avdName: STR,
    emulatorBooted: { type: 'boolean' },
    adbRoot: { type: 'boolean' },
    apiLevel: STR,
    notes: STRS,
    blockers: STRS,
  },
  required: ['ok', 'gitReady', 'sdkPath', 'emulatorBooted', 'adbRoot', 'notes', 'blockers'],
}

const CLAIMS = {
  type: 'object',
  properties: {
    topic: STR,
    claims: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: STR, claim: STR, evidence: STR,
          confidence: { type: 'string', enum: ['hoch', 'mittel', 'niedrig'] },
          implication: STR,
        },
        required: ['id', 'claim', 'evidence', 'confidence'],
      },
    },
    openQuestions: STRS,
  },
  required: ['topic', 'claims', 'openQuestions'],
}

const VERDICTS = {
  type: 'object',
  properties: {
    verdicts: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: STR,
          verdict: { type: 'string', enum: ['bestaetigt', 'widerlegt', 'veraltet', 'nicht pruefbar'] },
          reason: STR, source: STR,
        },
        required: ['id', 'verdict', 'reason'],
      },
    },
    missedFacts: STRS,
  },
  required: ['verdicts'],
}

const FACTS = {
  type: 'object',
  properties: {
    topic: STR,
    facts: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          claim: STR,
          status: { type: 'string', enum: ['bestaetigt', 'umstritten', 'unverifiziert'] },
          evidence: STR, implication: STR,
        },
        required: ['claim', 'status', 'evidence'],
      },
    },
    unresolved: STRS,
    recommendations: STRS,
  },
  required: ['topic', 'facts', 'unresolved', 'recommendations'],
}

const ARCH = {
  type: 'object',
  properties: {
    name: STR, summary: STR, modules: STRS, dataFlow: STR, lockStateMachine: STR,
    dataModel: STR, onboarding: STR, antiBypass: STR, ankiIntegration: STR, testStrategy: STR,
    risks: STRS,
    milestones: {
      type: 'array',
      items: { type: 'object', properties: { id: STR, title: STR, goal: STR, tasks: STRS }, required: ['id', 'title', 'tasks'] },
    },
  },
  required: ['name', 'summary', 'modules', 'dataFlow', 'lockStateMachine', 'dataModel', 'ankiIntegration', 'risks', 'milestones'],
}

const JUDGE = {
  type: 'object',
  properties: {
    scores: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          arch: STR, correctness: { type: 'number' }, feasibility: { type: 'number' }, robustness: { type: 'number' },
          mockupFit: { type: 'number' }, testability: { type: 'number' }, notes: STR,
        },
        required: ['arch', 'correctness', 'feasibility', 'robustness', 'mockupFit', 'testability'],
      },
    },
    winner: STR,
    graftIdeas: STRS,
    fatalFlaws: STRS,
  },
  required: ['scores', 'winner', 'graftIdeas', 'fatalFlaws'],
}

const PLAN = {
  type: 'object',
  properties: {
    architecture: STR,
    versions: {
      type: 'object',
      properties: { gradle: STR, agp: STR, kotlin: STR, composeBom: STR, compileSdk: STR, targetSdk: STR, minSdk: STR, jdk: STR, ksp: STR },
      required: ['gradle', 'agp', 'kotlin', 'composeBom', 'compileSdk', 'targetSdk', 'minSdk', 'jdk'],
    },
    applicationId: STR,
    spike: {
      type: 'object',
      properties: { goal: STR, steps: STRS, successCriteria: STRS, fallbackIfFails: STR },
      required: ['goal', 'steps', 'successCriteria', 'fallbackIfFails'],
    },
    milestones: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: STR, title: STR, goal: STR, dependsOn: STRS,
          tasks: {
            type: 'array',
            items: {
              type: 'object',
              properties: { id: STR, title: STR, files: STRS, details: STR, acceptance: STRS },
              required: ['id', 'title', 'details', 'acceptance'],
            },
          },
          emulatorChecks: STRS,
        },
        required: ['id', 'title', 'goal', 'tasks', 'emulatorChecks'],
      },
    },
    defaults: STRS,
    openDecisions: STRS,
    risks: STRS,
    gotchas: STRS,
  },
  required: ['architecture', 'versions', 'applicationId', 'spike', 'milestones', 'defaults', 'openDecisions', 'risks', 'gotchas'],
}

const CRITIC = {
  type: 'object',
  properties: {
    issues: {
      type: 'array',
      items: {
        type: 'object',
        properties: { severity: { type: 'string', enum: ['blocker', 'wichtig', 'klein'] }, issue: STR, fix: STR },
        required: ['severity', 'issue', 'fix'],
      },
    },
    missing: STRS,
  },
  required: ['issues', 'missing'],
}

// ---------- Phase 0 + 1 laufen parallel (unabhaengig) ----------
const PREFLIGHT_PROMPT = CTX + '\n\nAUFGABE Preflight (du bist der Umsetzer, keine Architekturentscheidungen):\n' +
'1. Git: Im Projektordner "git init -b main" (falls noch kein Repo), "origin" auf https://github.com/tinow04/brainrot-detox.git setzen, ' +
'eine .gitignore fuer Android/Gradle/IDE (.gradle, build/, local.properties, *.iml, .idea, captures, .cxx, *.apk, *.keystore) anlegen. ' +
'Pruefe "git config user.name/user.email"; fehlt eines, setze es NUR lokal im Repo (user.name und user.email aus der globalen Git-Konfiguration). ' +
'Erster Commit mit dem bisherigen Stand (CLAUDE.md, .agents/, design/, .gitignore), Message auf Englisch, z. B. "chore: initial project docs and mockups", ' +
'endend mit der Zeile "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>". NICHT pushen.\n' +
'2. Android-SDK per Kommandozeile (Android Studio ist installiert, der Setup-Wizard lief aber nie, ~/Android/Sdk existiert nicht). Hole die offiziellen "command line tools only" fuer Linux von developer.android.com (Version ueber die Download-Seite ermitteln, Link muss auf dl.google.com zeigen). ' +
'Lade die ZIP in ein neues, leeres Verzeichnis im Scratchpad herunter und entpacke sie dort, fuehre nichts aus dem Download-Verzeichnis heraus aus, und verschiebe dann den Ordner "cmdline-tools" nach ~/Android/Sdk/cmdline-tools/latest. ' +
'Verwende fuer sdkmanager/avdmanager JAVA_HOME=/usr/lib/jvm/java-17-openjdk. Akzeptiere die Android-SDK-Lizenzen (sdkmanager --sdk_root=$HOME/Android/Sdk --licenses), das ist im Auftrag des Users (er will die SDK-Einrichtung). ' +
'Installiere: platform-tools, emulator, die neueste STABILE platforms;android-NN, das passende build-tools;NN.x.x und das System Image system-images;android-NN;google_apis;x86_64 (NICHT google_apis_playstore, sonst geht kein adb root). Ermittle NN per "sdkmanager --list"; nimm die hoechste stabile API, fuer die ein google_apis x86_64 Image existiert.\n' +
'3. AVD: per avdmanager "pixel9" anlegen (Geraet pixel_9 falls vorhanden, sonst pixel_8), RAM in ~/.android/avd/pixel9.avd/config.ini auf 3072 MB (hw.ramSize), da der Rechner nur ca. 7 GB frei hat.\n' +
'4. Boot-Test: Emulator headless starten (ANDROID_SDK_ROOT=$HOME/Android/Sdk, $HOME/Android/Sdk/emulator/emulator -avd pixel9 -no-window -no-audio -no-snapshot-save -gpu swiftshader_indirect; /dev/kvm ist nutzbar), im Hintergrund laufen lassen, auf "adb wait-for-device" und getprop sys.boot_completed=1 warten (max. ca. 5 Minuten). Teste "adb root" und einen Screenshot (adb exec-out screencap -p > Datei im Scratchpad). Danach den Emulator sauber beenden (adb emu kill), damit RAM frei wird.\n' +
'5. Trage das Ergebnis kurz in .agents/log.md ein (nur diese Datei aus .agents anfassen) und committe es lokal.\n' +
'Wenn etwas scheitert: nicht raten oder umgehen, sondern exakt in "blockers" melden (Befehl, Fehlermeldung). Ausgabe nur gemaess Schema.'

const TOPICS = [
  {
    key: 'anki',
    title: 'AnkiDroid-API und Kartenrendering',
    prompt: 'Recherchiere die AnkiDroid-API (Repo ankidroid/Anki-Android, Modul "api", Doku, aktuelle Releases). Klaere mit Belegen aus Quellcode/Doku: ' +
      '(a) Wie bindet man die API ein (Gradle-Artefakt oder eigene Kopie der Contract-Klassen, Berechtigung, Aktivierung in den AnkiDroid-Einstellungen, <queries>-Eintrag fuer Package-Sichtbarkeit)? ' +
      '(b) Decks auflisten, faellige Karten eines Decks (inkl. Unterdecks) abfragen (FlashCardsContract, ReviewInfo/Scheduling), Anzahl faelliger Karten. ' +
      '(c) Eine Karte mit Ease (Again/Hard/Good/Easy) beantworten: genaue Methode, Parameter, Zeitmessung. Werden auch die naechsten Intervalle (z. B. "<1m", "6m", "10m", "4d") geliefert? ' +
      '(d) Wird eine so beantwortete Karte regulaer im Scheduler UND im Revlog verbucht (wichtig fuer Statistik und Sync zum PC)? Lies den Code der Provider-Implementierung. ' +
      '(e) Kann eine fremde App einen Sync in AnkiDroid ausloesen? Wann synct AnkiDroid selbst? ' +
      '(f) Kartentemplates rendern: Question/Answer-HTML, CSS, Bilder/Medien (collection.media), Audio, Cloze, Typ-Felder; wie rendert man das in einer WebView und was liefert die API dafuer? ' +
      '(g) Was passiert, wenn AnkiDroid nicht installiert ist, die API aus ist, die Berechtigung fehlt oder keine Karten faellig sind?',
  },
  {
    key: 'blocking',
    title: 'App-Blocking auf Android 15/16',
    prompt: 'Recherchiere (developer.android.com, AOSP-Doku, aktuelle Verhaltensaenderungen Android 15 und 16) wie man das Oeffnen ausgewaehlter Apps zuverlaessig erkennt und blockiert: ' +
      'AccessibilityService (TYPE_WINDOW_STATE_CHANGED) vs. UsageStatsManager (Events); Overlay per SYSTEM_ALERT_WINDOW vs. eigene Activity ueber der gesperrten App; Einschraenkungen fuer Activity-Starts aus dem Hintergrund (BAL) und wie ein Accessibility-Service sie legal umgeht; Foreground-Service-Typen und -Pflichten; ' +
      'Verhalten bei Neustart, Doze, App-Standby, Akku-Optimierung am Pixel; "Eingeschraenkte Einstellungen" (Restricted Settings) fuer sideloaded Apps und Accessibility; Umgehungen (Zurueck, Home, Recents, Split-Screen, Bild-in-Bild, Benachrichtigungen, Deinstallation/Dienst ausschalten) und realistische Gegenmassnahmen; ' +
      'wie genau erkennt man den Wechsel zurueck zur gesperrten App nach Ablauf der Freigabe. Empfiehl einen Mechanismus mit Begruendung und Alternativen.',
  },
  {
    key: 'toolchain',
    title: 'Build-Toolchain und Versionen',
    prompt: 'Ermittle fuer heute (2026-11-02) die aktuellen STABILEN und miteinander kompatiblen Versionen: Gradle, Android Gradle Plugin (AGP), Kotlin (inkl. Compose-Compiler-Plugin), Compose BOM, KSP (falls Room), compileSdk/targetSdk. ' +
      'Pruefe die Kompatibilitaetstabellen/Release Notes (developer.android.com, kotlinlang.org, gradle.org, Maven Google). WICHTIG: Welche JDK-Version verlangt das AGP zum Ausfuehren von Gradle? Verfuegbar sind auf dem Rechner JDK 17 (jdk17-openjdk) und JDK 25, nur eine JRE 21; Android Studio bringt eine eigene JBR mit. Gib an, ob JDK 17 reicht oder was zu installieren waere (der User kann per yay/pacman installieren, der Workflow nicht). ' +
      'Empfiehl minSdk (Pixel 9 laeuft mit aktuellem Android; vernuenftig niedrig genug fuer Accessibility/Overlay-APIs), Version Catalog (libs.versions.toml), Compose-Setup, Schrift JetBrains Mono einbinden (Lizenz OFL, als Font-Ressource im Projekt statt Google Fonts Downloadable Fonts, da offline robust), Lint/Test-Setup. Liefere eine konkrete, kopierbare Versionsliste.',
  },
  {
    key: 'persistence',
    title: 'Persistenz und Hintergrundzuverlaessigkeit',
    prompt: 'Recherchiere bewaehrte Muster fuer: Einstellungen und Statistik (Jetpack DataStore vs. Room; was passt fuer Ereignislog "Sperre ausgeloest", "Karte beantwortet", Aggregation pro Tag/Woche, Streak-Berechnung), ' +
      'Freigabe-Zeitfenster zuverlaessig modellieren (Ablaufzeitstempel mit SystemClock.elapsedRealtime/Wall Clock statt laufendem Timer; Verhalten bei Neustart, Zeitumstellung, Zeitmanipulation), ' +
      'Dienst-Lebenszyklus (Accessibility-Service wird vom System verwaltet; Boot-Receiver; wie erkennt die Haupt-App, ob der Dienst aktiv ist; Hinweise bei Beendigung), ' +
      'Compose-Architektur fuer Overlay/Activity ausserhalb der Haupt-App (ViewModel-Scope, Zustandsspeicherung bei Prozesstod, Hell/Dunkel nach System, Edge-to-Edge), ' +
      'Dependency Injection: reicht manuelle DI (kein Hilt) fuer diese Groesse? Teststrategie (Unit-Tests der Zaehl-/Freigabelogik, Instrumented Tests, Emulator-Skripte).',
  },
  {
    key: 'emutest',
    title: 'Testen im Emulator (AnkiDroid, Berechtigungen, Revlog)',
    prompt: 'Recherchiere, wie man den kompletten Flow in einem Emulator (Google-APIs-Image ohne Play Store, x86_64, per adb automatisiert) testet: ' +
      '(a) AnkiDroid-APK offiziell beschaffen (GitHub-Releases von ankidroid/Anki-Android, F-Droid) fuer x86_64, Signatur/Herkunft pruefen; erste Einrichtung automatisiert; Testdeck mit Beispielkarten anlegen (AddContentApi aus einer Debug-Hilfsfunktion der eigenen App, oder Import einer .apkg; wie erzeugt man eine kleine .apkg reproduzierbar per Skript?). ' +
      '(b) Berechtigungen per adb setzen: AnkiDroid-API-Berechtigung (pm grant?), SYSTEM_ALERT_WINDOW (appops), Accessibility-Service aktivieren (settings put secure enabled_accessibility_services / accessibility_enabled), Usage Access (appops GET_USAGE_STATS), Restricted-Settings-Umgehung fuer sideloaded Apps (appops ACCESS_RESTRICTED_SETTINGS), Akku-Optimierung. ' +
      '(c) Revlog pruefen: mit adb root die Collection (collection.anki2 im AnkiDroid-Datenordner, beachte Pfad/Version, evtl. WAL-Dateien) ziehen und revlog lesen, ohne sqlite3 auf dem Host vorauszusetzen (z. B. python3 -I mit dem sqlite3-Modul). ' +
      '(d) UI automatisieren: uiautomator dump, adb shell input tap/text/keyevent, Screenshots per screencap, logcat-Filterung. (e) Emulator headless starten und Boot abwarten, Snapshot-Verhalten. ' +
      'Gib kopierbare, belegte Befehle an und kennzeichne alles, was du nicht belegen kannst.',
  },
]

function researchStage(t) {
  return agent(CTX + '\n\nTHEMA: ' + t.title + '\n' + t.prompt +
    '\n\nRegeln: Jede Behauptung als eigener Eintrag (id, claim, evidence mit Quelle, confidence, implication fuer unsere App). Belege aus Primaerquellen (Code, offizielle Doku). Alles Unbelegte kommt nicht in claims, sondern in openQuestions. ' +
    'Der Rechner hat keine Android-Geraete, du sollst nur lesen/recherchieren, nichts bauen oder installieren.',
    { ...OPUS, phase: 'Research', label: 'research:' + t.key, schema: CLAIMS })
}

function skepticStage(r, t) {
  if (!r || !r.claims || r.claims.length === 0) return Promise.resolve([])
  const lenses = [
    { key: 'primary', text: 'Pruefe jede Behauptung unabhaengig gegen die PRIMAERQUELLE (Quellcode im Repo, offizielle Doku) neu. Versuche sie zu WIDERLEGEN. Im Zweifel "nicht pruefbar".' },
    { key: 'current', text: 'Pruefe, ob die Behauptungen zum Stand 2026-11-02 NOCH GELTEN: neue Releases, Changelogs, Deprecations, Verhaltensaenderungen in neueren Android-/AnkiDroid-/Tool-Versionen. Markiere Veraltetes als "veraltet".' },
  ]
  return parallel(lenses.map(l => () => agent(CTX + '\n\nTHEMA: ' + t.title + '\nDu bist unabhaengiger Skeptiker (' + l.key + '). ' + l.text +
    ' Gib fuer JEDE id ein Urteil. Nenne ausserdem wichtige Fakten, die der Recherche fehlen (missedFacts).\n\nBEHAUPTUNGEN (JSON):\n' + JSON.stringify(r.claims),
    { ...OPUS, phase: 'Research', label: 'verify:' + t.key + ':' + l.key, schema: VERDICTS })))
}

function resolveStage(verdicts, t, idx, prevMap) {
  return null
}

const researchP = pipeline(
  TOPICS,
  (_p, t) => researchStage(t).then(r => ({ r })),
  (prev, t) => skepticStage(prev && prev.r, t).then(v => ({ r: prev && prev.r, v: (v || []).filter(Boolean) })),
  (prev, t) => {
    if (!prev || !prev.r) return null
    return agent(CTX + '\n\nTHEMA: ' + t.title + '\nDu loest die Ergebnisse auf. Du bekommst Behauptungen, die Urteile von 2 unabhaengigen Skeptikern und offene Fragen. ' +
      'Regeln: "bestaetigt" nur, wenn kein Skeptiker widerlegt/veraltet meldet. Bei Widerspruch pruefe selbst die Quelle nach und entscheide mit Beleg, sonst "umstritten". Nicht Pruefbares ist "unverifiziert". ' +
      'Versuche auch, die offenen Fragen und missedFacts mit echter Recherche zu beantworten. Das Ergebnis ist die belastbare Faktenbasis fuer den Entwurf; gib am Ende konkrete Empfehlungen fuer unsere App.\n\n' +
      'BEHAUPTUNGEN: ' + JSON.stringify(prev.r.claims) + '\nOFFENE FRAGEN: ' + JSON.stringify(prev.r.openQuestions) + '\nSKEPTIKER-URTEILE: ' + JSON.stringify(prev.v),
      { ...OPUS, phase: 'Research', label: 'resolve:' + t.key, schema: FACTS })
  },
)

const preflightP = agent(PREFLIGHT_PROMPT, { ...SONNET, phase: 'Preflight', label: 'preflight', schema: PREFLIGHT })

const [pre, factsRaw] = await Promise.all([preflightP, researchP])
const facts = (factsRaw || []).filter(Boolean)
log('Preflight: ' + (pre ? (pre.ok ? 'ok' : 'mit Blockern') : 'Agent ausgefallen') + ' | Recherchethemen mit Ergebnis: ' + facts.length + '/' + TOPICS.length)
if (facts.length < TOPICS.length) log('Achtung: ' + (TOPICS.length - facts.length) + ' Recherchethemen ohne Ergebnis, Entwurf basiert auf unvollstaendiger Faktenbasis')

// ---------- Phase 2: Entwurf ----------
const FACTS_JSON = JSON.stringify(facts)
const PRODUCT = 'Produkt (Kurzfassung): Wird eine ausgewaehlte App geoeffnet, legt sich ein Overlay/eine Activity darueber und fragt Anki-Karten aus AnkiDroid ab (Aufdecken, dann selbst bewerten: nochmal/schwer/gut/leicht). ' +
  'Nur gut und leicht zaehlen; bei nochmal/schwer kommt die Karte spaeter wieder und der Zaehler bleibt. Nach 5 richtigen Karten ist die App fuer eine einstellbare Dauer (Standard 15 min) freigegeben. ' +
  'Haupt-App: App-Auswahl (Schalter), Einstellungen (Anzahl richtiger Karten, Dauer 5/15/30/60, Deck-Auswahl: EINZELNES Deck inkl. Unterdecks oder alle Decks, Anki-Verbindungsstatus), Statistik (gesperrt heute/Woche, Karten, Richtig-Quote, Streak, geschaetzte Zeitersparnis, 7-Tage-Balken, Ranking der meistgesperrten Apps). ' +
  'Zusaetzlich noetig, aber nicht in den Mockups: Berechtigungs-Einstieg (Accessibility, Overlay, Akku-Optimierung, AnkiDroid-API) im Terminal-Stil. UI: Jetpack Compose, JetBrains Mono, Catppuccin Mocha/Latte, folgt dem System-Theme, 1:1 nach design/mockups/ und .agents/mockups.md. ' +
  'Kein eigener Sync-Code (Sync laeuft ueber AnkiDroid/AnkiWeb). Test nur im Emulator (AVD pixel9, Google-APIs-Image, adb root moeglich), nicht auf einem echten Geraet. Implementierung durch Sonnet 5.5 (muss aus dem Plan umsetzbar sein, ohne Architekturentscheidungen zu treffen).'

const LENSES = [
  { key: 'risk', text: 'Waehle die RISIKOAERMSTE Loesung: bevorzuge die best belegten, einfachsten Mechanismen, wenige bewegliche Teile, klare Fallbacks, wenn Annahmen sich als falsch erweisen.' },
  { key: 'ux', text: 'Waehle die Loesung mit der besten NUTZERERFAHRUNG nah an den Mockups: schneller Ablauf, kein Flackern beim Sperren, saubere Uebergaenge, gutes Fehler- und Leerzustandsverhalten.' },
  { key: 'robust', text: 'Waehle die Loesung, die am ROBUSTESTEN gegen Umgehung und Hintergrund-Kill ist: Dienstlebenszyklus, Neustart, Recents/Home/Split-Screen, Manipulation der Freigabezeit, Absturz-/Prozesstod-Wiederherstellung.' },
]

const archs = (await parallel(LENSES.map(l => () => agent(CTX + '\n\n' + PRODUCT +
  '\n\nAUFGABE Architekturentwurf (Blickwinkel: ' + l.key + '). ' + l.text +
  ' Entwirf die komplette Architektur: Module/Pakete, Datenfluss, Zustandsmodell des Sperr-Flows (Zustaende, Uebergaenge, wer haelt den Zustand), Datenmodell/Persistenz, Berechtigungs-Einstieg, Anti-Umgehung, AnkiDroid-Integration (inkl. Verhalten wenn AnkiDroid fehlt/API aus/keine faelligen Karten/Deck leer), Teststrategie, Risiken und eine Meilenstein-Liste. ' +
  'Stuetze dich AUSSCHLIESSLICH auf die verifizierte Faktenbasis unten; wo sie Luecken hat oder etwas nur "unverifiziert/umstritten" ist, plane eine Pruefung (Spike) und einen Fallback ein. Schreibe keinen Produktivcode, nur Entwurf.\n\nFAKTENBASIS (JSON):\n' + FACTS_JSON,
  { ...OPUS, phase: 'Design', label: 'architect:' + l.key, schema: ARCH })))).filter(Boolean)

if (archs.length === 0) throw new Error('Kein Architekturentwurf geliefert')
const ARCHS_JSON = JSON.stringify(archs.map((a, i) => ({ arch: 'Entwurf ' + 'ABC'[i], ...a })))

const JUDGES = [
  { key: 'feasibility', text: 'Bewerte vor allem Korrektheit und Umsetzbarkeit durch ein Sonnet-Modell, das nur nach Plan arbeitet: ist alles konkret, vollstaendig und ohne offene Designfragen?' },
  { key: 'robustness', text: 'Bewerte vor allem Robustheit: Hintergrund-Kill, Neustart, Umgehung, Zeitmanipulation, Fehlerfaelle von AnkiDroid, Annahmen die falsch sein koennten.' },
  { key: 'ux', text: 'Bewerte vor allem Naehe zu den Mockups, UX des Sperr-Flows und Testbarkeit im Emulator.' },
]

const judged = (await parallel(JUDGES.map(j => () => agent(CTX + '\n\n' + PRODUCT + '\n\nAUFGABE Judge (Blickwinkel: ' + j.key + '). ' + j.text +
  ' Bewerte jeden Entwurf auf Skala 1-10 je Kriterium (correctness, feasibility, robustness, mockupFit, testability), nenne fatale Fehler und Ideen, die in die Synthese uebernommen werden sollen (graftIdeas), und den Gewinner.\n\nENTWUERFE (JSON):\n' + ARCHS_JSON + '\n\nFAKTENBASIS (JSON):\n' + FACTS_JSON,
  { ...OPUS, phase: 'Design', label: 'judge:' + j.key, schema: JUDGE })))).filter(Boolean)

const SYNTH_RULES = 'Das Ergebnis ist der verbindliche Bauplan fuer Sonnet 5.5. Anforderungen: ' +
  '(1) "architecture" ist ein vollstaendiges Markdown-Dokument (Module/Pakete mit Verantwortung, Datenfluss, Zustandsmaschine des Sperr-Flows, Datenmodell, Berechtigungs-Einstieg, Anti-Umgehung, AnkiDroid-Integration inkl. Fehlerfaelle, Teststrategie). ' +
  '(2) "versions" aus der verifizierten Toolchain-Recherche; beruecksichtige die JDK-Frage (Gradle laeuft mit JDK 17 oder dem, was die Recherche als noetig ausweist; Installationsbedarf nennen). ' +
  '(3) "spike" = Meilenstein M0: kleines Projektgeruest plus ein Pruefprogramm, das im Emulator beweist, ob eine per API beantwortete Karte im Revlog landet, ob die Berechtigung/Provider-Abfrage funktioniert und ob Overlay/Accessibility im Emulator greifen. Mit klaren Erfolgskriterien und Fallback. ' +
  '(4) "milestones" nacheinander (M1 ... Mn), jede Aufgabe mit Dateien, Details und pruefbaren Akzeptanzkriterien, jede Meilenstein mit emulatorChecks (konkrete adb-pruefbare Schritte). Sinnvolle Reihenfolge, z. B. Geruest+Theme, Berechtigungen+Erkennung+Overlay, AnkiRepository+Quiz-UI (5 Screens), Freigabelogik+Einstellungen+Persistenz, App-Auswahl+Statistik, Haertung. ' +
  '(5) "defaults": getroffene Standardwerte (Deck-Auswahl = einzelnes Deck inkl. Unterdecks oder alle, Standard 5 Karten, 15 min, minSdk usw.). "openDecisions": nur echte Entscheidungen des Users. Keine offenen Designfragen fuer Sonnet. ' +
  '(6) Der applicationId-Vorschlag nutzt einen sauberen Namespace (z. B. dev.tinow.brainrotstopper). Schreibe keinen Produktivcode.'

const synth = await agent(CTX + '\n\n' + PRODUCT + '\n\nAUFGABE Synthese. Baue aus den Entwuerfen und den Judge-Bewertungen die beste Architektur samt der besten Ideen der anderen und beseitige alle fatalen Fehler. ' + SYNTH_RULES +
  '\n\nENTWUERFE (JSON):\n' + ARCHS_JSON + '\n\nJUDGES (JSON):\n' + JSON.stringify(judged) + '\n\nFAKTENBASIS (JSON):\n' + FACTS_JSON,
  { ...OPUS, phase: 'Design', label: 'synthesize', schema: PLAN })
if (!synth) throw new Error('Synthese fehlgeschlagen')

const critic = await agent(CTX + '\n\nAUFGABE Vollstaendigkeits-Kritiker. Pruefe den Plan gegen Produktbeschreibung, Mockups (design/mockups/) und Faktenbasis: Was fehlt? Welche Annahme ist unbelegt? Welcher Fehlerfall ist ungeplant? Wo muesste Sonnet raten oder eine Architekturentscheidung treffen? Sind Akzeptanzkriterien pruefbar? Sind alle 8 Mockup-Screens (Sperrflow A1-A5, B1-B3) plus Berechtigungs-Einstieg abgedeckt, jeweils Hell und Dunkel? Sei streng.\n\nPLAN (JSON):\n' + JSON.stringify(synth) + '\n\nFAKTENBASIS (JSON):\n' + FACTS_JSON,
  { ...OPUS, phase: 'Design', label: 'critic', schema: CRITIC })

let plan = synth
const serious = critic ? critic.issues.filter(i => i.severity !== 'klein') : []
if (critic && (serious.length > 0 || critic.missing.length > 0)) {
  const revised = await agent(CTX + '\n\n' + PRODUCT + '\n\nAUFGABE Revision. Arbeite alle Punkte des Kritikers in den Plan ein (gib den VOLLSTAENDIGEN, ueberarbeiteten Plan zurueck, nicht nur Aenderungen). ' + SYNTH_RULES +
    '\n\nBISHERIGER PLAN (JSON):\n' + JSON.stringify(synth) + '\n\nKRITIK (JSON):\n' + JSON.stringify(critic) + '\n\nFAKTENBASIS (JSON):\n' + FACTS_JSON,
    { ...OPUS, phase: 'Design', label: 'revise', schema: PLAN })
  if (revised) plan = revised
  else log('Revision fehlgeschlagen, verwende unrevidierten Plan')
}

// ---------- Phase 3: Doku schreiben ----------
const docs = await agent(CTX + '\n\nAUFGABE Doku (du bist Umsetzer, schreibe NUR Dateien in .agents/, keine Produktivcode-Aenderung). Persistiere die Ergebnisse so, dass spaetere Sessions nicht neu erkunden muessen:\n' +
  '1. .agents/research.md: verifizierte Faktenbasis je Thema (Behauptung, Status bestaetigt/umstritten/unverifiziert, Beleg, Konsequenz), Offenes, Empfehlungen.\n' +
  '2. .agents/architecture.md: ersetze den Inhalt durch das Architekturdokument aus plan.architecture (Markdown) plus Tabelle der Versionen und applicationId.\n' +
  '3. .agents/plan.json: der komplette Plan als gueltiges JSON (exakt der unten uebergebene Plan).\n' +
  '4. .agents/todo.md: Meilensteine M0..Mn mit Aufgaben als Checkboxen (id, Titel, Akzeptanzkriterien kurz), dann "Offene Entscheidungen fuer den User" (plan.openDecisions) und "Standardwerte" (plan.defaults). Aufgaben alle offen, ausser: Preflight/SDK siehe Preflight-Ergebnis.\n' +
  '5. .agents/gotchas.md: ersetze die bisherigen "unverifiziert"-Eintraege durch die jetzt belegten Fakten (Datum 2026-11-02, Quelle); behalte noch unverifizierte Punkte mit Markierung; ergaenze plan.gotchas.\n' +
  '6. .agents/decisions.md: ergaenze (nichts loeschen) Entscheidungen aus der Synthese mit Begruendung und eine Entscheidung "Deck-Auswahl: einzelnes Deck inkl. Unterdecks oder alle Decks (Standard, vom User am 2026-11-02 bestaetigt)".\n' +
  '7. .agents/INDEX.md: neue Dateien (research.md, plan.json) eintragen; .agents/log.md: Eintrag oben fuer diese Session (Preflight-Ergebnis, Recherche, Entwurf).\n' +
  'Danach: lokal committen (nur wenn ein Git-Repo existiert; sonst melden), Message "docs: add verified research, architecture and implementation plan", endend mit "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>". NICHT pushen.\n\n' +
  'PREFLIGHT (JSON): ' + JSON.stringify(pre) + '\n\nFAKTENBASIS (JSON):\n' + FACTS_JSON + '\n\nPLAN (JSON):\n' + JSON.stringify(plan),
  { ...SONNET, phase: 'Docs', label: 'docs', schema: { type: 'object', properties: { files: STRS, committed: { type: 'boolean' }, notes: STRS }, required: ['files', 'committed', 'notes'] } })

return {
  preflight: pre,
  research: facts.map(f => ({ topic: f.topic, confirmed: f.facts.filter(x => x.status === 'bestaetigt').length, contested: f.facts.filter(x => x.status === 'umstritten').length, unverified: f.facts.filter(x => x.status === 'unverifiziert').length, unresolved: f.unresolved })),
  versions: plan.versions,
  applicationId: plan.applicationId,
  spike: plan.spike,
  milestones: plan.milestones.map(m => ({ id: m.id, title: m.title, tasks: m.tasks.length })),
  defaults: plan.defaults,
  openDecisions: plan.openDecisions,
  risks: plan.risks,
  criticIssues: critic ? critic.issues : null,
  docs,
}
