# The Nooki 10/10 product prompt

This is the brief for Nooki. Give it to any developer, designer or AI tool, and they should build
the same app. If a future idea conflicts with this page, change this page first.

---

## Your role

You are the founding product engineer and designer of Nooki, a native Android app. Build it as if
millions of ordinary people will trust it with the things they cannot afford to forget. Do not
build a demo. Do not build a portfolio piece. Every screen must help a real person on a busy day.

## The product in one sentence

**Nooki is a small fox who pays attention for you, so nothing you missed, forgot or promised slips
away.**

Most reminder apps only know what you typed into them. People do not forget because they lack a
to-do list. They forget because life keeps moving: a call comes in during a meeting, a bill arrives
in an SMS, a friend borrows money, a passport goes into "a safe place". Nooki watches for these
moments and brings them back at the right time.

## Who it is for

Nooki must feel natural to all of these people without a settings page:

| Person | A real day | What Nooki does |
| --- | --- | --- |
| **Student** | Assignment due Monday, exam in 3 weeks, fee last date, roommate owes ₹300 | Counts down to exams, warns before fee dates, remembers who owes what |
| **Office worker** | Back-to-back meetings, missed calls, rent, EMI, follow-ups | Catches missed calls, tracks bills, keeps a clean evening wrap |
| **Homemaker or parent** | School fees, gas cylinder, family medicines, doctor visits | Repeats monthly chores, keeps asking about medicine until it is confirmed |
| **Elder or caregiver** | Tablets, appointments, calling family | Large clear text, Hindi voice input, asks again when something is not confirmed |

Use plain language. Nobody should need to know what a "scheduler" or "category" is.

## The three questions

The whole app answers three questions. They are the structure of the home screen.

1. **What did I miss?** Calls I did not pick up, messages I have not opened, reminders I did not
   act on.
2. **What did I forget?** Money out with people, things I lent, tasks I keep pushing, bills that
   are getting close.
3. **What do I have to do?** Today's list, then what is coming up.

If a feature does not help answer one of these, it does not belong in the app.

## What to build

### 1. Tell Nooki: capture in one breath

One input, always one tap away. Understands how people really talk:

- English: "pay rent on the 1st every month"
- Hinglish: "kal subah 9 baje doctor", "har somvar gym"
- Hindi by voice: "कल शाम 6 बजे दवाई"
- Things to hold: "passport is in the blue bag"
- Money and lending: "Rahul owes me 500", "lent charger to Aman"
- Questions: "where is my passport"

Show what Nooki understood as you type, before saving. If there is no time, keep the item anyway and
bring it up in the evening. Never lose a thought because a field was empty.

Reach it from everywhere: the app, voice, the share sheet of any app, text selection in any app, a
quick settings tile, a long press on the app icon, and the home-screen widget.

### 2. Nooki notices

With clear permission, Nooki reads incoming notifications on the phone and picks out what matters:

- Missed calls: "Mom called, 2 hours ago"
- Messages still unopened after 2 hours: "Unread message from Aman"
- Bills and dues from SMS and email, with the amount and date
- Parcels arriving today
- Appointments, interviews, exams and tickets with a date

Rules that cannot be broken:

- Explain exactly what is read and why, inside the app, before asking for access.
- **Never store message content.** Keep who and when. For bills and events, keep only the name,
  amount and date.
- **Never touch OTPs, passwords, PINs or bank balances.** Drop them before anything is saved.
- Noticing is a suggestion. Nothing becomes a reminder until the person taps.
- The person can turn off each kind of noticing, and "off" means off.

### 3. Remember: Nooki holds things for you

- **Saved things.** "Car parked at B2, pillar 14." "Spare key is with Sharma aunty." Search finds
  them in a second.
- **Money and things out there.** Who owes you, who you owe, what you lent, for how long, with a
  running total.

### 4. Dates that matter

Birthdays, bills, insurance, passports, warranties and exams all need warnings ahead of the day.
Nooki sets those up itself: 30, 7 and 1 days before an expiry, 7 and 1 days before a birthday, 3 and
1 days before a bill. Yearly things repeat without being asked. People never have to do this maths.

### 5. Nothing quietly disappears

- Swiping away a notification does not mean it is done. It moves to "You missed".
- Repeating reminders survive being ignored. A missed rent reminder does not end the monthly chain.
- Medicine keeps asking until it is confirmed, up to a limit, then stays in "You missed".
- Reminders must survive reboots, time changes, app updates and battery savers.

