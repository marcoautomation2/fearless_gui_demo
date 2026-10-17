# Fearless Gui demo

A small Swing application that behaves the way the Fearless Gui design describes
(`tasks/ImprovedGuiAPI.txt` in ZeroToHero), so the design can be felt in hand
before it is implemented in the standard library.

## Run

Download the build for your system from the `demo` release of this repository:
`fearless_gui_demo_linux.tar.gz` or `fearless_gui_demo_windows.zip`.
Extract it and start `fearless_gui_demo/bin/fearless_gui_demo` (Linux) or
`fearless_gui_demo/fearless_gui_demo.exe` (Windows).
No Java installation is needed: the runtime is inside.

The first window is the launcher: the fields are the settings a Fearless program
fixes before the window opens.
`Show` opens the demo window with those settings; the launcher stays, so several
windows with different settings can be compared.
Under the form the launcher shows each window as the program sees it (state,
bounds, showing or not, focus, opacity), refreshed twice a second, and a log of
every transition, aspect ratio request, desk report (shown, hidden, iconified,
closed) and uncaught exception.
When a window seems to vanish, the log says what the program last did and what
the desk last reported.

## The four states

The demo follows the state machine of the design document; the terms are the
ones defined there (virtual pixel space, picture, bands, content area).

    State 1: FullScreen       Fearless corner, the desk's full screen.
    State 2: FearlessWindow   Fearless corner and resize handle; the window is
                              the picture, so there are no bands.
    State 3: DeskMaximized    desk border, the desk's maximized state; the restore
                              size is the largest desk window in the work area.
    State 4: DeskWindow       desk border, not maximized; the window is compacted,
                              so there are no bands once the desk has finished
                              resizing it.

    Corner drag              1 to 2, the drag going on; in 2 it moves the window.
    Resize handle drag       in 2 it resizes the window, bottom right fixed.
    Release within screenHeight/16 of the top left of the work area: 2 to 1.
    Corner double click      1 to 4, 2 to 4.
    Full screen key          1 to 2; 2, 3, 4 to 1.
    Desk maximize            4 to 3: a new window, maximized.
    Desk unmaximize          3 to 1 when the window has not moved by the time no
                             mouse button is down; a title bar drag (it moved)
                             stays in 4 and is compacted.
    In every state the picture covers at least the min screen area.

## Other behaviours

- The title is the fixed name, four spaces, then a subtitle the program changes
  (click the first button).
- The second button swaps the width and the height of the virtual pixel space:
  a Fearless window and a desk window keep the center and the area of their
  content area; a maximized window and full screen show new bands.
- With `Accept aspect ratio requests` the virtual pixel space takes the aspect
  ratio of every request; without it the bands stay until the window changes.
- After a desk resize, the demo compacts the window on the first repaint tick
  with no mouse button down: a desk resize by the pointer lasts as long as a
  button is held. After an unmaximize, once no button is down, a window that is
  exactly the restore size the demo gave it goes to full screen; a window the
  desk moved (a title bar drag) or arranged (a snap) stays a desk window.
  `Desk` reads the button state from the platform: `GetAsyncKeyState` on
  Windows, `XQueryPointer` on X11, `CGEventSourceButtonState` on macOS.
- The desk keeps no size of an earlier window. On Windows, each compaction
  also sets the window's restore size to its current size with
  `SetWindowPlacement`, which ends a snap: Java's `setBounds` moves a snapped
  window but leaves the size from before the snap as its restore size. When
  the desk maximizes a window, the demo sets its restore size the same way.
  On other desks a maximize replaces the window with a new maximized one.
- A desk window has the desk's minimum size: the smallest window whose picture
  covers the min screen area. The Windows peer of the toolkit hands the desk
  the minimum size in user space, and the desk reads it in device pixels, so
  on Windows the frame reports it multiplied by the display scale.
- The close key and the close button never close by themselves: they ask.
  With `Ask before closing` the question is drawn inside the picture.
- With `Pulse the opacity` the window opacity swings between the floor and
  opaque while it is a Fearless window, corner included; a desk window and a
  full screen window are opaque (the toolkit refuses opacity on both).
- Full screen is asked of the desk as such (`GraphicsDevice.setFullScreenWindow`,
  the `_NET_WM_STATE_FULLSCREEN` hint on X11): a borderless window merely
  sized like the screen is kept inside the work area by GNOME.
  The application runs with `-Dsun.java2d.d3d=false`: on Windows the Direct3D
  pipeline makes full screen an exclusive mode of the display, and on a desk
  scaled above 100% the bounds set while leaving it reach the native window
  unscaled. Run from a JDK, pass the same option, `--enable-native-access=demo`,
  and on Windows `--add-opens java.desktop/java.awt=demo` and
  `--add-opens java.desktop/sun.awt.windows=demo` (the window handle).
- The windows opened by `Show` share one program, and a program has one full
  screen window: a window entering full screen first turns the full screen
  window of another `Show` into a Fearless window.

## Build

From the repository root, with a JDK 26:

    java build/Build.java

The result is `out/app/fearless_gui_demo` (the application image) and the
archive in `out/dist`.
The GitHub Actions workflow builds Linux and Windows on every push to `main` and
attaches both archives to the `demo` release.
