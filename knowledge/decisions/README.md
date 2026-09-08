---
title: Ranged Weapons Mod — decisions log
type: index
layer: store
tags: [index]
---

# Ranged Weapons Mod — decisions log

**Append-only.** Never edit an entry's rationale; supersede via a new entry with
`supersedes: D-NNNN`. Every entry has a status: `Active` / `Superseded` /
`Rejected`.

A decision note is warranted when the call took more than five minutes of
thought and someone later would want to know *why*.

| Id | Topic |
|----|-------|
| D-0001 | The protocol's fallback tier operates our guns |
| D-0002 | Hold-to-fire is a press/release message and a server clock, never `Item.use` (superseded by D-0005) |
| D-0003 | Recoil is a camera offset that recovers, never the player's rotation |
| D-0004 | The art is generated, and its display transforms were calibrated by photograph |
| D-0005 | The press is vanilla's use, so interactions win; the release and the clock stay ours (superseded by D-0006) |
| D-0006 | The click is offered to the target with vanilla's calls, then cancelled: vanilla's item use drops the hand (superseded by D-0011) |
| D-0007 | The automatic class holds; every other class fires once per pull; aiming is a stance |
| D-0008 | Guns are assembled from parts described once, in the pure layer, and written out by data generation |
| D-0009 | A one-handed gun is held out level, by an arm pose added to the game's enum |
| D-0010 | Rounds of one family may differ; the gun loads one kind at a time; damage matches the mod the players compare to |
| D-0011 | The trigger is the attack key, cancelled outright; the use key is vanilla's |
