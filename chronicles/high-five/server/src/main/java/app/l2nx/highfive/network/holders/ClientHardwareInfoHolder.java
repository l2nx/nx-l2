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
package app.l2nx.highfive.network.holders;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.variables.AccountVariables;
import lombok.Getter;

/**
 * @author Mobius
 */
public class ClientHardwareInfoHolder {
    @Getter
    private final String macAddress;

    @Getter
    private final int windowsPlatformId;

    @Getter
    private final int windowsMajorVersion;

    @Getter
    private final int windowsMinorVersion;

    @Getter
    private final int windowsBuildNumber;

    @Getter
    private final int directxVersion;

    @Getter
    private final int directxRevision;

    @Getter
    private final String cpuName;

    @Getter
    private final int cpuSpeed;

    @Getter
    private final int cpuCoreCount;

    @Getter
    private final int vgaCount;

    @Getter
    private final int vgaPcxSpeed;

    @Getter
    private final int physMemorySlot1;

    @Getter
    private final int physMemorySlot2;

    @Getter
    private final int physMemorySlot3;

    @Getter
    private final int videoMemory;

    @Getter
    private final int vgaVersion;

    @Getter
    private final String vgaName;

    @Getter
    private final String vgaDriverVersion;

    public ClientHardwareInfoHolder(
            String macAddress,
            int windowsPlatformId,
            int windowsMajorVersion,
            int windowsMinorVersion,
            int windowsBuildNumber,
            int directxVersion,
            int directxRevision,
            String cpuName,
            int cpuSpeed,
            int cpuCoreCount,
            int vgaCount,
            int vgaPcxSpeed,
            int physMemorySlot1,
            int physMemorySlot2,
            int physMemorySlot3,
            int videoMemory,
            int vgaVersion,
            String vgaName,
            String vgaDriverVersion) {
        this.macAddress = macAddress;
        this.windowsPlatformId = windowsPlatformId;
        this.windowsMajorVersion = windowsMajorVersion;
        this.windowsMinorVersion = windowsMinorVersion;
        this.windowsBuildNumber = windowsBuildNumber;
        this.directxVersion = directxVersion;
        this.directxRevision = directxRevision;
        this.cpuName = cpuName;
        this.cpuSpeed = cpuSpeed;
        this.cpuCoreCount = cpuCoreCount;
        this.vgaCount = vgaCount;
        this.vgaPcxSpeed = vgaPcxSpeed;
        this.physMemorySlot1 = physMemorySlot1;
        this.physMemorySlot2 = physMemorySlot2;
        this.physMemorySlot3 = physMemorySlot3;
        this.videoMemory = videoMemory;
        this.vgaVersion = vgaVersion;
        this.vgaName = vgaName;
        this.vgaDriverVersion = vgaDriverVersion;
    }

    public ClientHardwareInfoHolder(String info) {
        final String[] split = info.split(AccountVariables.HWIDSLIT_VAR);
        macAddress = split[0];
        windowsPlatformId = Integer.valueOf(split[1]);
        windowsMajorVersion = Integer.valueOf(split[2]);
        windowsMinorVersion = Integer.valueOf(split[3]);
        windowsBuildNumber = Integer.valueOf(split[4]);
        directxVersion = Integer.valueOf(split[5]);
        directxRevision = Integer.valueOf(split[6]);
        cpuName = split[7];
        cpuSpeed = Integer.valueOf(split[8]);
        cpuCoreCount = Integer.valueOf(split[9]);
        vgaCount = Integer.valueOf(split[10]);
        vgaPcxSpeed = Integer.valueOf(split[11]);
        physMemorySlot1 = Integer.valueOf(split[12]);
        physMemorySlot2 = Integer.valueOf(split[13]);
        physMemorySlot3 = Integer.valueOf(split[14]);
        videoMemory = Integer.valueOf(split[15]);
        vgaVersion = Integer.valueOf(split[16]);
        vgaName = split[17];
        vgaDriverVersion = split[18];
    }

    /**
     * Save hardware info to account variables for later use.
     * @param player The Player related with this hardware info.
     */
    public void store(Player player) {
        final StringBuilder sb = new StringBuilder();
        sb.append(macAddress);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(windowsPlatformId);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(windowsMajorVersion);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(windowsMinorVersion);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(windowsBuildNumber);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(directxVersion);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(directxRevision);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(cpuName);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(cpuSpeed);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(cpuCoreCount);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(vgaCount);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(vgaPcxSpeed);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(physMemorySlot1);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(physMemorySlot2);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(physMemorySlot3);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(videoMemory);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(vgaVersion);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(vgaName);
        sb.append(AccountVariables.HWIDSLIT_VAR);
        sb.append(vgaDriverVersion);
        player.getAccountVariables().set(AccountVariables.HWID, sb.toString());
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ClientHardwareInfoHolder)
                && macAddress.equals(((ClientHardwareInfoHolder) obj).getMacAddress());
    }
}
