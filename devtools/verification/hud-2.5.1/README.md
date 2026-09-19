# Ammo HUD 2.5.1 verification

`runPhotoBooth -PboothHud` uses actual Backpacks+ 0.2.1 and Quick Slot 0.1.1,
with Complementary Unbound 5.8.1 and the existing animation packs. Each comparison
has the old HUD on the left and the final HUD on the right. Normal GUI crops are
magnified 2x; compact GUI captures already use four screen pixels per GUI pixel.

The old normal row overlaps Quick Slot and mount counts. The final panel clears
them, including G storage actions. Compact gear and both handedness cases remain
separate, and the renamed magazine is bounded. The reload bar shares the panel's
width. Visual acceptance for these overlap/readability checks: pass in all four
captured comparisons. This does not assess unrequested weapon-model aesthetics.
