
### Experimental 0.5

Vehicle matching now uses a small locally trained 96-input / 24-hidden-unit MLP,
exported as endian-stable weights in `vehicle.bin`. Other object classes still
use image templates. This is not a general trained game detector. Gold/blue
candidates outside the perspective road are filtered, and a lower-screen colour
cue assists player-lane estimation. Automatic flight classification is disabled;
select flight manually until a reliable flight detector exists.

The always-visible button explicitly says “Увімкнути авто” when gestures are off,
and green “АВТО ✓ · Пауза” when enabled. Manual gesture tests require paused auto
and no longer silently turn it off. Live boxes default off to reduce recursive
screen-capture feedback; the Photo action renders boxes separately. Prefer
Android app-only capture. Captured content dimensions resize the projection.

`tools/train_vehicle.py` documents annotations, augmentation, hard-negative
mining and the fifth development photo excluded from training. Training uses NumPy and Pillow locally.
`tools/vehicle-validation.json` reports only limited patch-level validation;
sliding-window detection can still mistake train roofs for vehicles. There is
no measured gameplay survival rate, and no on-device validation of 0.5.

### Experimental 0.6

A recognised `JUMP` or `SLIDE` obstacle now triggers its vertical manoeuvre even
when adjacent lanes are free. The planner waits for proximity or estimated
arrival instead of jumping at first recognition, and suppresses repeated vertical
commands for 650 ms. Flight modes still prohibit vertical gestures.

Hazards use their lower image edge for proximity, estimated arrival and lane
assignment. The nearest hazard per type/lane takes priority over higher-score
faraway matches. A wide slide bar covers every lane whose projected centre lies
inside its bounds. A high-confidence specific barrier match overrides an
intersecting generic vehicle patch. The tall-X blocking template is restored.
Vehicle matches still require lateral avoidance: their classifier does not
identify ramps or distinguish all variants of trains and fire engines.

Lane observations cannot immediately undo a completed manoeuvre. One pending
automatic gesture is allowed until completion or cancellation; this avoids
planning duplicate commands before the worker sees gesture completion. Vertical
swipes start at 52% of the screen height, above the bottom control panel.

Validation: 40 planner checks, 7 perspective/coverage checks, existing matcher
checks and Android APK compilation. No on-device collision-avoidance guarantee.

### Experimental 0.7: early two-lane route

The planner can now plan two consecutive lateral gestures while an intermediate
obstacle still has enough clearance. It starts avoiding hazards earlier, queues
the second gesture 60 ms after completion of the first, and checks the latest
destination detections before continuing. It refuses a route through an imminent
middle obstacle or into a portal. It expires commands derived from old frames,
invalidates queued commands on pause, and recovers from missing gesture callbacks.
48 planner regressions and the existing geometry/matcher checks cover the change.
No measured on-device latency or survival improvement is available.

This APK still uses the previous experimental recognition. Small cars, ramps,
beach hazards and object-type errors are NOT fixed. RAMP decision support is
prepared but has no validated detector supplying it.

A real pretrained TorchVision SSDlite neural detector was trained locally in
several experiments, including manually labelled gameplay and reviewed tracker
proposals. tools/detector-validation.json records its failure on development
images excluded from training. It is rejected for autopilot use and is NOT
included in the APK. The scripts document dataset preparation, tracking,
augmentation, training and ONNX export. More correct, diverse annotations and
independent validation are needed before replacing the recognizer.
