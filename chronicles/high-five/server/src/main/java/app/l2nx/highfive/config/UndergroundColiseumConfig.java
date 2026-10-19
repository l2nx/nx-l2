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
package app.l2nx.highfive.config;

import app.l2nx.highfive.schedule.RecurrenceSchedule;

/**
 * This class loads all the Underground Coliseum related configurations.
 * @author Mobius
 */
public class UndergroundColiseumConfig
{
	// File
	private static final String UNDERGROUND_COLISEUM_CONFIG_FILE = "./config/UndergroundColiseum.ini";
	
	// Constants
	public static RecurrenceSchedule UC_START_SCHEDULE;
	public static int UC_TIME_PERIOD;
	public static boolean UC_ALLOW_ANNOUNCE;
	public static int UC_PARTY_SIZE;
	public static int UC_RESS_TIME;
	
	public static void load()
	{
		final ConfigReader config = new ConfigReader(UNDERGROUND_COLISEUM_CONFIG_FILE);
		UC_START_SCHEDULE = new RecurrenceSchedule(config.getString("BattleStartRrule", "FREQ=DAILY;BYHOUR=17;BYMINUTE=0"));
		UC_TIME_PERIOD = config.getInt("BattlePeriod", 2);
		UC_ALLOW_ANNOUNCE = config.getBoolean("AllowAnnouncements", false);
		UC_PARTY_SIZE = config.getInt("PartySize", 7);
		UC_RESS_TIME = config.getInt("ResurrectionTime", 10);
	}
}
