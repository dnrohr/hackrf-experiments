# HackRF experiments

Self-contained Windows 11 tooling for a HackRF One. Installed applications and
generated captures stay out of Git.

The proposed Android survey application is defined in
[docs/android-rf-field-notebook-spec.md](docs/android-rf-field-notebook-spec.md).

## Installed software

Radioconda is installed in `.tools/radioconda`; HackRF Tools are updated to
version 2026.01.3, matching the official firmware bundle stored under `.tools`.
The environment provides:

- HackRF Tools (`hackrf_info`, `hackrf_sweep`, `hackrf_transfer`, and firmware tools)
- Gqrx for normal receive/listen experiments
- GNU Radio Companion for building signal-processing flows
- SoapyHackRF plus Python, NumPy, SciPy, and related SDR packages

Reinstall on another Windows 11 machine with:

```powershell
.\scripts\Install-HackRFTools.ps1
```

## First connection

1. Attach an antenna suitable for the frequency you want to receive.
2. Connect the HackRF directly to the PC with a USB data cable.
3. Run the hardware diagnostic:

   ```powershell
   .\scripts\Test-HackRF.ps1
   ```

4. If the script reports a driver problem, run `.\scripts\Start-Zadig.ps1` and
   use Zadig to replace the HackRF device's driver with **WinUSB**. In Zadig,
   enable **Options > List All Devices** if the HackRF is not initially shown.
   Do not select another USB device.
5. Start the receiver:

   ```powershell
   .\scripts\Start-Gqrx.ps1
   ```

In Gqrx, select a HackRF/SoapyHackRF input, start at 8 or 10 MS/s, choose WFM,
and tune to a strong local FM broadcast station. Start with the RF amplifier and
antenna power disabled and increase LNA/VGA gain gradually.

Use `.\scripts\Start-GNURadio.ps1` for GNU Radio Companion or
`.\scripts\Enter-SDRShell.ps1` for an SDR-configured PowerShell session.

Do not update firmware speculatively. If `Test-HackRF.ps1` shows firmware older
than 2026.01.3, close all SDR applications and run
`.\scripts\Update-HackRFFirmware.ps1`. The updater displays the current device
information and requires an explicit confirmation before writing anything.

## RF safety

- The HackRF One's maximum receiver input is **-5 dBm**. Never connect another
  transmitter directly; use suitable attenuation.
- Leave antenna-port power off unless a connected active antenna or LNA
  explicitly needs the HackRF's 3.0-3.3 V supply.
- Begin with receive-only experiments. Transmit only into a suitable 50-ohm
  antenna or dummy load and only where you are authorized to transmit.
- A Wi-Fi-router antenna is normally intended for 2.4 or 5 GHz. It can be useful
  there if its connector is true SMA and its gender matches, but many router
  antennas are RP-SMA and must not be forced onto the HackRF's SMA connector.
