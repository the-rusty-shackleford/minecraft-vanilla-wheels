/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/** Partitions: singleton/min/max rectangle, gaps/L-shapes/disconnected/duplicate/overflow;
 * off/on power, closed/mid/open travel, obstruction and reversal, invalid bounds. */
final class ShutterTest {
    @Test void boundedFilledRectangles() {
        for(int w:new int[]{1,4,16})for(int h:new int[]{1,5,16}) {
            var cells=new ArrayList<Shutter.Cell>();
            for(int x=0;x<w;x++)for(int y=0;y<h;y++)cells.add(new Shutter.Cell(x-8,y-60));
            var r=Shutter.rectangle(cells).orElseThrow();
            assertEquals(new Shutter.Rectangle(-8,-60,w,h),r);
            assertEquals(h*8-4,r.fullyRaised());
        }
    }
    @Test void incompleteAndOversizedCannotBecomeDoors() {
        assertTrue(Shutter.rectangle(List.of()).isEmpty());
        var a=new Shutter.Cell(0,0);
        assertTrue(Shutter.rectangle(List.of(a,a)).isEmpty());
        assertTrue(Shutter.rectangle(List.of(a,new Shutter.Cell(1,0),new Shutter.Cell(0,1))).isEmpty());
        assertTrue(Shutter.rectangle(List.of(a,new Shutter.Cell(2,0))).isEmpty());
        var longRow=new ArrayList<Shutter.Cell>();
        for(int x=0;x<17;x++)longRow.add(new Shutter.Cell(x,0));
        assertTrue(Shutter.rectangle(longRow).isEmpty());
        assertTrue(Shutter.rectangle(List.of(new Shutter.Cell(Integer.MIN_VALUE,0),new Shutter.Cell(Integer.MAX_VALUE,0))).isEmpty());
    }
    @Test void powerLevelAndSafetyDetermineMotion() {
        var door=new Shutter.Rectangle(0,0,5,4);
        int lift=0;
        for(int i=0;i<40;i++)lift=Shutter.advance(door,lift,true,false);
        assertEquals(28,lift);
        assertEquals(lift,Shutter.advance(door,lift,false,true));
        assertEquals(27,Shutter.advance(door,lift,false,false));
        assertEquals(28,Shutter.advance(door,27,true,true));
        for(int i=0;i<40;i++)lift=Shutter.advance(door,lift,false,false);
        assertEquals(0,lift);
    }
    @Test void invalidTravelIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new Shutter.Rectangle(0,0,17,2));
        assertThrows(IllegalArgumentException.class,()->new Shutter.Rectangle(0,0,1,0));
        var door=new Shutter.Rectangle(0,0,1,1);
        assertThrows(IllegalArgumentException.class,()->Shutter.advance(door,-1,true,false));
        assertThrows(IllegalArgumentException.class,()->Shutter.advance(door,5,false,false));
    }
}
