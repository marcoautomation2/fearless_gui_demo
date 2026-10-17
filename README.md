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

| | whole | part |
|---|---|---|
| Fearless corner | 1 full screen | 2 Fearless window |
| desk decorations | 3 decorated maximized | 4 decorated window |

The demo sets the bounds of a window when it creates the window and while the
window is a Fearless window; at no other time. So the Fearless window never
shows bands, and the other three states show bands when the window and the
canvas have different shapes.

| The user | Changes | Transitions |
|---|---|---|
| double clicks the corner | the decorations | 1 to 3, 2 to 4 |
| unmaximizes (button, title bar double click, key) | the decorations | 3 to 1 |
| maximizes | whole or part | 4 to 3 |
| drags the title bar of a maximized window | whole or part | 3 to 4 |
| drags the corner of a full screen window | whole or part | 1 to 2, the drag going on |
| releases the corner or the tip with the pointer within a corner's size of the top left of the work area | whole or part | 2 to 1 |
| presses the full screen key | both, toward full screen | 2, 3, 4 to 1 |
| presses the full screen key in full screen | whole or part | 1 to 2 |

The window that is left gives its bounds to the new one: a full screen window
gives the largest canvas that fits the work area, centered; any other window
gives its own bounds. A decorated window takes them as its outer bounds, with
the canvas fitted inside; coming from full screen it is then maximized.
In a Fearless window the corner drag moves the window and the tip drag resizes
it from the top left, bottom right fixed, canvas shape kept.

The edge rule: a Fearless window never leaves the work area. When a drag would
push its bottom or right edge past the work area, the window shrinks instead,
keeping its shape and its top left under the pointer, down to the min size
(`Min screen area` in the launcher); at the min size it stops following the
pointer. A drag never grows the window: its size at the press is the most it can
have during the drag, and the tip is the one way to grow it. The window has no
memory: what is on screen is the whole state.

## Other behaviours

- The content is drawn on a reference canvas of the given size, in canvas units.
  The window shows the whole canvas, uniformly scaled; the mouse position is
  reported in canvas units.
- The title is the fixed name, four spaces, then a subtitle the program changes
  (click the button).
- The window opens centered, covering the given fraction of the screen area,
  capped so that it fits the work area; the min size is capped the same way.
- Dragging an edge of the decorated window: the canvas is drawn at the fitting
  scale with bands in the leftover; when the drag settles, an aspect ratio
  request fires. With `Accept aspect ratio requests` the canvas takes the new
  shape; without it the bands stay. The demo never resizes a decorated window:
  the end of the desk's resize drag is not reported, and bounds set while the
  desk is still resizing make the two disagree on the size of the window.
- The close key and the close button never close by themselves: they ask.
  With `Ask before closing` the question is drawn inside the canvas.
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
  drag. After the event the demo watches the window position on its repaint
  ticks: still for 100 ms gives full screen; no such still period within
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
