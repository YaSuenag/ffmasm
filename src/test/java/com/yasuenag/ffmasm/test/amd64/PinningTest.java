/*
 * Copyright (C) 2023, Yasumasa Suenaga
 *
 * This file is part of ffmasm.
 *
 * ffmasm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ffmasm is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ffmasm.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.yasuenag.ffmasm.test.common;

import java.lang.foreign.ValueLayout;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.yasuenag.ffmasm.Pinning;


@EnabledOnOs(architectures = {"amd64"})
public class PinningTest{

  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testPinning() throws Throwable{
    int[] array = new int[]{1, 2, 3, 4};
    int[] expected = new int[]{5, 6, 7, 8};

    var pinnedMem = Pinning.getInstance()
                           .pin(array)
                           .reinterpret(ValueLayout.JAVA_INT.byteSize() * array.length);
    for(int idx = 0; idx < expected.length; idx++){
      pinnedMem.setAtIndex(ValueLayout.JAVA_INT, idx, expected[idx]);
    }
    Pinning.getInstance().unpin(pinnedMem);

    // Kick GC to check whether pinning prevent it.
    System.gc();

    Assertions.assertArrayEquals(expected, array);
  }

}
