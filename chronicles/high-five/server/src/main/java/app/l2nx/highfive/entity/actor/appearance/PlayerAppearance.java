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
package app.l2nx.highfive.entity.actor.appearance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class PlayerAppearance {
    public static final int DEFAULT_TITLE_COLOR = 0xECF9A2;

    private Player owner;

    private byte face;

    private byte hairColor;

    private byte hairStyle;

    private boolean female;

    private String visibleName;

    private String visibleTitle;

    private int nameColor = 0xFFFFFF;

    private int titleColor = DEFAULT_TITLE_COLOR;

    public PlayerAppearance(byte face, byte hColor, byte hStyle, boolean isFemale) {
        this.face = face;
        hairColor = hColor;
        hairStyle = hStyle;
        female = isFemale;
    }

    public Player getOwner() {
        WorldPartition.checkOwner();
        return owner;
    }

    public void setOwner(Player owner) {
        WorldPartition.checkOwner();
        this.owner = owner;
    }

    public byte getFace() {
        WorldPartition.checkOwner();
        return face;
    }

    public byte getHairColor() {
        WorldPartition.checkOwner();
        return hairColor;
    }

    public byte getHairStyle() {
        WorldPartition.checkOwner();
        return hairStyle;
    }

    public boolean isFemale() {
        WorldPartition.checkOwner();
        return female;
    }

    public String getVisibleName() {
        WorldPartition.checkOwner();
        return visibleName == null ? owner.getName() : visibleName;
    }

    public void setVisibleName(String visibleName) {
        WorldPartition.checkOwner();
        this.visibleName = visibleName;
    }

    public String getVisibleTitle() {
        WorldPartition.checkOwner();
        return visibleTitle == null ? owner.getTitle() : visibleTitle;
    }

    public void setVisibleTitle(String visibleTitle) {
        WorldPartition.checkOwner();
        this.visibleTitle = visibleTitle;
    }

    public int getNameColor() {
        WorldPartition.checkOwner();
        return nameColor;
    }

    public int getTitleColor() {
        WorldPartition.checkOwner();
        return titleColor;
    }

    public void setFace(int value) {
        WorldPartition.checkOwner();
        face = (byte) value;
    }

    public void setHairColor(int value) {
        WorldPartition.checkOwner();
        hairColor = (byte) value;
    }

    public void setHairStyle(int value) {
        WorldPartition.checkOwner();
        hairStyle = (byte) value;
    }

    public void setFemale() {
        WorldPartition.checkOwner();
        female = true;
    }

    public void setMale() {
        WorldPartition.checkOwner();
        female = false;
    }

    public void setNameColor(int nameColor) {
        WorldPartition.checkOwner();
        if (nameColor < 0) {
            return;
        }

        this.nameColor = nameColor;
    }

    public void setNameColor(int red, int green, int blue) {
        WorldPartition.checkOwner();
        nameColor = (red & 0xFF) + ((green & 0xFF) << 8) + ((blue & 0xFF) << 16);
    }

    public void setTitleColor(int titleColor) {
        WorldPartition.checkOwner();
        if (titleColor < 0) {
            return;
        }

        this.titleColor = titleColor;
    }

    public void setTitleColor(int red, int green, int blue) {
        WorldPartition.checkOwner();
        titleColor = (red & 0xFF) + ((green & 0xFF) << 8) + ((blue & 0xFF) << 16);
    }
}
