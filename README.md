# Quick Share PWA prototype
Installable PWA source for Android/ChromeOS/browser sharing and QR-paired WebRTC transfer.

## Run
Serve over HTTPS or localhost (required for PWA install, service workers and WebRTC). For example: `python3 -m http.server 8080`.

## Included
- PWA manifest, service-worker app-shell cache and original share-mark icon.
- `share_target` for inbound title/text/URL/files on compatible installed browsers; service worker persists shared payloads locally for the send flow.
- Web Share API for outbound sharing when supported.
- QR pairing, PeerJS/WebRTC datachannel transfer for text, links and files, animated receiver UI and swipe-to-send.
- Drag-and-drop/file picker, responsive Japanese Material-inspired styling.

## Limitations
This is not Google's Quick Share protocol and does not include its nearby discovery, trust model, or exact branded UI. The mark is a generic original share icon, not Google's asset. The reviewed Google Brand Resource Center explains that usage permission/conditions depend on the specific brand element; no official Quick Share downloadable developer asset/license was found in the public references checked, so the prototype keeps its original mark rather than copying third-party logo downloads. PWA share targets vary by browser; desktop Chrome may not list an installed PWA as a target. The public PeerJS broker is a third-party dependency; network/firewall restrictions, large files and multi-file reliability need testing. Android APK/native share-intent implementation is not included yet. File share-target behavior is implemented in the PWA but still needs testing on actual Android/ChromeOS devices. Do not use for sensitive transfers until a controlled signaling service and security review exist. No GitHub/Vercel deployment has been made.

## Android wrapper
The `android/` project is a native WebView shell with Android `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent filters for text and common file MIME types. It copies received shared files to the app cache, presents the shared text/filenames in the app, and uses Android's native chooser for outbound text sharing. A GitHub Actions workflow builds a debug APK artifact when triggered or when Android sources change. The APK has not yet been built or device-tested in this environment.
