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

1. Full screen, with the Fearless corner. Bands when the canvas and the screen
   have different shapes.
2. Fearless window, with the Fearless corner. Never bands: the window is always
   the canvas at some scale.
3. Decorated window maximized by the desk. Bands when the request is rejected.
4. Decorated window. Bands only while an edge drag has not settled.

| From | The user | To |
|---|---|---|
| 4 | maximizes | 3, as on every desk |
| 4 | moves, resizes, snaps, minimizes | 4, as on every desk; a resize settles back to the canvas shape |
| 3 | unmaximizes (button, title bar double click, key) | 1 |
| 3 | drags the title bar, which unmaximizes and moves | 4, as on every desk |
| 3 | resizes an edge, on a desk that unmaximizes in that direction only | 3, bands while any maximized bit is set |
| 1 | double clicks the corner | 3 |
| 1 | drags the corner or its tip | 2, at the largest size that fits the work area, under the pointer, the drag going on |
| 2 | double clicks the corner | 4, with the same bounds on screen and the same shape |
| 2 | drags the corner | 2, moved, under the edge rule |
| 2 | drags the tip | 2, resized from the top left, bottom right fixed, canvas shape kept, never below the min size |
| 2 | releases the corner or the tip with the pointer within a corner's size of the top left of the work area | 1 |
| 2, 3, 4 | presses the full screen key | 1 |
| 1 | presses the full screen key | 2, at the largest size that fits the work area, centered |

The edge rule: a Fearless window never leaves the work area. When a drag would
push its bottom or right edge past the work area, the window shrinks instead,
keeping its shape and its top left under the pointer, down to the min size
(`Min screen area` in the launcher); at the min size it stops following the
pointer. A drag never grows the window: the tip is the one way to grow it. The
window has no memory: what is on screen is the whole state.

## Other behaviours

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
- The close key, the close button and the corner never close by themselves:
  they ask. With `Ask before closing` the question is drawn inside the canvas.
- With `Pulse the opacity` the window opacity swings between the floor and
  opaque while it is a Fearless window, corner included; a decorated window
  and a full screen window are opaque (the toolkit refuses opacity on both).
  Keeping the corner opaque needs per-pixel translucency, and a per-pixel
  translucent window repainted continuously is shown or not at random under
  GNOME on XWayland, so the demo does not use it.
- Full screen is asked of the desk as such (`GraphicsDevice.setFullScreenWindow`,
  the `_NET_WM_STATE_FULLSCREEN` hint on X11): a borderless window merely
  sized like the screen is kept inside the work area by GNOME.
- The desk reports the same unmaximize for the button and for a title bar
  drag. After the event the demo watches the window position and the pointer
  on its repaint ticks: still for 100 ms gives full screen; movement within
  266 ms means the desk is moving the window, which stays decorated.
- Every swap of decorations is a new window, shown before the old one is
  disposed; the restored window shows for the still period plus whatever
  unmaximize animation the desk plays before the event.

## Build

From the repository root, with a JDK 26:

    java build/Build.java

The result is `out/app/fearless_gui_demo` (the application image) and the
archive in `out/dist`.
The GitHub Actions workflow builds Linux and Windows on every push to `main` and
attaches both archives to the `demo` release.
