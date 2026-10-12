# Relationship Referee — Android Studio rebuild files

Ready-to-paste files for recreating Relationship Referee in Android Studio with
real Google Play Billing. The app itself is simple:

- A native start screen with a **"Get a Verdict — $1"** button.
- The button runs a Google Play one-time purchase for the product **`verdict`**
  (consumable — after a successful payment it's consumed so it can be bought
  again next time).
- After payment, the app opens the existing referee form
  (https://daqtpi.com/referee) in a WebView so the user can submit their argument.
- On every app start it also checks for a paid-but-unfinished `verdict`
  purchase and completes it, so an interrupted purchase is never lost.

## What each file replaces

| File here | Paste it over this file in the project |
|---|---|
| `build.gradle.kts` | `app/build.gradle.kts` (the one inside the **app** folder — NOT the `build.gradle.kts` in the project root) |
| `AndroidManifest.xml` | `app/src/main/AndroidManifest.xml` |
| `MainActivity.kt` | `app/src/main/java/www/daqtpi/com/MainActivity.kt` |

Note the MainActivity folder path: the package `www.daqtpi.com` becomes the
folders `www/daqtpi/com`. In Android Studio's left panel (Android view) it's
simply **app → java → www.daqtpi.com → MainActivity.kt**.

## Paste order

1. **`build.gradle.kts` first.** Replace the whole file, then click
   **"Sync Now"** when Android Studio offers (top-right). This downloads the
   Play Billing libraries — without it, MainActivity will show red errors.
2. **`AndroidManifest.xml` next.** Whole-file replace.
3. **`MainActivity.kt` last.** Whole-file replace. If errors remain after the
   sync in step 1, screenshot them rather than guessing.

One heads-up: if Android Studio underlines the theme name
(`Theme.RelationshipReferee`) in the manifest in red, that just means the
template generated a slightly different theme name in your project — flag it
and it can be matched in one line.

## Before purchases can work — Play Console checklist

The code is only half of it. These Google-side steps are required, in
roughly this order:

1. **Upload key reset approved.** A signed build can only be uploaded once
   Google has approved the new upload key (requested Oct 2026; approval email
   gives the date the new key becomes active).
2. **Create the product.** Play Console → Relationship Referee →
   Monetize with Play → Products → **One-time products** → create product ID
   **`verdict`**, price **$1.00**, and **Activate** it. The ID must be exactly
   `verdict` (lowercase) or the app won't find it.
3. **Upload a signed AAB to a test track.** Build → Generate Signed App
   Bundle in Android Studio (signed with the new key), then upload it in
   Play Console under **Internal testing** (or Closed testing). Purchases
   only "exist" for Google once a build containing the BILLING permission
   has been uploaded — the product can't be tested before this.
4. **Add license testers.** Play Console → Settings → **License testing** →
   add the Gmail address(es) that will test purchases. Testers aren't charged
   real money and see test payment methods.
5. **Test on a real Android phone** signed into the Play Store with a tester
   account, using the test-track install link. Play Billing does not work
   properly in plain emulators.

Also: the upload's **versionCode** (set to 100 in `build.gradle.kts`) must be
higher than the current AppCreator24 build's version code. If Play rejects an
upload over versionCode, raise the number and build again.

## Known limits (v1, by design)

- **No server-side purchase verification.** The app trusts the purchase
  result on the device and immediately acknowledges + consumes it. There's
  no backend checking receipts with Google. For a $1 product this is a
  reasonable trade-off, but a technical user could bypass payment — and
  Google recommends server verification for anything higher-value.
- **The verdict itself is still the web form.** Paying unlocks
  https://daqtpi.com/referee in a WebView; submissions, email delivery, and
  the "verdict within 24 hours" wording all live on that page. If the page
  still says *free*, it needs its own update once payments go live (that was
  already the plan).
- **One purchase = one trip to the form.** Because the purchase is consumed
  on success, closing and reopening the app returns to the start screen and
  a new verdict costs another $1. The "Start over" button in the WebView
  header goes back to the start screen.
- **Pending payments** (slow payment methods) are handled by re-checking on
  app start/resume: once Google marks the purchase complete, the app
  consumes it and lets the user straight in.
