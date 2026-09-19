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
| D-0012 | Every sound is a cut of a CC0 field recording; synthesis is retired |
| D-0013 | Magazines are ordered loads on the gun's stack; the gun's capacity is the magazine's; the protocol is untouched |
| D-0014 | Steel is Metals and Materials'; our old id is an alias of its ingot |
| D-0015 | The one-handed arm is the crossbow's trigger arm, number for number, so animation packs recognise it |
| D-0016 | Shared grip is read at pose time; accepted aim is a synced attachment, reload keeps its existing component |
| D-0017 | Bounded magazine text, readable parts and decoded audio headroom |

- [D-0018](D-0018.md): Require separately installed Metals and Materials.

- [D-0019](D-0019.md): Six-shot revolver, built-in medium-ammo cylinder and articulated action.

- [D-0020](D-0020.md): Vertical shotgun-pump recipe avoids the wooden roof recipes.

- [D-0021](D-0021.md): Lower-right ammo panel clears Quick Slot and backpack controls.
