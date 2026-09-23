/* Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.chunkworks.rangedweaponsmod.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: all chambers including wrap; idle/future/active/completed shot;
 * hammer strike/hold/recock; reload closed/open/closed; invalid inputs. */
final class RevolverActionTest {
    @Test void everyShotIndexesOneSixthTurnIncludingWrap() {
        for (int chamber=0; chamber<6; chamber++) {
            double start=RevolverAction.sample(chamber,0,0).cylinder();
            assertEquals(30,RevolverAction.sample(chamber,4,0).cylinder()-start,1e-9);
            assertEquals(60,RevolverAction.sample(chamber,8,0).cylinder()-start,1e-9);
            assertEquals(chamber*60,RevolverAction.sample(chamber,100,0).cylinder(),1e-9);
            assertEquals(chamber*60,RevolverAction.sample(chamber,-1,0).cylinder(),1e-9);
        }
    }
    @Test void hammerStrikesThenRecocksBeforeTheNextShot() {
        assertEquals(35,RevolverAction.sample(1,0,0).hammer());
        assertEquals(17.5,RevolverAction.sample(1,.5,0).hammer());
        assertEquals(0,RevolverAction.sample(1,1,0).hammer());
        assertEquals(0,RevolverAction.sample(1,2,0).hammer());
        assertEquals(17.5,RevolverAction.sample(1,5,0).hammer());
        assertEquals(35,RevolverAction.sample(1,8,0).hammer());
        assertEquals(35,RevolverAction.sample(1,12,0).hammer());
    }
    @Test void reloadOpensAndClosesSmoothly() {
        assertEquals(0,RevolverAction.sample(0,-1,0).opening());
        assertEquals(.5,RevolverAction.sample(0,-1,.09).opening(),1e-9);
        assertEquals(1,RevolverAction.sample(0,-1,.5).opening());
        assertEquals(.5,RevolverAction.sample(0,-1,.91).opening(),1e-9);
        assertEquals(0,RevolverAction.sample(0,-1,1).opening());
    }
    @Test void rejectsInvalidPosesAndSamples() {
        for (int chamber : new int[]{-1,6})
            assertThrows(IllegalArgumentException.class,()->RevolverAction.sample(chamber,0,0));
        for (double bad : new double[]{Double.NaN,Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->RevolverAction.sample(0,bad,0));
            assertThrows(IllegalArgumentException.class,()->new RevolverAction(bad,0,0));
            assertThrows(IllegalArgumentException.class,()->new RevolverAction(0,bad,0));
        }
        for (double bad : new double[]{-.1,1.1,Double.NaN,Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->RevolverAction.sample(0,0,bad));
            assertThrows(IllegalArgumentException.class,()->new RevolverAction(0,0,bad));
        }
    }
}
