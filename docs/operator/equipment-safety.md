# Equipment and RF safety

## Receive-only boundary

The Android application exposes only initialize, identify, open, receive/sweep,
stop, statistics, and close operations. It contains no application-accessible
HackRF transmit call. Do not modify the native boundary or substitute an SDR
application with transmit controls during a field survey.

## Protect the receiver and phone

- The HackRF One receiver input must remain below its documented maximum
  (−5 dBm). Never cable a transmitter directly to it. Use a rated attenuator,
  DC block, filter, splitter, or shielded fixture selected by a competent RF
  operator when working near a conducted source.
- Attach the antenna before walking. Confirm SMA versus RP-SMA connector type;
  do not force mismatched connectors.
- Start with RF amplifier and antenna-port power off and conservative LNA/VGA
  gain. Record every deliberate gain change in a new equipment-profile version.
- Antenna-port power remains off unless a known compatible active antenna or
  LNA explicitly requires the HackRF supply. The MVP UI does not enable it.
- Use strain relief. Keep the HackRF, hub, and cables dry, ventilated, and clear
  of metal that could short connectors.
- Prefer a powered USB-C hub for long runs. Do not use a hub that backfeeds the
  phone or becomes hot. Stop acquisition before changing the USB topology.
- Survey only where walking and device use are safe and lawful. Do not watch the
  screen while crossing traffic or entering restricted areas.

## Controlled test sources

No over-the-air transmission is required. Use ambient signals or a
shielded/attenuated laboratory fixture. Confirm attenuation and receiver input
level before connection. Firmware work is outside the app: stop, close, and
disconnect the Android receive session first.
