/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.geoengine.util;

/**
 * An iterator that steps through points on a straight line using Bresenham's line-drawing algorithm.
 * <p>
 * Bresenham's algorithm calculates which grid points should be part of a straight line between two points.
 * </p>
 * @author Mobius
 */
public class GridLineIterator3D {
    // Current position of the iterator.
    private int currentX; // Current X position of the iterator.
    private int currentY; // Current Y position of the iterator.
    private int currentZ; // Current Z position of the iterator.

    // Target endpoint of the line.
    private final int targetX; // Target X endpoint of the line.
    private final int targetY; // Target Y endpoint of the line.
    private final int targetZ; // Target Z endpoint of the line.

    // Absolute distances along X, Y, and Z axes.
    private final int deltaX; // Absolute difference in X coordinates.
    private final int deltaY; // Absolute difference in Y coordinates.
    private final int deltaZ; // Absolute difference in Z coordinates.

    // Step directions for movement along the X, Y, and Z axes.
    private final int stepX; // Step direction for X (-1 or 1).
    private final int stepY; // Step direction for Y (-1 or 1).
    private final int stepZ; // Step direction for Z (-1 or 1).

    // Tracks the accumulated error terms for adjusting the minor axes.
    private int accumulatedErrorXY; // Accumulated error for the Y axis relative to X.
    private int accumulatedErrorXZ; // Accumulated error for the Z axis relative to X.

    // Indicates whether the iteration has started.
    private boolean hasStarted;

    /**
     * Initializes the iterator to step through points along a 3D line from (startX, startY, startZ) to (endX, endY, endZ).
     * @param startX the X-coordinate of the start point
     * @param startY the Y-coordinate of the start point
     * @param startZ the Z-coordinate of the start point
     * @param endX the X-coordinate of the end point
     * @param endY the Y-coordinate of the end point
     * @param endZ the Z-coordinate of the end point
     */
    public GridLineIterator3D(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        // Initialize current position to the starting point.
        currentX = startX;
        currentY = startY;
        currentZ = startZ;

        // Initialize target position.
        targetX = endX;
        targetY = endY;
        targetZ = endZ;

        // Calculate absolute differences in X, Y, and Z coordinates.
        deltaX = Math.abs(endX - startX);
        deltaY = Math.abs(endY - startY);
        deltaZ = Math.abs(endZ - startZ);

        // Determine step directions for X, Y, and Z based on the sign of the difference.
        stepX = startX < endX ? 1 : -1;
        stepY = startY < endY ? 1 : -1;
        stepZ = startZ < endZ ? 1 : -1;

        // Initialize accumulated error terms based on the dominant axis.
        // The dominant axis is the one with the largest delta, as it determines the primary iteration direction.
        if ((deltaX >= deltaY) && (deltaX >= deltaZ)) {
            accumulatedErrorXY = accumulatedErrorXZ = deltaX / 2;
        } else if ((deltaY >= deltaX) && (deltaY >= deltaZ)) {
            accumulatedErrorXY = accumulatedErrorXZ = deltaY / 2;
        } else {
            accumulatedErrorXY = accumulatedErrorXZ = deltaZ / 2;
        }

        // Set the initial iteration state to indicate the first point has not yet been processed.
        hasStarted = false;
    }

    /**
     * Advances the iterator to the next point on the line.
     * <p>
     * This method implements Bresenham's line-drawing algorithm,<br>
     * which uses accumulated error terms to decide when to step along the minor axes.
     * </p>
     * @return {@code true} if the iterator successfully moved to the next point;<br>
     *         {@code false} if the end of the line has been reached.
     */
    public boolean next() {
        if (!hasStarted) {
            // On the first call, mark the iteration as started but do not move.
            hasStarted = true;
            return true;
        }

        // Stop the iteration if the current position equals the target.
        if ((currentX == targetX) && (currentY == targetY) && (currentZ == targetZ)) {
            return false;
        }

        // Move along the dominant axis and adjust minor axes as needed.
        if ((deltaX >= deltaY) && (deltaX >= deltaZ)) {
            // Dominant axis X.
            currentX += stepX;

            // Adjust Y based on accumulated error.
            accumulatedErrorXY += deltaY;
            if (accumulatedErrorXY >= deltaX) {
                currentY += stepY;
                accumulatedErrorXY -= deltaX;
            }

            // Adjust Z based on accumulated error.
            accumulatedErrorXZ += deltaZ;
            if (accumulatedErrorXZ >= deltaX) {
                currentZ += stepZ;
                accumulatedErrorXZ -= deltaX;
            }
        } else if ((deltaY >= deltaX) && (deltaY >= deltaZ)) {
            // Dominant axis Y.
            currentY += stepY;

            // Adjust X based on accumulated error.
            accumulatedErrorXY += deltaX;
            if (accumulatedErrorXY >= deltaY) {
                currentX += stepX;
                accumulatedErrorXY -= deltaY;
            }

            // Adjust Z based on accumulated error.
            accumulatedErrorXZ += deltaZ;
            if (accumulatedErrorXZ >= deltaY) {
                currentZ += stepZ;
                accumulatedErrorXZ -= deltaY;
            }
        } else {
            // Dominant axis Z.
            currentZ += stepZ;

            // Adjust X based on accumulated error.
            accumulatedErrorXY += deltaX;
            if (accumulatedErrorXY >= deltaZ) {
                currentX += stepX;
                accumulatedErrorXY -= deltaZ;
            }

            // Adjust Y based on accumulated error.
            accumulatedErrorXZ += deltaY;
            if (accumulatedErrorXZ >= deltaZ) {
                currentY += stepY;
                accumulatedErrorXZ -= deltaZ;
            }
        }

        return true; // Continue iteration.
    }

    /**
     * Returns the current X-coordinate of the iterator's position.
     * @return the current X-coordinate
     */
    public int x() {
        return currentX;
    }

    /**
     * Returns the current Y-coordinate of the iterator's position.
     * @return the current Y-coordinate
     */
    public int y() {
        return currentY;
    }

    /**
     * Returns the current Z-coordinate of the iterator's position.
     * @return the current Z-coordinate
     */
    public int z() {
        return currentZ;
    }

    @Override
    public String toString() {
        return "[" + currentX + ", " + currentY + ", " + currentZ + "]";
    }
}