### 6. A daily rhythm, not a stream of pings

- **Morning brief:** what is on today, what is close, what is still open.
- **Evening wrap:** what did not get done, with one tap to move it all to tomorrow, plus a nudge if
  the phone needs charging overnight.
- Calendar events appear in the day if the person allows it.
- Nooki is one calm voice. Everything it noticed waits for the brief unless it truly cannot wait.

### 7. Nooki stays alive

The pet is the reason people open the app, not decoration. Nooki sleeps when things are calm,
looks up when something is close, and gets flustered when things pile up. Mood comes from real data,
never from random animation. Levels and XP reward care for your own life. They never guilt anyone.

### 8. Always within reach

A home-screen widget shows Nooki's mood and what needs attention. A quick settings tile and app
shortcuts open capture from anywhere.

## Rules that make it a 10

**Trust.** Everything stays on the phone. The app ships **without the internet permission**, and the
build fails if one appears. Reading notifications is only acceptable because the data cannot leave.

**Reliability.** A reminder app that misses one reminder has failed. Exact alarms are used only
when a late alarm causes harm, like medicine or something marked critical. Everything else uses
inexact alarms that the phone can batch. This also means no scary permission on day one.

**Calm.** Ask for a permission only at the moment it clearly helps. Never nag about the app itself.

**Honesty.** Never invent a fact. If Nooki is unsure, it asks. Delivery numbers and reliability
claims must come from real measurements.

**Respect.** Works in dark and light, with large text, with TalkBack, on cheap phones, and offline.
English and Hindi must both feel at home, including typefaces that support Devanagari.

## Design direction: the thread

Before phones, people tied a knot in a handkerchief or the end of a sari so they would not forget.
That is Nooki's visual idea.

- **The signature.** One thread runs down the left of Today, starting from Nooki's paw. Every item
  is a knot on that thread. Tap a knot to untie it, and the thing is done. Spend all the boldness
  here and keep everything else quiet.
- **The thread changes colour with meaning:** vermilion for missed, marigold for forgotten, indigo
  for to do, leaf green for done.
- **Palette.** Neel indigo `#1C2254`, night indigo `#12153A`, washed cotton `#EEF0FA`, marigold
  `#FFAE1A`, sindoor `#E23B3B`, leaf `#1F9D6B`.
- **Type.** Baloo 2 for headlines and Mukta for reading. Both were made for Latin and Devanagari
  together, so Hindi never looks like a fallback.
- **Layout.** Left aligned, one column, generous space. Lists hang off the thread instead of
  sitting in identical cards. Only the thing ringing right now gets a solid block.
- **Words.** Sentence case and plain verbs. Nooki says "You missed", not "Overdue items". Buttons say
  what they do. Errors say what happened and how to fix it.
- **Motion.** One moment when the app opens: the thread drops from Nooki. Otherwise, motion only
  answers something the person did.

## How it is built

Keep what already works: Kotlin, Jetpack Compose, Room as the single source of truth, AlarmManager,
BroadcastReceivers and MVI state. Add DataStore for settings, Glance for the widget, and a
NotificationListenerService for noticing. All parsing and decision logic stays in plain Kotlin with
no Android imports, so it can be unit tested. Every database change ships with a tested migration.
User data is never wiped.

The Android rules that shape this build:

- Since Android 14, exact alarms are denied by default for most new installs.
- Since Android 13, sideloaded apps must be unlocked by hand before notification access can be
  given, so onboarding must walk people through it.
- Google Play requires an in-app disclosure right before access is requested.
- A new personal Play account needs a closed test with 12 testers for 14 straight days.

## Not now

No login. No cloud sync. No AI chat. No ads. No social features. No location tracking. Each of these
would weaken the promise above or delay the core. Revisit them only after real people use Nooki
daily.

## Definition of done

Nooki is a 10 when all of these are true:

- [ ] A new person understands the app and saves something useful within 60 seconds.
- [ ] "kal subah 9 baje doctor" works by typing and by voice.
- [ ] A missed call shows up in "You missed" without the person doing anything.
- [ ] Nothing ever disappears silently, including swiped notifications and ignored repeats.
- [ ] An OTP never reaches the database, proven by a test.
- [ ] The shipped app has no internet permission, enforced by the build.
- [ ] Upgrading never loses data, proven by a migration test.
- [ ] Alarms survive a reboot on a real phone.
- [ ] It looks like nothing else in the Play Store and reads well in Hindi.
- [ ] Twelve real people use it for 14 days, and we publish what we measured.
