# Safety and guided test rules

## Non-negotiable protections

- Never test while the vehicle is moving by touching the head-unit screen.
- A moving capture must be started while parked, run hands-free, and stopped only after the vehicle is safely parked. Prefer a passenger to supervise.
- Do not operate the selector, steering-wheel controls, windows, or wipers in a way that conflicts with the vehicle owner's manual.
- Never deliberately induce an SRS/airbag fault, disconnect an SRS component, or send diagnostic/write commands to the airbag ECU.
- Never open factory UART devices or compete with the factory CAN/MCU services.
- Never overlay, block, or replace the reverse-camera surface.
- Automatic scans may read state and inspect Binder/provider availability. They must not issue vehicle-control writes.
- USB and Bluetooth media transport commands are allowed only when explicitly pressed by the user from the app or selected guided test.

## Music button test protocol

The app keeps its own UI active. It must not tell the user to leave the app and open the radio or factory music UI.

For each source:
1. Capture the current title, artist, album, and play state.
2. Present one wheel button with its icon/name.
3. Ask for exactly one press.
4. Observe for a bounded window; correlate a Logic callback, input/media event, or a matching media-state transition.
5. Mark PASS only with positive evidence. Mark NO OBSERVABLE RESPONSE separately from FAILED; offer one guided retry.
6. If two or more USB tracks are not present, the app must not use title change as proof of next/previous.

## Sport / manual CVT mode protocol

- First confirm stationary P and brake state.
- Confirm the selector's Sport/Manual state without forcing the lever.
- Collect CAN snapshots before, during, and after one known selector or +/- input.
- For gear ratio steps that require movement, start a background capture while parked; do not request screen interaction during the drive. Stop/review after the vehicle is parked.
- Do not infer manual gear numbers from a single ambiguous transition. Each candidate value must repeat in the correct sequence and correlate with a single known +/- action.
- If the decoder or capture source is unavailable, show "not yet verified"; never substitute the operator's memory for a CAN reading.

## Guided-step UX contract

Every manual task must include:
- Preconditions and a clear cancellation path.
- One physical action per screen.
- A prominent button/icon illustration identifying the intended control.
- Confirmation of the baseline state before enabling the action.
- Automatic observation and a short timeout.
- Retry, skip-with-reason, and cancel options.
- A result that distinguishes confirmed, inconclusive, and not available.

The app must not ask the user to enter hexadecimal values, use ADB, copy logs, or navigate multiple factory menus.
