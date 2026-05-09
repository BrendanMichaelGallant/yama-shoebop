# Yama Shoebop

A small RuneLite plugin for the Demonic Pacts League (Leagues VI). When you cast the League Home Teleport, the plugin plays a short bundled audio clip and suppresses the in-game teleport sound for the duration of the cast.

## How it works

- Listens for the local player's animation. When it matches the League Home Teleport animation ID (`13764`), the plugin starts the bundled audio clip on the RuneLite shared executor.
- During a short window after the trigger, both `SoundEffectPlayed` and self-`AreaSoundEffectPlayed` events are consumed so the in-game teleport sound is silenced while the clip plays.
- If the player moves to a new tile while the clip is playing (e.g. the teleport completes, or you walk away), the clip stops immediately and the mute window is cleared.

## Configuration

A single toggle: **Enabled**. Default: on.

## License

BSD-2-Clause. See `LICENSE`.
