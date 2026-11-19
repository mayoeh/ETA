---
title: "ETA · Show & Tell 2"
---

[← Back to the project page](./)

# Show & Tell 2

**Nov 19, 2026**

ETA tells you exactly when to leave for your next plan, based on where you are, how you’re getting there, and how long the trip really takes.

---

## What changed since Show & Tell 1

| Change | Why |
| --- | --- |
| **Directions API → Routes API** | Google now lists Directions as legacy; Routes API also gives turn-by-turn steps |
| Added **Record a route** | Save the way you actually walk and reuse it |
| Added **saved places** (Home, Work) | Pick a starting place quickly when making a plan |
| Added a **bottom tab bar** | Calendar · Routes · Today · Friends · Settings |

## What works in the APK

- **Login:** email/password with Firebase Authentication, plus password reset.
- **Plans in Firestore:** name, category, destination (Places search or a map pin), leaving-from place (my location, a saved place, or another place), start and end times, arrive-early buffer, one-time date or repeat days with start and end dates, travel mode (walk, bike, bus), and a route choice.
- **Today:** the next plan with a live countdown to when you should leave, from your GPS location and the Routes API.
- **“I’m leaving”:** pick a route (the fastest one or a saved one), then turn-by-turn directions: the map follows you, the banner shows the next turn, and progress is measured along the path.
- **Routes:** record a route with GPS, or save one from the Route screen; pick it for any plan.
- **Calendar:** week view (sized by start and end times) and month view.
- **Settings (DataStore):** default travel mode, arrive-early buffer, saved places, and a demo switch to simulate walking.
- **Security rules deployed**, not test mode: users only write their own data, and friends can only read plans marked shared.

## Live demo (3 minutes)

1. **Calendar → + Add plan:** “CS 4720 lecture,” Class, destination Rice Hall, leaving from my location, starting in about 15 minutes, walk. The **Travel time** box shows the Routes API result and when to leave.
2. **Save:** the plan appears in today’s column, loaded back from Firestore.
3. **Today:** the Next up card counts down to when I should leave.
4. **I’m leaving → Fastest route:** the map zooms in and follows me with turn-by-turn directions. I can’t walk around the classroom, so **Simulate walk** moves my position along the real route.
5. **Share ETA:** opens the Android share sheet with my arrival time.
6. **Rotate the phone:** nothing is lost.

## XP progress

| Feature | XP | Status |
| --- | --- | --- |
| Meaningful GPS (map, countdown, live progress) | 25 | Done |
| External API: Google Maps Platform Routes API | 20 | Done |
| 3+ settings in DataStore | 15 | Done |
| Share into other apps | 15 | Done |
| Step-count walking pace | 25 | Planned |
| Google sign-in | 10 | Planned |

**Working now: 75 XP.** With step count and Google sign-in: 110 proposed (100 counts).

## What’s left before Demo Day

| Feature | When |
| --- | --- |
| Friends: search, requests, shared plans (required “other users’ data”) | Nov 20–25 |
| Step-count walking pace | Nov 24–29 |
| Google sign-in | Nov 28–Dec 1 |
| “Leave now” notification | Nov 28–Dec 1 |
| Rotation and background checks, polish | Dec 2 |

Feature freeze: **Dec 1**.

## Biggest blocker

**Friends.** It’s the required interaction with other users’ data, and the friend-request security rules have to be built and tested before it can ship. It’s next on the list.

---

[← Back to the project page](./)
