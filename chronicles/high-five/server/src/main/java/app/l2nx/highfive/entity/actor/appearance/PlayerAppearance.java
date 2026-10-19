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

import app.l2nx.highfive.entity.actor.Player;
import lombok.Getter;
import lombok.Setter;

public class PlayerAppearance {
    public static final int DEFAULT_TITLE_COLOR = 0xECF9A2;

    @Getter
    @Setter
    private Player owner;

    @Getter
    private byte face;

    @Getter
    private byte hairColor;

    @Getter
    private byte hairStyle;

    @Getter
    private boolean female;

    /** The current visible name of this player, not necessarily the real one */
    @Setter
    private String visibleName;

    /** The current visible title of this player, not necessarily the real one */
    @Setter
    private String visibleTitle;

    /** The default name color is 0xFFFFFF. */
    @Getter
    private int nameColor = 0xFFFFFF;

    /** The default title color is 0xECF9A2. */
    @Getter
    private int titleColor = DEFAULT_TITLE_COLOR;

    public PlayerAppearance(byte face, byte hColor, byte hStyle, boolean isFemale) {
        this.face = face;
        hairColor = hColor;
        hairStyle = hStyle;
        female = isFemale;
    }

    /**
     * @return Returns the visibleName.
     */
    public String getVisibleName() {
        return visibleName == null ? owner.getName() : visibleName;
    }

    /**
     * @return Returns the visibleTitle.
     */
    public String getVisibleTitle() {
        return visibleTitle == null ? owner.getTitle() : visibleTitle;
    }

    /**
     * @param value
     */
    public void setFace(int value) {
        face = (byte) value;
    }

    /**
     * @param value
     */
    public void setHairColor(int value) {
        hairColor = (byte) value;
    }

    /**
     * @param value
     */
    public void setHairStyle(int value) {
        hairStyle = (byte) value;
    }

    public void setFemale() {
        female = true;
    }

    public void setMale() {
        female = false;
    }

    public void setNameColor(int nameColor) {
        if (nameColor < 0) {
            return;
        }

        this.nameColor = nameColor;
    }

    public void setNameColor(int red, int green, int blue) {
        nameColor = (red & 0xFF) + ((green & 0xFF) << 8) + ((blue & 0xFF) << 16);
    }

    public void setTitleColor(int titleColor) {
        if (titleColor < 0) {
            return;
        }

        this.titleColor = titleColor;
    }

    public void setTitleColor(int red, int green, int blue) {
        titleColor = (red & 0xFF) + ((green & 0xFF) << 8) + ((blue & 0xFF) << 16);
    }
}
