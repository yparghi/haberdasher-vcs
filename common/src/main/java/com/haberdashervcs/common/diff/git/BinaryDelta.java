/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.haberdashervcs.common.diff.git;

import java.util.ArrayList;
import java.util.List;

import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import org.eclipse.jgit.internal.JGitText;


/**
 * Recreate a stream from a base stream and a GIT pack delta.
 * <p>
 * This entire class is heavily cribbed from <code>patch-delta.c</code> in the
 * GIT project. The original delta patching code was written by Nicolas Pitre
 * (&lt;nico@cam.org&gt;).
 * </p>
 */
class BinaryDelta {


    static class PatchMapping {
		int baseStart = -1;
		int deltaStart = -1;
		int outStart = -1;
		int rangeSize = -1;

		@Override public String toString() {
		    String type;
		    int start;
		    if (baseStart >= 0) {
		        type = "BASE";
		        start = baseStart;
			} else {
		    	type = "DELTA";
		    	start = deltaStart;
			}
		    return String.format("Patch{%s %d -> %d, %d bytes}", type, start, outStart, rangeSize);
		}
	}


	/**
     * Calculate the byte ranges from the base or delta that correspond to the byte ranges of the patched result.
	 */
	static final List<PatchMapping> computePatchMappings(RandomAccessBytes delta) {

		int deltaPtr = 0;

		// Length of the base object (a variable length int).
		//
		// NOTE for HD: Though we don't use baseLen or resLen, they're necessary to set starting pointer positions, I
		// think.
		int baseLen = 0;
		int c, shift = 0;
		do {
			// TODO: Check the performance difference of using a buffer + readInto(). Maybe we can remove at() from RAB.
			c = delta.at(deltaPtr++) & 0xff;
			baseLen |= (c & 0x7f) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);

		// Length of the resulting object (a variable length int).
		int resLen = 0;
		shift = 0;
		do {
			c = delta.at(deltaPtr++) & 0xff;
			resLen |= (c & 0x7f) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);


		List<PatchMapping> out = new ArrayList<>();
		int resultPtr = 0;
		while (deltaPtr < delta.length()) {
			final int cmd = delta.at(deltaPtr++) & 0xff;
			if ((cmd & 0x80) != 0) {
				// Determine the segment of the base which should
				// be copied into the output. The segment is given
				// as an offset and a length.
				//
				int copyOffset = 0;
				if ((cmd & 0x01) != 0)
					copyOffset = delta.at(deltaPtr++) & 0xff;
				if ((cmd & 0x02) != 0)
					copyOffset |= (delta.at(deltaPtr++) & 0xff) << 8;
				if ((cmd & 0x04) != 0)
					copyOffset |= (delta.at(deltaPtr++) & 0xff) << 16;
				if ((cmd & 0x08) != 0)
					copyOffset |= (delta.at(deltaPtr++) & 0xff) << 24;

				int copySize = 0;
				if ((cmd & 0x10) != 0)
					copySize = delta.at(deltaPtr++) & 0xff;
				if ((cmd & 0x20) != 0)
					copySize |= (delta.at(deltaPtr++) & 0xff) << 8;
				if ((cmd & 0x40) != 0)
					copySize |= (delta.at(deltaPtr++) & 0xff) << 16;
				if (copySize == 0)
					copySize = 0x10000;

				//System.arraycopy(base, copyOffset, result, resultPtr, copySize);
				PatchMapping thisRange = new PatchMapping();
				thisRange.baseStart = copyOffset;
				thisRange.outStart = resultPtr;
				thisRange.rangeSize = copySize;
				out.add(thisRange);
				resultPtr += copySize;
			} else if (cmd != 0) {
				// Anything else the data is literal within the delta
				// itself.
				//

				//System.arraycopy(delta, deltaPtr, result, resultPtr, cmd);
				PatchMapping thisRange = new PatchMapping();
				thisRange.deltaStart = deltaPtr;
				thisRange.outStart = resultPtr;
				thisRange.rangeSize = cmd;
				out.add(thisRange);
				deltaPtr += cmd;
				resultPtr += cmd;

			} else {
				// cmd == 0 has been reserved for future encoding but
				// for now its not acceptable.
				//
				throw new IllegalArgumentException(
						JGitText.get().unsupportedCommand0);
			}
		}

		//return result;
		return out;
	}

}
