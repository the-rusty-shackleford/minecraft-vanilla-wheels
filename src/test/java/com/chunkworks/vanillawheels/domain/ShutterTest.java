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

    // The sinking kind (D-0036). Partitions: travel by kind; advance opening, closing, obstructed
    // closing and the limits, by each kind's step (a step that would pass open or shut stops there); sunkTop for the sill row, a middle row and the top row at drop 0, a
    // part block, a whole block and the full travel; out-of-range rows and drops.
    @Test void aSinkingCurtainTravelsTheWholeHeightARollingOneStopsUnderItsHeader() {
        var door=new Shutter.Rectangle(0,0,6,4);
        assertEquals(28,Shutter.travel(door,Shutter.Kind.ROLLING));
        assertEquals(32,Shutter.travel(door,Shutter.Kind.SINKING));
        int drop=0;
        for(int i=0;i<40;i++)drop=Shutter.advance(door,Shutter.Kind.SINKING,drop,true,false);
        assertEquals(32,drop);
        assertEquals(32,Shutter.advance(door,Shutter.Kind.SINKING,32,false,true),"closing waits while the doorway is occupied");
        assertEquals(28,Shutter.advance(door,Shutter.Kind.SINKING,32,false,false),"half a block a tick");
        assertEquals(4,Shutter.advance(door,Shutter.Kind.SINKING,0,true,false));
        assertEquals(32,Shutter.advance(door,Shutter.Kind.SINKING,30,true,false),"never past open");
        assertEquals(0,Shutter.advance(door,Shutter.Kind.SINKING,2,false,false),"never past shut");
        assertEquals(27,Shutter.advance(door,Shutter.Kind.ROLLING,28,false,false),"a rolling door an eighth a tick");
        for(int i=0;i<40;i++)drop=Shutter.advance(door,Shutter.Kind.SINKING,drop,false,false);
        assertEquals(0,drop);
        assertThrows(IllegalArgumentException.class,()->Shutter.advance(door,Shutter.Kind.SINKING,33,true,false));
        assertEquals(Shutter.advance(door,10,true,false),Shutter.advance(door,Shutter.Kind.ROLLING,10,true,false),"the old call is the rolling kind");
    }
    @Test void aSinkingCurtainEmptiesItsRowsFromTheTopDown() {
        var door=new Shutter.Rectangle(0,0,6,4);
        for(int row=0;row<4;row++)assertEquals(1.0,Shutter.sunkTop(door,row,0),1e-9,"shut: every row full");
        assertEquals(0.5,Shutter.sunkTop(door,3,4),1e-9,"half a block down: the top row half full");
        assertEquals(1.0,Shutter.sunkTop(door,2,4),1e-9);
        assertEquals(0.0,Shutter.sunkTop(door,3,8),1e-9,"a block down: the top row empty");
        assertEquals(0.25,Shutter.sunkTop(door,0,30),1e-9,"nearly open: a sliver at the sill");
        for(int row=0;row<4;row++)assertEquals(0.0,Shutter.sunkTop(door,row,32),1e-9,"open: every row empty");
        assertThrows(IllegalArgumentException.class,()->Shutter.sunkTop(door,4,0));
        assertThrows(IllegalArgumentException.class,()->Shutter.sunkTop(door,-1,0));
        assertThrows(IllegalArgumentException.class,()->Shutter.sunkTop(door,0,33));
    }
}
