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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.network.holders.ClientHardwareInfoHolder;

/**
 * @author Mobius
 */
public class RequestHardWareInfo extends ClientPacket {
    private String macAddress;
    private int windowsPlatformId;
    private int windowsMajorVersion;
    private int windowsMinorVersion;
    private int windowsBuildNumber;
    private int directxVersion;
    private int directxRevision;
    private String cpuName;
    private int cpuSpeed;
    private int cpuCoreCount;
    private int vgaCount;
    private int vgaPcxSpeed;
    private int physMemorySlot1;
    private int physMemorySlot2;
    private int physMemorySlot3;
    private int videoMemory;
    private int vgaVersion;
    private String vgaName;
    private String vgaDriverVersion;

    @Override
    protected void readImpl() {
        macAddress = readString();
        windowsPlatformId = readInt();
        windowsMajorVersion = readInt();
        windowsMinorVersion = readInt();
        windowsBuildNumber = readInt();
        directxVersion = readInt();
        directxRevision = readInt();
        readBytes(16);
        cpuName = readString();
        cpuSpeed = readInt();
        cpuCoreCount = readByte();
        readInt();
        vgaCount = readInt();
        vgaPcxSpeed = readInt();
        physMemorySlot1 = readInt();
        physMemorySlot2 = readInt();
        physMemorySlot3 = readInt();
        readByte();
        videoMemory = readInt();
        readInt();
        vgaVersion = readShort();
        vgaName = readString();
        vgaDriverVersion = readString();
    }

    @Override
    protected void runImpl() {
        getClient()
                .setHardwareInfo(new ClientHardwareInfoHolder(
                        macAddress,
                        windowsPlatformId,
                        windowsMajorVersion,
                        windowsMinorVersion,
                        windowsBuildNumber,
                        directxVersion,
                        directxRevision,
                        cpuName,
                        cpuSpeed,
                        cpuCoreCount,
                        vgaCount,
                        vgaPcxSpeed,
                        physMemorySlot1,
                        physMemorySlot2,
                        physMemorySlot3,
                        videoMemory,
                        vgaVersion,
                        vgaName,
                        vgaDriverVersion));
    }
}
