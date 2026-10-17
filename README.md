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

## What the demo window does

- The content is drawn on a reference canvas of the given size, in canvas units.
  The window shows the whole canvas, uniformly scaled; the mouse position is
  reported in canvas units.
- The title is the fixed name, four spaces, then a subtitle the program changes
  (click the button).
- The window opens centered, covering the given fraction of the screen area.
- Dragging an edge of the decorated window: while the drag lasts, the canvas is
  drawn at the fitting scale with bands in the leftover; when the drag settles,
  an aspect ratio request fires. With `Accept aspect ratio requests` the canvas
  takes the new shape; without it the window snaps back to the canvas shape.
- Maximize gives a maximized decorated window (bands when the request is
  rejected). Unmaximize gives the Fearless window: same bounds, no title bar,
  the Fearless corner at the top left.
- The corner: drag it to move the window, click it for the desk decorations.
- The full screen key toggles full screen; the corner is there too, and a click
  on it gives a decorated window at the bounds recorded before full screen.
- The close key, the close button and the corner never close by themselves:
  they ask. With `Ask before closing` the question is drawn inside the canvas.
- With `Pulse the opacity` the window opacity swings between the floor and
  opaque while it is a Fearless window; a decorated window and a full screen
  window are opaque (the toolkit refuses opacity on both).
- Full screen is asked of the desk as such (`GraphicsDevice.setFullScreenWindow`,
  the `_NET_WM_STATE_FULLSCREEN` hint on X11): a borderless window merely
  sized like the screen is kept inside the work area by GNOME.

## Build

From the repository root, with a JDK 26:

    java build/Build.java

The result is `out/app/fearless_gui_demo` (the application image) and the
archive in `out/dist`.
The GitHub Actions workflow builds Linux and Windows on every push to `main` and
attaches both archives to the `demo` release.
