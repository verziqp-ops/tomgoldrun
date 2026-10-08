
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
