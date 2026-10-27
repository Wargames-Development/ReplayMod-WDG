# Saving recordings with extended chunk masks

The `Saving replay file` crash with `Unsupported chunk section mask: 16777215`
occurs during automatic marker processing. The recorded packet has a mask with
bits above bit 15, while the bundled ReplayStudio chunk codec handles only 16
sections. Narrowing that mask would discard data or misread the compressed
terrain payload.

Before moving the original recording into `replay_recordings/raw` or creating
cut/split outputs, marker processing now checks both masks in every legacy
single and bulk chunk packet. If a mask does not fit the codec, it leaves the
complete recording untouched, completes saving, and reports that cuts/splits
were skipped in chat and the log. The recording's original timestamps, markers,
metadata, and compressed terrain remain intact. Supported recordings continue
through normal marker processing.

This is a preservation safeguard, not support for editing extended terrain.
Automatic cuts and splits for these recordings require a compatible chunk codec.

For a recording affected by the earlier crash, look for the original `.mcpr`
in the recording folder's `raw` subfolder. Copy it back to the recording folder
under a new name before opening it. The crash report alone cannot establish
whether a particular recording is recoverable.
