# PHANToM GO Launcher Icon

Source image for app launcher icon.

## How to Generate Android Icons

1. Open Android Studio
2. Right-click on `app` module → `New` → `Image Asset`
3. Select `Launcher Icons (Adaptive and Legacy)`
4. In `Foreground Layer` tab:
   - Asset Type: Image
   - Path: Select this file (`docs/app_icon.png`)
   - Resize: 70%
5. In `Background Layer` tab:
   - Asset Type: Color
   - Color: #1B1638 (dark purple)
6. Click `Next` then `Finish`

This will generate all required icon files:
- `mipmap-anydpi-v26/ic_launcher.xml` (adaptive)
- `mipmap-anydpi-v26/ic_launcher_round.xml` (adaptive)
- `mipmap-*/ic_launcher.png` (legacy, all densities)
- `mipmap-*/ic_launcher_round.png` (legacy, all densities)

## Icon Design
- Ghost character with navigation goggles
- Map/route element
- Link/share symbol
- Dark purple background (#1B1638)
- Modern, friendly, navigation-focused
