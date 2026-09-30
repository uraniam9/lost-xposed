# Lost Xposed

The settings Android keeps to itself.

A status bar clock you compose from pieces — mix time, fuzzy time, date, day and battery, then set size, colour, weight and font, with a live preview. Per-app display density, font scale and refresh rate, with presets scaled to your own device. Notification rules that block a message before it's posted, by app or keyword — which no notification listener can do, since by the time one sees a notification it has already arrived. Hardware key actions, a read-only power inspector, and a boot guard that disables a feature and reports itself if the device ever fails to boot with it active.

## Where it actually stands

Beta, as of 0.2.2-beta. Verified on one phone so far: a Nothing AIN065, Android 16, Vector 2.2. Six of the eight features have been seen doing their actual job there, including Clock Studio, Notification rules, Hardware keys and Power inspector. Per-app display and Text engine are partly tested, and the app says so on each feature's own card rather than claiming otherwise.

## Source

GPL-3.0-or-later. Full source, issue templates and every release: https://github.com/uraniam9/lost-xposed
