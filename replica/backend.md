# Backend: Firebase (Auth + Firestore + Analytics)

Local-first. The app works fully offline without an account; signing in with Google or Apple turns on
cross-device sync. Nothing leaves the phone while signed out (analytics only with the user's opt-in).

## How sync works

- Every local change is stamped per record (`updatedAt`, client clock, ms); deletes leave tombstones.
  Done generically in `SnapshotHabitRepository.mutate` by diffing before/after, so every feature is covered.
- Cloud: `users/{uid}/habits|folders|entries/{id}` with the app fields + `updatedAt`, `deleted`, `serverTime`.
- `SyncEngine` (shared): on sign-in, listen to the three collections; apply anything newer than local
  (last write wins per record); push local records newer than the cloud's. Pushing starts only after the
  first cloud snapshot, so a fresh install can't overwrite newer cloud data. Firestore's own offline cache
  queues writes while offline.
- Rules (`firebase/firestore.rules`): owner-only paths, only the 3 collections, field size limits,
  and an update must not be older than what's stored.
- Same Google/Apple account on two phones = same Firebase uid = same data.
- Sign out keeps local data. "Delete account" removes the cloud copy, then the Firebase user; local data stays.
  "Delete everything" in Settings deletes everywhere (tombstones sync).

Known limits (v1):
- Last-write-wins uses device clocks; a phone with a badly wrong clock can lose or win edits it shouldn't.
- Tombstones are never purged (they're tiny). Purge after 90 days can come later.
- Folder "collapsed" state syncs too (it's a folder field).

## Setup you need to do (Claude can't create accounts)

1. **Firebase project** → console.firebase.google.com → Add project (Analytics: on).
2. **Android app** → package `com.example.recurring` for now (changes in /replica-brand; then add the new
   package and download a new json). Add the **SHA-1** of your debug keystore
   (`./gradlew :androidApp:signingReport`) and later the release / Play signing SHA-1.
3. Download **google-services.json** → put it in `androidApp/` (git-ignored). The build generates the
   Firebase resources from it; without it the app builds with sync hidden.
4. **Authentication → Sign-in method**
   - Google: enable. (The web client ID used by Credential Manager is read from the json automatically.)
   - Apple: needs an Apple Developer account. Create a Services ID, register the return URL
     `https://<project-id>.firebaseapp.com/__/auth/handler`, create a Sign in with Apple key; enter
     Services ID, Team ID, key ID and private key in Firebase. (Firebase docs: "Authenticate Using Apple on Android".)
5. **Firestore** → Create database (production mode, region close to users, e.g. `eur3` or `asia-south1`).
   Deploy rules: `cd firebase && firebase deploy --only firestore:rules` (or paste `firestore.rules` in the console).
6. **Analytics**: nothing else. Collection stays off until the user opts in (manifest defaults + `setAnalyticsCollectionEnabled`).
7. **Backups**: Firestore → enable Point-in-time recovery or scheduled backups (paid plan); local JSON export still exists.

iOS (next milestone): add an iOS app in the same project, `GoogleService-Info.plist`, Sign in with Apple
capability, GoogleSignIn; implement `SyncBackend` / `AccountService` for iOS.

## Security checklist

- [x] Config only in `google-services.json` (git-ignored); no keys in source
- [x] Firestore rules: owner-only, kind allow-list, field validation, no older-overwrites-newer
- [ ] Rules tested with the emulator: a second user gets nothing (`firebase emulators:start` + rules unit test) — TODO
- [x] Account deletion deletes cloud data and the Firebase user (Apple App Store requirement)
- [x] Analytics opt-in, no advertising ID, no ad personalisation; events carry no names/notes/dates
- [x] No user data in logs (only record keys on write failures)
- [ ] App Check (Play Integrity / DeviceCheck) to keep other clients off the project — recommended before launch
- [ ] Privacy policy lists processors: Google Firebase (Auth, Firestore, Analytics), Apple (Sign in with Apple)
- [ ] Data Safety form (Play) / privacy labels (App Store): account email + name, user content (habits, notes), app interactions (if opted in)
